package com.labresource.platform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionUtilizationSummaryDto {
    private Long institutionId;
    private String institutionName;
    private Integer departmentCount;
    private Integer equipmentCount;
    private Double totalCapacityHours;
    private Double actualUsageHours;
    private Long totalAvailableMinutes;
    private Long actualUsageMinutes;
    private Double overallUtilizationRate; // calculated from sum of raw used minutes / sum of available minutes
    private Double historicalBenchmarkRate; // previous fixed 28-day period benchmark
    private Double trendPercentage;
    private Double sharedUsageHours;
    private Double exclusiveUsageHours;
    private Double targetUtilizationRate;
    private Double targetGap;
}
