package com.example.demo.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ExpenseRequest(

    @NotBlank
    @Size(max = 100)
    String title,

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    BigDecimal amount

) {}
