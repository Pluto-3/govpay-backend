package com.govpay.govpay_backend.wallet.service;

import com.govpay.govpay_backend.auth.entity.User;
import com.govpay.govpay_backend.auth.repository.UserRepository;
import com.govpay.govpay_backend.common.exception.GovPayException;
import com.govpay.govpay_backend.notification.dto.NotificationEvents.*;
import com.govpay.govpay_backend.notification.publisher.EventPublisher;
import com.govpay.govpay_backend.wallet.dto.WalletDto.*;
import com.govpay.govpay_backend.wallet.entity.Transaction;
import com.govpay.govpay_backend.wallet.entity.Wallet;
import com.govpay.govpay_backend.wallet.repository.TransactionRepository;
import com.govpay.govpay_backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;

    @Value("${govpay.wallet.low-balance-threshold:5000}")
    private Long lowBalanceThreshold;

    // ── Wallet creation ───────────────────────────────────────────────────────

    @Transactional
    public WalletResponse createWallet(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        if (walletRepository.existsByUserId(userId)) {
            throw new WalletAlreadyExistsException(userId.toString());
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(0L)
                .currency("TZS")
                .status(Wallet.WalletStatus.ACTIVE)
                .build();

        wallet = walletRepository.save(wallet);
        log.info("Wallet created for user: {}", userId);
        return WalletResponse.from(wallet);
    }

    // ── Balance query ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {
        Wallet wallet = findActiveWalletByUserId(userId);
        return WalletResponse.from(wallet);
    }

    // ── Top-up ────────────────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse topUp(UUID userId, TopUpRequest request) {
        // Idempotency check — if this reference was already processed, return it
        if (transactionRepository.existsByReference(request.getReference())) {
            Transaction existing = transactionRepository.findByReference(request.getReference()).get();
            log.info("Duplicate top-up request detected, returning existing: {}", request.getReference());
            return TransactionResponse.from(existing);
        }

        // Acquire pessimistic write lock before modifying balance
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId.toString()));

        if (!wallet.isActive()) {
            throw new WalletFrozenException();
        }

        Transaction tx = Transaction.builder()
                .recipientWallet(wallet)
                .type(Transaction.TransactionType.TOP_UP)
                .amount(request.getAmount())
                .currency(wallet.getCurrency())
                .description(request.getDescription() != null ? request.getDescription() : "Wallet top-up")
                .reference(request.getReference())
                .build();

        wallet.credit(request.getAmount());
        tx.markCompleted();

        walletRepository.save(wallet);
        tx = transactionRepository.save(tx);

        log.info("Top-up completed: user={} amount={} ref={}", userId, request.getAmount(), request.getReference());
        checkAndNotifyLowBalance(wallet);

        return TransactionResponse.from(tx);
    }

    // ── P2P Transfer ──────────────────────────────────────────────────────────
    // This is the most critical method — must be atomic and handle concurrency

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse transfer(UUID senderUserId, TransferRequest request) {
        // Idempotency check
        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : generateIdempotencyKey(senderUserId, request.getRecipientUserId(), request.getAmount());

        if (transactionRepository.existsByReference(idempotencyKey)) {
            Transaction existing = transactionRepository.findByReference(idempotencyKey).get();
            log.info("Duplicate transfer detected, returning existing: {}", idempotencyKey);
            return TransactionResponse.from(existing);
        }

        if (senderUserId.equals(request.getRecipientUserId())) {
            throw new SelfTransferException();
        }

        // Always lock wallets in consistent UUID order to prevent deadlocks.
        // If thread A locks wallet-1 then wallet-2, and thread B locks wallet-2
        // then wallet-1, they deadlock. Ordering by UUID breaks this cycle.
        UUID recipientUserId = request.getRecipientUserId();
        Wallet senderWallet, recipientWallet;

        if (senderUserId.compareTo(recipientUserId) < 0) {
            senderWallet   = walletRepository.findByUserIdWithLock(senderUserId)
                    .orElseThrow(() -> new WalletNotFoundException(senderUserId.toString()));
            recipientWallet = walletRepository.findByUserIdWithLock(recipientUserId)
                    .orElseThrow(() -> new WalletNotFoundException(recipientUserId.toString()));
        } else {
            recipientWallet = walletRepository.findByUserIdWithLock(recipientUserId)
                    .orElseThrow(() -> new WalletNotFoundException(recipientUserId.toString()));
            senderWallet   = walletRepository.findByUserIdWithLock(senderUserId)
                    .orElseThrow(() -> new WalletNotFoundException(senderUserId.toString()));
        }

        if (!senderWallet.isActive())    throw new WalletFrozenException();
        if (!recipientWallet.isActive()) throw new WalletFrozenException();

        if (!senderWallet.hasSufficientBalance(request.getAmount())) {
            // Publish failed event for notification
            eventPublisher.publishPaymentFailed(new PaymentFailedEvent(
                    senderUserId,
                    senderWallet.getUser().getEmail(),
                    java.math.BigDecimal.valueOf(request.getAmount(), 2),
                    "Insufficient balance"
            ));
            throw new InsufficientBalanceException();
        }

        Transaction tx = Transaction.builder()
                .senderWallet(senderWallet)
                .recipientWallet(recipientWallet)
                .type(Transaction.TransactionType.P2P_TRANSFER)
                .amount(request.getAmount())
                .currency(senderWallet.getCurrency())
                .description(request.getDescription())
                .reference(idempotencyKey)
                .build();

        senderWallet.debit(request.getAmount());
        recipientWallet.credit(request.getAmount());
        tx.markCompleted();

        walletRepository.save(senderWallet);
        walletRepository.save(recipientWallet);
        tx = transactionRepository.save(tx);

        log.info("Transfer completed: from={} to={} amount={}", senderUserId, recipientUserId, request.getAmount());

        // Publish notification events async — does not affect transaction outcome
        eventPublisher.publishPaymentCompleted(new PaymentCompletedEvent(
                tx.getId(),
                senderUserId,
                senderWallet.getUser().getEmail(),
                recipientUserId,
                recipientWallet.getUser().getEmail(),
                java.math.BigDecimal.valueOf(request.getAmount(), 2),
                tx.getCurrency(),
                request.getDescription()
        ));

        checkAndNotifyLowBalance(senderWallet);

        return TransactionResponse.from(tx);
    }

    // ── Transaction history ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(UUID userId, Pageable pageable) {
        Wallet wallet = findActiveWalletByUserId(userId);
        return transactionRepository.findByWalletId(wallet.getId(), pageable)
                .map(TransactionResponse::from);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Wallet findActiveWalletByUserId(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId.toString()));
    }

    private void checkAndNotifyLowBalance(Wallet wallet) {
        if (wallet.getBalance() < lowBalanceThreshold) {
            eventPublisher.publishLowBalance(new LowBalanceEvent(
                    wallet.getUser().getId(),
                    wallet.getUser().getEmail(),
                    wallet.getUser().getFirstName(),
                    wallet.getDisplayBalance(),
                    java.math.BigDecimal.valueOf(lowBalanceThreshold, 2),
                    wallet.getCurrency()
            ));
        }
    }

    private String generateIdempotencyKey(UUID sender, UUID recipient, Long amount) {
        return String.format("transfer-%s-%d-%d",
                UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                amount,
                System.currentTimeMillis());
    }

    // ── Wallet-specific exceptions ────────────────────────────────────────────

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class WalletNotFoundException extends GovPayException {
        public WalletNotFoundException(String userId) {
            super("Wallet not found for user: " + userId);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class WalletAlreadyExistsException extends GovPayException {
        public WalletAlreadyExistsException(String userId) {
            super("Wallet already exists for user: " + userId);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InsufficientBalanceException extends GovPayException {
        public InsufficientBalanceException() {
            super("Insufficient wallet balance for this transaction");
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class WalletFrozenException extends GovPayException {
        public WalletFrozenException() {
            super("Wallet is frozen or closed");
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class SelfTransferException extends GovPayException {
        public SelfTransferException() {
            super("Cannot transfer to your own wallet");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ResourceNotFoundException extends GovPayException {
        public ResourceNotFoundException(String resource, String id) {
            super(resource + " not found: " + id);
        }
    }
}
