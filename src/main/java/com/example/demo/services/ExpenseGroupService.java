package com.example.demo.services;

import com.example.demo.dto.*;
import com.example.demo.model.AppUser;
import com.example.demo.model.ExpenseGroup;
import com.example.demo.model.GroupMember;
import com.example.demo.model.GroupRole;
import com.example.demo.model.MemberStatus;
import com.example.demo.repository.ExpenseGroupRepository;
import com.example.demo.repository.ExpenseRepository;
import com.example.demo.repository.GroupMemberRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseGroupService {

    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final BalanceService balanceService;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, UUID creatorUserId) {
        AppUser creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency().trim().toUpperCase()
                : "INR";

        ExpenseGroup group = ExpenseGroup.builder()
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .currency(currency)
                .createdBy(creator)
                .build();

        group = groupRepository.save(group);

        GroupMember adminMember = GroupMember.builder()
                .group(group)
                .user(creator)
                .role(GroupRole.ADMIN)
                .status(MemberStatus.ACTIVE)
                .build();

        groupMemberRepository.save(adminMember);

        return toGroupResponse(group, 1L, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups(UUID userId) {
        List<ExpenseGroup> groups = groupRepository.findAllActiveByUserId(userId);
        List<GroupResponse> responseList = new ArrayList<>();

        for (ExpenseGroup group : groups) {
            long memberCount = groupMemberRepository.countByGroup_IdAndStatus(group.getId(), MemberStatus.ACTIVE);
            BigDecimal totalExpenses = expenseRepository.sumTotalAmountByGroupId(group.getId());
            BigDecimal userNetBalance = balanceService.calculateUserNetBalance(group.getId(), userId);

            responseList.add(toGroupResponse(group, memberCount, totalExpenses, userNetBalance));
        }

        return responseList;
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupDetails(UUID groupId, UUID userId) {
        ExpenseGroup group = groupRepository.findByIdAndArchivedAtIsNull(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        boolean isMember = groupMemberRepository.existsByGroup_IdAndUser_IdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this group");
        }

        List<GroupMember> members = groupMemberRepository.findAllByGroup_IdAndStatus(groupId, MemberStatus.ACTIVE);
        List<GroupMemberDto> memberDtos = members.stream()
                .map(this::toGroupMemberDto)
                .toList();

        return GroupDetailResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .currency(group.getCurrency())
                .createdBy(toUserSummary(group.getCreatedBy()))
                .createdAt(group.getCreatedAt())
                .members(memberDtos)
                .build();
    }

    @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, UUID userId) {
        ExpenseGroup group = groupRepository.findByIdAndArchivedAtIsNull(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        verifyAdminPermission(groupId, userId);

        if (request.name() != null && !request.name().isBlank()) {
            group.setName(request.name().trim());
        }

        if (request.description() != null) {
            group.setDescription(request.description().trim());
        }

        if (request.currency() != null && !request.currency().isBlank()) {
            String newCurrency = request.currency().trim().toUpperCase();
            if (!newCurrency.equalsIgnoreCase(group.getCurrency())) {
                long expenseCount = expenseRepository.countByGroup_IdAndDeletedAtIsNull(groupId);
                if (expenseCount > 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Cannot change group currency after expenses have been recorded"
                    );
                }
                group.setCurrency(newCurrency);
            }
        }

        group = groupRepository.save(group);

        long memberCount = groupMemberRepository.countByGroup_IdAndStatus(groupId, MemberStatus.ACTIVE);
        BigDecimal totalExpenses = expenseRepository.sumTotalAmountByGroupId(groupId);
        BigDecimal userNetBalance = balanceService.calculateUserNetBalance(groupId, userId);

        return toGroupResponse(group, memberCount, totalExpenses, userNetBalance);
    }

    @Transactional
    public void archiveGroup(UUID groupId, UUID userId) {
        ExpenseGroup group = groupRepository.findByIdAndArchivedAtIsNull(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        verifyAdminPermission(groupId, userId);

        group.setArchivedAt(LocalDateTime.now());
        groupRepository.save(group);
    }

    public void verifyAdminPermission(UUID groupId, UUID userId) {
        GroupMember member = groupMemberRepository.findByGroup_IdAndUser_IdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not an active member of this group"));

        if (member.getRole() != GroupRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin permission required for this operation");
        }
    }

    public void verifyMemberPermission(UUID groupId, UUID userId) {
        boolean isMember = groupMemberRepository.existsByGroup_IdAndUser_IdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not an active member of this group");
        }
    }

    private GroupResponse toGroupResponse(ExpenseGroup group, long memberCount, BigDecimal totalExpenses, BigDecimal userNetBalance) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .currency(group.getCurrency())
                .memberCount(memberCount)
                .totalExpenses(totalExpenses != null ? totalExpenses : BigDecimal.ZERO)
                .currentUserNetBalance(userNetBalance != null ? userNetBalance : BigDecimal.ZERO)
                .createdAt(group.getCreatedAt())
                .build();
    }

    private GroupMemberDto toGroupMemberDto(GroupMember member) {
        AppUser u = member.getUser();
        return GroupMemberDto.builder()
                .id(member.getId())
                .userId(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .profileImageUrl(u.getProfileImageUrl())
                .role(member.getRole())
                .status(member.getStatus())
                .joinedAt(member.getJoinedAt())
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
