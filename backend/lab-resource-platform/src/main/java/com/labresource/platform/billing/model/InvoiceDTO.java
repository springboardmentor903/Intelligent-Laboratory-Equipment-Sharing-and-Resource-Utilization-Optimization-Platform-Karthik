package com.labresource.platform.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDTO {

    private Long id;
    private String invoiceNumber;
    private Long institutionId;
    private String institutionName;

    private Long recipientInstitutionId;
    private String recipientInstitutionName;

    private Long recipientDepartmentId;
    private String recipientDepartmentName;

    private InvoiceType invoiceType;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    private BigDecimal subtotal;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;

    private LocalDate dueDate;
    private boolean overdue;

    private InvoiceStatus status;
    private long chargeCount;

    private LocalDateTime issuedAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
}
