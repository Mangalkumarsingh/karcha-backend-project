package com.example.demo.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record GroupBalancesResponse(
    UUID groupId,
    String currency,
    BigDecimal totalExpenses,
    List<MemberBalanceDto> memberBalances,
    List<SuggestedTransferDto> suggestedTransfers
) {}
