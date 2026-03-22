package com.govpay.govpay_backend.admin.service;

import com.govpay.govpay_backend.admin.dto.AdminDto.*;
import com.govpay.govpay_backend.admin.repository.AdminRepository;
import com.govpay.govpay_backend.auth.entity.User;
import com.govpay.govpay_backend.auth.repository.UserRepository;
import com.govpay.govpay_backend.common.exception.GovPayException;
import com.govpay.govpay_backend.wallet.entity.Transaction;
import com.govpay.govpay_backend.wallet.entity.Wallet;
import com.govpay.govpay_backend.wallet.repository.TransactionRepository;
import com.govpay.govpay_backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AdminRepository adminRepository;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        long totalUsers       = userRepository.count();
        long activeUsers      = adminRepository.countByStatus(User.UserStatus.ACTIVE);
        long suspendedUsers   = adminRepository.countByStatus(User.UserStatus.SUSPENDED);
        long pendingKyc       = adminRepository.countByKycStatus(User.KycStatus.PENDING)
                + adminRepository.countByKycStatus(User.KycStatus.SUBMITTED);
        long approvedKyc      = adminRepository.countByKycStatus(User.KycStatus.APPROVED);
        long totalWallets     = walletRepository.count();
        BigDecimal totalBal   = adminRepository.sumAllWalletBalances();
        long totalTx          = transactionRepository.count();
        long completedTx      = adminRepository.countByTransactionStatus(Transaction.TransactionStatus.COMPLETED);
        long failedTx         = adminRepository.countByTransactionStatus(Transaction.TransactionStatus.FAILED);
        BigDecimal totalVol   = adminRepository.sumTotalVolume();
        BigDecimal topUpVol   = adminRepository.sumVolumeByType(Transaction.TransactionType.TOP_UP);
        BigDecimal transferVol= adminRepository.sumVolumeByType(Transaction.TransactionType.P2P_TRANSFER);
        BigDecimal utilityVol = adminRepository.sumVolumeByType(Transaction.TransactionType.UTILITY_PAYMENT);

        return DashboardResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .suspendedUsers(suspendedUsers)
                .pendingKyc(pendingKyc)
                .approvedKyc(approvedKyc)
                .totalWallets(totalWallets)
                .totalWalletBalance(totalBal)
                .totalTransactions(totalTx)
                .completedTransactions(completedTx)
                .failedTransactions(failedTx)
                .totalVolume(totalVol)
                .totalTopUpVolume(topUpVol)
                .totalTransferVolume(transferVol)
                .totalUtilityVolume(utilityVol)
                .build();
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(user -> {
                    Wallet wallet = walletRepository.findByUserId(user.getId()).orElse(null);
                    return AdminUserResponse.from(user, wallet);
                });
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));
        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        return AdminUserResponse.from(user, wallet);
    }

    // ── Wallet actions ────────────────────────────────────────────────────────

    @Transactional
    public AdminUserResponse freezeWallet(UUID userId, String adminEmail) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId.toString()));

        if (wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            throw new WalletAlreadyFrozenException();
        }

        wallet.setStatus(Wallet.WalletStatus.FROZEN);
        walletRepository.save(wallet);

        log.info("Wallet frozen: userId={} by={}", userId, adminEmail);
        User user = userRepository.findById(userId).orElseThrow();
        return AdminUserResponse.from(user, wallet);
    }

    @Transactional
    public AdminUserResponse unfreezeWallet(UUID userId, String adminEmail) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId.toString()));

        if (wallet.getStatus() != Wallet.WalletStatus.FROZEN) {
            throw new WalletNotFrozenException();
        }

        wallet.setStatus(Wallet.WalletStatus.ACTIVE);
        walletRepository.save(wallet);

        log.info("Wallet unfrozen: userId={} by={}", userId, adminEmail);
        User user = userRepository.findById(userId).orElseThrow();
        return AdminUserResponse.from(user, wallet);
    }

    // ── User account actions ──────────────────────────────────────────────────

    @Transactional
    public AdminUserResponse suspendUser(UUID userId, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        if (user.getStatus() == User.UserStatus.SUSPENDED) {
            throw new UserAlreadySuspendedException();
        }

        user.setStatus(User.UserStatus.SUSPENDED);
        userRepository.save(user);

        log.info("User suspended: userId={} by={}", userId, adminEmail);
        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        return AdminUserResponse.from(user, wallet);
    }

    @Transactional
    public AdminUserResponse reinstateUser(UUID userId, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        if (user.getStatus() != User.UserStatus.SUSPENDED) {
            throw new UserNotSuspendedException();
        }

        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);

        log.info("User reinstated: userId={} by={}", userId, adminEmail);
        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        return AdminUserResponse.from(user, wallet);
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminTransactionResponse> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable)
                .map(AdminTransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public List<AdminTransactionResponse> getTransactionReport(
            Instant from, Instant to, int page, int size) {

        if (from == null) from = Instant.now().minus(30, ChronoUnit.DAYS);
        if (to == null)   to   = Instant.now();

        return adminRepository.findTransactionsBetween(from, to, page, size)
                .stream()
                .map(AdminTransactionResponse::from)
                .toList();
    }

    // ── Exceptions ────────────────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class UserNotFoundException extends GovPayException {
        public UserNotFoundException(String id) { super("User not found: " + id); }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class WalletNotFoundException extends GovPayException {
        public WalletNotFoundException(String id) { super("Wallet not found for user: " + id); }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class WalletAlreadyFrozenException extends GovPayException {
        public WalletAlreadyFrozenException() { super("Wallet is already frozen"); }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class WalletNotFrozenException extends GovPayException {
        public WalletNotFrozenException() { super("Wallet is not frozen"); }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class UserAlreadySuspendedException extends GovPayException {
        public UserAlreadySuspendedException() { super("User is already suspended"); }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class UserNotSuspendedException extends GovPayException {
        public UserNotSuspendedException() { super("User is not suspended"); }
    }
}
