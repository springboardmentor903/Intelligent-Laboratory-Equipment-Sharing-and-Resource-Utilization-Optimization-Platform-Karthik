package com.labresource.platform.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageChargeDTO {

    private Long id;
    private Long bookingId;
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;

    private Long userId;
    private String userName;
    private String userEmail;

    private Long institutionId;
    private String institutionName;

    private Long userInstitutionId;
    private String userInstitutionName;

    private Long departmentId;
    private String departmentName;

    private ChargeType chargeType;
    private BigDecimal hourlyRate;
    private Long usageMinutes;
    private BigDecimal usageHours;
    private BigDecimal amount;
    private ChargeStatus status;

    private Long invoiceId;
    private String invoiceNumber;
    private Long sharingAgreementId;
    private LocalDateTime createdAt;
}
