package com.labresource.platform.calibration.service;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class CalibrationRecordService {

    private static final List<String> VALID_RESULTS =
            List.of("PASS", "FAIL", "CONDITIONAL");

    private final CalibrationRecordRepository calibrationRecordRepository;
    private final EquipmentRepository equipmentRepository;

    public CalibrationRecordService(
            CalibrationRecordRepository calibrationRecordRepository,
            EquipmentRepository equipmentRepository) {
        this.calibrationRecordRepository = calibrationRecordRepository;
        this.equipmentRepository = equipmentRepository;
    }

    public List<CalibrationRecord> getByEquipment(Long equipmentId) {
        requireEquipment(equipmentId);
        return calibrationRecordRepository
                .findByEquipment_IdOrderByCalibrationDateDesc(equipmentId);
    }

    @Transactional
    public CalibrationRecord create(
            Long equipmentId, CalibrationRecord record) {
        Equipment equipment = requireEquipment(equipmentId);

        if (record.getCalibrationDate() == null
                || record.getNextDueDate() == null) {
            throw new IllegalArgumentException(
                    "Calibration date and next due date are required.");
        }
        if (!record.getNextDueDate().isAfter(record.getCalibrationDate())) {
            throw new IllegalArgumentException(
                    "Next due date must be after the calibration date.");
        }
        if (record.getResult() == null || record.getResult().isBlank()) {
            throw new IllegalArgumentException("Calibration result is required.");
        }

        String result = record.getResult().trim().toUpperCase(Locale.ROOT);
        if (!VALID_RESULTS.contains(result)) {
            throw new IllegalArgumentException(
                    "Calibration result must be PASS, FAIL, or CONDITIONAL.");
        }
        if (record.getNotes() != null && record.getNotes().length() > 1000) {
            throw new IllegalArgumentException(
                    "Calibration notes must be 1000 characters or fewer.");
        }

        record.setId(null);
        record.setEquipment(equipment);
        record.setResult(result);
        record.setCreatedAt(LocalDateTime.now());
        if (record.getNotes() != null) {
            record.setNotes(record.getNotes().trim());
        }
        return calibrationRecordRepository.save(record);
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
