package com.example.demo.repository;

import com.example.demo.model.Settlement;
import com.example.demo.model.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    Page<Settlement> findAllByGroup_IdOrderByCreatedAtDesc(UUID groupId, Pageable pageable);

    List<Settlement> findAllByGroup_IdAndStatus(UUID groupId, SettlementStatus status);

    Optional<Settlement> findByIdAndGroup_Id(UUID id, UUID groupId);

    Optional<Settlement> findByGroup_IdAndIdempotencyKey(UUID groupId, String idempotencyKey);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.fromUser.id = :userId AND s.status = :status")
    BigDecimal sumAmountByGroupIdAndFromUserIdAndStatus(
            @Param("groupId") UUID groupId,
            @Param("userId") UUID userId,
            @Param("status") SettlementStatus status);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.toUser.id = :userId AND s.status = :status")
    BigDecimal sumAmountByGroupIdAndToUserIdAndStatus(
            @Param("groupId") UUID groupId,
            @Param("userId") UUID userId,
            @Param("status") SettlementStatus status);
}
