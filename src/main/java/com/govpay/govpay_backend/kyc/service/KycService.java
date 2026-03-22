package com.govpay.govpay_backend.kyc.service;

import com.govpay.govpay_backend.auth.entity.User;
import com.govpay.govpay_backend.auth.repository.UserRepository;
import com.govpay.govpay_backend.common.exception.GovPayException;
import com.govpay.govpay_backend.kyc.dto.KycDto.*;
import com.govpay.govpay_backend.kyc.entity.KycDocument;
import com.govpay.govpay_backend.kyc.repository.KycDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private final KycDocumentRepository kycDocumentRepository;
    private final UserRepository userRepository;

    // ── User: submit KYC ──────────────────────────────────────────────────────

    @Transactional
    public KycDocumentResponse submitKyc(UUID userId, SubmitKycRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        // Block if already approved
        if (user.getKycStatus() == User.KycStatus.APPROVED) {
            throw new KycAlreadyApprovedException();
        }

        // Block if a pending submission already exists
        if (kycDocumentRepository.existsByUserIdAndStatus(userId, KycDocument.KycDocumentStatus.PENDING)) {
            throw new KycAlreadySubmittedException();
        }

        KycDocument document = KycDocument.builder()
                .user(user)
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber().trim())
                .notes(request.getNotes())
                .status(KycDocument.KycDocumentStatus.PENDING)
                .build();

        document = kycDocumentRepository.save(document);

        // Update user KYC status to SUBMITTED
        user.setKycStatus(User.KycStatus.SUBMITTED);
        userRepository.save(user);

        log.info("KYC submitted: userId={} docType={}", userId, request.getDocumentType());
        return KycDocumentResponse.from(document);
    }

    // ── User: get own KYC status ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public KycStatusResponse getMyKycStatus(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        KycDocument latest = kycDocumentRepository
                .findTopByUserIdOrderBySubmittedAtDesc(userId)
                .orElse(null);

        return KycStatusResponse.builder()
                .userId(userId)
                .kycStatus(user.getKycStatus())
                .latestDocument(latest != null ? KycDocumentResponse.from(latest) : null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<KycDocumentResponse> getMyDocuments(UUID userId) {
        return kycDocumentRepository.findByUserIdOrderBySubmittedAtDesc(userId)
                .stream()
                .map(KycDocumentResponse::from)
                .toList();
    }

    // ── Admin: list pending submissions ───────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<KycDocumentResponse> getPendingSubmissions(Pageable pageable) {
        return kycDocumentRepository
                .findByStatus(KycDocument.KycDocumentStatus.PENDING, pageable)
                .map(KycDocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<KycDocumentResponse> getAllSubmissions(Pageable pageable) {
        return kycDocumentRepository.findAll(pageable)
                .map(KycDocumentResponse::from);
    }

    // ── Admin: approve ────────────────────────────────────────────────────────

    @Transactional
    public KycDocumentResponse approveKyc(UUID documentId, String adminEmail) {
        KycDocument document = findDocument(documentId);

        if (document.getStatus() != KycDocument.KycDocumentStatus.PENDING) {
            throw new KycNotReviewableException(document.getStatus().name());
        }

        document.setStatus(KycDocument.KycDocumentStatus.APPROVED);
        document.setReviewedBy(adminEmail);
        document.setReviewedAt(Instant.now());
        kycDocumentRepository.save(document);

        // Update user KYC status to APPROVED
        User user = document.getUser();
        user.setKycStatus(User.KycStatus.APPROVED);
        userRepository.save(user);

        log.info("KYC approved: documentId={} userId={} by={}", documentId, user.getId(), adminEmail);
        return KycDocumentResponse.from(document);
    }

    // ── Admin: reject ─────────────────────────────────────────────────────────

    @Transactional
    public KycDocumentResponse rejectKyc(UUID documentId, ReviewKycRequest request, String adminEmail) {
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new RejectionReasonRequiredException();
        }

        KycDocument document = findDocument(documentId);

        if (document.getStatus() != KycDocument.KycDocumentStatus.PENDING) {
            throw new KycNotReviewableException(document.getStatus().name());
        }

        document.setStatus(KycDocument.KycDocumentStatus.REJECTED);
        document.setRejectionReason(request.getReason());
        document.setReviewedBy(adminEmail);
        document.setReviewedAt(Instant.now());
        kycDocumentRepository.save(document);

        // Reset user KYC status to PENDING so they can resubmit
        User user = document.getUser();
        user.setKycStatus(User.KycStatus.PENDING);
        userRepository.save(user);

        log.info("KYC rejected: documentId={} userId={} by={} reason={}",
                documentId, user.getId(), adminEmail, request.getReason());
        return KycDocumentResponse.from(document);
    }

    // ── Admin: get single document ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public KycDocumentResponse getDocument(UUID documentId) {
        return KycDocumentResponse.from(findDocument(documentId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private KycDocument findDocument(UUID documentId) {
        return kycDocumentRepository.findById(documentId)
                .orElseThrow(() -> new KycDocumentNotFoundException(documentId.toString()));
    }

    // ── Exceptions ────────────────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class KycAlreadyApprovedException extends GovPayException {
        public KycAlreadyApprovedException() {
            super("Your identity has already been verified");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class KycAlreadySubmittedException extends GovPayException {
        public KycAlreadySubmittedException() {
            super("You already have a pending KYC submission. Please wait for review.");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class KycDocumentNotFoundException extends GovPayException {
        public KycDocumentNotFoundException(String id) {
            super("KYC document not found: " + id);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class KycNotReviewableException extends GovPayException {
        public KycNotReviewableException(String status) {
            super("KYC document cannot be reviewed — current status: " + status);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class RejectionReasonRequiredException extends GovPayException {
        public RejectionReasonRequiredException() {
            super("A rejection reason is required");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ResourceNotFoundException extends GovPayException {
        public ResourceNotFoundException(String resource, String id) {
            super(resource + " not found: " + id);
        }
    }
}