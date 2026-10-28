package com.example.demo.repository;

import com.example.demo.model.GroupMember;
import com.example.demo.model.GroupRole;
import com.example.demo.model.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    Optional<GroupMember> findByGroup_IdAndUser_Id(UUID groupId, UUID userId);

    Optional<GroupMember> findByGroup_IdAndUser_IdAndStatus(UUID groupId, UUID userId, MemberStatus status);

    List<GroupMember> findAllByGroup_IdAndStatus(UUID groupId, MemberStatus status);

    long countByGroup_IdAndRoleAndStatus(UUID groupId, GroupRole role, MemberStatus status);

    long countByGroup_IdAndStatus(UUID groupId, MemberStatus status);

    boolean existsByGroup_IdAndUser_IdAndStatus(UUID groupId, UUID userId, MemberStatus status);

    boolean existsByGroup_IdAndUser_PhoneNumberAndStatus(UUID groupId, String phoneNumber, MemberStatus status);

    List<GroupMember> findAllByUser_IdAndStatus(UUID userId, MemberStatus status);
}
