package com.labresource.platform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HourlyPeakSlotDto {
    private Integer hour; // 0..23
    private String hourLabel; // "00:00", "01:00", ...
    private Double totalUsageHours;
    private Integer peakConcurrency;
    private Double averageConcurrency;
    private Double utilizationScore; // 0.0 - 100.0%
}
