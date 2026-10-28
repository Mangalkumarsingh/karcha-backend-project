package com.example.demo.dto;

import com.example.demo.model.SplitType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateSharedExpenseRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    String title,

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Amount cannot have more than 2 decimal places")
    BigDecimal amount,

    @NotNull(message = "Payer user ID is required")
    UUID paidByUserId,

    SplitType splitType,

    @NotEmpty(message = "At least one participant is required")
    List<UUID> participantIds
) {}
