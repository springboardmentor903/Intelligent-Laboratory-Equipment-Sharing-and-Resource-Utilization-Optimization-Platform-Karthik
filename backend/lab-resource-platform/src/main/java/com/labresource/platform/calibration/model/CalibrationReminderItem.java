package com.labresource.platform.calibration.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalibrationReminderItem {

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private Long departmentId;
    private String departmentName;
    private LocalDate latestCalibrationDate;
    private LocalDate nextDueDate;
    private Long daysUntilDue;
    private String calibrationState; // EXPIRED, DUE_SOON
    private Long activeWorkOrderId;
    private String activeWorkOrderNumber;
    private String activeWorkOrderStatus;
}
