package com.example.demo.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 100, message = "Name must not exceed 100 characters")
    String name,

    @Size(max = 500, message = "Profile image URL must not exceed 500 characters")
    String profileImageUrl
) {}
