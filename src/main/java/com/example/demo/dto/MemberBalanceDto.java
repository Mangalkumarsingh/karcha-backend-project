package com.example.demo.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record MemberBalanceDto(
    UUID userId,
    String name,
    BigDecimal totalPaid,
    BigDecimal totalShare,
    BigDecimal settlementsSent,
    BigDecimal settlementsReceived,
    BigDecimal netBalance
) {}
