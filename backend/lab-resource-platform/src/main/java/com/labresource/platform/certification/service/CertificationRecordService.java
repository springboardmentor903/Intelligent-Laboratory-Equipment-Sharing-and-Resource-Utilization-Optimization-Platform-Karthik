package com.labresource.platform.certification.service;

import com.labresource.platform.certification.entity.CertificationRecord;
import com.labresource.platform.certification.repository.CertificationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CertificationRecordService {

    private final CertificationRecordRepository certificationRecordRepository;
    private final EquipmentRepository equipmentRepository;

    public CertificationRecordService(
            CertificationRecordRepository certificationRecordRepository,
            EquipmentRepository equipmentRepository) {
        this.certificationRecordRepository = certificationRecordRepository;
        this.equipmentRepository = equipmentRepository;
    }

    public List<CertificationRecord> getByEquipment(Long equipmentId) {
        requireEquipment(equipmentId);
        return certificationRecordRepository
                .findByEquipment_IdOrderByIssueDateDesc(equipmentId);
    }

    @Transactional
    public CertificationRecord create(
            Long equipmentId, CertificationRecord record) {
        Equipment equipment = requireEquipment(equipmentId);

        if (record.getCertificateType() == null
                || record.getCertificateType().isBlank()
                || record.getIssuingOrganization() == null
                || record.getIssuingOrganization().isBlank()) {
            throw new IllegalArgumentException(
                    "Certificate type and issuing organization are required.");
        }
        if (record.getIssueDate() == null || record.getExpiryDate() == null) {
            throw new IllegalArgumentException(
                    "Issue date and expiry date are required.");
        }
        if (!record.getExpiryDate().isAfter(record.getIssueDate())) {
            throw new IllegalArgumentException(
                    "Expiry date must be after the issue date.");
        }
        if (record.getCertificateType().trim().length() > 150
                || record.getIssuingOrganization().trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Certificate type or issuing organization is too long.");
        }
        if (record.getDocumentReference() != null
                && record.getDocumentReference().length() > 500) {
            throw new IllegalArgumentException(
                    "Document reference must be 500 characters or fewer.");
        }

        record.setId(null);
        record.setEquipment(equipment);
        record.setCertificateType(record.getCertificateType().trim());
        record.setIssuingOrganization(record.getIssuingOrganization().trim());
        if (record.getDocumentReference() != null) {
            record.setDocumentReference(record.getDocumentReference().trim());
        }
        record.setCreatedAt(LocalDateTime.now());
        return certificationRecordRepository.save(record);
    }

    private Equipment requireEquipment(Long equipmentId) {
        if (equipmentId == null) {
            throw new IllegalArgumentException("Equipment ID is required.");
        }
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Equipment not found with ID: " + equipmentId));
    }
}
