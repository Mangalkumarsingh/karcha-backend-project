package com.example.demo.dto;

import com.example.demo.model.GroupRole;
import com.example.demo.model.MemberStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record GroupMemberDto(
    UUID id,
    UUID userId,
    String name,
    String email,
    String phoneNumber,
    String profileImageUrl,
    GroupRole role,
    MemberStatus status,
    LocalDateTime joinedAt
) {}
