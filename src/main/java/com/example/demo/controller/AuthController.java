package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.services.AuthService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/otp/request")
    public ResponseEntity<Map<String, Object>> requestOtp(
            @Valid @RequestBody OtpRequest request) {

        authService.requestOtp(request);

        return ResponseEntity.ok(
            Map.of(
                "success", true,
                "message", "If eligible, a verification code has been sent",
                "expiresInSeconds", 300,
                "resendAfterSeconds", 60
            )
        );
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request) {

        return ResponseEntity.ok(
            authService.verifyOtp(request)
        );
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(
            @RequestBody GoogleLoginRequest request) {

        return ResponseEntity.ok(
            authService.loginWithGoogle(request.googleToken())
        );
    }

    public record GoogleLoginRequest(
        @NotBlank String googleToken
    ) {}
}