package com.example.demo.repository;

import com.example.demo.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    // Legacy personal expense queries
    List<Expense> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<Expense> findByIdAndUser_Id(UUID expenseId, UUID userId);

    // Group expense queries
    List<Expense> findAllByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID groupId);

    Page<Expense> findAllByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID groupId, Pageable pageable);

    Optional<Expense> findByIdAndGroup_IdAndDeletedAtIsNull(UUID expenseId, UUID groupId);

    long countByGroup_IdAndDeletedAtIsNull(UUID groupId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId AND e.deletedAt IS NULL")
    BigDecimal sumTotalAmountByGroupId(@Param("groupId") UUID groupId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId AND e.paidBy.id = :userId AND e.deletedAt IS NULL")
    BigDecimal sumAmountByGroupIdAndPaidByUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);
}
