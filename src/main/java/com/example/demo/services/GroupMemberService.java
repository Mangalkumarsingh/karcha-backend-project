package com.example.demo.services;

import com.example.demo.dto.GroupMemberDto;
import com.example.demo.dto.InvitationResponse;
import com.example.demo.dto.InviteMemberRequest;
import com.example.demo.dto.UserSummaryDto;
import com.example.demo.model.*;
import com.example.demo.repository.ExpenseGroupRepository;
import com.example.demo.repository.GroupInvitationRepository;
import com.example.demo.repository.GroupMemberRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;

    @Transactional
    public InvitationResponse inviteMember(UUID groupId, InviteMemberRequest request, UUID inviterUserId) {
        ExpenseGroup group = groupRepository.findByIdAndArchivedAtIsNull(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        // Caller must be an active member of the group
        AppUser inviter = userRepository.findById(inviterUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean isMember = groupMemberRepository.existsByGroup_IdAndUser_IdAndStatus(
                groupId, inviterUserId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You must be an active member to invite others");
        }

        String normalizedPhone = PhoneUtil.normalizePhoneNumber(request.phoneNumber());

        // Check if user is already an active member
        boolean alreadyActiveMember = groupMemberRepository.existsByGroup_IdAndUser_PhoneNumberAndStatus(
                groupId, normalizedPhone, MemberStatus.ACTIVE
        );
        if (alreadyActiveMember) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already an active member of this group");
        }

        // Check if there's already a pending, unexpired invitation
        LocalDateTime now = LocalDateTime.now();
        boolean invitationExists = invitationRepository.existsByGroup_IdAndInvitedPhoneNumberAndStatusAndExpiresAtAfter(
                groupId, normalizedPhone, InvitationStatus.PENDING, now
        );
        if (invitationExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending invitation already exists for this phone number");
        }

        // Check if registered user exists for this phone
        AppUser invitedUser = userRepository.findByPhoneNumber(normalizedPhone).orElse(null);

        GroupInvitation invitation = GroupInvitation.builder()
                .group(group)
                .invitedPhoneNumber(normalizedPhone)
                .invitedUser(invitedUser)
                .invitedBy(inviter)
                .status(InvitationStatus.PENDING)
                .expiresAt(now.plusDays(7))
                .build();

        invitation = invitationRepository.save(invitation);

        return toInvitationResponse(invitation);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> getMyPendingInvitations(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        LocalDateTime now = LocalDateTime.now();
        Set<GroupInvitation> pendingInvitations = new LinkedHashSet<>();

        pendingInvitations.addAll(invitationRepository.findAllByInvitedUser_IdAndStatusAndExpiresAtAfter(
                userId, InvitationStatus.PENDING, now
        ));

        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
            pendingInvitations.addAll(invitationRepository.findAllByInvitedPhoneNumberAndStatusAndExpiresAtAfter(
                    user.getPhoneNumber(), InvitationStatus.PENDING, now
            ));
        }

        return pendingInvitations.stream()
                .map(this::toInvitationResponse)
                .toList();
    }

    @Transactional
    public void acceptInvitation(UUID invitationId, UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        GroupInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is no longer pending");
        }

        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation has expired");
        }

        // Verify that this invitation was meant for the authenticated user
        boolean matchesUser = (invitation.getInvitedUser() != null && invitation.getInvitedUser().getId().equals(userId))
                || (user.getPhoneNumber() != null && user.getPhoneNumber().equals(invitation.getInvitedPhoneNumber()));

        if (!matchesUser) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to accept this invitation");
        }

        ExpenseGroup group = invitation.getGroup();
        if (group.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This group has been archived");
        }

        // Check if member record already exists
        Optional<GroupMember> existingMemberOpt = groupMemberRepository.findByGroup_IdAndUser_Id(group.getId(), userId);
        if (existingMemberOpt.isPresent()) {
            GroupMember existing = existingMemberOpt.get();
            if (existing.getStatus() == MemberStatus.ACTIVE) {
                invitation.setStatus(InvitationStatus.ACCEPTED);
                invitationRepository.save(invitation);
                return;
            }
            existing.setStatus(MemberStatus.ACTIVE);
            existing.setRole(GroupRole.MEMBER);
            groupMemberRepository.save(existing);
        } else {
            GroupMember newMember = GroupMember.builder()
                    .group(group)
                    .user(user)
                    .role(GroupRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .build();
            groupMemberRepository.save(newMember);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setInvitedUser(user);
        invitationRepository.save(invitation);
    }

    @Transactional
    public void rejectInvitation(UUID invitationId, UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        GroupInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is no longer pending");
        }

        boolean matchesUser = (invitation.getInvitedUser() != null && invitation.getInvitedUser().getId().equals(userId))
                || (user.getPhoneNumber() != null && user.getPhoneNumber().equals(invitation.getInvitedPhoneNumber()));

        if (!matchesUser) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to reject this invitation");
        }

        invitation.setStatus(InvitationStatus.REJECTED);
        invitationRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberDto> getGroupMembers(UUID groupId, UUID userId) {
        boolean isMember = groupMemberRepository.existsByGroup_IdAndUser_IdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not an active member of this group");
        }

        return groupMemberRepository.findAllByGroup_IdAndStatus(groupId, MemberStatus.ACTIVE).stream()
                .map(this::toGroupMemberDto)
                .toList();
    }

    @Transactional
    public void removeMember(UUID groupId, UUID targetUserId, UUID callerUserId) {
        GroupMember targetMember = groupMemberRepository.findByGroup_IdAndUser_IdAndStatus(
                groupId, targetUserId, MemberStatus.ACTIVE
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active member not found in group"));

        boolean isSelf = callerUserId.equals(targetUserId);

        if (!isSelf) {
            // Must be admin to remove another member
            GroupMember callerMember = groupMemberRepository.findByGroup_IdAndUser_IdAndStatus(
                    groupId, callerUserId, MemberStatus.ACTIVE
            ).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not an active member of this group"));

            if (callerMember.getRole() != GroupRole.ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can remove other members");
            }
        }

        // Check if removing the last admin
        if (targetMember.getRole() == GroupRole.ADMIN) {
            long activeAdminCount = groupMemberRepository.countByGroup_IdAndRoleAndStatus(
                    groupId, GroupRole.ADMIN, MemberStatus.ACTIVE
            );
            if (activeAdminCount <= 1) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot remove or leave as the last admin. Promote another member to admin first."
                );
            }
        }

        // Check unsettled balance
        BigDecimal netBalance = balanceService.calculateUserNetBalance(groupId, targetUserId);
        if (netBalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot remove or leave member with unsettled balance: " + netBalance + ". All debts must be settled first."
            );
        }

        targetMember.setStatus(isSelf ? MemberStatus.LEFT : MemberStatus.REMOVED);
        groupMemberRepository.save(targetMember);
    }

    private InvitationResponse toInvitationResponse(GroupInvitation invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .groupId(invitation.getGroup().getId())
                .groupName(invitation.getGroup().getName())
                .invitedPhoneNumber(invitation.getInvitedPhoneNumber())
                .invitedBy(toUserSummary(invitation.getInvitedBy()))
                .status(invitation.getStatus())
                .createdAt(invitation.getCreatedAt())
                .expiresAt(invitation.getExpiresAt())
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
