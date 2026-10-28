package com.example.demo.services;

import com.example.demo.dto.*;
import com.example.demo.model.AppUser;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

// Google Authentication
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.security.SecureRandom;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GoogleIdTokenVerifier googleVerifier;
    private final TokenService tokenService;
    private final OtpSender otpSender;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void requestOtp(OtpRequest request) {

        String phone = request.phoneNumber();

        AppUser user = userRepository.findByPhoneNumber(phone)
            .orElseGet(() ->
                userRepository.save(
                    AppUser.builder()
                        .phoneNumber(phone)
                        .phoneVerified(false)
                        .build()
                )
            );

        Instant now = Instant.now();

        if (user.getOtpLastSentAt() != null &&
            now.isBefore(user.getOtpLastSentAt().plusSeconds(60))) {
            throw new ResponseStatusException(
                TOO_MANY_REQUESTS,
                "Please try again later"
            );
        }

        String otp = String.format(
            "%06d",
            secureRandom.nextInt(1_000_000)
        );

        user.setOtpHash(passwordEncoder.encode(otp));
        user.setOtpExpiryTime(now.plus(5, ChronoUnit.MINUTES));
        user.setOtpLastSentAt(now);
        user.setOtpAttempts(0);

        userRepository.save(user);

        // Send through your configured SMS provider.
        otpSender.send(phone, otp);
    }

    @Transactional
    public AuthResponse verifyOtp(OtpVerifyRequest request) {

        AppUser user = userRepository
            .findByPhoneNumber(request.phoneNumber())
            .orElseThrow(() ->
                new ResponseStatusException(
                    UNAUTHORIZED, "Invalid or expired OTP"
                )
            );

        if (user.getOtpHash() == null ||
            user.getOtpExpiryTime() == null ||
            Instant.now().isAfter(user.getOtpExpiryTime())) {

            throw new ResponseStatusException(
                UNAUTHORIZED, "Invalid or expired OTP"
            );
        }

        if (user.getOtpAttempts() >= 5) {
            throw new ResponseStatusException(
                TOO_MANY_REQUESTS, "Too many attempts"
            );
        }

        if (!passwordEncoder.matches(
                request.otp(), user.getOtpHash())) {

            user.setOtpAttempts(user.getOtpAttempts() + 1);
            userRepository.save(user);

            throw new ResponseStatusException(
                UNAUTHORIZED, "Invalid or expired OTP"
            );
        }

        boolean isNewUser = !user.isPhoneVerified();

        user.setPhoneVerified(true);
        user.setOtpHash(null);
        user.setOtpExpiryTime(null);
        user.setOtpAttempts(0);

        userRepository.save(user);

        return buildResponse(
            user, "PHONE", isNewUser
        );
    }

    @Transactional
    public AuthResponse loginWithGoogle(String idTokenString) {

        if (idTokenString == null || idTokenString.isBlank()) {
            throw new ResponseStatusException(
                BAD_REQUEST, "Google ID token required"
            );
        }

        GoogleIdToken googleToken;

        try {
            googleToken = googleVerifier.verify(idTokenString);
        } catch (Exception e) {
            throw new ResponseStatusException(
                UNAUTHORIZED, "Invalid Google token"
            );
        }

        if (googleToken == null) {
            throw new ResponseStatusException(
                UNAUTHORIZED, "Invalid Google token"
            );
        }

        GoogleIdToken.Payload payload = googleToken.getPayload();

        String googleSubject = payload.getSubject();
        String email = payload.getEmail();

        if (googleSubject == null ||
            email == null ||
            !Boolean.TRUE.equals(payload.getEmailVerified())) {

            throw new ResponseStatusException(
                UNAUTHORIZED, "Google account not verified"
            );
        }

        // Do not automatically link accounts by email.
        AppUser user = userRepository
            .findByGoogleSubject(googleSubject)
            .orElse(null);

        boolean isNewUser = false;

        if (user == null) {

            // Prevent automatic takeover/linking by matching email.
            if (userRepository.findByEmail(email).isPresent()) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Account linking requires verification"
                );
            }

            user = AppUser.builder()
                .email(email)
                .googleSubject(googleSubject)
                .emailVerified(true)
                .build();

            user = userRepository.save(user);
            isNewUser = true;
        }

        return buildResponse(
            user, "GOOGLE", isNewUser
        );
    }

    private AuthResponse buildResponse(
        AppUser user,
        String loginType,
        boolean isNewUser
    ) {

        String accessToken = tokenService.generateAccessToken(user);

        return AuthResponse.builder()
            .id(user.getId())
            .phoneNumber(user.getPhoneNumber())
            .email(user.getEmail())
            .loginType(loginType)
            .newUser(isNewUser)
            .accessToken(accessToken)
            .tokenType("Bearer")
            .expiresIn(900)
            .message("Login successful")
            .build();
    }
}