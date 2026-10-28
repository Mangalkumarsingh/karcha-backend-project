package com.example.demo.dto;

import com.example.demo.model.PaymentMethod;
import com.example.demo.model.SettlementStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record SettlementResponse(
    UUID id,
    UUID groupId,
    UserSummaryDto fromUser,
    UserSummaryDto toUser,
    BigDecimal amount,
    String currency,
    PaymentMethod paymentMethod,
    SettlementStatus status,
    String note,
    LocalDateTime createdAt,
    LocalDateTime confirmedAt
) {}
