package com.labresource.platform.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentAllocationDTO {

    private Long departmentId;
    private String departmentName;
    private BigDecimal internalCost;
    private BigDecimal externalCost;
    private BigDecimal totalCost;
    private BigDecimal percentageShare;
    private long chargeCount;
}
