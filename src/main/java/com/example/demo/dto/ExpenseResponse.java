package com.example.demo.dto;

import com.example.demo.model.SplitType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record ExpenseResponse(
    UUID id,
    UUID groupId,
    String title,
    String description,
    BigDecimal amount,
    String currency,
    UserSummaryDto paidBy,
    UserSummaryDto createdBy,
    int participantCount,
    SplitType splitType,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<ExpenseSplitDto> splits
) {}
