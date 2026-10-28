package com.example.demo.repository;

import com.example.demo.model.GroupInvitation;
import com.example.demo.model.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, UUID> {

    List<GroupInvitation> findAllByInvitedUser_IdAndStatusAndExpiresAtAfter(
            UUID userId, InvitationStatus status, LocalDateTime now);

    List<GroupInvitation> findAllByInvitedPhoneNumberAndStatusAndExpiresAtAfter(
            String phoneNumber, InvitationStatus status, LocalDateTime now);

    Optional<GroupInvitation> findByIdAndStatus(UUID id, InvitationStatus status);

    boolean existsByGroup_IdAndInvitedPhoneNumberAndStatusAndExpiresAtAfter(
            UUID groupId, String phoneNumber, InvitationStatus status, LocalDateTime now);
}
