package com.labresource.platform.sharing.repository;
import com.labresource.platform.sharing.entity.SharingAccessGrant; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.Optional;
public interface SharingAccessGrantRepository extends JpaRepository<SharingAccessGrant,Long>{ List<SharingAccessGrant> findByUserId(Long id); List<SharingAccessGrant> findByAgreementId(Long id); Optional<SharingAccessGrant> findByAgreementIdAndUserId(Long agreementId,Long userId); }
