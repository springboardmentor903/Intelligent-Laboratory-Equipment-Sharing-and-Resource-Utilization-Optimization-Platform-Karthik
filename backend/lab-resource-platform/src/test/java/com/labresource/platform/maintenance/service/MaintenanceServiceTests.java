package com.labresource.platform.maintenance.service;

import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.maintenance.entity.Maintenance;
import com.labresource.platform.maintenance.repository.MaintenanceRepository;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTests {
    @Mock private MaintenanceRepository maintenanceRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private UserRepository userRepository;

    private MaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceService(maintenanceRepository,
                equipmentRepository, userRepository);
    }

    @Test
    void rejectsUnknownEquipmentOnCreate() {
        Maintenance record = validRecord();
        when(equipmentRepository.existsById(2L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.createMaintenance(record));

        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownReportingUserOnCreate() {
        Maintenance record = validRecord();
        when(equipmentRepository.existsById(2L)).thenReturn(true);
        when(userRepository.existsById(3L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.createMaintenance(record));

        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void rejectsAssignmentToUnknownUser() {
        Maintenance record = validRecord();
        record.setId(5L);
        when(maintenanceRepository.findById(5L)).thenReturn(Optional.of(record));
        when(userRepository.existsById(8L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.assignTechnician(5L, 8L));

        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void reportedWorkCannotSkipAssignmentAndStart() {
        Maintenance record = validRecord();
        record.setId(6L);
        when(maintenanceRepository.findById(6L)).thenReturn(Optional.of(record));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(6L, "COMPLETED"));

        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void assignedWorkCanStartAndCompletionRecordsTimes() {
        Maintenance record = validRecord();
        record.setId(7L);
        record.setAssignedTo(8L);
        record.setStatus("ASSIGNED");
        when(maintenanceRepository.findById(7L)).thenReturn(Optional.of(record));
        when(maintenanceRepository.save(any(Maintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Maintenance started = service.updateStatus(7L, "IN_PROGRESS");
        assert started.getStartedAt() != null;

        Maintenance completed = service.updateStatus(7L, "COMPLETED");
        assert completed.getCompletedAt() != null;
        verify(maintenanceRepository, org.mockito.Mockito.times(2)).save(record);
    }

    @Test
    void assignmentCannotBeChangedAfterWorkStarts() {
        Maintenance record = validRecord();
        record.setId(8L);
        record.setStatus("IN_PROGRESS");
        record.setStartedAt(LocalDateTime.now());
        when(maintenanceRepository.findById(8L)).thenReturn(Optional.of(record));

        assertThrows(IllegalArgumentException.class,
                () -> service.assignTechnician(8L, 9L));

        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void resolutionNotesMustBeNonblankAndFitMappedColumn() {
        Maintenance record = validRecord();
        record.setId(9L);
        when(maintenanceRepository.findById(9L)).thenReturn(Optional.of(record));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateResolutionNotes(9L, " "));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateResolutionNotes(9L, "x".repeat(1001)));

        verify(maintenanceRepository, never()).save(any());
    }

    private Maintenance validRecord() {
        Maintenance record = new Maintenance();
        record.setEquipmentId(2L);
        record.setReportedBy(3L);
        record.setDescription("Repair needed");
        return record;
    }
}
