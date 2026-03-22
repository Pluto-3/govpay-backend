package com.govpay.govpay_backend.kyc.dto;

import com.govpay.govpay_backend.auth.entity.User;
import com.govpay.govpay_backend.kyc.entity.KycDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

public class KycDto {

    // ── Requests ──────────────────────────────────────────────────────────────

    @Data
    public static class SubmitKycRequest {

        @NotNull(message = "Document type is required")
        private KycDocument.DocumentType documentType;

        @NotBlank(message = "Document number is required")
        @Size(min = 5, max = 100, message = "Document number must be between 5 and 100 characters")
        private String documentNumber;

        @Size(max = 500, message = "Notes too long")
        private String notes;
    }

    @Data
    public static class ReviewKycRequest {

        @Size(max = 500, message = "Reason too long")
        private String reason; // required for rejection, optional for approval
    }

    // ── Responses ─────────────────────────────────────────────────────────────

    @Data @Builder
    public static class KycDocumentResponse {
        private UUID id;
        private UUID userId;
        private String userEmail;
        private String userFullName;
        private KycDocument.DocumentType documentType;
        private String documentNumber;
        private String notes;
        private KycDocument.KycDocumentStatus status;
        private String rejectionReason;
        private String reviewedBy;
        private Instant reviewedAt;
        private Instant submittedAt;

        public static KycDocumentResponse from(KycDocument doc) {
            return KycDocumentResponse.builder()
                    .id(doc.getId())
                    .userId(doc.getUser().getId())
                    .userEmail(doc.getUser().getEmail())
                    .userFullName(doc.getUser().getFullName())
                    .documentType(doc.getDocumentType())
                    .documentNumber(doc.getDocumentNumber())
                    .notes(doc.getNotes())
                    .status(doc.getStatus())
                    .rejectionReason(doc.getRejectionReason())
                    .reviewedBy(doc.getReviewedBy())
                    .reviewedAt(doc.getReviewedAt())
                    .submittedAt(doc.getSubmittedAt())
                    .build();
        }
    }

    @Data @Builder
    public static class KycStatusResponse {
        private UUID userId;
        private User.KycStatus kycStatus;
        private KycDocumentResponse latestDocument;
    }
}