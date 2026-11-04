package com.labresource.platform.analytics;

import com.labresource.platform.analytics.controller.AnalyticsController;
import com.labresource.platform.analytics.dto.*;
import com.labresource.platform.analytics.service.UtilizationAnalyticsService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTests {

    @Mock private UtilizationAnalyticsService service;
    @Mock private ResourceAccessPolicy resourceAccess;

    private AnalyticsController controller;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsController(service, resourceAccess);
        auth = mock(Authentication.class);
    }

    @Test
    void getEquipmentUtilizationScopesInstitutionAndReturns200() {
        when(resourceAccess.scopedInstitutionId(eq(5L), eq(auth))).thenReturn(5L);
        EquipmentUtilizationRateDto dto = EquipmentUtilizationRateDto.builder()
                .equipmentId(12L)
                .equipmentName("Laser Scanner")
                .utilizationRate(45.5)
                .build();
        when(service.getEquipmentUtilizationRates(eq(5L), eq(null), any(), any(), any(), any()))
                .thenReturn(List.of(dto));

        ResponseEntity<List<EquipmentUtilizationRateDto>> response = controller.getEquipmentUtilization(
                5L, null, null, null, null, null, auth);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getFirst().getEquipmentName()).isEqualTo("Laser Scanner");
    }

    @Test
    void getDemandAnalysisReturnsMetrics() {
        when(resourceAccess.scopedInstitutionId(eq(null), eq(auth))).thenReturn(1L);
        DemandAnalysisDto demand = DemandAnalysisDto.builder()
                .totalRequests(10L)
                .rejectionRate(10.0)
                .build();
        when(service.getDemandAnalysis(eq(1L), any(), any(), any(), any()))
                .thenReturn(demand);

        ResponseEntity<DemandAnalysisDto> response = controller.getDemandAnalysis(
                null, null, null, null, null, auth);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getTotalRequests()).isEqualTo(10L);
    }

    @Test
    void getHeatmapReturnsMatrix() {
        when(resourceAccess.scopedInstitutionId(eq(null), eq(auth))).thenReturn(1L);
        HeatmapDataDto heatmap = HeatmapDataDto.builder()
                .matrix(Collections.emptyList())
                .averageUtilizationRate(15.2)
                .build();
        when(service.getHeatmapData(eq(1L), any(), any(), any(), any()))
                .thenReturn(heatmap);

        ResponseEntity<HeatmapDataDto> response = controller.getHeatmap(
                null, null, null, null, null, auth);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getAverageUtilizationRate()).isEqualTo(15.2);
    }

    @Test
    void getIdleTimeReportsForcesMandatoryWindowAndReturnsAlerts() {
        when(resourceAccess.scopedInstitutionId(eq(10L), eq(auth))).thenReturn(10L);
        IdleTimeReportDto report = IdleTimeReportDto.builder()
                .equipmentId(101L)
                .equipmentName("Centrifuge X")
                .idlePercentage(92.0)
                .alertLevel("CRITICAL")
                .alertMessage("High idle time. Consider sharing.")
                .build();
        when(service.getIdleTimeReports(eq(10L), eq(2L), eq(null), eq(null), eq(8.0), eq(70.0)))
                .thenReturn(List.of(report));

        LocalDateTime requestedStart = LocalDateTime.now().minusDays(30);
        LocalDateTime requestedEnd = LocalDateTime.now();

        ResponseEntity<List<IdleTimeReportDto>> response = controller.getIdleTimeReports(
                10L, 2L, requestedStart, requestedEnd, 8.0, 70.0, auth);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getFirst().getAlertLevel()).isEqualTo("CRITICAL");
    }
}
