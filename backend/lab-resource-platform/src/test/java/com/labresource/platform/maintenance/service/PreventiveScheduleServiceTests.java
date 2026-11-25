package com.labresource.platform.maintenance.service;

import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.maintenance.entity.PreventiveMaintenanceSchedule;
import com.labresource.platform.maintenance.entity.WorkOrder;
import com.labresource.platform.maintenance.repository.PreventiveMaintenanceScheduleRepository;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreventiveScheduleServiceTests {

    @Mock private PreventiveMaintenanceScheduleRepository scheduleRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private WorkOrderService workOrderService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PreventiveScheduleService service;

    private User managerUser;
    private Equipment testEquipment;
    private Role managerRole;

    @BeforeEach
    void setUp() {
        service = new PreventiveScheduleService(
                scheduleRepository,
                equipmentRepository,
                workOrderRepository,
                workOrderService,
                userRepository,
                userRoleRepository,
                roleRepository,
                "Asia/Kolkata"
        );

        managerUser = new User();
        managerUser.setId(10L);
        managerUser.setEmail("manager@test.com");
        managerUser.setFullName("Lab Manager");
        managerUser.setInstitutionId(1L);

        testEquipment = new Equipment();
        testEquipment.setId(100L);
        testEquipment.setName("Confocal Microscope");
        testEquipment.setInstitutionId(1L);
        testEquipment.setStatus("AVAILABLE");

        managerRole = new Role();
        managerRole.setId((short) 1);
        managerRole.setName("LAB_MANAGER");
    }

    private void mockManager() {
        when(userRepository.findByEmail(managerUser.getEmail())).thenReturn(Optional.of(managerUser));
        UserRole ur = new UserRole(managerUser.getId(), managerRole.getId());
        when(userRoleRepository.findByUserId(managerUser.getId())).thenReturn(List.of(ur));
        when(roleRepository.findById(managerRole.getId())).thenReturn(Optional.of(managerRole));
    }

    @Test
    void computeScheduleStatus_computesCorrectly() {
        LocalDate today = LocalDate.now();

        PreventiveMaintenanceSchedule sched = new PreventiveMaintenanceSchedule();
        sched.setActive(false);
        sched.setNextDueDate(today.plusDays(10));
        assertEquals("INACTIVE", service.computeScheduleStatus(sched));

        sched.setActive(true);
        sched.setNextDueDate(today.minusDays(1));
        assertEquals("OVERDUE", service.computeScheduleStatus(sched));

        sched.setNextDueDate(today.plusDays(3));
        assertEquals("DUE_SOON", service.computeScheduleStatus(sched));

        sched.setNextDueDate(today.plusDays(15));
        assertEquals("OK", service.computeScheduleStatus(sched));
    }

    @Test
    void createSchedule_success() {
        mockManager();
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(testEquipment));
        when(scheduleRepository.save(any(PreventiveMaintenanceSchedule.class))).thenAnswer(inv -> {
            PreventiveMaintenanceSchedule s = inv.getArgument(0);
            s.setId(444L);
            return s;
        });

        LocalDate due = LocalDate.now().plusDays(30);
        PreventiveMaintenanceSchedule created = service.createSchedule(
                100L, "Monthly Laser Calibration", "Align optics", 30, due, true, "manager@test.com");

        assertNotNull(created);
        assertEquals("Monthly Laser Calibration", created.getTitle());
        assertEquals(30, created.getRepeatPeriodDays());
        assertEquals(due, created.getNextDueDate());
        assertTrue(created.getActive());
        assertEquals("OK", created.getStatus());
    }

    @Test
    void runDailyPreventiveMaintenanceJob_createsWorkOrderForDueSchedule() {
        PreventiveMaintenanceSchedule dueSched = new PreventiveMaintenanceSchedule();
        dueSched.setId(444L);
        dueSched.setEquipmentId(100L);
        dueSched.setTitle("Monthly Calibration");
        dueSched.setDescription("Optical alignment");
        dueSched.setRepeatPeriodDays(30);
        dueSched.setNextDueDate(LocalDate.now().plusDays(2));
        dueSched.setActive(true);

        when(scheduleRepository.findDueSchedules(any(LocalDate.class))).thenReturn(List.of(dueSched));
        when(workOrderRepository.existsByScheduleIdAndStatusIn(eq(444L), any())).thenReturn(false);
        when(workOrderService.generateNextWorkOrderNumber()).thenReturn("WO-2026-000009");
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> {
            WorkOrder wo = inv.getArgument(0);
            wo.setId(555L);
            return wo;
        });

        service.runDailyPreventiveMaintenanceJob();

        verify(workOrderRepository).save(argThat(wo ->
                wo.getEquipmentId().equals(100L) &&
                wo.getScheduleId().equals(444L) &&
                "PREVENTIVE".equals(wo.getWorkOrderType()) &&
                "OPEN".equals(wo.getStatus())
        ));
        verify(workOrderService).recordServiceLog(eq(555L), eq(0L), eq("STATUS_CHANGE"), isNull(), eq("OPEN"), anyString());
    }
}
