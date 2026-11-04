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
public class HeatmapDataDto {
    private List<HeatmapCellDto> matrix; // 168 cells
    private Double averageUtilizationRate;
    private Long totalUsageHours;
    private Integer totalActiveBookings;
}
