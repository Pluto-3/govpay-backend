package com.govpay.govpay_backend.kyc.repository;

import com.govpay.govpay_backend.kyc.entity.KycDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByUserIdOrderBySubmittedAtDesc(UUID userId);

    Optional<KycDocument> findTopByUserIdOrderBySubmittedAtDesc(UUID userId);

    // Admin — list all pending submissions
    Page<KycDocument> findByStatus(KycDocument.KycDocumentStatus status, Pageable pageable);

    boolean existsByUserIdAndStatus(UUID userId, KycDocument.KycDocumentStatus status);

    // Check if user already has an approved document
    boolean existsByUserIdAndStatusIn(UUID userId, List<KycDocument.KycDocumentStatus> statuses);
}
