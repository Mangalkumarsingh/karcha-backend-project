package com.example.demo.dto;

import com.example.demo.model.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSettlementRequest(
    @NotNull(message = "From user ID is required")
    UUID fromUserId,

    @NotNull(message = "To user ID is required")
    UUID toUserId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Amount cannot have more than 2 decimal places")
    BigDecimal amount,

    PaymentMethod paymentMethod,

    @Size(max = 255, message = "Note cannot exceed 255 characters")
    String note,

    @Size(max = 100, message = "Idempotency key cannot exceed 100 characters")
    String idempotencyKey
) {}
