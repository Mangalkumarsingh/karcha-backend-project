package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
    @NotBlank
    @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "Phone number must be in E.164 format")
    String phoneNumber,

    @NotBlank
    String otp
) {}
