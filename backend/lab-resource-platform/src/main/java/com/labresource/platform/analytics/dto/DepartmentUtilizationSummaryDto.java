package com.labresource.platform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentUtilizationSummaryDto {
    private Long departmentId;
    private String departmentName;
    private Long institutionId;
    private Integer equipmentCount;
    private Double totalCapacityHours;
    private Double actualUsageHours;
    private Long totalAvailableMinutes;
    private Long actualUsageMinutes;
    private Double averageUtilizationRate; // calculated from sum of raw used minutes / sum of available minutes
    private Double sharedUsageHours;
    private Double exclusiveUsageHours;
    private Double targetUtilizationRate;
    private Double targetGap;
}
