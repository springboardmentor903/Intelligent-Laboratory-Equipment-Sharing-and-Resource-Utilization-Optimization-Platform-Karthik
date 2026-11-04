package com.labresource.platform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentUtilizationRateDto {
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private Long departmentId;
    private String departmentName;
    private Long institutionId;
    private Integer quantity;
    private Double totalCapacityHours;
    private Double actualUsageHours;
    private Long totalAvailableMinutes;
    private Long actualUsageMinutes;
    private Double utilizationRate; // 0.0 - 100.0% or null if available minutes == 0
    private Double exclusiveUsageHours;
    private Double sharedUsageHours;
    private Long sessionCount;
    private Double historicalBenchmarkRate; // previous fixed 28-day period rate %
    private Double trendPercentage; // delta vs benchmark (+/-)
    private Double targetUtilizationRate; // explicit configurable target % (null if not specified)
    private Double targetGap; // delta vs target (+/-)
}
