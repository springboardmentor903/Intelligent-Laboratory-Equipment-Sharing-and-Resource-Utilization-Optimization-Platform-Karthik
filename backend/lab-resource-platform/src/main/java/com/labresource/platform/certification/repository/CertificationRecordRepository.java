package com.labresource.platform.certification.repository;

import com.labresource.platform.certification.entity.CertificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificationRecordRepository
        extends JpaRepository<CertificationRecord, Long> {

    List<CertificationRecord> findByEquipment_IdOrderByIssueDateDesc(
            Long equipmentId);
}
