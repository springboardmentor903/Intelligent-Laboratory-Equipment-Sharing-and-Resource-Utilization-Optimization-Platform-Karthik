package com.labresource.platform.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialSummaryDTO {

    // Issued invoices
    private BigDecimal issuedInvoiced;
    private BigDecimal issuedPaid;
    private BigDecimal issuedOutstanding;
    private BigDecimal issuedOverdue;

    // Received invoices
    private BigDecimal receivedInvoiced;
    private BigDecimal receivedPaid;
    private BigDecimal receivedOutstanding;
    private BigDecimal receivedOverdue;

    // Pending charges awaiting invoicing
    private BigDecimal pendingChargesAmount;
    private long pendingChargesCount;
}
