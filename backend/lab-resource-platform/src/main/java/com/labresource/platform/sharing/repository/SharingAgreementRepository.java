package com.labresource.platform.sharing.repository;

import com.labresource.platform.sharing.entity.SharingAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SharingAgreementRepository extends JpaRepository<SharingAgreement, Long> {
    List<SharingAgreement> findByPartnerInstitutionId(Long id);
    List<SharingAgreement> findByOwnerInstitutionId(Long id);
    List<SharingAgreement> findByOwnerInstitutionIdAndPartnerInstitutionIdIn(Long ownerId, List<Long> partnerIds);
}
