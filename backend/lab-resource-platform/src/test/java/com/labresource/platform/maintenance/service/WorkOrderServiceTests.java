package com.labresource.platform.maintenance.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingAuditLogRepository;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.booking.waitlist.repository.WaitlistRepository;
import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.maintenance.entity.EquipmentDowntimeLog;
import com.labresource.platform.maintenance.entity.WorkOrder;
import com.labresource.platform.maintenance.entity.WorkOrderServiceLog;
import com.labresource.platform.maintenance.model.CompleteWorkOrderRequest;
import com.labresource.platform.maintenance.model.TechnicianTasksResponse;
import com.labresource.platform.maintenance.model.TechnicianWorkloadDTO;
import com.labresource.platform.maintenance.model.TechnicianWorkloadProjection;
import com.labresource.platform.maintenance.repository.EquipmentDowntimeLogRepository;
import com.labresource.platform.maintenance.repository.PreventiveMaintenanceScheduleRepository;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import com.labresource.platform.maintenance.repository.WorkOrderServiceLogRepository;
import com.labresource.platform.storage.repository.StoredDocumentRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderServiceTests {

    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private WorkOrderServiceLogRepository serviceLogRepository;
    @Mock private EquipmentDowntimeLogRepository downtimeLogRepository;
    @Mock private PreventiveMaintenanceScheduleRepository scheduleRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingAuditLogRepository bookingAuditLogRepository;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DowntimeAnalyticsService downtimeAnalyticsService;
    @Mock private CalibrationRecordRepository calibrationRecordRepository;
    @Mock private StoredDocumentRepository storedDocumentRepository;

    private WorkOrderService service;

    private User managerUser;
    private User techUser;
    private User adminUser;
    private Equipment testEquipment;
    private Role managerRole;
    private Role techRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(
                workOrderRepository,
                serviceLogRepository,
                downtimeLogRepository,
                scheduleRepository,
                equipmentRepository,
                userRepository,
                userRoleRepository,
                roleRepository,
                bookingRepository,
                bookingAuditLogRepository,
                waitlistRepository,
                eventPublisher,
                downtimeAnalyticsService,
                calibrationRecordRepository,
                storedDocumentRepository
        );

        managerUser = new User();
        managerUser.setId(10L);
        managerUser.setEmail("manager@test.com");
        managerUser.setFullName("Lab Manager");
        managerUser.setInstitutionId(1L);

        techUser = new User();
        techUser.setId(15L);
        techUser.setEmail("tech@test.com");
        techUser.setFullName("Lab Tech");
        techUser.setInstitutionId(1L);

        adminUser = new User();
        adminUser.setId(99L);
        adminUser.setEmail("admin@sys.com");
        adminUser.setFullName("System Admin");

        testEquipment = new Equipment();
        testEquipment.setId(100L);
        testEquipment.setName("HPLC Spectrometer");
        testEquipment.setInstitutionId(1L);
        testEquipment.setStatus("AVAILABLE");

        managerRole = new Role();
        managerRole.setId((short) 1);
        managerRole.setName("LAB_MANAGER");

        techRole = new Role();
        techRole.setId((short) 2);
        techRole.setName("LAB_TECHNICIAN");

        adminRole = new Role();
        adminRole.setId((short) 3);
        adminRole.setName("SYSTEM_ADMIN");
    }

    private void mockUser(User user, Role role) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        UserRole ur = new UserRole(user.getId(), role.getId());
        when(userRoleRepository.findByUserId(user.getId())).thenReturn(List.of(ur));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
    }

    @Test
    void generateNextWorkOrderNumber_formatsCorrectly() {
        int year = LocalDateTime.now().getYear();
        String prefix = "WO-" + year + "-";
        when(workOrderRepository.findMaxWorkOrderNumber(prefix)).thenReturn(Optional.empty());
        String number1 = service.generateNextWorkOrderNumber();
        assertEquals(String.format("WO-%d-000001", year), number1);

        when(workOrderRepository.findMaxWorkOrderNumber(prefix)).thenReturn(Optional.of(String.format("WO-%d-000042", year)));
        String number2 = service.generateNextWorkOrderNumber();
        assertEquals(String.format("WO-%d-000043", year), number2);
    }

    @Test
    void createWorkOrder_success_asOpen() {
        mockUser(managerUser, managerRole);
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(testEquipment));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> {
            WorkOrder wo = inv.getArgument(0);
            wo.setId(777L);
            return wo;
        });

        WorkOrder created = service.createWorkOrder(
                100L, null, "Quarterly PM", "Routine check", "PREVENTIVE", "MEDIUM", null, null, "manager@test.com");

        assertNotNull(created);
        assertEquals("OPEN", created.getStatus());
        assertEquals("PREVENTIVE", created.getWorkOrderType());
        assertEquals(100L, created.getEquipmentId());
        assertNotNull(created.getWorkOrderNumber());
    }

    @Test
    void startWorkOrder_setsEquipmentUnderMaintenance_andCancelsBookings() {
        mockUser(managerUser, managerRole);

        WorkOrder wo = new WorkOrder();
        wo.setId(777L);
        wo.setEquipmentId(100L);
        wo.setStatus("ASSIGNED");
        wo.setAssignedTo(15L);

        when(workOrderRepository.findById(777L)).thenReturn(Optional.of(wo));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(testEquipment));

        Booking booking = new Booking();
        booking.setId(901L);
        booking.setEquipmentId(100L);
        booking.setUserId(20L);
        booking.setStatus("CONFIRMED");
        booking.setStartTime(LocalDateTime.now().plusHours(1));
        booking.setEndTime(LocalDateTime.now().plusHours(3));

        when(bookingRepository.findOverlappingBookingsForMaintenance(eq(100L), any(), any(), any()))
                .thenReturn(List.of(booking));

        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime expectedEnd = LocalDateTime.now().plusHours(4);
        WorkOrder started = service.startWorkOrder(777L, expectedEnd, "manager@test.com");

        assertEquals("IN_PROGRESS", started.getStatus());
        assertNotNull(started.getStartedAt());
        assertEquals("UNDER_MAINTENANCE", testEquipment.getStatus());
        assertEquals("CANCELLED", booking.getStatus());
        verify(equipmentRepository).save(testEquipment);
        verify(bookingRepository).save(booking);
        verify(downtimeLogRepository).save(any(EquipmentDowntimeLog.class));
    }

    @Test
    void completeWorkOrder_success_restoresEquipmentAvailable() {
        mockUser(managerUser, managerRole);

        WorkOrder wo = new WorkOrder();
        wo.setId(777L);
        wo.setEquipmentId(100L);
        wo.setStatus("IN_PROGRESS");
        wo.setStartedAt(LocalDateTime.now().minusHours(2));

        when(workOrderRepository.findById(777L)).thenReturn(Optional.of(wo));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(testEquipment));
        when(workOrderRepository.countByEquipmentIdAndStatus(100L, "IN_PROGRESS")).thenReturn(1L);

        EquipmentDowntimeLog activeLog = new EquipmentDowntimeLog();
        activeLog.setId(333L);
        activeLog.setWorkOrderId(777L);
        activeLog.setEquipmentId(100L);
        activeLog.setStartTime(LocalDateTime.now().minusHours(2));
        when(downtimeLogRepository.findFirstByWorkOrderIdAndEndTimeIsNullOrderByStartTimeDesc(777L)).thenReturn(Optional.of(activeLog));

        when(downtimeAnalyticsService.calculateOperatingHoursDowntime(eq(100L), any(), any())).thenReturn(120);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkOrder completed = service.completeWorkOrder(777L, "Pump replaced and calibrated", new BigDecimal("150.00"), "manager@test.com");

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals("AVAILABLE", testEquipment.getStatus());
        assertEquals("Pump replaced and calibrated", completed.getResolutionNotes());
        assertEquals(new BigDecimal("150.00"), completed.getCost());
        assertNotNull(activeLog.getEndTime());
        assertEquals(120, activeLog.getOperatingDowntimeMinutes());
        verify(equipmentRepository).save(testEquipment);
        verify(downtimeLogRepository).save(activeLog);
    }

    @Test
    void systemAdmin_forbidden_forWorkOrders() {
        mockUser(adminUser, adminRole);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.createWorkOrder(100L, null, "T", "D", "PREVENTIVE", "LOW", null, null, "admin@sys.com"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void completeWorkOrder_withCalibrationDetails_createsCalibrationRecordInSameTransaction() {
        mockUser(managerUser, managerRole);

        WorkOrder wo = new WorkOrder();
        wo.setId(999L);
        wo.setEquipmentId(100L);
        wo.setStatus("IN_PROGRESS");
        wo.setWorkOrderType("CALIBRATION");
        wo.setStartedAt(LocalDateTime.now().minusHours(1));

        when(workOrderRepository.findById(999L)).thenReturn(Optional.of(wo));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(testEquipment));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        when(calibrationRecordRepository.save(any(CalibrationRecord.class))).thenAnswer(inv -> {
            CalibrationRecord rec = inv.getArgument(0);
            rec.setId(505L);
            return rec;
        });

        CompleteWorkOrderRequest req = CompleteWorkOrderRequest.builder()
                .resolutionNotes("Annual calibration complete")
                .cost(new BigDecimal("250.00"))
                .calibrationDate(java.time.LocalDate.now())
                .nextDueDate(java.time.LocalDate.now().plusMonths(6))
                .result("PASS")
                .certificateNumber("CERT-XYZ-2026")
                .build();

        WorkOrder completed = service.completeWorkOrder(999L, req, "manager@test.com");

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals(505L, completed.getCalibrationRecordId());
        verify(calibrationRecordRepository).save(argThat(cal ->
                "PASS".equals(cal.getResult()) &&
                "CERT-XYZ-2026".equals(cal.getCertificateNumber()) &&
                cal.getWorkOrderId() == 999L
        ));
    }

    @Test
    void getTechniciansWorkload_usesGroupedQuery_returnsWorkloadCounts() {
        mockUser(managerUser, managerRole);

        Role techRoleEntity = new Role("LAB_TECHNICIAN", "Tech");
        techRoleEntity.setId((short) 2);
        when(roleRepository.findByName("LAB_TECHNICIAN")).thenReturn(Optional.of(techRoleEntity));

        UserRole ur = new UserRole(15L, (short) 2);
        when(userRoleRepository.findByRoleId((short) 2)).thenReturn(List.of(ur));

        techUser.setMembershipApproved(true);
        when(userRepository.findAllById(List.of(15L))).thenReturn(List.of(techUser));

        TechnicianWorkloadProjection projection = mock(TechnicianWorkloadProjection.class);
        when(projection.getTechnicianId()).thenReturn(15L);
        when(projection.getTotalTasks()).thenReturn(5L);
        when(projection.getActiveTasks()).thenReturn(3L);
        when(projection.getInProgressTasks()).thenReturn(1L);
        when(projection.getOverdueTasks()).thenReturn(1L);
        when(projection.getNextScheduledDate()).thenReturn(LocalDateTime.now().plusDays(2));

        when(workOrderRepository.findWorkloadByTechnicianIds(eq(List.of(15L)), any())).thenReturn(List.of(projection));

        List<TechnicianWorkloadDTO> workloads = service.getTechniciansWorkload(1L, "manager@test.com");

        assertEquals(1, workloads.size());
        TechnicianWorkloadDTO dto = workloads.get(0);
        assertEquals(15L, dto.getId());
        assertEquals(5L, dto.getTotalTasks());
        assertEquals(3L, dto.getActiveTasks());
        assertEquals(1L, dto.getInProgressTasks());
        assertEquals(1L, dto.getOverdueTasks());
        assertNotNull(dto.getNextScheduledDate());
        verify(workOrderRepository).findWorkloadByTechnicianIds(eq(List.of(15L)), any());
    }

    @Test
    void getTechnicianTasks_calculatesTaskGroupsAndOverdue() {
        mockUser(techUser, techRole);

        WorkOrder overdueWo = new WorkOrder();
        overdueWo.setId(1L);
        overdueWo.setAssignedTo(15L);
        overdueWo.setStatus("ASSIGNED");
        overdueWo.setScheduledDate(LocalDateTime.now().minusDays(1)); // overdue

        WorkOrder todayWo = new WorkOrder();
        todayWo.setId(2L);
        todayWo.setAssignedTo(15L);
        todayWo.setStatus("IN_PROGRESS");
        todayWo.setScheduledDate(LocalDateTime.now()); // today

        when(workOrderRepository.findByAssignedTo(15L)).thenReturn(List.of(overdueWo, todayWo));
        when(workOrderRepository.findTechnicianTasks(eq(15L), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(overdueWo, todayWo)));

        TechnicianTasksResponse resp = service.getTechnicianTasks(
                15L, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10), "tech@test.com"
        );

        assertNotNull(resp);
        assertEquals(2, resp.getContent().size());
        assertEquals(1L, resp.getCounts().get("overdue"));
        assertEquals(1L, resp.getCounts().get("inProgress"));
        assertEquals(1L, resp.getCounts().get("assigned"));

        assertTrue(resp.getTaskGroups().get("overdue").contains(overdueWo));
        assertTrue(resp.getTaskGroups().get("today").contains(todayWo));
    }
}
