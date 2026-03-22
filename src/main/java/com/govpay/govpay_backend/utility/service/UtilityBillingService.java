package com.govpay.govpay_backend.utility.service;

import com.govpay.govpay_backend.auth.entity.User;
import com.govpay.govpay_backend.auth.repository.UserRepository;
import com.govpay.govpay_backend.common.exception.GovPayException;
import com.govpay.govpay_backend.utility.dto.UtilityDto.*;
import com.govpay.govpay_backend.utility.entity.Bill;
import com.govpay.govpay_backend.utility.entity.UtilityProvider;
import com.govpay.govpay_backend.utility.repository.BillRepository;
import com.govpay.govpay_backend.utility.repository.UtilityProviderRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilityBillingService {

    private final BillRepository billRepository;
    private final UtilityProviderRepository utilityProviderRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    // Mock bill amounts per utility type (in smallest unit)
    private static final Map<UtilityProvider.UtilityType, long[]> MOCK_AMOUNT_RANGES = Map.of(
            UtilityProvider.UtilityType.WATER,          new long[]{500_00L,  15000_00L},
            UtilityProvider.UtilityType.ELECTRICITY,    new long[]{1000_00L, 50000_00L},
            UtilityProvider.UtilityType.TAX,            new long[]{5000_00L, 200000_00L},
            UtilityProvider.UtilityType.FINE,           new long[]{3000_00L, 30000_00L},
            UtilityProvider.UtilityType.GOVERNMENT_FEE, new long[]{1000_00L, 20000_00L},
            UtilityProvider.UtilityType.OTHER,          new long[]{500_00L,  10000_00L}
    );

    // ── List services ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UtilityProviderResponse> listServices() {
        return utilityProviderRepository.findByIsActiveTrue()
                .stream()
                .map(UtilityProviderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UtilityProviderResponse> listServicesByType(String type) {
        UtilityProvider.UtilityType utilityType = parseType(type);
        return utilityProviderRepository.findByTypeAndIsActiveTrue(utilityType)
                .stream()
                .map(UtilityProviderResponse::from)
                .toList();
    }

    // ── Generate bill ─────────────────────────────────────────────────────────

    @Transactional
    public BillResponse generateBill(UUID userId, GenerateBillRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        UtilityProvider provider = utilityProviderRepository.findByCode(request.getServiceCode())
                .orElseThrow(() -> new ServiceNotFoundException(request.getServiceCode()));

        if (!provider.getIsActive()) {
            throw new ServiceUnavailableException(request.getServiceCode());
        }

        // Prevent duplicate unpaid bills for the same service
        if (billRepository.existsByUserIdAndUtilityServiceIdAndStatus(
                userId, provider.getId(), Bill.BillStatus.UNPAID)) {
            throw new DuplicateBillException(provider.getName());
        }

        // Use provided amount or generate a realistic mock amount
        long amount = request.getAmount() != null
                ? request.getAmount()
                : generateMockAmount(provider.getType());

        Bill bill = Bill.builder()
                .user(user)
                .utilityService(provider)
                .amount(amount)
                .currency("TZS")
                .status(Bill.BillStatus.UNPAID)
                .dueDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .description(request.getDescription() != null
                        ? request.getDescription()
                        : provider.getName() + " bill for " + user.getFirstName())
                .build();

        bill = billRepository.save(bill);
        log.info("Bill generated: user={} service={} amount={}", userId, provider.getCode(), amount);
        return BillResponse.from(bill);
    }

    // ── Get bills ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BillResponse> getUserBills(UUID userId, Pageable pageable) {
        return billRepository.findByUserId(userId, pageable)
                .map(BillResponse::from);
    }

    @Transactional(readOnly = true)
    public BillResponse getBill(UUID userId, UUID billId) {
        Bill bill = billRepository.findByIdAndUserId(billId, userId)
                .orElseThrow(() -> new BillNotFoundException(billId.toString()));
        return BillResponse.from(bill);
    }

    // ── Pay bill ──────────────────────────────────────────────────────────────
    // Atomic: debit wallet + mark bill paid in one transaction

    @Transactional
    public BillResponse payBill(UUID userId, UUID billId) {
        Bill bill = billRepository.findByIdAndUserId(billId, userId)
                .orElseThrow(() -> new BillNotFoundException(billId.toString()));

        if (!bill.isPayable()) {
            throw new BillNotPayableException(bill.getStatus().name());
        }

        // Acquire pessimistic lock on wallet — same pattern as P2P transfer
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId.toString()));

        if (!wallet.isActive()) {
            throw new WalletFrozenException();
        }

        if (!wallet.hasSufficientBalance(bill.getAmount())) {
            throw new InsufficientBalanceException();
        }

        // Create transaction record
        Transaction tx = Transaction.builder()
                .senderWallet(wallet)
                .type(Transaction.TransactionType.UTILITY_PAYMENT)
                .amount(bill.getAmount())
                .currency(bill.getCurrency())
                .description("Payment for " + bill.getUtilityService().getName())
                .reference("BILL-" + bill.getId().toString().replace("-", "").substring(0, 16))
                .build();

        wallet.debit(bill.getAmount());
        tx.markCompleted();

        walletRepository.save(wallet);
        tx = transactionRepository.save(tx);

        // Mark bill paid and link transaction
        bill.setStatus(Bill.BillStatus.PAID);
        bill.setTransaction(tx);
        bill.setPaidAt(Instant.now());
        bill = billRepository.save(bill);

        log.info("Bill paid: billId={} userId={} amount={}", billId, userId, bill.getAmount());
        return BillResponse.from(bill);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private long generateMockAmount(UtilityProvider.UtilityType type) {
        long[] range = MOCK_AMOUNT_RANGES.getOrDefault(type, new long[]{500_00L, 10000_00L});
        return range[0] + (long) (new Random().nextDouble() * (range[1] - range[0]));
    }

    private UtilityProvider.UtilityType parseType(String type) {
        try {
            return UtilityProvider.UtilityType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidUtilityTypeException(type);
        }
    }

    // ── Exceptions ────────────────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ServiceNotFoundException extends GovPayException {
        public ServiceNotFoundException(String code) {
            super("Utility service not found: " + code);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class ServiceUnavailableException extends GovPayException {
        public ServiceUnavailableException(String code) {
            super("Utility service is currently unavailable: " + code);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateBillException extends GovPayException {
        public DuplicateBillException(String service) {
            super("You already have an unpaid bill for: " + service);
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class BillNotFoundException extends GovPayException {
        public BillNotFoundException(String id) {
            super("Bill not found: " + id);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class BillNotPayableException extends GovPayException {
        public BillNotPayableException(String status) {
            super("Bill cannot be paid — current status: " + status);
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class WalletNotFoundException extends GovPayException {
        public WalletNotFoundException(String userId) {
            super("Wallet not found for user: " + userId);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InsufficientBalanceException extends GovPayException {
        public InsufficientBalanceException() {
            super("Insufficient wallet balance to pay this bill");
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class WalletFrozenException extends GovPayException {
        public WalletFrozenException() {
            super("Wallet is frozen or closed");
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidUtilityTypeException extends GovPayException {
        public InvalidUtilityTypeException(String type) {
            super("Invalid utility type: " + type + ". Valid types: WATER, ELECTRICITY, TAX, FINE, GOVERNMENT_FEE, OTHER");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ResourceNotFoundException extends GovPayException {
        public ResourceNotFoundException(String resource, String id) {
            super(resource + " not found: " + id);
        }
    }
}
