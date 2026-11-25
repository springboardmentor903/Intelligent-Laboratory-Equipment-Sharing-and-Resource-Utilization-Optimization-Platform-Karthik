package com.labresource.platform.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDetailDTO {

    private InvoiceDTO invoice;
    private List<InvoiceItemDTO> items;
    private List<UsageChargeDTO> charges;
}
