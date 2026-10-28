package com.example.demo.dto;

import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private UUID id;
    private String phoneNumber;
    private String email;
    private String loginType;
    private boolean newUser;

    private String accessToken;
    private String tokenType;
    private long expiresIn;

    private String message;
}