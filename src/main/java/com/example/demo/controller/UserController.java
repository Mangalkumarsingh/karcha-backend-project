package com.example.demo.controller;

import com.example.demo.dto.InvitationResponse;
import com.example.demo.dto.UpdateProfileRequest;
import com.example.demo.dto.UserProfileResponse;
import com.example.demo.security.SecurityUtil;
import com.example.demo.services.GroupMemberService;
import com.example.demo.services.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final GroupMemberService groupMemberService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
    }

    @GetMapping("/me/invitations")
    public ResponseEntity<List<InvitationResponse>> getMyInvitations(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        return ResponseEntity.ok(groupMemberService.getMyPendingInvitations(userId));
    }
}
