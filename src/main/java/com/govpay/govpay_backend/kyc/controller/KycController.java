package com.govpay.govpay_backend.kyc.controller;

import com.govpay.govpay_backend.auth.repository.UserRepository;
import com.govpay.govpay_backend.common.response.ApiResponse;
import com.govpay.govpay_backend.kyc.dto.KycDto.*;
import com.govpay.govpay_backend.kyc.service.KycService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;
    private final UserRepository userRepository;

    // ── User endpoints ────────────────────────────────────────────────────────

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitKycRequest request
    ) {
        UUID userId = resolveUserId(userDetails);
        KycDocumentResponse doc = kycService.submitKyc(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("KYC submitted successfully. We'll review within 24 hours.", doc));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getStatus(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(kycService.getMyKycStatus(userId)));
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<KycDocumentResponse>>> getMyDocuments(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(kycService.getMyDocuments(userId)));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<KycDocumentResponse>>> getPending(
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(kycService.getPendingSubmissions(pageable)));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<KycDocumentResponse>>> getAll(
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(kycService.getAllSubmissions(pageable)));
    }

    @GetMapping("/admin/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> getDocument(
            @PathVariable UUID documentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(kycService.getDocument(documentId)));
    }

    @PostMapping("/admin/{documentId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> approve(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        KycDocumentResponse doc = kycService.approveKyc(documentId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("KYC approved successfully", doc));
    }

    @PostMapping("/admin/{documentId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> reject(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReviewKycRequest request
    ) {
        KycDocumentResponse doc = kycService.rejectKyc(documentId, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("KYC rejected", doc));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }
}