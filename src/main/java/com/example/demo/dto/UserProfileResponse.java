package com.example.demo.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UserProfileResponse(
    UUID id,
    String name,
    String phoneNumber,
    String email,
    String profileImageUrl,
    LocalDateTime createdAt
) {}
