package com.labresource.platform.calibration.service;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.model.CalibrationOverviewItem;
import com.labresource.platform.calibration.model.CalibrationReminderItem;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.maintenance.entity.WorkOrder;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalibrationRecordServiceTests {

    @Mock
    private CalibrationRecordRepository calibrationRecordRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    private CalibrationRecordService service;

    @BeforeEach
    void setUp() {
        service = new CalibrationRecordService(
                calibrationRecordRepository,
                equipmentRepository,
                workOrderRepository,
                departmentRepository,
                userRepository,
                "Asia/Kolkata"
        );
    }

    @Test
    void schedulesUpcomingCalibrationTaskSuccessfully() {
        Equipment equipment = new Equipment();
        equipment.setId(10L);
        equipment.setName("Spectrometer");
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));
        when(calibrationRecordRepository.save(any(CalibrationRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CalibrationRecord task = new CalibrationRecord();
        task.setScheduledDate(LocalDate.now().plusDays(15));
        task.setAssignedTo(5L);
        task.setNotes("Annual standard re-calibration");

        CalibrationRecord scheduled = service.schedule(10L, task);

        assertNotNull(scheduled);
        assertEquals("SCHEDULED", scheduled.getStatus());
        assertEquals(5L, scheduled.getAssignedTo());
        assertNull(scheduled.getResult());
        assertNull(scheduled.getCalibrationDate());
        assertEquals(equipment, scheduled.getEquipment());
        verify(calibrationRecordRepository).save(any(CalibrationRecord.class));
    }

    @Test
    void completesScheduledCalibrationWithValidResult() {
        Equipment equipment = new Equipment();
        equipment.setId(10L);

        CalibrationRecord existing = new CalibrationRecord();
        existing.setId(42L);
        existing.setEquipment(equipment);
        existing.setStatus("SCHEDULED");
        existing.setScheduledDate(LocalDate.now());

        when(calibrationRecordRepository.findById(42L)).thenReturn(Optional.of(existing));
        when(calibrationRecordRepository.save(any(CalibrationRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LocalDate completedDate = LocalDate.now();
        LocalDate nextDue = completedDate.plusMonths(6);

        CalibrationRecord completed = service.complete(
                42L, completedDate, "PASS", nextDue, "Laser re-aligned cleanly", "CERT-2026-99"
        );

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals("PASS", completed.getResult());
        assertEquals(completedDate, completed.getCalibrationDate());
        assertEquals(nextDue, completed.getNextDueDate());
        assertEquals("CERT-2026-99", completed.getCertificateNumber());
        verify(calibrationRecordRepository).save(existing);
    }

    @Test
    void rejectsCompletionWithNextDueDateBeforeCalibrationDate() {
        CalibrationRecord existing = new CalibrationRecord();
        existing.setId(42L);
        when(calibrationRecordRepository.findById(42L)).thenReturn(Optional.of(existing));

        LocalDate completedDate = LocalDate.now();
        LocalDate invalidDue = completedDate.minusDays(1);

        assertThrows(IllegalArgumentException.class, () ->
                service.complete(42L, completedDate, "PASS", invalidDue, "Notes", null));
    }

    @Test
    void rejectsCompletionWithInvalidResult() {
        CalibrationRecord existing = new CalibrationRecord();
        existing.setId(42L);
        when(calibrationRecordRepository.findById(42L)).thenReturn(Optional.of(existing));

        LocalDate completedDate = LocalDate.now();
        LocalDate nextDue = completedDate.plusMonths(6);

        assertThrows(IllegalArgumentException.class, () ->
                service.complete(42L, completedDate, "INVALID_RESULT", nextDue, "Notes", null));
    }

    @Test
    void calculatesSummaryAccurately() {
        CalibrationRecord r1 = new CalibrationRecord();
        r1.setResult("PASS");
        r1.setStatus("COMPLETED");
        r1.setNextDueDate(LocalDate.now().plusDays(60));

        CalibrationRecord r2 = new CalibrationRecord();
        r2.setStatus("SCHEDULED");
        r2.setNextDueDate(LocalDate.now().plusDays(10)); // due soon

        CalibrationRecord r3 = new CalibrationRecord();
        r3.setResult("FAIL");
        r3.setStatus("COMPLETED");
        r3.setNextDueDate(LocalDate.now().minusDays(5)); // overdue

        when(calibrationRecordRepository.findAll()).thenReturn(List.of(r1, r2, r3));

        Map<String, Object> summary = service.getSummary(null);

        assertEquals(3L, summary.get("total"));
        assertEquals(1L, summary.get("passed"));
        assertEquals(1L, summary.get("failed"));
        assertEquals(1L, summary.get("scheduled"));
        assertEquals(1L, summary.get("overdue"));
        assertEquals(1L, summary.get("dueSoon"));
    }

    @Test
    void calculatesDynamicCalibrationStatesAccurately() {
        Equipment e1 = new Equipment(); e1.setId(1L); e1.setName("Equip Valid");
        Equipment e2 = new Equipment(); e2.setId(2L); e2.setName("Equip Due Soon");
        Equipment e3 = new Equipment(); e3.setId(3L); e3.setName("Equip Expired");
        Equipment e4 = new Equipment(); e4.setId(4L); e4.setName("Equip None");

        when(equipmentRepository.findByInstitutionId(1L)).thenReturn(List.of(e1, e2, e3, e4));

        CalibrationRecord r1 = new CalibrationRecord();
        r1.setNextDueDate(LocalDate.now().plusDays(60));
        when(calibrationRecordRepository.findFirstByEquipment_IdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(r1));

        CalibrationRecord r2 = new CalibrationRecord();
        r2.setNextDueDate(LocalDate.now().plusDays(10));
        when(calibrationRecordRepository.findFirstByEquipment_IdOrderByCreatedAtDesc(2L)).thenReturn(Optional.of(r2));

        CalibrationRecord r3 = new CalibrationRecord();
        r3.setNextDueDate(LocalDate.now().minusDays(5));
        when(calibrationRecordRepository.findFirstByEquipment_IdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(r3));

        when(calibrationRecordRepository.findFirstByEquipment_IdOrderByCreatedAtDesc(4L)).thenReturn(Optional.empty());

        Page<CalibrationOverviewItem> page = service.getCalibrationOverview(1L, null, null, null, Pageable.unpaged());
        List<CalibrationOverviewItem> items = page.getContent();

        assertEquals(4, items.size());

        CalibrationOverviewItem item1 = items.stream().filter(i -> i.getEquipmentId() == 1L).findFirst().orElseThrow();
        assertEquals("VALID", item1.getCalibrationState());

        CalibrationOverviewItem item2 = items.stream().filter(i -> i.getEquipmentId() == 2L).findFirst().orElseThrow();
        assertEquals("DUE_SOON", item2.getCalibrationState());

        CalibrationOverviewItem item3 = items.stream().filter(i -> i.getEquipmentId() == 3L).findFirst().orElseThrow();
        assertEquals("EXPIRED", item3.getCalibrationState());

        CalibrationOverviewItem item4 = items.stream().filter(i -> i.getEquipmentId() == 4L).findFirst().orElseThrow();
        assertEquals("NONE", item4.getCalibrationState());
        assertNull(item4.getDaysUntilDue());
    }

    @Test
    void ordersRemindersByUrgencyExpiredFirstThenDueSoon() {
        Equipment e1 = new Equipment(); e1.setId(1L); e1.setName("Equip Valid");
        Equipment e2 = new Equipment(); e2.setId(2L); e2.setName("Equip Due Soon");
        Equipment e3 = new Equipment(); e3.setId(3L); e3.setName("Equip Expired");

        when(equipmentRepository.findAll()).thenReturn(List.of(e1, e2, e3));

        CalibrationRecord r1 = new CalibrationRecord();
        r1.setNextDueDate(LocalDate.now().plusDays(60));
        when(calibrationRecordRepository.findFirstByEquipment_IdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(r1));

        CalibrationRecord r2 = new CalibrationRecord();
        r2.setNextDueDate(LocalDate.now().plusDays(10));
        when(calibrationRecordRepository.findFirstByEquipment_IdOrderByCreatedAtDesc(2L)).thenReturn(Optional.of(r2));

        CalibrationRecord r3 = new CalibrationRecord();
        r3.setNextDueDate(LocalDate.now().minusDays(5));
        when(calibrationRecordRepository.findFirstByEquipment_IdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(r3));

        List<CalibrationReminderItem> reminders = service.getCalibrationReminders(null);

        assertEquals(2, reminders.size());
        assertEquals("EXPIRED", reminders.get(0).getCalibrationState());
        assertEquals(3L, reminders.get(0).getEquipmentId());
        assertEquals("DUE_SOON", reminders.get(1).getCalibrationState());
        assertEquals(2L, reminders.get(1).getEquipmentId());
    }

    @Test
    void scheduledJobCreatesCalibrationWorkOrderWhenNoneActive() {
        Equipment e = new Equipment(); e.setId(10L); e.setName("NMR Spectrometer"); e.setAssetTag("NMR-01");
        when(equipmentRepository.findAll()).thenReturn(List.of(e));

        CalibrationRecord r = new CalibrationRecord();
        r.setNextDueDate(LocalDate.now().minusDays(2)); // expired
        when(calibrationRecordRepository.findFirstByEquipment_IdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(r));

        // No active calibration work order
        when(workOrderRepository.existsByEquipmentIdAndWorkOrderTypeAndStatusNotIn(
                eq(10L), eq("CALIBRATION"), any()
        )).thenReturn(false);

        when(workOrderRepository.findMaxWorkOrderNumber(any())).thenReturn(Optional.empty());
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        service.runDailyCalibrationRenewalJob();

        verify(workOrderRepository).save(argThat(wo ->
                "CALIBRATION".equals(wo.getWorkOrderType()) &&
                "HIGH".equals(wo.getPriority()) &&
                wo.getEquipmentId() == 10L
        ));
    }

    @Test
    void scheduledJobSkipsCreationIfActiveCalibrationWorkOrderAlreadyExists() {
        Equipment e = new Equipment(); e.setId(10L); e.setName("NMR Spectrometer");
        when(equipmentRepository.findAll()).thenReturn(List.of(e));

        CalibrationRecord r = new CalibrationRecord();
        r.setNextDueDate(LocalDate.now().minusDays(2));
        when(calibrationRecordRepository.findFirstByEquipment_IdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(r));

        // Active calibration work order already exists!
        when(workOrderRepository.existsByEquipmentIdAndWorkOrderTypeAndStatusNotIn(
                eq(10L), eq("CALIBRATION"), any()
        )).thenReturn(true);

        service.runDailyCalibrationRenewalJob();

        verify(workOrderRepository, never()).save(any(WorkOrder.class));
    }
}
