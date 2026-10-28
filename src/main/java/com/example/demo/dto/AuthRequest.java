package com.example.demo.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    @Pattern(
        regexp = "^\\+[1-9][0-9]{7,14}$",
        message = "Phone number E.164 format mein hona chahiye"
    )
    private String phoneNumber;

    private String googleToken;
}