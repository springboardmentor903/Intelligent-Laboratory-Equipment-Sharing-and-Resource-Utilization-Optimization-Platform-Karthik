package com.labresource.platform.calibration.repository;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CalibrationRecordRepository
        extends JpaRepository<CalibrationRecord, Long> {

    List<CalibrationRecord> findByEquipment_IdOrderByCalibrationDateDesc(Long equipmentId);

    List<CalibrationRecord> findByEquipment_IdOrderByCreatedAtDesc(Long equipmentId);

    Optional<CalibrationRecord> findFirstByEquipment_IdOrderByCalibrationDateDesc(Long equipmentId);

    Optional<CalibrationRecord> findFirstByEquipment_IdOrderByCreatedAtDesc(Long equipmentId);

    List<CalibrationRecord> findByEquipment_InstitutionIdOrderByNextDueDateAsc(Long institutionId);

    List<CalibrationRecord> findByAssignedToOrderByNextDueDateAsc(Long assignedTo);

    List<CalibrationRecord> findByStatus(String status);

    List<CalibrationRecord> findByNextDueDateBeforeAndStatusNot(LocalDate date, String status);

    List<CalibrationRecord> findByNextDueDateBetween(LocalDate start, LocalDate end);

    Optional<CalibrationRecord> findByWorkOrderId(Long workOrderId);
}
