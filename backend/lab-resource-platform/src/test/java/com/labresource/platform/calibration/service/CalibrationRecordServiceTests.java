package com.labresource.platform.calibration.service;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalibrationRecordServiceTests {

    @Mock
    private CalibrationRecordRepository calibrationRecordRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    private CalibrationRecordService service;

    @BeforeEach
    void setUp() {
        service = new CalibrationRecordService(calibrationRecordRepository, equipmentRepository);
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
}
