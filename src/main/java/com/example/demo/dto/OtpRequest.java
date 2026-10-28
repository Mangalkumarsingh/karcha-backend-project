package com.example.demo.dto;

import jakarta.validation.constraints.*;

public record OtpRequest(
    @NotBlank
    @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
    String phoneNumber
) {}
