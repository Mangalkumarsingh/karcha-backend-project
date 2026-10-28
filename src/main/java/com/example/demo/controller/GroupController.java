package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.security.SecurityUtil;
import com.example.demo.services.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final ExpenseGroupService expenseGroupService;
    private final GroupMemberService groupMemberService;
    private final SharedExpenseService sharedExpenseService;
    private final BalanceService balanceService;
    private final SettlementService settlementService;

    // --- Module C: Group Management ---

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseGroupService.createGroup(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> getMyGroups(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(expenseGroupService.getMyGroups(userId));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailResponse> getGroupDetails(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(expenseGroupService.getGroupDetails(groupId, userId));
    }

    @PatchMapping("/{groupId}")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(expenseGroupService.updateGroup(groupId, request, userId));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Map<String, Object>> deleteGroup(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        expenseGroupService.archiveGroup(groupId, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Group archived successfully"));
    }

    // --- Module D: Members and Invitations ---

    @PostMapping("/{groupId}/invitations")
    public ResponseEntity<InvitationResponse> inviteMember(
            @PathVariable UUID groupId,
            @Valid @RequestBody InviteMemberRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupMemberService.inviteMember(groupId, request, userId));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberDto>> getGroupMembers(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(groupMemberService.getGroupMembers(groupId, userId));
    }

    @DeleteMapping("/{groupId}/members/{memberUserId}")
    public ResponseEntity<Map<String, Object>> removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID memberUserId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID callerUserId = SecurityUtil.getUserId(jwt);
        groupMemberService.removeMember(groupId, memberUserId, callerUserId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Member removed or left group successfully"));
    }

    // --- Module E: Shared Expenses ---

    @PostMapping("/{groupId}/expenses")
    public ResponseEntity<ExpenseResponse> addExpense(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateSharedExpenseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sharedExpenseService.createExpense(groupId, request, userId));
    }

    @GetMapping("/{groupId}/expenses")
    public ResponseEntity<Page<ExpenseSummaryDto>> getGroupExpenses(
            @PathVariable UUID groupId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(sharedExpenseService.getGroupExpenses(groupId, pageable, userId));
    }

    @GetMapping("/{groupId}/expenses/{expenseId}")
    public ResponseEntity<ExpenseResponse> getExpenseDetails(
            @PathVariable UUID groupId,
            @PathVariable UUID expenseId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(sharedExpenseService.getExpenseDetails(groupId, expenseId, userId));
    }

    @PutMapping("/{groupId}/expenses/{expenseId}")
    public ResponseEntity<ExpenseResponse> updateExpense(
            @PathVariable UUID groupId,
            @PathVariable UUID expenseId,
            @Valid @RequestBody UpdateSharedExpenseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(sharedExpenseService.updateExpense(groupId, expenseId, request, userId));
    }

    @DeleteMapping("/{groupId}/expenses/{expenseId}")
    public ResponseEntity<Map<String, Object>> deleteExpense(
            @PathVariable UUID groupId,
            @PathVariable UUID expenseId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        sharedExpenseService.deleteExpense(groupId, expenseId, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Expense deleted successfully"));
    }

    // --- Module F: Group Balances ---

    @GetMapping("/{groupId}/balances")
    public ResponseEntity<GroupBalancesResponse> getGroupBalances(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(balanceService.calculateGroupBalances(groupId, userId));
    }

    // --- Module G: Settlements ---

    @PostMapping("/{groupId}/settlements")
    public ResponseEntity<SettlementResponse> recordSettlement(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateSettlementRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(settlementService.recordSettlement(groupId, request, userId));
    }

    @GetMapping("/{groupId}/settlements")
    public ResponseEntity<Page<SettlementResponse>> getGroupSettlements(
            @PathVariable UUID groupId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(settlementService.getGroupSettlements(groupId, pageable, userId));
    }

    @PostMapping("/{groupId}/settlements/{settlementId}/confirm")
    public ResponseEntity<SettlementResponse> confirmSettlement(
            @PathVariable UUID groupId,
            @PathVariable UUID settlementId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(settlementService.confirmSettlement(groupId, settlementId, userId));
    }

    @PostMapping("/{groupId}/settlements/{settlementId}/reject")
    public ResponseEntity<SettlementResponse> rejectSettlement(
            @PathVariable UUID groupId,
            @PathVariable UUID settlementId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(settlementService.rejectSettlement(groupId, settlementId, userId));
    }
}
