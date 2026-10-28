package com.example.demo.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ExpenseSplitDto(
    UUID id,
    UUID userId,
    String userName,
    BigDecimal shareAmount
) {}
