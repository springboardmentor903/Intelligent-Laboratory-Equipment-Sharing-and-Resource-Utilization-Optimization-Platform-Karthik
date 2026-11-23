package com.labresource.platform.calibration.service;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
        record.setStatus("COMPLETED");
        record.setCreatedAt(LocalDateTime.now());
        if (record.getNotes() != null) {
            record.setNotes(record.getNotes().trim());
        }
        return calibrationRecordRepository.save(record);
    }

    public List<CalibrationRecord> getAll(Long institutionId) {
        if (institutionId != null) {
            return calibrationRecordRepository.findByEquipment_InstitutionIdOrderByNextDueDateAsc(institutionId);
        }
        return calibrationRecordRepository.findAll();
    }

    public List<CalibrationRecord> getByTechnician(Long technicianId) {
        if (technicianId == null) return List.of();
        return calibrationRecordRepository.findByAssignedToOrderByNextDueDateAsc(technicianId);
    }

    public List<CalibrationRecord> getDueCalibrations(Long institutionId, int daysAhead) {
        LocalDate cutoff = LocalDate.now().plusDays(daysAhead > 0 ? daysAhead : 30);
        List<CalibrationRecord> records = getAll(institutionId);
        return records.stream()
                .filter(r -> r.getNextDueDate() != null && !r.getNextDueDate().isAfter(cutoff))
                .toList();
    }

    @Transactional
    public CalibrationRecord schedule(Long equipmentId, CalibrationRecord record) {
        Equipment equipment = requireEquipment(equipmentId);

        LocalDate dueDate = record.getNextDueDate() != null ? record.getNextDueDate() : record.getScheduledDate();
        if (dueDate == null) {
            throw new IllegalArgumentException("A scheduled date or next due date is required.");
        }

        record.setId(null);
        record.setEquipment(equipment);
        record.setNextDueDate(dueDate);
        record.setScheduledDate(record.getScheduledDate() != null ? record.getScheduledDate() : dueDate);
        record.setStatus("SCHEDULED");
        record.setResult(null);
        record.setCalibrationDate(null);
        record.setCreatedAt(LocalDateTime.now());
        if (record.getNotes() != null) {
            record.setNotes(record.getNotes().trim());
        }
        return calibrationRecordRepository.save(record);
    }

    @Transactional
    public CalibrationRecord complete(Long id, LocalDate calibrationDate, String result,
                                      LocalDate nextDueDate, String notes, String certificateNumber) {
        CalibrationRecord record = calibrationRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Calibration record not found with ID: " + id));

        LocalDate completedDate = calibrationDate != null ? calibrationDate : LocalDate.now();

        if (nextDueDate == null) {
            throw new IllegalArgumentException("Next due date is required.");
        }
        if (!nextDueDate.isAfter(completedDate)) {
            throw new IllegalArgumentException("Next due date must be after the calibration date.");
        }
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("Calibration result is required.");
        }

        String normalizedResult = result.trim().toUpperCase(Locale.ROOT);
        if (!VALID_RESULTS.contains(normalizedResult)) {
            throw new IllegalArgumentException("Calibration result must be PASS, FAIL, or CONDITIONAL.");
        }

        record.setCalibrationDate(completedDate);
        record.setNextDueDate(nextDueDate);
        record.setResult(normalizedResult);
        record.setStatus("COMPLETED");
        if (notes != null && !notes.isBlank()) {
            record.setNotes(notes.trim());
        }
        if (certificateNumber != null && !certificateNumber.isBlank()) {
            record.setCertificateNumber(certificateNumber.trim());
        }

        return calibrationRecordRepository.save(record);
    }

    public java.util.Map<String, Object> getSummary(Long institutionId) {
        List<CalibrationRecord> records = getAll(institutionId);
        LocalDate today = LocalDate.now();
        LocalDate dueSoonCutoff = today.plusDays(30);

        long total = records.size();
        long passed = records.stream().filter(r -> "PASS".equalsIgnoreCase(r.getResult())).count();
        long failed = records.stream().filter(r -> "FAIL".equalsIgnoreCase(r.getResult())).count();
        long conditional = records.stream().filter(r -> "CONDITIONAL".equalsIgnoreCase(r.getResult())).count();
        long scheduled = records.stream().filter(r -> "SCHEDULED".equalsIgnoreCase(r.getStatus())).count();
        long overdue = records.stream().filter(r -> r.getNextDueDate() != null && r.getNextDueDate().isBefore(today)).count();
        long dueSoon = records.stream().filter(r -> r.getNextDueDate() != null
                && !r.getNextDueDate().isBefore(today)
                && !r.getNextDueDate().isAfter(dueSoonCutoff)).count();

        java.util.Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("total", total);
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("conditional", conditional);
        summary.put("scheduled", scheduled);
        summary.put("overdue", overdue);
        summary.put("dueSoon", dueSoon);
        return summary;
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
