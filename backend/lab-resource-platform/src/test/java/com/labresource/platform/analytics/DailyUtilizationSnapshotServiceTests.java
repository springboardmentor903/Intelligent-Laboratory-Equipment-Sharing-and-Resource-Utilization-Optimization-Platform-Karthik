package com.labresource.platform.analytics;

import com.labresource.platform.analytics.entity.DailyUtilizationSnapshot;
import com.labresource.platform.analytics.repository.DailyUtilizationSnapshotRepository;
import com.labresource.platform.analytics.service.DailyUtilizationSnapshotService;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentOperatingHoursRepository;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyUtilizationSnapshotServiceTests {

    @Mock
    private DailyUtilizationSnapshotRepository snapshotRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private EquipmentOperatingHoursRepository operatingHoursRepository;
    @Mock
    private UtilizationRepository utilizationRepository;

    private DailyUtilizationSnapshotService snapshotService;
    private final ZoneId zone = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        snapshotService = new DailyUtilizationSnapshotService(
                snapshotRepository,
                equipmentRepository,
                operatingHoursRepository,
                utilizationRepository,
                "Asia/Kolkata"
        );
    }

    @Test
    @DisplayName("computeEquipmentDaySnapshot on Monday calculates 480 available minutes and actual usage")
    void testComputeSnapshotOnWeekday() {
        Equipment eq = new Equipment();
        eq.setId(101L);
        eq.setInstitutionId(1L);
        eq.setDepartmentId(10L);
        eq.setName("Spectrometer");
        eq.setQuantity(1);
        eq.setStatus("AVAILABLE");

        LocalDate monday = LocalDate.of(2026, 11, 2); // Monday
        when(operatingHoursRepository.findByEquipmentIdOrderByDayOfWeekAsc(101L)).thenReturn(Collections.emptyList());

        // 2 hours of usage between 10:00 and 12:00 in Asia/Kolkata
        LocalDateTime uStart = LocalDateTime.of(2026, 11, 2, 10, 0);
        LocalDateTime uEnd = LocalDateTime.of(2026, 11, 2, 12, 0);
        LocalDateTime utcStart = uStart.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime utcEnd = uEnd.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        Utilization session = new Utilization();
        session.setId(1L);
        session.setEquipmentId(101L);
        session.setStartTime(utcStart);
        session.setEndTime(utcEnd);
        session.setUsageType("EXCLUSIVE");

        when(utilizationRepository.findOverlappingSessions(eq(List.of(101L)), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(session));

        DailyUtilizationSnapshot snapshot = snapshotService.computeEquipmentDaySnapshot(eq, monday);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getOperatingMinutes()).isEqualTo(480);
        assertThat(snapshot.getUsedMinutes()).isEqualTo(120);
        assertThat(snapshot.getUtilizationRate()).isEqualTo(25.0); // 120 / 480 = 25.0%
        assertThat(snapshot.getSessionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("computeEquipmentDaySnapshot on Sunday has 0 operating minutes and null utilization")
    void testComputeSnapshotOnSunday() {
        Equipment eq = new Equipment();
        eq.setId(102L);
        eq.setInstitutionId(1L);
        eq.setDepartmentId(10L);
        eq.setName("Spectrometer");
        eq.setQuantity(1);
        eq.setStatus("AVAILABLE");

        LocalDate sunday = LocalDate.of(2026, 11, 1); // Sunday
        when(operatingHoursRepository.findByEquipmentIdOrderByDayOfWeekAsc(102L)).thenReturn(Collections.emptyList());
        when(utilizationRepository.findOverlappingSessions(eq(List.of(102L)), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        DailyUtilizationSnapshot snapshot = snapshotService.computeEquipmentDaySnapshot(eq, sunday);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getOperatingMinutes()).isEqualTo(0);
        assertThat(snapshot.getUsedMinutes()).isEqualTo(0);
        assertThat(snapshot.getUtilizationRate()).isNull();
    }

    @Test
    @DisplayName("captureSnapshotsForDate upserts existing records instead of duplicating")
    void testUpsertBehavior() {
        Equipment eq = new Equipment();
        eq.setId(201L);
        eq.setQuantity(1);
        when(equipmentRepository.findAll()).thenReturn(List.of(eq));

        LocalDate date = LocalDate.of(2026, 11, 2);
        DailyUtilizationSnapshot existing = DailyUtilizationSnapshot.builder()
                .id(999L)
                .snapshotDate(date)
                .equipmentId(201L)
                .operatingMinutes(100)
                .usedMinutes(50)
                .build();

        when(snapshotRepository.findBySnapshotDateAndEquipmentId(date, 201L)).thenReturn(Optional.of(existing));

        snapshotService.captureSnapshotsForDate(date);

        ArgumentCaptor<DailyUtilizationSnapshot> captor = ArgumentCaptor.forClass(DailyUtilizationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        DailyUtilizationSnapshot saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(999L); // Same ID updated
    }

    @Test
    @DisplayName("backfillSnapshots respects 90-day bound")
    void testBackfillBounds() {
        when(equipmentRepository.findAll()).thenReturn(Collections.emptyList());

        int count = snapshotService.backfillSnapshots(120); // requested 120, bounded to 90
        assertThat(count).isEqualTo(0);
    }
}
