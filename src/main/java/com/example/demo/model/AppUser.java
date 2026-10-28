package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, length = 16)
    private String phoneNumber;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String googleSubject;

    @Builder.Default
    private boolean phoneVerified = false;

    @Builder.Default
    private boolean emailVerified = false;

    @Column(length = 100)
    private String name;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "otp_hash")
    private String otpHash;

    @Column(name = "otp_expiry_time")
    private Instant otpExpiryTime;

    @Builder.Default
    private int otpAttempts = 0;

    private Instant otpLastSentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(
        mappedBy = "user",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<Expense> expenses;
}