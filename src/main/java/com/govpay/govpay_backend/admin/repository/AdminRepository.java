package com.govpay.govpay_backend.admin.repository;

import com.govpay.govpay_backend.auth.entity.User;
import com.govpay.govpay_backend.wallet.entity.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminRepository {

    @PersistenceContext
    private final EntityManager em;

    // ── User stats ────────────────────────────────────────────────────────────

    public long countByStatus(User.UserStatus status) {
        return em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    public long countByKycStatus(User.KycStatus kycStatus) {
        return em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.kycStatus = :kycStatus", Long.class)
                .setParameter("kycStatus", kycStatus)
                .getSingleResult();
    }

    // ── Wallet stats ──────────────────────────────────────────────────────────

    public BigDecimal sumAllWalletBalances() {
        Long result = em.createQuery(
                        "SELECT SUM(w.balance) FROM Wallet w WHERE w.status = 'ACTIVE'", Long.class)
                .getSingleResult();
        return result != null ? BigDecimal.valueOf(result, 2) : BigDecimal.ZERO;
    }

    // ── Transaction stats ─────────────────────────────────────────────────────

    public long countByTransactionStatus(Transaction.TransactionStatus status) {
        return em.createQuery(
                        "SELECT COUNT(t) FROM Transaction t WHERE t.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    public BigDecimal sumVolumeByType(Transaction.TransactionType type) {
        Long result = em.createQuery(
                        "SELECT SUM(t.amount) FROM Transaction t WHERE t.type = :type AND t.status = 'COMPLETED'", Long.class)
                .setParameter("type", type)
                .getSingleResult();
        return result != null ? BigDecimal.valueOf(result, 2) : BigDecimal.ZERO;
    }

    public BigDecimal sumTotalVolume() {
        Long result = em.createQuery(
                        "SELECT SUM(t.amount) FROM Transaction t WHERE t.status = 'COMPLETED'", Long.class)
                .getSingleResult();
        return result != null ? BigDecimal.valueOf(result, 2) : BigDecimal.ZERO;
    }

    // ── Transaction report ────────────────────────────────────────────────────

    public List<Transaction> findTransactionsBetween(Instant from, Instant to, int page, int size) {
        return em.createQuery(
                        "SELECT t FROM Transaction t WHERE t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC",
                        Transaction.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public long countTransactionsBetween(Instant from, Instant to) {
        return em.createQuery(
                        "SELECT COUNT(t) FROM Transaction t WHERE t.createdAt BETWEEN :from AND :to", Long.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
    }
}