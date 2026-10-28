package com.example.demo.repository;

import com.example.demo.model.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, UUID> {

    List<ExpenseSplit> findAllByExpense_Id(UUID expenseId);

    void deleteAllByExpense_Id(UUID expenseId);

    @Query("SELECT COALESCE(SUM(s.shareAmount), 0) FROM ExpenseSplit s WHERE s.expense.group.id = :groupId AND s.user.id = :userId AND s.expense.deletedAt IS NULL")
    BigDecimal sumShareAmountByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT s FROM ExpenseSplit s WHERE s.expense.group.id = :groupId AND s.expense.deletedAt IS NULL")
    List<ExpenseSplit> findAllActiveByGroupId(@Param("groupId") UUID groupId);
}
