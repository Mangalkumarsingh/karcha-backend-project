package com.example.demo.services;

import com.example.demo.dto.GroupBalancesResponse;
import com.example.demo.dto.MemberBalanceDto;
import com.example.demo.dto.SuggestedTransferDto;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final SettlementRepository settlementRepository;

    @Transactional(readOnly = true)
    public GroupBalancesResponse calculateGroupBalances(UUID groupId, UUID callerUserId) {
        ExpenseGroup group = groupRepository.findByIdAndArchivedAtIsNull(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        // Check if caller is active member
        boolean isMember = groupMemberRepository.existsByGroup_IdAndUser_IdAndStatus(
                groupId, callerUserId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not an active member of this group");
        }

        return computeGroupBalances(group);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateUserNetBalance(UUID groupId, UUID userId) {
        BigDecimal totalPaid = expenseRepository.sumAmountByGroupIdAndPaidByUserId(groupId, userId);
        BigDecimal totalShare = expenseSplitRepository.sumShareAmountByGroupIdAndUserId(groupId, userId);
        BigDecimal settlementsSent = settlementRepository.sumAmountByGroupIdAndFromUserIdAndStatus(
                groupId, userId, SettlementStatus.CONFIRMED);
        BigDecimal settlementsReceived = settlementRepository.sumAmountByGroupIdAndToUserIdAndStatus(
                groupId, userId, SettlementStatus.CONFIRMED);

        return totalPaid.subtract(totalShare)
                .add(settlementsSent)
                .subtract(settlementsReceived)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private GroupBalancesResponse computeGroupBalances(ExpenseGroup group) {
        UUID groupId = group.getId();
        List<GroupMember> activeMembers = groupMemberRepository.findAllByGroup_IdAndStatus(
                groupId, MemberStatus.ACTIVE
        );

        // Fetch all active expenses
        List<Expense> expenses = expenseRepository.findAllByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(groupId);
        List<ExpenseSplit> splits = expenseSplitRepository.findAllActiveByGroupId(groupId);
        List<Settlement> confirmedSettlements = settlementRepository.findAllByGroup_IdAndStatus(
                groupId, SettlementStatus.CONFIRMED
        );

        BigDecimal totalExpenses = BigDecimal.ZERO;
        Map<UUID, BigDecimal> totalPaidMap = new HashMap<>();
        Map<UUID, BigDecimal> totalShareMap = new HashMap<>();
        Map<UUID, BigDecimal> settlementsSentMap = new HashMap<>();
        Map<UUID, BigDecimal> settlementsReceivedMap = new HashMap<>();
        Map<UUID, String> memberNameMap = new HashMap<>();

        for (GroupMember member : activeMembers) {
            UUID uid = member.getUser().getId();
            totalPaidMap.put(uid, BigDecimal.ZERO);
            totalShareMap.put(uid, BigDecimal.ZERO);
            settlementsSentMap.put(uid, BigDecimal.ZERO);
            settlementsReceivedMap.put(uid, BigDecimal.ZERO);

            String name = member.getUser().getName();
            if (name == null || name.isBlank()) {
                name = member.getUser().getPhoneNumber() != null ? member.getUser().getPhoneNumber() : member.getUser().getEmail();
            }
            memberNameMap.put(uid, name);
        }

        for (Expense expense : expenses) {
            totalExpenses = totalExpenses.add(expense.getAmount());
            UUID payerId = expense.getPaidBy() != null ? expense.getPaidBy().getId() : expense.getUser().getId();
            totalPaidMap.put(payerId, totalPaidMap.getOrDefault(payerId, BigDecimal.ZERO).add(expense.getAmount()));
        }

        for (ExpenseSplit split : splits) {
            UUID uid = split.getUser().getId();
            totalShareMap.put(uid, totalShareMap.getOrDefault(uid, BigDecimal.ZERO).add(split.getShareAmount()));
        }

        for (Settlement settlement : confirmedSettlements) {
            UUID fromId = settlement.getFromUser().getId();
            UUID toId = settlement.getToUser().getId();
            settlementsSentMap.put(fromId, settlementsSentMap.getOrDefault(fromId, BigDecimal.ZERO).add(settlement.getAmount()));
            settlementsReceivedMap.put(toId, settlementsReceivedMap.getOrDefault(toId, BigDecimal.ZERO).add(settlement.getAmount()));
        }

        List<MemberBalanceDto> memberBalances = new ArrayList<>();
        List<ParticipantBalance> debtors = new ArrayList<>();
        List<ParticipantBalance> creditors = new ArrayList<>();

        for (GroupMember member : activeMembers) {
            UUID uid = member.getUser().getId();
            BigDecimal paid = totalPaidMap.getOrDefault(uid, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = totalShareMap.getOrDefault(uid, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sent = settlementsSentMap.getOrDefault(uid, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal rec = settlementsReceivedMap.getOrDefault(uid, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            BigDecimal netBalance = paid.subtract(share).add(sent).subtract(rec).setScale(2, RoundingMode.HALF_UP);
            String name = memberNameMap.getOrDefault(uid, "Unknown");

            memberBalances.add(MemberBalanceDto.builder()
                    .userId(uid)
                    .name(name)
                    .totalPaid(paid)
                    .totalShare(share)
                    .settlementsSent(sent)
                    .settlementsReceived(rec)
                    .netBalance(netBalance)
                    .build());

            if (netBalance.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new ParticipantBalance(uid, name, netBalance.abs()));
            } else if (netBalance.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new ParticipantBalance(uid, name, netBalance));
            }
        }

        // Suggested transfers calculation (Greedy Debt Simplification)
        List<SuggestedTransferDto> suggestedTransfers = new ArrayList<>();
        debtors.sort((a, b) -> b.amount.compareTo(a.amount));
        creditors.sort((a, b) -> b.amount.compareTo(a.amount));

        int dIdx = 0;
        int cIdx = 0;
        while (dIdx < debtors.size() && cIdx < creditors.size()) {
            ParticipantBalance debtor = debtors.get(dIdx);
            ParticipantBalance creditor = creditors.get(cIdx);

            BigDecimal transferAmount = debtor.amount.min(creditor.amount);
            if (transferAmount.compareTo(new BigDecimal("0.009")) > 0) {
                suggestedTransfers.add(SuggestedTransferDto.builder()
                        .fromUserId(debtor.userId)
                        .fromUserName(debtor.userName)
                        .toUserId(creditor.userId)
                        .toUserName(creditor.userName)
                        .amount(transferAmount.setScale(2, RoundingMode.HALF_UP))
                        .build());
            }

            debtor.amount = debtor.amount.subtract(transferAmount);
            creditor.amount = creditor.amount.subtract(transferAmount);

            if (debtor.amount.compareTo(new BigDecimal("0.009")) <= 0) {
                dIdx++;
            }
            if (creditor.amount.compareTo(new BigDecimal("0.009")) <= 0) {
                cIdx++;
            }
        }

        return GroupBalancesResponse.builder()
                .groupId(groupId)
                .currency(group.getCurrency())
                .totalExpenses(totalExpenses.setScale(2, RoundingMode.HALF_UP))
                .memberBalances(memberBalances)
                .suggestedTransfers(suggestedTransfers)
                .build();
    }

    private static class ParticipantBalance {
        final UUID userId;
        final String userName;
        BigDecimal amount;

        ParticipantBalance(UUID userId, String userName, BigDecimal amount) {
            this.userId = userId;
            this.userName = userName;
            this.amount = amount;
        }
    }
}
