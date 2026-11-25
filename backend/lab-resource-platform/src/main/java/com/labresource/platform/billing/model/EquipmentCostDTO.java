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
public class EquipmentCostDTO {

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private Long departmentId;
    private String departmentName;

    private Long usageMinutes;
    private BigDecimal usageHours;
    private BigDecimal internalCost;
    private BigDecimal externalCost;
    private BigDecimal unassignedCost;
    private BigDecimal totalUsageCost;
    private BigDecimal maintenanceCost;
}
