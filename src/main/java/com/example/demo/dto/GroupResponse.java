package com.example.demo.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record GroupResponse(
    UUID id,
    String name,
    String description,
    String currency,
    long memberCount,
    BigDecimal totalExpenses,
    BigDecimal currentUserNetBalance,
    LocalDateTime createdAt
) {}
