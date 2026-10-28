package com.example.demo.services;

import com.example.demo.dto.*;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SharedExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public ExpenseResponse createExpense(UUID groupId, CreateSharedExpenseRequest request, UUID callerUserId) {
        ExpenseGroup group = groupRepository.findByIdAndArchivedAtIsNull(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        verifyActiveMember(groupId, callerUserId);

        AppUser creator = userRepository.findById(callerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expense amount must be greater than zero");
        }

        // Validate payer
        AppUser payer = userRepository.findById(request.paidByUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payer not found"));
        verifyActiveMember(groupId, payer.getId());

        // Validate participants
        List<UUID> participantIds = request.participantIds();
        Set<UUID> uniqueIds = new LinkedHashSet<>(participantIds);
        if (uniqueIds.size() != participantIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate participants are not allowed");
        }

        List<AppUser> participants = new ArrayList<>();
        for (UUID pid : uniqueIds) {
            verifyActiveMember(groupId, pid);
            AppUser pUser = userRepository.findById(pid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant user not found: " + pid));
            participants.add(pUser);
        }

        SplitType splitType = request.splitType() != null ? request.splitType() : SplitType.EQUAL;

        Expense expense = Expense.builder()
                .title(request.title().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .amount(request.amount().setScale(2, RoundingMode.HALF_UP))
                .currency(group.getCurrency())
                .user(creator)
                .paidBy(payer)
                .group(group)
                .splitType(splitType)
                .build();

        expense = expenseRepository.save(expense);

        // Calculate deterministic equal split shares
        List<ExpenseSplit> splits = calculateEqualSplits(expense, participants, expense.getAmount());
        splits = expenseSplitRepository.saveAll(splits);
        expense.setSplits(splits);

        return toExpenseResponse(expense);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseSummaryDto> getGroupExpenses(UUID groupId, Pageable pageable, UUID callerUserId) {
        verifyActiveMember(groupId, callerUserId);

        Page<Expense> expensePage = expenseRepository.findAllByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(groupId, pageable);
        return expensePage.map(this::toExpenseSummary);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseDetails(UUID groupId, UUID expenseId, UUID callerUserId) {
        verifyActiveMember(groupId, callerUserId);

        Expense expense = expenseRepository.findByIdAndGroup_IdAndDeletedAtIsNull(expenseId, groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found in this group"));

        return toExpenseResponse(expense);
    }

    @Transactional
    public ExpenseResponse updateExpense(UUID groupId, UUID expenseId, UpdateSharedExpenseRequest request, UUID callerUserId) {
        verifyActiveMember(groupId, callerUserId);

        Expense expense = expenseRepository.findByIdAndGroup_IdAndDeletedAtIsNull(expenseId, groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found in this group"));

        verifyEditPermission(groupId, expense, callerUserId);

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expense amount must be greater than zero");
        }

        // Validate payer
        AppUser payer = userRepository.findById(request.paidByUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payer not found"));
        verifyActiveMember(groupId, payer.getId());

        // Validate participants
        List<UUID> participantIds = request.participantIds();
        Set<UUID> uniqueIds = new LinkedHashSet<>(participantIds);
        if (uniqueIds.size() != participantIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate participants are not allowed");
        }

        List<AppUser> participants = new ArrayList<>();
        for (UUID pid : uniqueIds) {
            verifyActiveMember(groupId, pid);
            AppUser pUser = userRepository.findById(pid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant user not found: " + pid));
            participants.add(pUser);
        }

        SplitType splitType = request.splitType() != null ? request.splitType() : SplitType.EQUAL;

        expense.setTitle(request.title().trim());
        expense.setDescription(request.description() != null ? request.description().trim() : null);
        expense.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        expense.setPaidBy(payer);
        expense.setSplitType(splitType);
        expense.setUpdatedAt(LocalDateTime.now());

        // Delete old splits and save new ones atomically
        expenseSplitRepository.deleteAllByExpense_Id(expense.getId());
        expenseSplitRepository.flush();

        List<ExpenseSplit> newSplits = calculateEqualSplits(expense, participants, expense.getAmount());
        newSplits = expenseSplitRepository.saveAll(newSplits);
        expense.setSplits(newSplits);

        expense = expenseRepository.save(expense);
        return toExpenseResponse(expense);
    }

    @Transactional
    public void deleteExpense(UUID groupId, UUID expenseId, UUID callerUserId) {
        verifyActiveMember(groupId, callerUserId);

        Expense expense = expenseRepository.findByIdAndGroup_IdAndDeletedAtIsNull(expenseId, groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found in this group"));

        verifyEditPermission(groupId, expense, callerUserId);

        expense.setDeletedAt(LocalDateTime.now());
        expenseRepository.save(expense);
    }

    private void verifyActiveMember(UUID groupId, UUID userId) {
        boolean isMember = groupMemberRepository.existsByGroup_IdAndUser_IdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User " + userId + " is not an active member of this group");
        }
    }

    private void verifyEditPermission(UUID groupId, Expense expense, UUID callerUserId) {
        // Creator or group Admin can edit/delete
        if (expense.getUser().getId().equals(callerUserId)) {
            return;
        }

        GroupMember callerMember = groupMemberRepository.findByGroup_IdAndUser_IdAndStatus(
                groupId, callerUserId, MemberStatus.ACTIVE
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not an active member of this group"));

        if (callerMember.getRole() != GroupRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the expense creator or group admins can edit or delete this expense");
        }
    }

    public List<ExpenseSplit> calculateEqualSplits(Expense expense, List<AppUser> participants, BigDecimal totalAmount) {
        int n = participants.size();
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participants list cannot be empty");
        }

        long totalMinorUnits = totalAmount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        long baseShareMinor = totalMinorUnits / n;
        long remainderMinor = totalMinorUnits % n;

        List<ExpenseSplit> splits = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long currentMinor = baseShareMinor + (i < remainderMinor ? 1 : 0);
            BigDecimal share = BigDecimal.valueOf(currentMinor)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);

            splits.add(ExpenseSplit.builder()
                    .expense(expense)
                    .user(participants.get(i))
                    .shareAmount(share)
                    .build());
        }

        return splits;
    }

    private ExpenseResponse toExpenseResponse(Expense expense) {
        List<ExpenseSplit> splits = expense.getSplits();
        if (splits == null || splits.isEmpty()) {
            splits = expenseSplitRepository.findAllByExpense_Id(expense.getId());
        }

        List<ExpenseSplitDto> splitDtos = splits.stream()
                .map(s -> ExpenseSplitDto.builder()
                        .id(s.getId())
                        .userId(s.getUser().getId())
                        .userName(s.getUser().getName() != null ? s.getUser().getName() : s.getUser().getPhoneNumber())
                        .shareAmount(s.getShareAmount())
                        .build())
                .toList();

        return ExpenseResponse.builder()
                .id(expense.getId())
                .groupId(expense.getGroup() != null ? expense.getGroup().getId() : null)
                .title(expense.getTitle())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .paidBy(toUserSummary(expense.getPaidBy()))
                .createdBy(toUserSummary(expense.getUser()))
                .participantCount(splits.size())
                .splitType(expense.getSplitType())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .splits(splitDtos)
                .build();
    }

    private ExpenseSummaryDto toExpenseSummary(Expense expense) {
        int participantCount = expense.getSplits() != null ? expense.getSplits().size() : 0;
        if (participantCount == 0) {
            participantCount = expenseSplitRepository.findAllByExpense_Id(expense.getId()).size();
        }

        return ExpenseSummaryDto.builder()
                .id(expense.getId())
                .groupId(expense.getGroup() != null ? expense.getGroup().getId() : null)
                .title(expense.getTitle())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .paidBy(toUserSummary(expense.getPaidBy()))
                .createdBy(toUserSummary(expense.getUser()))
                .participantCount(participantCount)
                .splitType(expense.getSplitType())
                .createdAt(expense.getCreatedAt())
                .build();
    }

    private UserSummaryDto toUserSummary(AppUser user) {
        if (user == null) return null;
        return UserSummaryDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
