package com.labresource.platform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdleTimeReportDto {
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private Long departmentId;
    private String departmentName;
    private Double totalOperatingHours;
    private Double actualUsageHours;
    private Double idleHours;
    private Double idlePercentage;
    private LocalDateTime lastUsedAt;
    private Boolean idleAlert;
    private String alertLevel; // NORMAL, WARNING, CRITICAL
    private String alertMessage;
}
