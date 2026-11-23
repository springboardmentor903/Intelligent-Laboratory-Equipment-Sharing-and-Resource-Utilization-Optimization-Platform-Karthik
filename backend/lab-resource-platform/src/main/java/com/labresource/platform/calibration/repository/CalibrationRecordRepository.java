package com.labresource.platform.calibration.repository;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CalibrationRecordRepository
        extends JpaRepository<CalibrationRecord, Long> {

    List<CalibrationRecord> findByEquipment_IdOrderByCalibrationDateDesc(
            Long equipmentId);

    List<CalibrationRecord> findByEquipment_InstitutionIdOrderByNextDueDateAsc(
            Long institutionId);

    List<CalibrationRecord> findByAssignedToOrderByNextDueDateAsc(
            Long assignedTo);

    List<CalibrationRecord> findByStatus(String status);

    List<CalibrationRecord> findByNextDueDateBeforeAndStatusNot(LocalDate date, String status);

    List<CalibrationRecord> findByNextDueDateBetween(LocalDate start, LocalDate end);
}
