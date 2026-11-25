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
public class InvoiceItemDTO {

    private Long id;
    private Long invoiceId;
    private Long chargeId;
    private Long equipmentId;
    private String equipmentName;
    private Long userId;
    private String userName;
    private Long usageMinutes;
    private BigDecimal usageHours;
    private BigDecimal hourlyRate;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
