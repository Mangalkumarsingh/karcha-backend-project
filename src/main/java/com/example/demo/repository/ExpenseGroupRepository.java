package com.example.demo.repository;

import com.example.demo.model.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, UUID> {

    Optional<ExpenseGroup> findByIdAndArchivedAtIsNull(UUID id);

    @Query("SELECT g FROM ExpenseGroup g JOIN g.members m WHERE m.user.id = :userId AND m.status = 'ACTIVE' AND g.archivedAt IS NULL ORDER BY g.createdAt DESC")
    List<ExpenseGroup> findAllActiveByUserId(@Param("userId") UUID userId);
}
