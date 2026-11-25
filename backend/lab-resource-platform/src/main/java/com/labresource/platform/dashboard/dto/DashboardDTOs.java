package com.labresource.platform.dashboard.dto;

import com.labresource.platform.notification.dto.NotificationDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DashboardDTOs {

    private DashboardDTOs() {}

    public record BookingSummaryDTO(
            Long id,
            Long equipmentId,
            String equipmentName,
            String assetTag,
            String departmentName,
            Long userId,
            String userFullName,
            String userEmail,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status,
            String purpose,
            LocalDateTime createdAt
    ) {}

    public record ResearcherUsageSummaryDTO(
            Map<String, Long> bookingsByStatus,
            double hoursUsed,
            long noShowCount,
            BigDecimal usageCost
    ) {}

    public record WaitlistOfferDTO(
            Long waitlistId,
            Long equipmentId,
            String equipmentName,
            LocalDateTime requestedStartTime,
            LocalDateTime requestedEndTime,
            LocalDateTime offerExpiresAt,
            String status
    ) {}

    public record ResearcherWaitlistDTO(
            long waitingCount,
            long offeredCount,
            List<WaitlistOfferDTO> activeOffers
    ) {}

    public record EquipmentRecommendationDTO(
            Long equipmentId,
            String equipmentName,
            String assetTag,
            String category,
            BigDecimal hourlyRate,
            String status,
            String reason
    ) {}

    public record ResearcherDashboardResponse(
            List<BookingSummaryDTO> nextBookings,
            Map<String, Long> availabilityOverview,
            ResearcherUsageSummaryDTO usageSummary,
            ResearcherWaitlistDTO waitlist,
            long unreadNotificationCount,
            List<NotificationDTO> latestNotifications,
            List<EquipmentRecommendationDTO> recommendations
    ) {}

    public record UtilizationTargetDTO(
            double averageUtilization,
            double targetUtilization,
            double gap
    ) {}

    public record HighDemandEquipmentDTO(
            Long equipmentId,
            String equipmentName,
            String category,
            long bookingCount,
            double utilizationRate,
            long waitlistCount
    ) {}

    public record MaintenanceEquipmentDTO(
            Long equipmentId,
            String equipmentName,
            String assetTag,
            String status,
            String departmentName,
            LocalDateTime since
    ) {}

    public record PreventiveScheduleAlertDTO(
            Long scheduleId,
            Long equipmentId,
            String equipmentName,
            String frequency,
            LocalDate nextDueDate,
            boolean isOverdue,
            long daysUntilDue
    ) {}

    public record ManagerDashboardResponse(
            int reportingDays,
            String scope,
            Long departmentId,
            UtilizationTargetDTO utilizationAgainstTarget,
            List<Map<String, Object>> utilizationHeatmap,
            double bookingAdoptionPercentage,
            double noShowRate,
            List<HighDemandEquipmentDTO> highDemandEquipment,
            Map<String, Long> openWorkOrdersByStatus,
            List<MaintenanceEquipmentDTO> equipmentUnderMaintenance,
            List<PreventiveScheduleAlertDTO> preventiveSchedulesDueSoon,
            Map<String, Long> calibrationOverviewCounts,
            List<BookingSummaryDTO> pendingBookingApprovals,
            List<Map<String, Object>> departmentSharingRequests
    ) {}

    public record MonthlyTrendDTO(
            String month,
            int year,
            double averageUtilization,
            int sessionCount,
            double totalOperatingHours,
            double totalUsedHours
    ) {}

    public record SharingAnalyticsSummaryDTO(
            long totalSharingRequests,
            long approvedAgreements,
            double sharedBookingHours,
            BigDecimal accruedSharingRevenue
    ) {}

    public record DepartmentPairDTO(
            String equipmentDepartmentName,
            String userDepartmentName,
            double hoursUsed,
            long bookingCount
    ) {}

    public record CrossDepartmentSharingDTO(
            double totalCrossDepartmentHours,
            List<DepartmentPairDTO> topDepartmentPairs
    ) {}

    public record CostAndBudgetSummaryDTO(
            BigDecimal totalUsageCost,
            BigDecimal totalBudgetAllocated,
            BigDecimal totalBudgetSpent,
            double budgetUtilizationPercentage
    ) {}

    public record DepartmentUtilizationItemDTO(
            Long departmentId,
            String departmentName,
            double averageUtilization,
            double targetUtilization,
            double gap
    ) {}

    public record IdleEquipmentItemDTO(
            Long equipmentId,
            String equipmentName,
            String assetTag,
            String departmentName,
            long daysIdle,
            LocalDateTime lastUsedAt
    ) {}

    public record InstitutionDashboardResponse(
            Long institutionId,
            List<DepartmentUtilizationItemDTO> departmentUtilization,
            List<MonthlyTrendDTO> monthlyUtilizationTrends,
            List<IdleEquipmentItemDTO> idleEquipmentAlerts,
            SharingAnalyticsSummaryDTO interInstitutionSharingAnalytics,
            CrossDepartmentSharingDTO crossDepartmentSharing,
            CostAndBudgetSummaryDTO costAndBudgetSummary
    ) {}

    public record ProcurementRecommendationDTO(
            String reasonCode,
            String targetType,
            String categoryName,
            Long equipmentId,
            String equipmentName,
            String assetTag,
            Map<String, Object> metrics,
            String recommendationMessage
    ) {}

    public record EquipmentLifecycleDTO(
            Long equipmentId,
            String equipmentName,
            String assetTag,
            String departmentName,
            LocalDate purchaseDate,
            Integer ageMonths,
            String currentStatus,
            String latestCalibrationState,
            long workOrderCount,
            double totalDowntimeHours,
            BigDecimal purchaseCost,
            BigDecimal maintenanceCost,
            BigDecimal usageCharges,
            Double roiPercentage
    ) {}

    public record InstitutionUserDTO(
            Long id,
            String fullName,
            String email,
            Long departmentId,
            String departmentName,
            List<String> roles,
            String membershipStatus,
            Boolean membershipApproved,
            LocalDateTime createdAt
    ) {}

    public record SystemMonitoringResponse(
            long totalUsers,
            long totalEquipment,
            long totalBookings,
            long equipmentUnderMaintenanceCount,
            long failedNotificationsLast7Days,
            long pendingDepartmentHeadCount,
            LocalDate lastUtilizationSnapshotDate,
            List<InstitutionUserDTO> users
    ) {}
}
