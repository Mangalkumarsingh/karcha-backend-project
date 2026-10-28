package com.example.demo.services;

import com.example.demo.dto.CreateSettlementRequest;
import com.example.demo.dto.SettlementResponse;
import com.example.demo.dto.UserSummaryDto;
import com.example.demo.model.*;
import com.example.demo.repository.ExpenseGroupRepository;
import com.example.demo.repository.GroupMemberRepository;
import com.example.demo.repository.SettlementRepository;
import com.example.demo.repository.UserRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public SettlementResponse recordSettlement(UUID groupId, CreateSettlementRequest request, UUID callerUserId) {
        ExpenseGroup group = groupRepository.findByIdAndArchivedAtIsNull(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        verifyActiveMember(groupId, callerUserId);

        if (request.fromUserId().equals(request.toUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Self transfers are not allowed");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement amount must be greater than zero");
        }

        // Verify both users are active group members
        verifyActiveMember(groupId, request.fromUserId());
        verifyActiveMember(groupId, request.toUserId());

        // Authorization: caller must be payer (fromUserId) or group ADMIN
        boolean isPayer = callerUserId.equals(request.fromUserId());
        if (!isPayer) {
            GroupMember callerMember = groupMemberRepository.findByGroup_IdAndUser_IdAndStatus(
                    groupId, callerUserId, MemberStatus.ACTIVE
            ).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active member not found"));

            if (callerMember.getRole() != GroupRole.ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the payer or a group admin can record this settlement");
            }
        }

        // Idempotency check
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<Settlement> existing = settlementRepository.findByGroup_IdAndIdempotencyKey(groupId, request.idempotencyKey().trim());
            if (existing.isPresent()) {
                return toSettlementResponse(existing.get());
            }
        }

        AppUser fromUser = userRepository.findById(request.fromUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payer user not found"));

        AppUser toUser = userRepository.findById(request.toUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient user not found"));

        PaymentMethod method = request.paymentMethod() != null ? request.paymentMethod() : PaymentMethod.CASH;

        Settlement settlement = Settlement.builder()
                .group(group)
                .fromUser(fromUser)
                .toUser(toUser)
                .amount(request.amount().setScale(2, RoundingMode.HALF_UP))
                .currency(group.getCurrency())
                .paymentMethod(method)
                .status(SettlementStatus.PENDING)
                .note(request.note() != null ? request.note().trim() : null)
                .idempotencyKey(request.idempotencyKey() != null ? request.idempotencyKey().trim() : null)
                .build();

        settlement = settlementRepository.save(settlement);

        return toSettlementResponse(settlement);
    }

    @Transactional(readOnly = true)
    public Page<SettlementResponse> getGroupSettlements(UUID groupId, Pageable pageable, UUID callerUserId) {
        verifyActiveMember(groupId, callerUserId);

        Page<Settlement> settlements = settlementRepository.findAllByGroup_IdOrderByCreatedAtDesc(groupId, pageable);
        return settlements.map(this::toSettlementResponse);
    }

    @Transactional
    public SettlementResponse confirmSettlement(UUID groupId, UUID settlementId, UUID callerUserId) {
        verifyActiveMember(groupId, callerUserId);

        Settlement settlement = settlementRepository.findByIdAndGroup_Id(settlementId, groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement not found in this group"));

        if (settlement.getStatus() == SettlementStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement is already confirmed");
        }
        if (settlement.getStatus() == SettlementStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot confirm a rejected settlement");
        }

        // Recipient (toUser) or group ADMIN can confirm
        boolean isRecipient = callerUserId.equals(settlement.getToUser().getId());
        if (!isRecipient) {
            GroupMember callerMember = groupMemberRepository.findByGroup_IdAndUser_IdAndStatus(
                    groupId, callerUserId, MemberStatus.ACTIVE
            ).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active member not found"));

            if (callerMember.getRole() != GroupRole.ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient or a group admin can confirm this settlement");
            }
        }

        settlement.setStatus(SettlementStatus.CONFIRMED);
        settlement.setConfirmedAt(LocalDateTime.now());
        settlement = settlementRepository.save(settlement);

        return toSettlementResponse(settlement);
    }

    @Transactional
    public SettlementResponse rejectSettlement(UUID groupId, UUID settlementId, UUID callerUserId) {
        verifyActiveMember(groupId, callerUserId);

        Settlement settlement = settlementRepository.findByIdAndGroup_Id(settlementId, groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement not found in this group"));

        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement is no longer pending");
        }

        boolean isRecipient = callerUserId.equals(settlement.getToUser().getId());
        if (!isRecipient) {
            GroupMember callerMember = groupMemberRepository.findByGroup_IdAndUser_IdAndStatus(
                    groupId, callerUserId, MemberStatus.ACTIVE
            ).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active member not found"));

            if (callerMember.getRole() != GroupRole.ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient or a group admin can reject this settlement");
            }
        }

        settlement.setStatus(SettlementStatus.REJECTED);
        settlement = settlementRepository.save(settlement);

        return toSettlementResponse(settlement);
    }

    private void verifyActiveMember(UUID groupId, UUID userId) {
        boolean isMember = groupMemberRepository.existsByGroup_IdAndUser_IdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User " + userId + " is not an active member of this group");
        }
    }

    private SettlementResponse toSettlementResponse(Settlement settlement) {
        return SettlementResponse.builder()
                .id(settlement.getId())
                .groupId(settlement.getGroup().getId())
                .fromUser(toUserSummary(settlement.getFromUser()))
                .toUser(toUserSummary(settlement.getToUser()))
                .amount(settlement.getAmount())
                .currency(settlement.getCurrency())
                .paymentMethod(settlement.getPaymentMethod())
                .status(settlement.getStatus())
                .note(settlement.getNote())
                .createdAt(settlement.getCreatedAt())
                .confirmedAt(settlement.getConfirmedAt())
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
