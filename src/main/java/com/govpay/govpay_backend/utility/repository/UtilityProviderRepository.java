package com.govpay.govpay_backend.utility.repository;

import com.govpay.govpay_backend.utility.entity.UtilityProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UtilityProviderRepository extends JpaRepository<UtilityProvider, UUID> {

    List<UtilityProvider> findByIsActiveTrue();

    Optional<UtilityProvider> findByCode(String code);

    List<UtilityProvider> findByTypeAndIsActiveTrue(UtilityProvider.UtilityType type);
}