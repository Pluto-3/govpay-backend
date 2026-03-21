package com.govpay.govpay_backend.wallet.repository;

import com.govpay.govpay_backend.wallet.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReference(String reference);

    boolean existsByReference(String reference);

    // All transactions where this wallet is sender or recipient
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.senderWallet.id = :walletId
           OR t.recipientWallet.id = :walletId
        ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByWalletId(@Param("walletId") UUID walletId, Pageable pageable);

    @Query("""
        SELECT t FROM Transaction t
        WHERE (t.senderWallet.id = :walletId OR t.recipientWallet.id = :walletId)
          AND t.type = :type
        ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByWalletIdAndType(
            @Param("walletId") UUID walletId,
            @Param("type") Transaction.TransactionType type,
            Pageable pageable
    );
}