package com.labresource.platform.dashboard;

import com.labresource.platform.analytics.entity.DailyUtilizationSnapshot;
import com.labresource.platform.analytics.repository.DailyUtilizationSnapshotRepository;
import com.labresource.platform.billing.entity.DepartmentBudget;
import com.labresource.platform.billing.repository.DepartmentBudgetRepository;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.booking.waitlist.entity.WaitlistEntry;
import com.labresource.platform.booking.waitlist.repository.WaitlistRepository;
import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.dashboard.dto.DashboardDTOs.*;
import com.labresource.platform.dashboard.service.DashboardService;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.maintenance.entity.EquipmentDowntimeLog;
import com.labresource.platform.maintenance.entity.PreventiveMaintenanceSchedule;
import com.labresource.platform.maintenance.entity.WorkOrder;
import com.labresource.platform.maintenance.repository.EquipmentDowntimeLogRepository;
import com.labresource.platform.maintenance.repository.PreventiveMaintenanceScheduleRepository;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import com.labresource.platform.notification.entity.Notification;
import com.labresource.platform.notification.model.DeliveryStatus;
import com.labresource.platform.notification.model.NotificationType;
import com.labresource.platform.notification.model.ReferenceType;
import com.labresource.platform.notification.repository.NotificationDeliveryRepository;
import com.labresource.platform.notification.repository.NotificationRepository;
import com.labresource.platform.sharing.entity.SharingAgreement;
import com.labresource.platform.sharing.entity.SharingRequest;
import com.labresource.platform.sharing.repository.SharingAgreementRepository;
import com.labresource.platform.sharing.repository.SharingRequestRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {

    @Mock private UserRepository userRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationDeliveryRepository notificationDeliveryRepository;
    @Mock private UsageChargeRepository usageChargeRepository;
    @Mock private DepartmentBudgetRepository departmentBudgetRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private PreventiveMaintenanceScheduleRepository preventiveScheduleRepository;
    @Mock private CalibrationRecordRepository calibrationRecordRepository;
    @Mock private EquipmentDowntimeLogRepository downtimeLogRepository;
    @Mock private DailyUtilizationSnapshotRepository snapshotRepository;
    @Mock private SharingRequestRepository sharingRequestRepository;
    @Mock private SharingAgreementRepository sharingAgreementRepository;
    @Mock private UserService userService;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                userRepository,
                equipmentRepository,
                bookingRepository,
                waitlistRepository,
                notificationRepository,
                notificationDeliveryRepository,
                usageChargeRepository,
                departmentBudgetRepository,
                departmentRepository,
                workOrderRepository,
                preventiveScheduleRepository,
                calibrationRecordRepository,
                downtimeLogRepository,
                snapshotRepository,
                sharingRequestRepository,
                sharingAgreementRepository,
                userService
        );
    }

    @Test
    void getResearcherDashboard_assemblesAllSectionsAccurately() {
        User user = new User();
        user.setId(10L);
        user.setEmail("researcher@test.edu");
        user.setFullName("Dr. Researcher");
        user.setInstitutionId(1L);

        Equipment eq = new Equipment();
        eq.setId(101L);
        eq.setName("Electron Microscope");
        eq.setAssetTag("EM-01");
        eq.setCategory("Microscopy");
        eq.setStatus("AVAILABLE");
        eq.setHourlyRate(BigDecimal.valueOf(150.0));

        Booking b1 = new Booking();
        b1.setId(501L);
        b1.setEquipmentId(101L);
        b1.setUserId(10L);
        b1.setStatus("CONFIRMED");
        b1.setStartTime(LocalDateTime.now().plusHours(2));
        b1.setEndTime(LocalDateTime.now().plusHours(4));

        when(bookingRepository.findUpcomingUserBookings(eq(10L), anyList(), any(), any()))
                .thenReturn(List.of(b1));
        when(equipmentRepository.findAllById(anyList())).thenReturn(List.of(eq));
        when(equipmentRepository.findByInstitutionId(1L)).thenReturn(List.of(eq));

        Booking bPast = new Booking();
        bPast.setId(401L);
        bPast.setEquipmentId(101L);
        bPast.setUserId(10L);
        bPast.setStatus("COMPLETED");
        bPast.setStartTime(LocalDateTime.now().minusDays(5));
        bPast.setEndTime(LocalDateTime.now().minusDays(5).plusHours(2));

        when(bookingRepository.findUserBookingsSince(eq(10L), any())).thenReturn(List.of(bPast));
        when(usageChargeRepository.sumUserChargesSince(eq(10L), any())).thenReturn(BigDecimal.valueOf(300.0));

        WaitlistEntry we = new WaitlistEntry();
        we.setId(801L);
        we.setUserId(10L);
        we.setEquipmentId(101L);
        we.setStatus("WAITING");
        when(waitlistRepository.findByUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(we));

        when(notificationRepository.countByRecipientIdAndReadFalse(10L)).thenReturn(2L);

        Notification n = new Notification(10L, NotificationType.BOOKING_CONFIRMED, "Booking Confirmed", "Your booking was confirmed", ReferenceType.BOOKING, 501L);
        when(notificationRepository.findByRecipientId(eq(10L), any()))
                .thenReturn(new PageImpl<>(List.of(n)));

        ResearcherDashboardResponse response = dashboardService.getResearcherDashboard(user);

        assertNotNull(response);
        assertEquals(1, response.nextBookings().size());
        assertEquals("Electron Microscope", response.nextBookings().get(0).equipmentName());
        assertTrue(response.availabilityOverview().containsKey("AVAILABLE"));
        assertEquals(2.0, response.usageSummary().hoursUsed());
        assertEquals(BigDecimal.valueOf(300.0), response.usageSummary().usageCost());
        assertEquals(1L, response.waitlist().waitingCount());
        assertEquals(2L, response.unreadNotificationCount());
        assertFalse(response.recommendations().isEmpty());
    }

    @Test
    void getManagerDashboard_calculatesUtilizationAndRespectsDepartmentHeadScope() {
        User deptHead = new User();
        deptHead.setId(20L);
        deptHead.setInstitutionId(1L);
        deptHead.setDepartmentId(5L);

        when(userService.getRoleNames(20L)).thenReturn(List.of("DEPARTMENT_HEAD"));

        Equipment eq = new Equipment();
        eq.setId(102L);
        eq.setName("Centrifuge");
        eq.setDepartmentId(5L);
        eq.setStatus("AVAILABLE");

        when(equipmentRepository.findByDepartmentId(5L)).thenReturn(List.of(eq));

        Booking b = new Booking();
        b.setId(601L);
        b.setEquipmentId(102L);
        b.setStatus("COMPLETED");
        b.setStartTime(LocalDateTime.now().minusDays(2));
        b.setEndTime(LocalDateTime.now().minusDays(2).plusHours(3));

        when(bookingRepository.findByEquipmentIdInAndPeriod(anyList(), any(), any())).thenReturn(List.of(b));

        ManagerDashboardResponse response = dashboardService.getManagerDashboard(deptHead, 30, null);

        assertNotNull(response);
        assertEquals("DEPARTMENT", response.scope());
        assertEquals(5L, response.departmentId());
        assertTrue(response.bookingAdoptionPercentage() > 0);
        assertNotNull(response.utilizationAgainstTarget());
    }

    @Test
    void getInstitutionDashboard_aggregatesCrossDeptSharingAndBudgets() {
        Long institutionId = 1L;

        Department d1 = new Department();
        d1.setId(10L);
        d1.setName("Biology");
        d1.setInstitutionId(1L);

        Department d2 = new Department();
        d2.setId(20L);
        d2.setName("Physics");
        d2.setInstitutionId(1L);

        when(departmentRepository.findByInstitutionId(1L)).thenReturn(List.of(d1, d2));

        Equipment eq = new Equipment();
        eq.setId(201L);
        eq.setName("DNA Sequencer");
        eq.setDepartmentId(10L);
        eq.setStatus("AVAILABLE");
        eq.setInstitutionId(1L);

        when(equipmentRepository.findByInstitutionId(1L)).thenReturn(List.of(eq));
        when(equipmentRepository.findByDepartmentId(10L)).thenReturn(List.of(eq));
        when(equipmentRepository.findByDepartmentId(20L)).thenReturn(List.of());

        User userPhysics = new User();
        userPhysics.setId(50L);
        userPhysics.setDepartmentId(20L);
        when(userRepository.findAllById(anyList())).thenReturn(List.of(userPhysics));

        Booking crossBooking = new Booking();
        crossBooking.setId(701L);
        crossBooking.setEquipmentId(201L);
        crossBooking.setUserId(50L);
        crossBooking.setStatus("COMPLETED");
        crossBooking.setStartTime(LocalDateTime.now().minusDays(3));
        crossBooking.setEndTime(LocalDateTime.now().minusDays(3).plusHours(4));

        when(bookingRepository.findByEquipmentIdInAndPeriod(anyList(), any(), any())).thenReturn(List.of(crossBooking));

        DepartmentBudget budget = new DepartmentBudget();
        budget.setDepartmentId(10L);
        budget.setAllocatedAmount(BigDecimal.valueOf(50000.0));
        when(departmentBudgetRepository.findByInstitutionIdAndFiscalYear(eq(1L), anyInt())).thenReturn(List.of(budget));
        when(usageChargeRepository.sumDepartmentSpend(eq(10L), any(), any())).thenReturn(BigDecimal.valueOf(15000.0));
        when(usageChargeRepository.sumTotalChargesByInstitution(1L)).thenReturn(BigDecimal.valueOf(12000.0));

        InstitutionDashboardResponse response = dashboardService.getInstitutionDashboard(1L, 30);

        assertNotNull(response);
        assertEquals(1L, response.institutionId());
        assertEquals(2, response.departmentUtilization().size());
        assertEquals(4.0, response.crossDepartmentSharing().totalCrossDepartmentHours());
        assertFalse(response.crossDepartmentSharing().topDepartmentPairs().isEmpty());
        assertEquals("Biology", response.crossDepartmentSharing().topDepartmentPairs().get(0).equipmentDepartmentName());
        assertEquals("Physics", response.crossDepartmentSharing().topDepartmentPairs().get(0).userDepartmentName());
        assertEquals(BigDecimal.valueOf(50000.0), response.costAndBudgetSummary().totalBudgetAllocated());
    }

    @Test
    void getProcurementRecommendations_flagsHighDemandAndHighMaintenance() {
        Long institutionId = 1L;

        Equipment eqHighMaint = new Equipment();
        eqHighMaint.setId(301L);
        eqHighMaint.setName("Aging Spectrometer");
        eqHighMaint.setCategory("Spectroscopy");
        eqHighMaint.setPurchaseCost(BigDecimal.valueOf(100000.0));

        when(equipmentRepository.findByInstitutionId(1L)).thenReturn(List.of(eqHighMaint));

        // Maintenance cost 40,000 in last 12m (40% >= 30%)
        List<Object[]> maintCosts = new ArrayList<>();
        maintCosts.add(new Object[]{301L, BigDecimal.valueOf(40000.0)});
        when(workOrderRepository.sumCompletedCostByEquipmentIdsAndPeriod(eq(List.of(301L)), any(), any()))
                .thenReturn(maintCosts);

        List<ProcurementRecommendationDTO> recommendations = dashboardService.getProcurementRecommendations(institutionId);

        assertNotNull(recommendations);
        boolean hasHighMaint = recommendations.stream().anyMatch(r -> "HIGH_MAINTENANCE".equals(r.reasonCode()));
        assertTrue(hasHighMaint);
    }

    @Test
    void getEquipmentLifecycleAndRoi_computesRoiAccurately() {
        Long institutionId = 1L;

        Equipment eq = new Equipment();
        eq.setId(401L);
        eq.setName("Flow Cytometer");
        eq.setAssetTag("FC-01");
        eq.setPurchaseDate(LocalDate.now().minusYears(2));
        eq.setPurchaseCost(BigDecimal.valueOf(200000.0));
        eq.setStatus("AVAILABLE");

        Page<Equipment> page = new PageImpl<>(List.of(eq));
        when(equipmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // Maint: 20,000, Usage: 50,000 -> Net: 30,000 -> ROI: (30000 / 200000) * 100 = 15.0%
        List<Object[]> woCostList = new ArrayList<>();
        woCostList.add(new Object[]{401L, BigDecimal.valueOf(20000.0)});
        when(workOrderRepository.sumCompletedCostByEquipmentIdsAndPeriod(eq(List.of(401L)), isNull(), isNull()))
                .thenReturn(woCostList);

        List<Object[]> chargeList = new ArrayList<>();
        chargeList.add(new Object[]{401L, BigDecimal.valueOf(50000.0)});
        when(usageChargeRepository.sumChargesByEquipmentIds(eq(List.of(401L))))
                .thenReturn(chargeList);

        Page<EquipmentLifecycleDTO> result = dashboardService.getEquipmentLifecycleAndRoi(institutionId, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        EquipmentLifecycleDTO dto = result.getContent().get(0);
        assertEquals(15.0, dto.roiPercentage());
        assertEquals(24, dto.ageMonths());
    }

    @Test
    void getSystemMonitoring_returnsCountsAndFailedDeliveries() {
        Long institutionId = 1L;

        when(userRepository.countByInstitutionId(1L)).thenReturn(45L);
        when(equipmentRepository.findByInstitutionId(1L)).thenReturn(List.of(new Equipment()));
        when(notificationDeliveryRepository.countByStatusAndCreatedAtAfter(eq(DeliveryStatus.FAILED), any(Instant.class)))
                .thenReturn(3L);

        DailyUtilizationSnapshot snapshot = new DailyUtilizationSnapshot();
        snapshot.setSnapshotDate(LocalDate.of(2026, 11, 24));
        when(snapshotRepository.findTopByInstitutionIdOrderBySnapshotDateDesc(1L))
                .thenReturn(Optional.of(snapshot));

        SystemMonitoringResponse response = dashboardService.getSystemMonitoring(institutionId);

        assertNotNull(response);
        assertEquals(45L, response.totalUsers());
        assertEquals(3L, response.failedNotificationsLast7Days());
        assertEquals(LocalDate.of(2026, 11, 24), response.lastUtilizationSnapshotDate());
    }
}
