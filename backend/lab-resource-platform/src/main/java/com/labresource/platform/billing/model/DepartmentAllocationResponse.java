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
public class DepartmentAllocationResponse {

    private List<DepartmentAllocationDTO> allocations;
    private DepartmentAllocationDTO unallocated;
    private BigDecimal grandTotalCost;
}
