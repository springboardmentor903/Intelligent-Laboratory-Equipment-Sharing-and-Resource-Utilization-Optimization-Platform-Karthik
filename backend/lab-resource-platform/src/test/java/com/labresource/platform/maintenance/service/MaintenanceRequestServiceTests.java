package com.labresource.platform.maintenance.service;

import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.maintenance.entity.MaintenanceRequest;
import com.labresource.platform.maintenance.entity.WorkOrder;
import com.labresource.platform.maintenance.repository.MaintenanceRequestRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaintenanceRequestServiceTests {

    @Mock private MaintenanceRequestRepository requestRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private WorkOrderService workOrderService;

    private MaintenanceRequestService service;

    private User labManagerUser;
    private User researcherUser;
    private User systemAdminUser;
    private Equipment testEquipment;
    private Role labManagerRole;
    private Role researcherRole;
    private Role systemAdminRole;

    @BeforeEach
    void setUp() {
        service = new MaintenanceRequestService(
                requestRepository,
                equipmentRepository,
                userRepository,
                userRoleRepository,
                roleRepository,
                workOrderRepository,
                workOrderService
        );

        labManagerUser = new User();
        labManagerUser.setId(10L);
        labManagerUser.setEmail("manager@test.com");
        labManagerUser.setFullName("Lab Manager");
        labManagerUser.setInstitutionId(1L);

        researcherUser = new User();
        researcherUser.setId(20L);
        researcherUser.setEmail("researcher@test.com");
        researcherUser.setFullName("Researcher");
        researcherUser.setInstitutionId(1L);

        systemAdminUser = new User();
        systemAdminUser.setId(99L);
        systemAdminUser.setEmail("admin@sys.com");
        systemAdminUser.setFullName("System Admin");

        testEquipment = new Equipment();
        testEquipment.setId(100L);
        testEquipment.setName("Confocal Microscope");
        testEquipment.setInstitutionId(1L);
        testEquipment.setStatus("AVAILABLE");

        labManagerRole = new Role();
        labManagerRole.setId((short) 1);
        labManagerRole.setName("LAB_MANAGER");

        researcherRole = new Role();
        researcherRole.setId((short) 2);
        researcherRole.setName("RESEARCHER_STUDENT");

        systemAdminRole = new Role();
        systemAdminRole.setId((short) 3);
        systemAdminRole.setName("SYSTEM_ADMIN");
    }

    private void mockUserRoles(User user, Role role) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        UserRole ur = new UserRole(user.getId(), role.getId());
        when(userRoleRepository.findByUserId(user.getId())).thenReturn(List.of(ur));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
    }

    @Test
    void submitRequest_success_byResearcher() {
        mockUserRoles(researcherUser, researcherRole);
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(testEquipment));
        when(requestRepository.save(any(MaintenanceRequest.class))).thenAnswer(inv -> {
            MaintenanceRequest req = inv.getArgument(0);
            req.setId(500L);
            return req;
        });

        MaintenanceRequest result = service.submitRequest(100L, "Laser alignment off", "Beam is fluctuating", "HIGH", "researcher@test.com");

        assertNotNull(result);
        assertEquals("Laser alignment off", result.getTitle());
        assertEquals("HIGH", result.getPriority());
        assertEquals("PENDING", result.getStatus());
        assertEquals(20L, result.getRequesterId());
        assertEquals(100L, result.getEquipmentId());
    }

    @Test
    void submitRequest_forbidden_forSystemAdmin() {
        mockUserRoles(systemAdminUser, systemAdminRole);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.submitRequest(100L, "Title", "Desc", "MEDIUM", "admin@sys.com"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(requestRepository, never()).save(any());
    }

    @Test
    void submitRequest_forbidden_differentInstitution() {
        testEquipment.setInstitutionId(2L); // Different from researcher's 1L
        mockUserRoles(researcherUser, researcherRole);
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(testEquipment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.submitRequest(100L, "Title", "Desc", "MEDIUM", "researcher@test.com"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(requestRepository, never()).save(any());
    }

    @Test
    void approveRequest_success_autoCreatesCorrectiveWorkOrder() {
        mockUserRoles(labManagerUser, labManagerRole);

        MaintenanceRequest pendingReq = new MaintenanceRequest();
        pendingReq.setId(500L);
        pendingReq.setEquipmentId(100L);
        pendingReq.setRequesterId(20L);
        pendingReq.setInstitutionId(1L);
        pendingReq.setTitle("Laser off");
        pendingReq.setDescription("Fluctuating");
        pendingReq.setPriority("HIGH");
        pendingReq.setStatus("PENDING");

        when(requestRepository.findById(500L)).thenReturn(Optional.of(pendingReq));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(testEquipment));

        WorkOrder autoCreatedWo = new WorkOrder();
        autoCreatedWo.setId(888L);
        autoCreatedWo.setWorkOrderNumber("WO-2026-000001");
        when(workOrderService.createCorrectiveWorkOrderFromRequest(eq(pendingReq), eq(labManagerUser)))
                .thenReturn(autoCreatedWo);

        when(requestRepository.save(any(MaintenanceRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        MaintenanceRequest approved = service.approveRequest(500L, "Approved for inspection", "manager@test.com");

        assertEquals("APPROVED", approved.getStatus());
        assertEquals(10L, approved.getReviewedBy());
        assertNotNull(approved.getReviewedAt());
        assertEquals(888L, approved.getWorkOrderId());
        assertEquals("Approved for inspection", approved.getReviewRemarks());
    }

    @Test
    void rejectRequest_requiresRemarks() {
        mockUserRoles(labManagerUser, labManagerRole);

        MaintenanceRequest pendingReq = new MaintenanceRequest();
        pendingReq.setId(500L);
        pendingReq.setEquipmentId(100L);
        pendingReq.setInstitutionId(1L);
        pendingReq.setStatus("PENDING");

        when(requestRepository.findById(500L)).thenReturn(Optional.of(pendingReq));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(testEquipment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.rejectRequest(500L, "   ", "manager@test.com"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void rejectRequest_success() {
        mockUserRoles(labManagerUser, labManagerRole);

        MaintenanceRequest pendingReq = new MaintenanceRequest();
        pendingReq.setId(500L);
        pendingReq.setEquipmentId(100L);
        pendingReq.setInstitutionId(1L);
        pendingReq.setStatus("PENDING");

        when(requestRepository.findById(500L)).thenReturn(Optional.of(pendingReq));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(testEquipment));
        when(requestRepository.save(any(MaintenanceRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        MaintenanceRequest rejected = service.rejectRequest(500L, "Not a hardware issue", "manager@test.com");

        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("Not a hardware issue", rejected.getReviewRemarks());
        assertEquals(10L, rejected.getReviewedBy());
    }
}
