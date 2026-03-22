package com.govpay.govpay_backend.utility.controller;

import com.govpay.govpay_backend.auth.repository.UserRepository;
import com.govpay.govpay_backend.common.response.ApiResponse;
import com.govpay.govpay_backend.utility.dto.UtilityDto.*;
import com.govpay.govpay_backend.utility.service.UtilityBillingService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/utility")
@RequiredArgsConstructor
public class UtilityController {

    private final UtilityBillingService billingService;
    private final UserRepository userRepository;

    // ── Services ──────────────────────────────────────────────────────────────

    @GetMapping("/services")
    public ResponseEntity<ApiResponse<List<UtilityProviderResponse>>> listServices(
            @RequestParam(required = false) String type
    ) {
        List<UtilityProviderResponse> services = type != null
                ? billingService.listServicesByType(type)
                : billingService.listServices();
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    // ── Bills ─────────────────────────────────────────────────────────────────

    @PostMapping("/bills/generate")
    public ResponseEntity<ApiResponse<BillResponse>> generateBill(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody GenerateBillRequest request
    ) {
        UUID userId = resolveUserId(userDetails);
        BillResponse bill = billingService.generateBill(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bill generated successfully", bill));
    }

    @GetMapping("/bills")
    public ResponseEntity<ApiResponse<Page<BillResponse>>> getUserBills(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(billingService.getUserBills(userId, pageable)));
    }

    @GetMapping("/bills/{billId}")
    public ResponseEntity<ApiResponse<BillResponse>> getBill(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID billId
    ) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(billingService.getBill(userId, billId)));
    }

    @PostMapping("/bills/{billId}/pay")
    public ResponseEntity<ApiResponse<BillResponse>> payBill(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID billId
    ) {
        UUID userId = resolveUserId(userDetails);
        BillResponse bill = billingService.payBill(userId, billId);
        return ResponseEntity.ok(ApiResponse.success("Bill paid successfully", bill));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }
}