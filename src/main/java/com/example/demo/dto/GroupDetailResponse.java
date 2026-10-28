package com.example.demo.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record GroupDetailResponse(
    UUID id,
    String name,
    String description,
    String currency,
    UserSummaryDto createdBy,
    LocalDateTime createdAt,
    List<GroupMemberDto> members
) {}
