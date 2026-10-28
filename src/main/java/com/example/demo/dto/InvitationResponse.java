package com.example.demo.dto;

import com.example.demo.model.InvitationStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record InvitationResponse(
    UUID id,
    UUID groupId,
    String groupName,
    String invitedPhoneNumber,
    UserSummaryDto invitedBy,
    InvitationStatus status,
    LocalDateTime createdAt,
    LocalDateTime expiresAt
) {}
