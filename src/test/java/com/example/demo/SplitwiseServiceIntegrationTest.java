package com.example.demo;

import com.example.demo.dto.*;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SplitwiseServiceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseGroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private ExpenseGroupService expenseGroupService;

    @Autowired
    private GroupMemberService groupMemberService;

    @Autowired
    private SharedExpenseService sharedExpenseService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private SettlementService settlementService;

    private AppUser userA;
    private AppUser userB;
    private AppUser userC;

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
        expenseSplitRepository.deleteAll();
        expenseRepository.deleteAll();
        groupInvitationRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(AppUser.builder()
                .name("Alice")
                .phoneNumber("+919876543210")
                .email("alice@example.com")
                .phoneVerified(true)
                .build());

        userB = userRepository.save(AppUser.builder()
                .name("Bob")
                .phoneNumber("+919876543211")
                .email("bob@example.com")
                .phoneVerified(true)
                .build());

        userC = userRepository.save(AppUser.builder()
                .name("Charlie")
                .phoneNumber("+919876543212")
                .email("charlie@example.com")
                .phoneVerified(true)
                .build());
    }

    @Test
    @DisplayName("Complete Splitwise End-to-End Flow: Group, Invite, Expense, Balances, Settlement, Removal")
    void testCompleteSplitwiseFlow() {
        // 1. Group Creation: Alice creates "Goa Trip"
        CreateGroupRequest groupReq = new CreateGroupRequest("Goa Trip", "Friends trip", "INR");
        GroupResponse groupResp = expenseGroupService.createGroup(groupReq, userA.getId());

        assertNotNull(groupResp.id());
        assertEquals("Goa Trip", groupResp.name());
        assertEquals(1, groupResp.memberCount());

        UUID groupId = groupResp.id();

        // Check Alice is Admin
        GroupMember aliceMember = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, userA.getId()).orElseThrow();
        assertEquals(GroupRole.ADMIN, aliceMember.getRole());
        assertEquals(MemberStatus.ACTIVE, aliceMember.getStatus());

        // 2. Invitation: Alice invites Bob
        InviteMemberRequest inviteReq = new InviteMemberRequest("+919876543211");
        InvitationResponse inviteResp = groupMemberService.inviteMember(groupId, inviteReq, userA.getId());
        assertNotNull(inviteResp.id());
        assertEquals(InvitationStatus.PENDING, inviteResp.status());

        // 3. Duplicate Invitation Prevention
        assertThrows(ResponseStatusException.class, () ->
                groupMemberService.inviteMember(groupId, inviteReq, userA.getId())
        );

        // 4. Bob accepts invitation
        List<InvitationResponse> bobInvites = groupMemberService.getMyPendingInvitations(userB.getId());
        assertEquals(1, bobInvites.size());
        assertEquals(inviteResp.id(), bobInvites.get(0).id());

        groupMemberService.acceptInvitation(inviteResp.id(), userB.getId());

        // Verify Bob is now active MEMBER
        GroupMember bobMember = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, userB.getId()).orElseThrow();
        assertEquals(GroupRole.MEMBER, bobMember.getRole());
        assertEquals(MemberStatus.ACTIVE, bobMember.getStatus());

        // 5. Unauthorized group access prevention: Charlie (not a member) tries to view group
        assertThrows(ResponseStatusException.class, () ->
                expenseGroupService.getGroupDetails(groupId, userC.getId())
        );

        // 6. Expense Creation: Alice pays ₹4000 split equally between Alice and Bob
        CreateSharedExpenseRequest expReq = new CreateSharedExpenseRequest(
                "Hotel Booking",
                "Hotel in North Goa",
                new BigDecimal("4000.00"),
                userA.getId(),
                SplitType.EQUAL,
                List.of(userA.getId(), userB.getId())
        );
        ExpenseResponse expResp = sharedExpenseService.createExpense(groupId, expReq, userA.getId());
        assertNotNull(expResp.id());
        assertEquals(new BigDecimal("4000.00"), expResp.amount());
        assertEquals(2, expResp.splits().size());
        assertEquals(new BigDecimal("2000.00"), expResp.splits().get(0).shareAmount());
        assertEquals(new BigDecimal("2000.00"), expResp.splits().get(1).shareAmount());

        // 7. Balances check after first expense:
        // Alice paid 4000, share 2000 -> net +2000
        // Bob paid 0, share 2000 -> net -2000
        GroupBalancesResponse balances1 = balanceService.calculateGroupBalances(groupId, userA.getId());
        assertEquals(new BigDecimal("4000.00"), balances1.totalExpenses());

        MemberBalanceDto aBal1 = balances1.memberBalances().stream()
                .filter(b -> b.userId().equals(userA.getId())).findFirst().orElseThrow();
        MemberBalanceDto bBal1 = balances1.memberBalances().stream()
                .filter(b -> b.userId().equals(userB.getId())).findFirst().orElseThrow();

        assertEquals(new BigDecimal("2000.00"), aBal1.netBalance());
        assertEquals(new BigDecimal("-2000.00"), bBal1.netBalance());

        assertEquals(1, balances1.suggestedTransfers().size());
        assertEquals(userB.getId(), balances1.suggestedTransfers().get(0).fromUserId());
        assertEquals(userA.getId(), balances1.suggestedTransfers().get(0).toUserId());
        assertEquals(new BigDecimal("2000.00"), balances1.suggestedTransfers().get(0).amount());

        // 8. Multiple payers: Bob pays ₹1000 for dinner split between Alice and Bob
        CreateSharedExpenseRequest dinnerReq = new CreateSharedExpenseRequest(
                "Beach Dinner",
                "Dinner at Curlies",
                new BigDecimal("1000.00"),
                userB.getId(),
                SplitType.EQUAL,
                List.of(userA.getId(), userB.getId())
        );
        sharedExpenseService.createExpense(groupId, dinnerReq, userB.getId());

        // Updated balances:
        // Alice: paid 4000, share 2000 + 500 = 2500 -> net +1500
        // Bob: paid 1000, share 2000 + 500 = 2500 -> net -1500
        GroupBalancesResponse balances2 = balanceService.calculateGroupBalances(groupId, userB.getId());
        assertEquals(new BigDecimal("5000.00"), balances2.totalExpenses());

        MemberBalanceDto aBal2 = balances2.memberBalances().stream()
                .filter(b -> b.userId().equals(userA.getId())).findFirst().orElseThrow();
        MemberBalanceDto bBal2 = balances2.memberBalances().stream()
                .filter(b -> b.userId().equals(userB.getId())).findFirst().orElseThrow();

        assertEquals(new BigDecimal("1500.00"), aBal2.netBalance());
        assertEquals(new BigDecimal("-1500.00"), bBal2.netBalance());

        assertEquals(1, balances2.suggestedTransfers().size());
        assertEquals(new BigDecimal("1500.00"), balances2.suggestedTransfers().get(0).amount());

        // 9. Partial Settlement: Bob pays ₹500 to Alice
        CreateSettlementRequest setReq = new CreateSettlementRequest(
                userB.getId(),
                userA.getId(),
                new BigDecimal("500.00"),
                PaymentMethod.UPI,
                "Partial settlement via UPI",
                "idem-key-1"
        );
        SettlementResponse setResp = settlementService.recordSettlement(groupId, setReq, userB.getId());
        assertNotNull(setResp.id());
        assertEquals(SettlementStatus.PENDING, setResp.status());

        // Duplicate settlement prevention with same idempotency key
        SettlementResponse duplicateResp = settlementService.recordSettlement(groupId, setReq, userB.getId());
        assertEquals(setResp.id(), duplicateResp.id());

        // Unconfirmed settlement does not affect balances
        BigDecimal netBobBeforeConfirm = balanceService.calculateUserNetBalance(groupId, userB.getId());
        assertEquals(new BigDecimal("-1500.00"), netBobBeforeConfirm);

        // 10. Recipient Alice confirms the settlement
        SettlementResponse confirmedSetResp = settlementService.confirmSettlement(groupId, setResp.id(), userA.getId());
        assertEquals(SettlementStatus.CONFIRMED, confirmedSetResp.status());
        assertNotNull(confirmedSetResp.confirmedAt());

        // Cannot confirm twice
        assertThrows(ResponseStatusException.class, () ->
                settlementService.confirmSettlement(groupId, setResp.id(), userA.getId())
        );

        // Balances after partial settlement confirmation:
        // Alice: net +1000
        // Bob: net -1000
        GroupBalancesResponse balances3 = balanceService.calculateGroupBalances(groupId, userA.getId());
        MemberBalanceDto aBal3 = balances3.memberBalances().stream()
                .filter(b -> b.userId().equals(userA.getId())).findFirst().orElseThrow();
        MemberBalanceDto bBal3 = balances3.memberBalances().stream()
                .filter(b -> b.userId().equals(userB.getId())).findFirst().orElseThrow();

        assertEquals(new BigDecimal("1000.00"), aBal3.netBalance());
        assertEquals(new BigDecimal("-1000.00"), bBal3.netBalance());

        // 11. Member removal protection: Bob tries to leave with unsettled balance -> Conflict
        assertThrows(ResponseStatusException.class, () ->
                groupMemberService.removeMember(groupId, userB.getId(), userB.getId())
        );

        // 12. Settle remaining ₹1000
        CreateSettlementRequest finalSetReq = new CreateSettlementRequest(
                userB.getId(),
                userA.getId(),
                new BigDecimal("1000.00"),
                PaymentMethod.CASH,
                "Final cash settlement",
                "idem-key-2"
        );
        SettlementResponse finalSetResp = settlementService.recordSettlement(groupId, finalSetReq, userB.getId());
        settlementService.confirmSettlement(groupId, finalSetResp.id(), userA.getId());

        // Balances are now 0.00
        GroupBalancesResponse balancesFinal = balanceService.calculateGroupBalances(groupId, userA.getId());
        MemberBalanceDto aBalFinal = balancesFinal.memberBalances().stream()
                .filter(b -> b.userId().equals(userA.getId())).findFirst().orElseThrow();
        MemberBalanceDto bBalFinal = balancesFinal.memberBalances().stream()
                .filter(b -> b.userId().equals(userB.getId())).findFirst().orElseThrow();

        assertEquals(new BigDecimal("0.00"), aBalFinal.netBalance());
        assertEquals(new BigDecimal("0.00"), bBalFinal.netBalance());
        assertTrue(balancesFinal.suggestedTransfers().isEmpty());

        // 13. Bob can now leave group safely
        groupMemberService.removeMember(groupId, userB.getId(), userB.getId());
        GroupMember bobAfterLeave = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, userB.getId()).orElseThrow();
        assertEquals(MemberStatus.LEFT, bobAfterLeave.getStatus());

        // 14. Admin Alice tries to leave as the last admin -> Bad Request
        assertThrows(ResponseStatusException.class, () ->
                groupMemberService.removeMember(groupId, userA.getId(), userA.getId())
        );
    }

    @Test
    @DisplayName("Validation and permissions: negative amounts, non-member payer, self settlement")
    void testValidations() {
        CreateGroupRequest groupReq = new CreateGroupRequest("Test Group", null, "INR");
        GroupResponse groupResp = expenseGroupService.createGroup(groupReq, userA.getId());
        UUID groupId = groupResp.id();

        // 1. Negative amount expense -> Bad Request
        CreateSharedExpenseRequest negExpense = new CreateSharedExpenseRequest(
                "Invalid", null, new BigDecimal("-50.00"), userA.getId(), SplitType.EQUAL, List.of(userA.getId())
        );
        assertThrows(ResponseStatusException.class, () ->
                sharedExpenseService.createExpense(groupId, negExpense, userA.getId())
        );

        // 2. Non-member payer -> 403 or 404
        CreateSharedExpenseRequest nonMemberPayer = new CreateSharedExpenseRequest(
                "Invalid Payer", null, new BigDecimal("100.00"), userC.getId(), SplitType.EQUAL, List.of(userA.getId())
        );
        assertThrows(ResponseStatusException.class, () ->
                sharedExpenseService.createExpense(groupId, nonMemberPayer, userA.getId())
        );

        // 3. Self settlement -> Bad Request
        CreateSettlementRequest selfSet = new CreateSettlementRequest(
                userA.getId(), userA.getId(), new BigDecimal("100.00"), PaymentMethod.CASH, null, null
        );
        assertThrows(ResponseStatusException.class, () ->
                settlementService.recordSettlement(groupId, selfSet, userA.getId())
        );

        // 4. Negative settlement amount -> Bad Request
        CreateSettlementRequest negSet = new CreateSettlementRequest(
                userA.getId(), userB.getId(), new BigDecimal("-10.00"), PaymentMethod.CASH, null, null
        );
        assertThrows(ResponseStatusException.class, () ->
                settlementService.recordSettlement(groupId, negSet, userA.getId())
        );
    }
}
