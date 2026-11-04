package com.labresource.platform.analytics.controller;

import com.labresource.platform.analytics.dto.*;
import com.labresource.platform.analytics.service.DailyUtilizationSnapshotService;
import com.labresource.platform.analytics.service.UtilizationAnalyticsService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/analytics", "/api/utilization/analytics"})
public class AnalyticsController {

    private final UtilizationAnalyticsService analyticsService;
    private final ResourceAccessPolicy resourceAccess;

    @Autowired(required = false)
    private DailyUtilizationSnapshotService snapshotService;

    public AnalyticsController(
            UtilizationAnalyticsService analyticsService,
            ResourceAccessPolicy resourceAccess) {
        this.analyticsService = analyticsService;
        this.resourceAccess = resourceAccess;
    }

    public void setSnapshotService(DailyUtilizationSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/utilization/equipment")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<EquipmentUtilizationRateDto>> getEquipmentUtilization(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Double operatingHoursPerDay,
            @RequestParam(required = false) Double targetUtilizationRate,
            Authentication auth) {

        Long scopedInstitution = resourceAccess.scopedInstitutionId(institutionId, auth);
        return ResponseEntity.ok(analyticsService.getEquipmentUtilizationRates(
                scopedInstitution, departmentId, start, end, operatingHoursPerDay, targetUtilizationRate));
    }

    @GetMapping("/utilization/departments")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<DepartmentUtilizationSummaryDto>> getDepartmentUtilization(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Double operatingHoursPerDay,
            @RequestParam(required = false) Double targetUtilizationRate,
            Authentication auth) {

        Long scopedInstitution = resourceAccess.scopedInstitutionId(institutionId, auth);
        return ResponseEntity.ok(analyticsService.getDepartmentUtilization(
                scopedInstitution, start, end, operatingHoursPerDay, targetUtilizationRate));
    }

    @GetMapping("/utilization/institution")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<InstitutionUtilizationSummaryDto> getInstitutionUtilization(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Double operatingHoursPerDay,
            @RequestParam(required = false) Double targetUtilizationRate,
            Authentication auth) {

        Long scopedInstitution = resourceAccess.scopedInstitutionId(institutionId, auth);
        return ResponseEntity.ok(analyticsService.getInstitutionUtilization(
                scopedInstitution, start, end, operatingHoursPerDay, targetUtilizationRate));
    }

    @GetMapping("/demand")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<DemandAnalysisDto> getDemandAnalysis(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Authentication auth) {

        Long scopedInstitution = resourceAccess.scopedInstitutionId(institutionId, auth);
        return ResponseEntity.ok(analyticsService.getDemandAnalysis(
                scopedInstitution, departmentId, equipmentId, start, end));
    }

    @GetMapping("/idle-time")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<IdleTimeReportDto>> getIdleTimeReports(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Double operatingHoursPerDay,
            @RequestParam(required = false) Double thresholdPercent,
            Authentication auth) {

        Long scopedInstitution = resourceAccess.scopedInstitutionId(institutionId, auth);
        // Idle alerts mandatory rule: always evaluate last 7 completed calendar days ending yesterday; ignore dashboard filters.
        return ResponseEntity.ok(analyticsService.getIdleTimeReports(
                scopedInstitution, departmentId, null, null, operatingHoursPerDay, thresholdPercent));
    }

    @GetMapping("/peak-usage")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<PeakUsagePatternDto> getPeakUsage(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Authentication auth) {

        Long scopedInstitution = resourceAccess.scopedInstitutionId(institutionId, auth);
        return ResponseEntity.ok(analyticsService.getPeakUsagePattern(
                scopedInstitution, departmentId, equipmentId, start, end));
    }

    @GetMapping("/heatmap")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<HeatmapDataDto> getHeatmap(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Authentication auth) {

        Long scopedInstitution = resourceAccess.scopedInstitutionId(institutionId, auth);
        return ResponseEntity.ok(analyticsService.getHeatmapData(
                scopedInstitution, departmentId, equipmentId, start, end));
    }

    @PostMapping("/snapshots/run")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<?> runDailySnapshot() {
        if (snapshotService == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Snapshot service unavailable"));
        }
        snapshotService.runDailySnapshot();
        return ResponseEntity.ok(Map.of("message", "Daily snapshot completed successfully"));
    }

    @PostMapping("/snapshots/backfill")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<?> backfillSnapshots(@RequestParam(defaultValue = "90") int days) {
        if (snapshotService == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Snapshot service unavailable"));
        }
        int created = snapshotService.backfillSnapshots(days);
        return ResponseEntity.ok(Map.of("message", "Backfill completed successfully", "snapshotsCreated", created));
    }
}
