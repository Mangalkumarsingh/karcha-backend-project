package com.example.demo.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record UserSummaryDto(
    UUID id,
    String name,
    String email,
    String phoneNumber,
    String profileImageUrl
) {}
