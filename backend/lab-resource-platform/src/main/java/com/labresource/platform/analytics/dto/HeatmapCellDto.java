package com.labresource.platform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapCellDto {
    private Integer dayOfWeek; // 1 = Monday ... 7 = Sunday
    private String dayName; // "Mon", "Tue", ...
    private Integer hour; // 0..23
    private Long usageMinutes;
    private Double utilizationScore; // 0.0 - 100.0%
    private Integer intensityLevel; // 0..4
    private Integer bookingCount;
}
