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
public class CalibrationOverviewItem {

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private Long departmentId;
    private String departmentName;
    private Long institutionId;
    private LocalDate latestCalibrationDate;
    private LocalDate nextDueDate;
    private Long daysUntilDue;
    private String calibrationState; // VALID, DUE_SOON, EXPIRED, NONE
    private String latestResult;
    private String latestCertificateNumber;
    private Long latestCertificateDocumentId;
    private String latestCertificateReference;
    private Long activeWorkOrderId;
    private String activeWorkOrderNumber;
    private String activeWorkOrderStatus;
}
