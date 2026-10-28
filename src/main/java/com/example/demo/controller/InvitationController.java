package com.example.demo.controller;

import com.example.demo.security.SecurityUtil;
import com.example.demo.services.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final GroupMemberService groupMemberService;

    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<Map<String, Object>> acceptInvitation(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        groupMemberService.acceptInvitation(invitationId, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Invitation accepted successfully"));
    }

    @PostMapping("/{invitationId}/reject")
    public ResponseEntity<Map<String, Object>> rejectInvitation(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtil.getUserId(jwt);
        groupMemberService.rejectInvitation(invitationId, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Invitation rejected"));
    }
}
