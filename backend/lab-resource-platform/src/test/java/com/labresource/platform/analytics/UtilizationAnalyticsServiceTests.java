package com.labresource.platform.analytics;

import com.labresource.platform.analytics.dto.*;
import com.labresource.platform.analytics.service.UtilizationAnalyticsService;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.entity.EquipmentOperatingHours;
import com.labresource.platform.equipment.repository.EquipmentOperatingHoursRepository;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UtilizationAnalyticsServiceTests {

    @Mock private UtilizationRepository utilizationRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private EquipmentOperatingHoursRepository operatingHoursRepository;

    private UtilizationAnalyticsService service;
    private Equipment microscope;
    private Equipment spectrometer;

    @BeforeEach
    void setUp() {
        service = new UtilizationAnalyticsService(
                utilizationRepository,
                bookingRepository,
                equipmentRepository,
                departmentRepository,
                institutionRepository,
                operatingHoursRepository,
                "UTC"
        );

        microscope = new Equipment();
        microscope.setId(10L);
        microscope.setName("Electron Microscope");
        microscope.setAssetTag("MIC-010");
        microscope.setInstitutionId(1L);
        microscope.setDepartmentId(100L);
        microscope.setQuantity(1); // 1 unit capacity
        microscope.setStatus("AVAILABLE");

        spectrometer = new Equipment();
        spectrometer.setId(20L);
        spectrometer.setName("Mass Spectrometer");
        spectrometer.setAssetTag("SPEC-020");
        spectrometer.setInstitutionId(1L);
        spectrometer.setDepartmentId(100L);
        spectrometer.setQuantity(2); // 2 units capacity
        spectrometer.setStatus("AVAILABLE");

        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(microscope));
        when(equipmentRepository.findById(20L)).thenReturn(Optional.of(spectrometer));
        when(equipmentRepository.findAll()).thenReturn(List.of(microscope, spectrometer));
    }

    @Test
    void mondayToFridayCapacityAndWeekendExclusion() {
        // Full week Monday 2026-11-02 00:00 to Sunday 2026-11-08 23:59:59 (7 days)
        // Default Mon-Fri 09:00-17:00 = 5 days * 8h = 40 hours = 2400 minutes for 1 unit
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 8, 23, 59, 59);

        long availMinutes = service.calculateAvailableMinutes(microscope, start, end);
        assertThat(availMinutes).isEqualTo(2400L); // Exactly 40 operating hours
    }

    @Test
    void weekendOnlyWindowReturnsZeroAvailableMinutesAndNullUtilization() {
        // Saturday 2026-11-07 00:00 to Sunday 2026-11-08 23:59:59 (weekend)
        LocalDateTime satStart = LocalDateTime.of(2026, 11, 7, 0, 0);
        LocalDateTime sunEnd = LocalDateTime.of(2026, 11, 8, 23, 59, 59);

        long availMinutes = service.calculateAvailableMinutes(microscope, satStart, sunEnd);
        assertThat(availMinutes).isEqualTo(0L);

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(utilizationRepository.findOverlappingSessions(anyList(), any(), any())).thenReturn(List.of());

        List<EquipmentUtilizationRateDto> rates = service.getEquipmentUtilizationRates(1L, null, satStart, sunEnd, 24.0);
        assertThat(rates).hasSize(1);
        assertThat(rates.getFirst().getTotalAvailableMinutes()).isEqualTo(0L);
        assertThat(rates.getFirst().getUtilizationRate()).isNull(); // Zero capacity returns null
    }

    @Test
    void customConfiguredDaysAndMissingDayBehavior() {
        // Custom schedule: Tuesday only, 10:00 to 14:00 (4 hours = 240 minutes)
        // Mon, Wed, Thu, Fri, Sat, Sun are missing -> they must be closed
        EquipmentOperatingHours tuesdaySchedule = EquipmentOperatingHours.builder()
                .equipmentId(10L)
                .dayOfWeek(2) // Tuesday
                .openTime(LocalTime.of(10, 0))
                .closeTime(LocalTime.of(14, 0))
                .isClosed(false)
                .build();

        when(operatingHoursRepository.findByEquipmentIdOrderByDayOfWeekAsc(10L))
                .thenReturn(List.of(tuesdaySchedule));

        LocalDateTime weekStart = LocalDateTime.of(2026, 11, 2, 0, 0); // Monday
        LocalDateTime weekEnd = LocalDateTime.of(2026, 11, 8, 23, 59, 59); // Sunday

        long availMinutes = service.calculateAvailableMinutes(microscope, weekStart, weekEnd);
        assertThat(availMinutes).isEqualTo(240L); // Exactly 4 hours on Tuesday, 0 on other 6 days
    }

    @Test
    void utcAndPlatformTimezoneDateBoundaries() {
        // Service configured with Asia/Kolkata (+05:30)
        UtilizationAnalyticsService istService = new UtilizationAnalyticsService(
                utilizationRepository, bookingRepository, equipmentRepository,
                departmentRepository, institutionRepository, operatingHoursRepository, "Asia/Kolkata");

        // UTC timestamp: Monday 2026-11-02 23:30:00 UTC
        // Converted to IST (+05:30): Tuesday 2026-11-03 05:00:00 IST
        LocalDateTime utcTime = LocalDateTime.of(2026, 11, 2, 23, 30);
        LocalDateTime istTime = istService.toPlatformTime(utcTime);

        assertThat(istTime).isEqualTo(LocalDateTime.of(2026, 11, 3, 5, 0));

        // In IST, 05:00 is before opening (09:00). So usage from 05:00 to 08:00 IST is clipped to 0
        Utilization session = new Utilization();
        session.setEquipmentId(10L);
        session.setStartTime(LocalDateTime.of(2026, 11, 2, 23, 30)); // 05:00 IST
        session.setEndTime(LocalDateTime.of(2026, 11, 3, 2, 30));   // 08:00 IST

        var usage = istService.calculateActualUsageMinutes(
                microscope, List.of(session),
                LocalDateTime.of(2026, 11, 3, 0, 0),
                LocalDateTime.of(2026, 11, 3, 23, 59, 59));

        assertThat(usage.totalMinutes()).isEqualTo(0L); // 0 minutes because 05:00-08:00 is outside 09:00-17:00
    }

    @Test
    void overnightUsageCrossingMidnightClippedToOperatingHours() {
        // Session starts Monday 15:00, ends Tuesday 11:00
        // Operating hours are 09:00 to 17:00
        // Monday clipped: 15:00 to 17:00 = 120 mins
        // Tuesday clipped: 09:00 to 11:00 = 120 mins
        // Total clipped = 240 mins (4 hours)
        Utilization overnight = new Utilization();
        overnight.setEquipmentId(10L);
        overnight.setStartTime(LocalDateTime.of(2026, 11, 2, 15, 0));
        overnight.setEndTime(LocalDateTime.of(2026, 11, 3, 11, 0));

        LocalDateTime windowStart = LocalDateTime.of(2026, 11, 2, 0, 0);
        LocalDateTime windowEnd = LocalDateTime.of(2026, 11, 3, 23, 59);

        var usage = service.calculateActualUsageMinutes(microscope, List.of(overnight), windowStart, windowEnd);
        assertThat(usage.totalMinutes()).isEqualTo(240L);
    }

    @Test
    void usageBeforeOpeningAndAfterClosingIgnored() {
        // Session 1: Monday 07:00 to 09:00 (before open)
        // Session 2: Monday 17:00 to 20:00 (after close)
        Utilization u1 = new Utilization();
        u1.setEquipmentId(10L);
        u1.setStartTime(LocalDateTime.of(2026, 11, 2, 7, 0));
        u1.setEndTime(LocalDateTime.of(2026, 11, 2, 9, 0));

        Utilization u2 = new Utilization();
        u2.setEquipmentId(10L);
        u2.setStartTime(LocalDateTime.of(2026, 11, 2, 17, 0));
        u2.setEndTime(LocalDateTime.of(2026, 11, 2, 20, 0));

        LocalDateTime windowStart = LocalDateTime.of(2026, 11, 2, 0, 0);
        LocalDateTime windowEnd = LocalDateTime.of(2026, 11, 2, 23, 59);

        var usage = service.calculateActualUsageMinutes(microscope, List.of(u1, u2), windowStart, windowEnd);
        assertThat(usage.totalMinutes()).isEqualTo(0L);
    }

    @Test
    void multiEquipmentAggregationUsingRawMinutesFirst() {
        when(equipmentRepository.findAll()).thenReturn(List.of(microscope, spectrometer));
        Department dept = new Department(1L, "Biology");
        dept.setId(100L);
        when(departmentRepository.findAllById(Set.of(100L))).thenReturn(List.of(dept));

        // Window: 1 day (Monday) 00:00 to 23:59:59
        // Microscope (qty 1): 480 mins available. Used: 240 mins (50% rate)
        // Spectrometer (qty 2): 960 mins available. Used: 120 mins (12.5% rate)
        // Department sum: total available = 1440 mins, total used = 360 mins
        // True Department rate = 360 / 1440 * 100% = 25.0%
        // (If averaged: (50% + 12.5%)/2 = 31.25%, which is wrong)
        LocalDateTime monStart = LocalDateTime.of(2026, 11, 2, 0, 0);
        LocalDateTime monEnd = LocalDateTime.of(2026, 11, 2, 23, 59, 59);

        Utilization u1 = new Utilization();
        u1.setEquipmentId(10L);
        u1.setStartTime(LocalDateTime.of(2026, 11, 2, 10, 0));
        u1.setEndTime(LocalDateTime.of(2026, 11, 2, 14, 0)); // 240 mins

        Utilization u2 = new Utilization();
        u2.setEquipmentId(20L);
        u2.setStartTime(LocalDateTime.of(2026, 11, 2, 14, 0));
        u2.setEndTime(LocalDateTime.of(2026, 11, 2, 16, 0)); // 120 mins

        when(utilizationRepository.findOverlappingSessions(anyList(), any(), any()))
                .thenReturn(List.of(u1, u2));

        List<DepartmentUtilizationSummaryDto> summaries = service.getDepartmentUtilization(
                1L, monStart, monEnd, 24.0, 30.0);

        assertThat(summaries).hasSize(1);
        DepartmentUtilizationSummaryDto summary = summaries.getFirst();
        assertThat(summary.getTotalAvailableMinutes()).isEqualTo(1440L);
        assertThat(summary.getActualUsageMinutes()).isEqualTo(360L);
        assertThat(summary.getAverageUtilizationRate()).isEqualTo(25.0);
        assertThat(summary.getTargetUtilizationRate()).isEqualTo(30.0);
        assertThat(summary.getTargetGap()).isEqualTo(-5.0); // 25.0 - 30.0 = -5.0%
    }

    @Test
    void previous28DayBenchmarkComparison() {
        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        Department dept = new Department(1L, "Biology");
        dept.setId(100L);
        when(departmentRepository.findAllById(Set.of(100L))).thenReturn(List.of(dept));

        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 0, 0); // Monday
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 23, 59, 59);

        // Current session: 240 mins (50%)
        Utilization current = new Utilization();
        current.setEquipmentId(10L);
        current.setStartTime(LocalDateTime.of(2026, 11, 2, 10, 0));
        current.setEndTime(LocalDateTime.of(2026, 11, 2, 14, 0));

        // 28-day benchmark session
        Utilization bench = new Utilization();
        bench.setEquipmentId(10L);
        bench.setStartTime(LocalDateTime.of(2026, 10, 15, 10, 0));
        bench.setEndTime(LocalDateTime.of(2026, 10, 15, 12, 0)); // 120 mins

        when(utilizationRepository.findOverlappingSessions(anyList(), any(), any()))
                .thenReturn(List.of(current))
                .thenReturn(List.of(bench));

        List<EquipmentUtilizationRateDto> rates = service.getEquipmentUtilizationRates(1L, null, start, end, 24.0);
        assertThat(rates).hasSize(1);
        assertThat(rates.getFirst().getUtilizationRate()).isEqualTo(50.0);
        assertThat(rates.getFirst().getHistoricalBenchmarkRate()).isNotNull();
        assertThat(rates.getFirst().getTrendPercentage()).isNotNull();
    }

    @Test
    void exactDemandFormulasAnd80PercentHighDemandThreshold() {
        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));

        // Window: 1 Monday (480 available mins for 1 unit)
        LocalDateTime monStart = LocalDateTime.of(2026, 11, 2, 0, 0);
        LocalDateTime monEnd = LocalDateTime.of(2026, 11, 2, 23, 59, 59);

        // Booking 1: CONFIRMED 09:00-15:00 = 6 hours = 360 mins
        Booking b1 = new Booking();
        b1.setEquipmentId(10L);
        b1.setStatus("CONFIRMED");
        b1.setStartTime(LocalDateTime.of(2026, 11, 2, 9, 0));
        b1.setEndTime(LocalDateTime.of(2026, 11, 2, 15, 0));

        // Booking 2: REJECTED
        Booking b2 = new Booking();
        b2.setEquipmentId(10L);
        b2.setStatus("REJECTED");
        b2.setStartTime(LocalDateTime.of(2026, 11, 2, 15, 0));
        b2.setEndTime(LocalDateTime.of(2026, 11, 2, 16, 0));

        // Booking 3: CANCELLED
        Booking b3 = new Booking();
        b3.setEquipmentId(10L);
        b3.setStatus("CANCELLED");
        b3.setStartTime(LocalDateTime.of(2026, 11, 2, 16, 0));
        b3.setEndTime(LocalDateTime.of(2026, 11, 2, 17, 0));

        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(b1, b2, b3));

        DemandAnalysisDto demand = service.getDemandAnalysis(1L, null, null, monStart, monEnd);

        assertThat(demand.getTotalRequests()).isEqualTo(3L);
        assertThat(demand.getFulfilledBookings()).isEqualTo(1L);
        assertThat(demand.getRejectedBookings()).isEqualTo(1L);
        assertThat(demand.getCancelledBookings()).isEqualTo(1L);
        assertThat(demand.getFulfillmentRate()).isEqualTo(33.3);
        assertThat(demand.getRejectionRate()).isEqualTo(33.3);
        assertThat(demand.getCancellationRate()).isEqualTo(33.3);
        // Booked minutes = 360, available = 480 -> bookingRate = 360/480 = 75.0%
        assertThat(demand.getBookingRate()).isEqualTo(75.0);
        // 75% is < 80% threshold and contention <= 1.0 -> isHighDemand is false
        assertThat(demand.getIsHighDemand()).isFalse();

        // Now test >= 80% threshold
        // Add 1 more hour to b1 so booked = 420 mins (420/480 = 87.5%)
        b1.setEndTime(LocalDateTime.of(2026, 11, 2, 16, 0));
        DemandAnalysisDto demandHigh = service.getDemandAnalysis(1L, null, null, monStart, monEnd);
        assertThat(demandHigh.getBookingRate()).isEqualTo(87.5);
        assertThat(demandHigh.getIsHighDemand()).isTrue();
    }

    @Test
    void peakTieBreakersFollowUtilizationThenUsageThenEarlierHour() {
        when(equipmentRepository.findByInstitutionId(1L)).thenReturn(List.of(microscope));

        // Create sessions:
        // Hour 9: 60 mins usage
        // Hour 10: 60 mins usage
        // Both hour 9 and 10 have identical utilization score (100%) and identical usage (1.0h).
        // The earlier hour (9) must be selected as busiestHourOfDay.
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 23, 59, 59);

        Utilization u1 = new Utilization();
        u1.setEquipmentId(10L);
        u1.setStartTime(LocalDateTime.of(2026, 11, 2, 9, 0));
        u1.setEndTime(LocalDateTime.of(2026, 11, 2, 10, 0));

        Utilization u2 = new Utilization();
        u2.setEquipmentId(10L);
        u2.setStartTime(LocalDateTime.of(2026, 11, 2, 10, 0));
        u2.setEndTime(LocalDateTime.of(2026, 11, 2, 11, 0));

        when(utilizationRepository.findOverlappingSessions(anyList(), any(), any()))
                .thenReturn(List.of(u1, u2));

        PeakUsagePatternDto pattern = service.getPeakUsagePattern(1L, null, 10L, start, end);

        assertThat(pattern.getBusiestHourOfDay()).isEqualTo(9); // Hour 9 wins tie-breaker over hour 10!
        assertThat(pattern.getTopFivePeakSlots().get(0).getHour()).isEqualTo(9);
        assertThat(pattern.getTopFivePeakSlots().get(1).getHour()).isEqualTo(10);
    }

    @Test
    void idleAlertsExcludeMaintenanceAndDecommissionedEquipment() {
        Equipment maintEq = new Equipment();
        maintEq.setId(30L);
        maintEq.setName("Broken Centrifuge");
        maintEq.setStatus("MAINTENANCE");
        maintEq.setInstitutionId(1L);

        Equipment decomEq = new Equipment();
        decomEq.setId(40L);
        decomEq.setName("Retired Autoclave");
        decomEq.setStatus("DECOMMISSIONED");
        decomEq.setInstitutionId(1L);

        when(equipmentRepository.findAll())
                .thenReturn(List.of(microscope, maintEq, decomEq));

        when(utilizationRepository.findOverlappingSessions(anyList(), any(), any()))
                .thenReturn(Collections.emptyList());

        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59); // 7 completed days

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);

        // Only active/available microscope must be reported, MAINTENANCE and DECOMMISSIONED must be excluded
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getEquipmentId()).isEqualTo(10L);
        assertThat(reports.get(0).getIdlePercentage()).isEqualTo(100.0);
        assertThat(reports.get(0).getIdleAlert()).isTrue();
        assertThat(reports.get(0).getAlertLevel()).isEqualTo("CRITICAL");
    }
    @Test
    void idleAlertsIncludeOnlyAvailableAndBookedAndStrictlyExcludeAllOthers() {
        // Equipment 1: AVAILABLE -> included
        Equipment eqAvailable = new Equipment();
        eqAvailable.setId(101L);
        eqAvailable.setName("Available Centrifuge");
        eqAvailable.setStatus("AVAILABLE");
        eqAvailable.setInstitutionId(1L);

        // Equipment 2: BOOKED -> included
        Equipment eqBooked = new Equipment();
        eqBooked.setId(102L);
        eqBooked.setName("Booked Spectrophotometer");
        eqBooked.setStatus("BOOKED");
        eqBooked.setInstitutionId(1L);

        // Equipment 3: IN_USE -> EXCLUDED
        Equipment eqInUse = new Equipment();
        eqInUse.setId(103L);
        eqInUse.setName("Active Microscope");
        eqInUse.setStatus("IN_USE");
        eqInUse.setInstitutionId(1L);

        // Equipment 4: UNDER_MAINTENANCE -> EXCLUDED
        Equipment eqUnderMaint = new Equipment();
        eqUnderMaint.setId(104L);
        eqUnderMaint.setName("Fixing Sequencer");
        eqUnderMaint.setStatus("UNDER_MAINTENANCE");
        eqUnderMaint.setInstitutionId(1L);

        // Equipment 5: MAINTENANCE -> EXCLUDED
        Equipment eqMaint = new Equipment();
        eqMaint.setId(105L);
        eqMaint.setName("Calibrating Scale");
        eqMaint.setStatus("MAINTENANCE");
        eqMaint.setInstitutionId(1L);

        // Equipment 6: OUT_OF_SERVICE -> EXCLUDED
        Equipment eqOutOfService = new Equipment();
        eqOutOfService.setId(106L);
        eqOutOfService.setName("Broken Freezer");
        eqOutOfService.setStatus("OUT_OF_SERVICE");
        eqOutOfService.setInstitutionId(1L);

        // Equipment 7: RETIRED -> EXCLUDED
        Equipment eqRetired = new Equipment();
        eqRetired.setId(107L);
        eqRetired.setName("Retired PCR");
        eqRetired.setStatus("RETIRED");
        eqRetired.setInstitutionId(1L);

        // Equipment 8: DECOMMISSIONED -> EXCLUDED
        Equipment eqDecom = new Equipment();
        eqDecom.setId(108L);
        eqDecom.setName("Decommissioned Hood");
        eqDecom.setStatus("DECOMMISSIONED");
        eqDecom.setInstitutionId(1L);

        // Equipment 9: null status -> EXCLUDED
        Equipment eqNullStatus = new Equipment();
        eqNullStatus.setId(109L);
        eqNullStatus.setName("Unknown Status Unit");
        eqNullStatus.setStatus(null);
        eqNullStatus.setInstitutionId(1L);

        when(equipmentRepository.findAll())
                .thenReturn(List.of(eqAvailable, eqBooked, eqInUse, eqUnderMaint, eqMaint, eqOutOfService, eqRetired, eqDecom, eqNullStatus));
        when(utilizationRepository.findOverlappingSessions(anyList(), any(), any()))
                .thenReturn(Collections.emptyList());

        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59);

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);

        // Strictly only AVAILABLE and BOOKED equipment are reported
        assertThat(reports).hasSize(2);
        assertThat(reports).extracting(IdleTimeReportDto::getEquipmentId).containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    void demandAnalysisExactFormulasForNoShowAndCancellation() {
        LocalDateTime monStart = LocalDateTime.of(2026, 11, 2, 0, 0);
        LocalDateTime monEnd = LocalDateTime.of(2026, 11, 2, 23, 59, 59);

        // 1. COMPLETED (attended)
        Booking b1 = new Booking();
        b1.setEquipmentId(10L);
        b1.setStatus("COMPLETED");
        b1.setStartTime(LocalDateTime.of(2026, 11, 2, 9, 0));
        b1.setEndTime(LocalDateTime.of(2026, 11, 2, 10, 0));

        // 2. IN_USE (attended)
        Booking b2 = new Booking();
        b2.setEquipmentId(10L);
        b2.setStatus("IN_USE");
        b2.setStartTime(LocalDateTime.of(2026, 11, 2, 10, 0));
        b2.setEndTime(LocalDateTime.of(2026, 11, 2, 11, 0));

        // 3. NO_SHOW
        Booking b3 = new Booking();
        b3.setEquipmentId(10L);
        b3.setStatus("NO_SHOW");
        b3.setStartTime(LocalDateTime.of(2026, 11, 2, 11, 0));
        b3.setEndTime(LocalDateTime.of(2026, 11, 2, 12, 0));

        // 4. CANCELLED
        Booking b4 = new Booking();
        b4.setEquipmentId(10L);
        b4.setStatus("CANCELLED");
        b4.setStartTime(LocalDateTime.of(2026, 11, 2, 12, 0));
        b4.setEndTime(LocalDateTime.of(2026, 11, 2, 13, 0));

        // 5. REJECTED
        Booking b5 = new Booking();
        b5.setEquipmentId(10L);
        b5.setStatus("REJECTED");
        b5.setStartTime(LocalDateTime.of(2026, 11, 2, 13, 0));
        b5.setEndTime(LocalDateTime.of(2026, 11, 2, 14, 0));

        // 6. PENDING
        Booking b6 = new Booking();
        b6.setEquipmentId(10L);
        b6.setStatus("PENDING");
        b6.setStartTime(LocalDateTime.of(2026, 11, 2, 14, 0));
        b6.setEndTime(LocalDateTime.of(2026, 11, 2, 15, 0));

        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(b1, b2, b3, b4, b5, b6));

        DemandAnalysisDto demand = service.getDemandAnalysis(1L, null, null, monStart, monEnd);

        assertThat(demand.getTotalRequests()).isEqualTo(6L);
        assertThat(demand.getCancelledBookings()).isEqualTo(1L);
        assertThat(demand.getNoShowBookings()).isEqualTo(1L);
        assertThat(demand.getRejectedBookings()).isEqualTo(1L);
        assertThat(demand.getPendingBookings()).isEqualTo(1L);

        // Cancellation rate = CANCELLED / totalRequests = 1 / 6 = 16.7%
        assertThat(demand.getCancellationRate()).isEqualTo(16.7);

        // Attendance opportunities = COMPLETED(1) + IN_USE(1) + NO_SHOW(1) = 3
        // No-show rate = NO_SHOW / attendance opportunities = 1 / 3 = 33.3%
        assertThat(demand.getNoShowRate()).isEqualTo(33.3);
    }

    @Test
    void idleAlertsConfirmedAndApprovedBookingsReduceIdleTime() {
        // Microscope has 40 hours available in Monday-Sunday window (5 open days * 8h)
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0); // Monday
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59); // Sunday

        // 1. Confirmed booking for 8 hours (Monday 09:00 to 17:00)
        Booking confirmed = new Booking();
        confirmed.setEquipmentId(10L);
        confirmed.setStatus("CONFIRMED");
        confirmed.setStartTime(LocalDateTime.of(2026, 10, 26, 9, 0));
        confirmed.setEndTime(LocalDateTime.of(2026, 10, 26, 17, 0)); // 8 hours

        // 2. Approved booking for 2 hours (Tuesday 09:00 to 11:00)
        Booking approved = new Booking();
        approved.setEquipmentId(10L);
        approved.setStatus("APPROVED");
        approved.setStartTime(LocalDateTime.of(2026, 10, 27, 9, 0));
        approved.setEndTime(LocalDateTime.of(2026, 10, 27, 11, 0)); // 2 hours

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(confirmed, approved));

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);

        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);
        assertThat(report.getTotalOperatingHours()).isEqualTo(40.0);
        assertThat(report.getActualUsageHours()).isEqualTo(10.0); // 10 booked hours
        assertThat(report.getIdleHours()).isEqualTo(30.0); // 40 - 10 = 30 idle hours
        assertThat(report.getIdlePercentage()).isEqualTo(75.0); // 30/40 = 75.0%
        assertThat(report.getIdleAlert()).isFalse(); // 75.0% < 90.0%
        assertThat(report.getAlertLevel()).isEqualTo("NORMAL");
    }

    @Test
    void idleAlertsInUseCompletedAndNoShowBookingsCounted() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59);

        // IN_USE: 2 hours
        Booking bInUse = new Booking();
        bInUse.setEquipmentId(10L);
        bInUse.setStatus("IN_USE");
        bInUse.setStartTime(LocalDateTime.of(2026, 10, 26, 9, 0));
        bInUse.setEndTime(LocalDateTime.of(2026, 10, 26, 11, 0));

        // COMPLETED: 3 hours
        Booking bCompleted = new Booking();
        bCompleted.setEquipmentId(10L);
        bCompleted.setStatus("COMPLETED");
        bCompleted.setStartTime(LocalDateTime.of(2026, 10, 27, 9, 0));
        bCompleted.setEndTime(LocalDateTime.of(2026, 10, 27, 12, 0));

        // NO_SHOW: 5 hours
        Booking bNoShow = new Booking();
        bNoShow.setEquipmentId(10L);
        bNoShow.setStatus("NO_SHOW");
        bNoShow.setStartTime(LocalDateTime.of(2026, 10, 28, 9, 0));
        bNoShow.setEndTime(LocalDateTime.of(2026, 10, 28, 14, 0));

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(bInUse, bCompleted, bNoShow));

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);
        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);
        assertThat(report.getActualUsageHours()).isEqualTo(10.0); // 2 + 3 + 5 = 10 hours
        assertThat(report.getIdleHours()).isEqualTo(30.0);
        assertThat(report.getIdlePercentage()).isEqualTo(75.0);
    }

    @Test
    void idleAlertsExcludeCancelledRejectedAndPendingBookings() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59);

        Booking bCancelled = new Booking();
        bCancelled.setEquipmentId(10L);
        bCancelled.setStatus("CANCELLED");
        bCancelled.setStartTime(LocalDateTime.of(2026, 10, 26, 9, 0));
        bCancelled.setEndTime(LocalDateTime.of(2026, 10, 26, 17, 0));

        Booking bRejected = new Booking();
        bRejected.setEquipmentId(10L);
        bRejected.setStatus("REJECTED");
        bRejected.setStartTime(LocalDateTime.of(2026, 10, 27, 9, 0));
        bRejected.setEndTime(LocalDateTime.of(2026, 10, 27, 17, 0));

        Booking bPendingApp = new Booking();
        bPendingApp.setEquipmentId(10L);
        bPendingApp.setStatus("PENDING_APPROVAL");
        bPendingApp.setStartTime(LocalDateTime.of(2026, 10, 28, 9, 0));
        bPendingApp.setEndTime(LocalDateTime.of(2026, 10, 28, 17, 0));

        Booking bPending = new Booking();
        bPending.setEquipmentId(10L);
        bPending.setStatus("PENDING");
        bPending.setStartTime(LocalDateTime.of(2026, 10, 29, 9, 0));
        bPending.setEndTime(LocalDateTime.of(2026, 10, 29, 17, 0));

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(bCancelled, bRejected, bPendingApp, bPending));

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);
        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);
        assertThat(report.getActualUsageHours()).isEqualTo(0.0); // none counted
        assertThat(report.getIdleHours()).isEqualTo(40.0);
        assertThat(report.getIdlePercentage()).isEqualTo(100.0);
        assertThat(report.getAlertLevel()).isEqualTo("CRITICAL");
    }

    @Test
    void idleAlertsOverlappingAndDuplicateBookingsNotDoubleCounted() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59);

        // Single-unit microscope: quantity = 1
        // Booking 1: Monday 10:00 to 14:00 (4 hours)
        // Booking 2: Monday 12:00 to 16:00 (4 hours) - overlaps from 12:00 to 14:00
        // Union is 10:00 to 16:00 = 6 hours (must NOT be double counted to 8h)
        Booking b1 = new Booking();
        b1.setEquipmentId(10L);
        b1.setStatus("CONFIRMED");
        b1.setStartTime(LocalDateTime.of(2026, 10, 26, 10, 0));
        b1.setEndTime(LocalDateTime.of(2026, 10, 26, 14, 0));

        Booking b2 = new Booking();
        b2.setEquipmentId(10L);
        b2.setStatus("APPROVED");
        b2.setStartTime(LocalDateTime.of(2026, 10, 26, 12, 0));
        b2.setEndTime(LocalDateTime.of(2026, 10, 26, 16, 0));

        // Duplicate booking: identical interval Monday 10:00 to 14:00
        Booking bDuplicate = new Booking();
        bDuplicate.setEquipmentId(10L);
        bDuplicate.setStatus("CONFIRMED");
        bDuplicate.setStartTime(LocalDateTime.of(2026, 10, 26, 10, 0));
        bDuplicate.setEndTime(LocalDateTime.of(2026, 10, 26, 14, 0));

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(b1, b2, bDuplicate));

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);
        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);
        assertThat(report.getActualUsageHours()).isEqualTo(6.0); // exactly 6 hours, not 12
        assertThat(report.getIdleHours()).isEqualTo(34.0); // 40 - 6 = 34
        assertThat(report.getIdlePercentage()).isEqualTo(85.0); // 34/40 = 85.0%
    }

    @Test
    void idleAlertsMultiUnitCapacityHandledAppropriatelyWithoutExceedingAvailableUnits() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59);

        // Spectrometer: quantity = 2 -> 40h * 2 = 80 total operating hours
        // Three concurrent bookings on Monday 10:00 to 14:00 (4 hours each)
        // With quantity = 2, only 2 units can be concurrently booked = 2 * 4h = 8h (not 12h)
        Booking b1 = new Booking();
        b1.setEquipmentId(20L);
        b1.setStatus("CONFIRMED");
        b1.setStartTime(LocalDateTime.of(2026, 10, 26, 10, 0));
        b1.setEndTime(LocalDateTime.of(2026, 10, 26, 14, 0));

        Booking b2 = new Booking();
        b2.setEquipmentId(20L);
        b2.setStatus("CONFIRMED");
        b2.setStartTime(LocalDateTime.of(2026, 10, 26, 10, 0));
        b2.setEndTime(LocalDateTime.of(2026, 10, 26, 14, 0));

        Booking b3 = new Booking();
        b3.setEquipmentId(20L);
        b3.setStatus("CONFIRMED");
        b3.setStartTime(LocalDateTime.of(2026, 10, 26, 10, 0));
        b3.setEndTime(LocalDateTime.of(2026, 10, 26, 14, 0));

        when(equipmentRepository.findAll()).thenReturn(List.of(spectrometer));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(b1, b2, b3));

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);
        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);
        assertThat(report.getTotalOperatingHours()).isEqualTo(80.0);
        assertThat(report.getActualUsageHours()).isEqualTo(8.0); // capped at 2 units * 4h = 8h
        assertThat(report.getIdleHours()).isEqualTo(72.0); // 80 - 8 = 72h
        assertThat(report.getIdlePercentage()).isEqualTo(90.0); // 72 / 80 = 90.0%
        assertThat(report.getAlertLevel()).isEqualTo("CRITICAL");
    }

    @Test
    void idleAlertsBookingsOutsideOperatingHoursAndClosedDaysExcluded() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59);

        // Before open: Monday 06:00 to 09:00
        Booking bEarly = new Booking();
        bEarly.setEquipmentId(10L);
        bEarly.setStatus("CONFIRMED");
        bEarly.setStartTime(LocalDateTime.of(2026, 10, 26, 6, 0));
        bEarly.setEndTime(LocalDateTime.of(2026, 10, 26, 9, 0));

        // After close: Monday 17:00 to 21:00
        Booking bLate = new Booking();
        bLate.setEquipmentId(10L);
        bLate.setStatus("CONFIRMED");
        bLate.setStartTime(LocalDateTime.of(2026, 10, 26, 17, 0));
        bLate.setEndTime(LocalDateTime.of(2026, 10, 26, 21, 0));

        // Weekend: Saturday 10:00 to 14:00 (Saturday is closed)
        Booking bWeekend = new Booking();
        bWeekend.setEquipmentId(10L);
        bWeekend.setStatus("CONFIRMED");
        bWeekend.setStartTime(LocalDateTime.of(2026, 10, 31, 10, 0));
        bWeekend.setEndTime(LocalDateTime.of(2026, 10, 31, 14, 0));

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(bEarly, bLate, bWeekend));

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);
        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);
        assertThat(report.getActualUsageHours()).isEqualTo(0.0);
        assertThat(report.getIdleHours()).isEqualTo(40.0);
        assertThat(report.getIdlePercentage()).isEqualTo(100.0);
    }

    @Test
    void idleAlertsPartialBookingOverlapWithOperatingHoursClippedCorrectly() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59);

        // Booking from Monday 07:00 to 11:00 (4 hours total, but 07:00-09:00 is off-hours)
        // Only 09:00 to 11:00 (2 hours) should be counted!
        Booking b = new Booking();
        b.setEquipmentId(10L);
        b.setStatus("CONFIRMED");
        b.setStartTime(LocalDateTime.of(2026, 10, 26, 7, 0));
        b.setEndTime(LocalDateTime.of(2026, 10, 26, 11, 0));

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(b));

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);
        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);
        assertThat(report.getActualUsageHours()).isEqualTo(2.0); // exactly 2 operating hours
        assertThat(report.getIdleHours()).isEqualTo(38.0); // 40 - 2 = 38
        assertThat(report.getIdlePercentage()).isEqualTo(95.0); // 38/40 = 95.0%
        assertThat(report.getAlertLevel()).isEqualTo("CRITICAL");
    }

    @Test
    void idleAlertsEnforcesLastSevenCompletedDaysEndingYesterday() {
        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(Collections.emptyList());

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, null, null, null, null);
        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);

        // A 7-completed-day period always spans 5 operating weekdays (40 hours) for a quantity 1 asset
        assertThat(report.getTotalOperatingHours()).isEqualTo(40.0);
        assertThat(report.getIdleHours()).isEqualTo(40.0);
        assertThat(report.getIdlePercentage()).isEqualTo(100.0);
    }

    @Test
    void idleAlertsTodayBookingsDoNotAffectIdleAlerts() {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        Booking todayBooking = new Booking();
        todayBooking.setEquipmentId(10L);
        todayBooking.setStatus("CONFIRMED");
        todayBooking.setStartTime(today.atTime(9, 0));
        todayBooking.setEndTime(today.atTime(17, 0));

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(Collections.emptyList()); // today's booking is outside the 7 completed days ending yesterday

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, null, null, null, null);
        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);
        assertThat(report.getActualUsageHours()).isEqualTo(0.0);
        assertThat(report.getIdlePercentage()).isEqualTo(100.0);
    }

    @Test
    void idleAlertsSingleNullDateParameterHandlingIsSafe() {
        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(Collections.emptyList());

        LocalDateTime someDate = LocalDateTime.of(2026, 10, 20, 0, 0);

        // 1. start != null, end == null
        List<IdleTimeReportDto> reports1 = service.getIdleTimeReports(1L, null, someDate, null, null, null);
        assertThat(reports1).hasSize(1);
        assertThat(reports1.get(0).getTotalOperatingHours()).isEqualTo(40.0);

        // 2. start == null, end != null
        List<IdleTimeReportDto> reports2 = service.getIdleTimeReports(1L, null, null, someDate, null, null);
        assertThat(reports2).hasSize(1);
        assertThat(reports2.get(0).getTotalOperatingHours()).isEqualTo(40.0);
    }

    @Test
    void idleAlertsExactlyNinetyPercentIdleProducesCritical() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59);

        // 40 hours available. Exactly 4 hours booked -> 36 hours idle -> 36/40 = 90.0%
        Booking b = new Booking();
        b.setEquipmentId(10L);
        b.setStatus("CONFIRMED");
        b.setStartTime(LocalDateTime.of(2026, 10, 26, 9, 0));
        b.setEndTime(LocalDateTime.of(2026, 10, 26, 13, 0)); // 4 hours

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(b));

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);
        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);
        assertThat(report.getIdlePercentage()).isEqualTo(90.0);
        assertThat(report.getIdleAlert()).isTrue();
        assertThat(report.getAlertLevel()).isEqualTo("CRITICAL");
    }

    @Test
    void idleAlertsCustomOperatingScheduleContinuedSupport() {
        // Microscope configured with custom Tuesday-only schedule 10:00 to 14:00 (4 hours)
        EquipmentOperatingHours tuesdaySchedule = EquipmentOperatingHours.builder()
                .equipmentId(10L)
                .dayOfWeek(2) // Tuesday
                .openTime(LocalTime.of(10, 0))
                .closeTime(LocalTime.of(14, 0))
                .isClosed(false)
                .build();

        when(operatingHoursRepository.findByEquipmentIdOrderByDayOfWeekAsc(10L))
                .thenReturn(List.of(tuesdaySchedule));

        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59);

        // Tuesday booking from 10:00 to 12:00 (2 hours booked out of 4)
        Booking b = new Booking();
        b.setEquipmentId(10L);
        b.setStatus("CONFIRMED");
        b.setStartTime(LocalDateTime.of(2026, 10, 27, 10, 0));
        b.setEndTime(LocalDateTime.of(2026, 10, 27, 12, 0));

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope));
        when(bookingRepository.findOverlappingBookings(anyList(), any(), any()))
                .thenReturn(List.of(b));

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(1L, null, start, end, 8.0, 90.0);
        assertThat(reports).hasSize(1);
        IdleTimeReportDto report = reports.get(0);
        assertThat(report.getTotalOperatingHours()).isEqualTo(4.0);
        assertThat(report.getActualUsageHours()).isEqualTo(2.0);
        assertThat(report.getIdleHours()).isEqualTo(2.0);
        assertThat(report.getIdlePercentage()).isEqualTo(50.0);
        assertThat(report.getAlertLevel()).isEqualTo("NORMAL");
    }

    @Test
    void idleAlertsInstitutionAndDepartmentScopingPreserved() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 23, 59, 59);

        // Assign distinct departments: microscope in dept 100, spectrometer in dept 200
        microscope.setDepartmentId(100L);
        spectrometer.setDepartmentId(200L);
        when(equipmentRepository.findAll()).thenReturn(List.of(microscope, spectrometer));

        List<IdleTimeReportDto> reports = service.getIdleTimeReports(null, 100L, start, end, 8.0, 90.0);
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getEquipmentId()).isEqualTo(10L);
        assertThat(reports.get(0).getDepartmentId()).isEqualTo(100L);
    }
}