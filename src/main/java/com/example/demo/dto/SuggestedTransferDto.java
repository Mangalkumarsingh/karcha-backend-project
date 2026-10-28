package com.example.demo.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record SuggestedTransferDto(
    UUID fromUserId,
    String fromUserName,
    UUID toUserId,
    String toUserName,
    BigDecimal amount
) {}
