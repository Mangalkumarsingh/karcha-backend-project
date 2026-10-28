package com.example.demo.services;

import com.example.demo.model.AppUser;
import lombok.RequiredArgsConstructor;

import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;

    public String generateAccessToken(AppUser user) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("expense-app")
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .claim("userId", user.getId().toString())
            .build();

        return jwtEncoder.encode(
            JwtEncoderParameters.from(claims)
        ).getTokenValue();
    }
}
