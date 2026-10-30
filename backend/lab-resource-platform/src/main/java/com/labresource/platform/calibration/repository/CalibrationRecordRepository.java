package com.labresource.platform.calibration.repository;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalibrationRecordRepository
        extends JpaRepository<CalibrationRecord, Long> {

    List<CalibrationRecord> findByEquipment_IdOrderByCalibrationDateDesc(
            Long equipmentId);
}
