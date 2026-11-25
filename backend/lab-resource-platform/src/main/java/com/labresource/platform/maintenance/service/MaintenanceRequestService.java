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
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MaintenanceRequestService {

    private final MaintenanceRequestRepository requestRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;

    public MaintenanceRequestService(
            MaintenanceRequestRepository requestRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            WorkOrderRepository workOrderRepository,
            @Lazy WorkOrderService workOrderService) {
        this.requestRepository = requestRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.workOrderRepository = workOrderRepository;
        this.workOrderService = workOrderService;
    }

    private User getActor(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User authentication required");
        }
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Set<String> getUserRoles(Long userId) {
        if (userId == null) return Set.of();
        List<UserRole> urs = userRoleRepository.findByUserId(userId);
        return urs.stream()
                .map(UserRole::getRoleId)
                .map(roleRepository::findById)
                .flatMap(Optional::stream)
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    private void checkNotSystemAdmin(Set<String> roles) {
        if (roles.contains("SYSTEM_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "System administrators do not have access to maintenance operations.");
        }
    }

    @Transactional
    public MaintenanceRequest submitRequest(Long equipmentId, String title, String description, String priority, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        boolean allowed = roles.stream().anyMatch(r -> List.of(
                "RESEARCHER_STUDENT", "STUDENT", "DEPARTMENT_HEAD", "LAB_TECHNICIAN", "LAB_MANAGER"
        ).contains(r));

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to submit maintenance requests.");
        }

        if (equipmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment ID is required.");
        }
        if (title == null || title.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required.");
        }
        if (description == null || description.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required.");
        }

        Equipment eq = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        // If user belongs to an institution, ensure equipment belongs to the same institution
        if (user.getInstitutionId() != null && !Objects.equals(user.getInstitutionId(), eq.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only submit maintenance requests for equipment in your institution.");
        }

        String normPriority = priority == null || priority.trim().isBlank() ? "MEDIUM" : priority.trim().toUpperCase();
        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(normPriority)) {
            normPriority = "MEDIUM";
        }

        MaintenanceRequest req = new MaintenanceRequest();
        req.setEquipmentId(eq.getId());
        req.setRequesterId(user.getId());
        req.setInstitutionId(eq.getInstitutionId() != null ? eq.getInstitutionId() : (user.getInstitutionId() != null ? user.getInstitutionId() : 0L));
        req.setTitle(title.trim());
        req.setDescription(description.trim());
        req.setPriority(normPriority);
        req.setStatus("PENDING");
        req.setCreatedAt(LocalDateTime.now());

        MaintenanceRequest saved = requestRepository.save(req);
        enrichRequest(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceRequest> getMyRequests(String userEmail, String status, Long equipmentId, String priority, Pageable pageable) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        String normStatus = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        String normPriority = priority != null && !priority.isBlank() ? priority.trim().toUpperCase() : null;

        Page<MaintenanceRequest> page = requestRepository.findMyRequestsWithFilters(
                user.getId(), normStatus, equipmentId, normPriority, pageable
        );
        page.getContent().forEach(this::enrichRequest);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceRequest> getQueue(String userEmail, String status, Long equipmentId, String priority, Pageable pageable) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        boolean canAccess = roles.contains("LAB_MANAGER") || roles.contains("INSTITUTION_ADMIN");
        if (!canAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers and Institution Administrators can view the request queue.");
        }

        Long instId = user.getInstitutionId();
        if (instId == null) {
            return Page.empty(pageable);
        }

        String normStatus = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        String normPriority = priority != null && !priority.isBlank() ? priority.trim().toUpperCase() : null;

        Page<MaintenanceRequest> page = requestRepository.findQueueWithFilters(
                instId, normStatus, equipmentId, normPriority, pageable
        );
        page.getContent().forEach(this::enrichRequest);
        return page;
    }

    @Transactional
    public MaintenanceRequest approveRequest(Long requestId, String remarks, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        if (!roles.contains("LAB_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers can approve maintenance requests.");
        }

        MaintenanceRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maintenance request not found"));

        if (!Objects.equals(user.getInstitutionId(), req.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only approve requests for your own institution.");
        }

        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is already " + req.getStatus());
        }

        req.setStatus("APPROVED");
        req.setReviewedBy(user.getId());
        req.setReviewedAt(LocalDateTime.now());
        if (remarks != null && !remarks.isBlank()) {
            req.setReviewRemarks(remarks.trim());
        }

        // Automatically create a CORRECTIVE work order in the same transaction
        WorkOrder wo = workOrderService.createCorrectiveWorkOrderFromRequest(req, user);
        req.setWorkOrderId(wo.getId());

        MaintenanceRequest saved = requestRepository.save(req);
        enrichRequest(saved);
        return saved;
    }

    @Transactional
    public MaintenanceRequest rejectRequest(Long requestId, String remarks, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        if (!roles.contains("LAB_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers can reject maintenance requests.");
        }

        if (remarks == null || remarks.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection remarks are required.");
        }

        MaintenanceRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maintenance request not found"));

        if (!Objects.equals(user.getInstitutionId(), req.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only reject requests for your own institution.");
        }

        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is already " + req.getStatus());
        }

        req.setStatus("REJECTED");
        req.setReviewedBy(user.getId());
        req.setReviewedAt(LocalDateTime.now());
        req.setReviewRemarks(remarks.trim());

        MaintenanceRequest saved = requestRepository.save(req);
        enrichRequest(saved);
        return saved;
    }

    public void enrichRequest(MaintenanceRequest req) {
        if (req == null) return;
        if (req.getEquipmentId() != null) {
            equipmentRepository.findById(req.getEquipmentId()).ifPresent(eq -> {
                req.setEquipmentName(eq.getName());
                req.setAssetTag(eq.getAssetTag());
            });
        }
        if (req.getRequesterId() != null) {
            userRepository.findById(req.getRequesterId()).ifPresent(u -> {
                req.setRequesterName(u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail());
                req.setRequesterEmail(u.getEmail());
            });
        }
        if (req.getReviewedBy() != null) {
            userRepository.findById(req.getReviewedBy()).ifPresent(u -> {
                req.setReviewerName(u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail());
            });
        }
        if (req.getWorkOrderId() != null) {
            workOrderRepository.findById(req.getWorkOrderId()).ifPresent(wo -> {
                req.setWorkOrderNumber(wo.getWorkOrderNumber());
            });
        }
    }
}
