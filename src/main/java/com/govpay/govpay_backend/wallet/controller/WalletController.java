package com.govpay.govpay_backend.wallet.controller;

import com.govpay.govpay_backend.auth.repository.UserRepository;
import com.govpay.govpay_backend.common.response.ApiResponse;
import com.govpay.govpay_backend.wallet.dto.WalletDto.*;
import com.govpay.govpay_backend.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<WalletResponse>> createWallet(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = resolveUserId(userDetails);
        WalletResponse wallet = walletService.createWallet(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Wallet created successfully", wallet));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(walletService.getWallet(userId)));
    }

    @PostMapping("/top-up")
    public ResponseEntity<ApiResponse<TransactionResponse>> topUp(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TopUpRequest request
    ) {
        UUID userId = resolveUserId(userDetails);
        TransactionResponse tx = walletService.topUp(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Top-up successful", tx));
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransferRequest request
    ) {
        UUID userId = resolveUserId(userDetails);
        TransactionResponse tx = walletService.transfer(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Transfer successful", tx));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID userId = resolveUserId(userDetails);
        Page<TransactionResponse> transactions = walletService.getTransactionHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found: " + userDetails.getUsername()))
                .getId();
    }
}