package com.govpay.govpay_backend.utility.repository;

import com.govpay.govpay_backend.utility.entity.Bill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillRepository extends JpaRepository<Bill, UUID> {

    Page<Bill> findByUserId(UUID userId, Pageable pageable);

    List<Bill> findByUserIdAndStatus(UUID userId, Bill.BillStatus status);

    @Query("SELECT b FROM Bill b WHERE b.user.id = :userId AND b.id = :billId")
    Optional<Bill> findByIdAndUserId(@Param("billId") UUID billId, @Param("userId") UUID userId);

    boolean existsByUserIdAndUtilityServiceIdAndStatus(
            UUID userId, UUID utilityServiceId, Bill.BillStatus status);
}