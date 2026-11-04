package com.labresource.platform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeakUsagePatternDto {
    private List<HourlyPeakSlotDto> timeSlots;
    private List<HourlyPeakSlotDto> topFivePeakSlots;
    private String busiestDayOfWeek; // e.g. "WEDNESDAY"
    private Integer busiestHourOfDay; // 0..23
    private Integer maxPeakConcurrency;
    private Integer totalCapacityUnits;
    private Double capacitySaturationRate; // percentage
}
