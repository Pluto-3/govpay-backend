package com.govpay.govpay_backend.admin.controller;

import com.govpay.govpay_backend.admin.dto.AdminDto.*;
import com.govpay.govpay_backend.admin.service.AdminService;
import com.govpay.govpay_backend.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDashboard()));
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAllUsers(pageable)));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserDetail(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getUserDetail(userId)));
    }

    // ── Wallet actions ────────────────────────────────────────────────────────

    @PatchMapping("/users/{userId}/freeze")
    public ResponseEntity<ApiResponse<AdminUserResponse>> freezeWallet(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        AdminUserResponse user = adminService.freezeWallet(userId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Wallet frozen successfully", user));
    }

    @PatchMapping("/users/{userId}/unfreeze")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unfreezeWallet(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        AdminUserResponse user = adminService.unfreezeWallet(userId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Wallet unfrozen successfully", user));
    }

    // ── User account actions ──────────────────────────────────────────────────

    @PatchMapping("/users/{userId}/suspend")
    public ResponseEntity<ApiResponse<AdminUserResponse>> suspendUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        AdminUserResponse user = adminService.suspendUser(userId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("User suspended", user));
    }

    @PatchMapping("/users/{userId}/reinstate")
    public ResponseEntity<ApiResponse<AdminUserResponse>> reinstateUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        AdminUserResponse user = adminService.reinstateUser(userId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("User reinstated", user));
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<AdminTransactionResponse>>> getAllTransactions(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAllTransactions(pageable)));
    }

    @GetMapping("/reports/transactions")
    public ResponseEntity<ApiResponse<List<AdminTransactionResponse>>> getTransactionReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        List<AdminTransactionResponse> report = adminService.getTransactionReport(from, to, page, size);
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}
