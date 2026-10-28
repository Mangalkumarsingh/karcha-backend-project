package com.example.demo.dto;

import com.example.demo.model.SplitType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ExpenseSummaryDto(
    UUID id,
    UUID groupId,
    String title,
    BigDecimal amount,
    String currency,
    UserSummaryDto paidBy,
    UserSummaryDto createdBy,
    int participantCount,
    SplitType splitType,
    LocalDateTime createdAt
) {}
