package com.labresource.platform.maintenance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteWorkOrderRequest {

    private String resolutionNotes;
    private BigDecimal cost;

    // Optional calibration details
    private LocalDate calibrationDate;
    private String result; // PASS, FAIL, CONDITIONAL
    private LocalDate nextDueDate;
    private String certificateNumber;
    private String calibrationNotes;
    private Long documentId;

    // Optional report document
    private Long reportDocumentId;
}
