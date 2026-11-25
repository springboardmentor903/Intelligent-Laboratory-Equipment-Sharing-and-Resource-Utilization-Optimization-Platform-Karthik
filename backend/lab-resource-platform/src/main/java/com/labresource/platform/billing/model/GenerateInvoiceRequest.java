package com.labresource.platform.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateInvoiceRequest {

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private InvoiceType invoiceType; // INTER_INSTITUTION, DEPARTMENT_CHARGEBACK
    private Long recipientInstitutionId; // optional filter
    private Long recipientDepartmentId;  // optional filter
}
