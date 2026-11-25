package com.labresource.platform.maintenance.service;

import com.labresource.platform.analytics.service.DailyUtilizationSnapshotService;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.entity.BookingAuditLog;
import com.labresource.platform.booking.event.BookingMaintenanceCancelledEvent;
import com.labresource.platform.booking.event.BookingReleasedEvent;
import com.labresource.platform.booking.repository.BookingAuditLogRepository;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.booking.waitlist.entity.WaitlistEntry;
import com.labresource.platform.booking.waitlist.repository.WaitlistRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.maintenance.entity.*;
import com.labresource.platform.maintenance.repository.*;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderServiceLogRepository serviceLogRepository;
    private final EquipmentDowntimeLogRepository downtimeLogRepository;
    private final PreventiveMaintenanceScheduleRepository scheduleRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final BookingRepository bookingRepository;
    private final BookingAuditLogRepository bookingAuditLogRepository;
    private final WaitlistRepository waitlistRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DowntimeAnalyticsService downtimeAnalyticsService;

    public WorkOrderService(
            WorkOrderRepository workOrderRepository,
            WorkOrderServiceLogRepository serviceLogRepository,
            EquipmentDowntimeLogRepository downtimeLogRepository,
            PreventiveMaintenanceScheduleRepository scheduleRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            BookingRepository bookingRepository,
            @Lazy BookingAuditLogRepository bookingAuditLogRepository,
            @Lazy WaitlistRepository waitlistRepository,
            ApplicationEventPublisher eventPublisher,
            @Lazy DowntimeAnalyticsService downtimeAnalyticsService) {
        this.workOrderRepository = workOrderRepository;
        this.serviceLogRepository = serviceLogRepository;
        this.downtimeLogRepository = downtimeLogRepository;
        this.scheduleRepository = scheduleRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.bookingRepository = bookingRepository;
        this.bookingAuditLogRepository = bookingAuditLogRepository;
        this.waitlistRepository = waitlistRepository;
        this.eventPublisher = eventPublisher;
        this.downtimeAnalyticsService = downtimeAnalyticsService;
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
    public synchronized String generateNextWorkOrderNumber() {
        int year = LocalDate.now().getYear();
        String prefix = "WO-" + year + "-";
        Optional<String> maxNumber = workOrderRepository.findMaxWorkOrderNumber(prefix);
        long nextSeq = 1;
        if (maxNumber.isPresent()) {
            String numStr = maxNumber.get().substring(prefix.length());
            try {
                nextSeq = Long.parseLong(numStr) + 1;
            } catch (NumberFormatException ignored) {
            }
        }
        return String.format("%s%06d", prefix, nextSeq);
    }

    @Transactional
    public WorkOrder createCorrectiveWorkOrderFromRequest(MaintenanceRequest req, User creator) {
        WorkOrder wo = new WorkOrder();
        wo.setWorkOrderNumber(generateNextWorkOrderNumber());
        wo.setEquipmentId(req.getEquipmentId());
        wo.setRequestId(req.getId());
        wo.setTitle(req.getTitle());
        wo.setDescription(req.getDescription());
        wo.setWorkOrderType("CORRECTIVE");
        wo.setPriority(req.getPriority());
        wo.setStatus("OPEN");
        wo.setCreatedBy(creator.getId());
        wo.setCreatedAt(LocalDateTime.now());
        wo.setUpdatedAt(LocalDateTime.now());

        WorkOrder saved = workOrderRepository.save(wo);

        recordServiceLog(saved.getId(), creator.getId(), "STATUS_CHANGE", null, "OPEN",
                "Work order created automatically from approved maintenance request #" + req.getId());

        enrichWorkOrder(saved);
        return saved;
    }

    @Transactional
    public WorkOrder createWorkOrder(
            Long equipmentId,
            Long scheduleId,
            String title,
            String description,
            String workOrderType,
            String priority,
            Long assignedTo,
            LocalDateTime scheduledDate,
            String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        if (!roles.contains("LAB_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers can create work orders.");
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

        if (!Objects.equals(user.getInstitutionId(), eq.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Equipment must belong to your institution.");
        }

        String normType = "PREVENTIVE".equalsIgnoreCase(workOrderType) ? "PREVENTIVE" : "CORRECTIVE";
        String normPriority = priority == null || priority.trim().isBlank() ? "MEDIUM" : priority.trim().toUpperCase();
        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(normPriority)) {
            normPriority = "MEDIUM";
        }

        WorkOrder wo = new WorkOrder();
        wo.setWorkOrderNumber(generateNextWorkOrderNumber());
        wo.setEquipmentId(eq.getId());
        wo.setScheduleId(scheduleId);
        wo.setTitle(title.trim());
        wo.setDescription(description.trim());
        wo.setWorkOrderType(normType);
        wo.setPriority(normPriority);
        wo.setScheduledDate(scheduledDate);
        wo.setCreatedBy(user.getId());
        wo.setCreatedAt(LocalDateTime.now());
        wo.setUpdatedAt(LocalDateTime.now());

        if (assignedTo != null) {
            User tech = userRepository.findById(assignedTo)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned technician not found"));
            Set<String> techRoles = getUserRoles(tech.getId());
            if (!techRoles.contains("LAB_TECHNICIAN")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned user must be a Lab Technician");
            }
            if (!Objects.equals(tech.getInstitutionId(), eq.getInstitutionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned technician must belong to the same institution as the equipment");
            }
            wo.setAssignedTo(tech.getId());
            wo.setAssignedAt(LocalDateTime.now());
            wo.setStatus("ASSIGNED");
        } else {
            wo.setStatus("OPEN");
        }

        WorkOrder saved = workOrderRepository.save(wo);

        recordServiceLog(saved.getId(), user.getId(), "STATUS_CHANGE", null, saved.getStatus(),
                "Work order created. Status: " + saved.getStatus());

        enrichWorkOrder(saved);
        return saved;
    }

    @Transactional
    public WorkOrder createWorkOrder(
            Long equipmentId,
            String title,
            String description,
            String workOrderType,
            String priority,
            Long assignedTo,
            LocalDateTime scheduledDate,
            String userEmail) {
        return createWorkOrder(equipmentId, null, title, description, workOrderType, priority, assignedTo, scheduledDate, userEmail);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrder> getWorkOrders(
            String status,
            String workOrderType,
            String priority,
            Long equipmentId,
            Boolean assignedToMe,
            Pageable pageable,
            String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        boolean isTech = roles.contains("LAB_TECHNICIAN");
        boolean isManager = roles.contains("LAB_MANAGER");
        boolean isAdmin = roles.contains("INSTITUTION_ADMIN");

        if (!isTech && !isManager && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view work orders.");
        }

        Long instId = user.getInstitutionId();
        if (instId == null) {
            return Page.empty(pageable);
        }

        List<Equipment> instEquipment = equipmentRepository.findByInstitutionId(instId);
        List<Long> equipmentIds = instEquipment.stream().map(Equipment::getId).toList();
        if (equipmentIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Long assignedFilter = null;
        if (isTech && !isManager) {
            // Lab Technician can access ONLY their assigned work orders
            assignedFilter = user.getId();
        } else if (Boolean.TRUE.equals(assignedToMe)) {
            assignedFilter = user.getId();
        }

        String normStatus = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        String normType = workOrderType != null && !workOrderType.isBlank() ? workOrderType.trim().toUpperCase() : null;
        String normPriority = priority != null && !priority.isBlank() ? priority.trim().toUpperCase() : null;

        Page<WorkOrder> page = workOrderRepository.findWorkOrdersWithFilters(
                equipmentIds, normStatus, normType, normPriority, equipmentId, assignedFilter, pageable
        );
        page.getContent().forEach(this::enrichWorkOrder);
        return page;
    }

    @Transactional(readOnly = true)
    public WorkOrder getWorkOrderById(Long id, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        WorkOrder wo = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));

        Equipment eq = equipmentRepository.findById(wo.getEquipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        if (!Objects.equals(user.getInstitutionId(), eq.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Work order does not belong to your institution.");
        }

        boolean isTech = roles.contains("LAB_TECHNICIAN");
        boolean isManager = roles.contains("LAB_MANAGER");
        boolean isAdmin = roles.contains("INSTITUTION_ADMIN");

        if (!isTech && !isManager && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        if (isTech && !isManager && !isAdmin && !Objects.equals(wo.getAssignedTo(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lab Technicians can only access their assigned work orders.");
        }

        enrichWorkOrder(wo);
        return wo;
    }

    @Transactional
    public WorkOrder assignTechnician(Long workOrderId, Long technicianId, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        if (!roles.contains("LAB_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers can assign work orders.");
        }

        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));

        if (List.of("COMPLETED", "CANCELLED").contains(wo.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot assign a " + wo.getStatus() + " work order.");
        }

        Equipment eq = equipmentRepository.findById(wo.getEquipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        if (!Objects.equals(user.getInstitutionId(), eq.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Equipment does not belong to your institution.");
        }

        if (technicianId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Technician ID is required.");
        }

        User tech = userRepository.findById(technicianId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Technician not found"));

        Set<String> techRoles = getUserRoles(tech.getId());
        if (!techRoles.contains("LAB_TECHNICIAN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned user must be a Lab Technician");
        }
        if (!Objects.equals(tech.getInstitutionId(), eq.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned technician must belong to the same institution as the equipment");
        }

        String prevStatus = wo.getStatus();
        wo.setAssignedTo(tech.getId());
        wo.setAssignedAt(LocalDateTime.now());
        if ("OPEN".equals(wo.getStatus())) {
            wo.setStatus("ASSIGNED");
        }
        wo.setUpdatedAt(LocalDateTime.now());

        WorkOrder saved = workOrderRepository.save(wo);

        recordServiceLog(saved.getId(), user.getId(), "ASSIGNMENT", prevStatus, saved.getStatus(),
                "Assigned to technician: " + (tech.getFullName() != null ? tech.getFullName() : tech.getEmail()));

        enrichWorkOrder(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStartPreview(Long workOrderId, LocalDateTime proposedEndTime, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));

        Equipment eq = equipmentRepository.findById(wo.getEquipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        if (!Objects.equals(user.getInstitutionId(), eq.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Equipment does not belong to your institution.");
        }

        if (proposedEndTime == null || !proposedEndTime.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected maintenance end time must be in the future.");
        }

        LocalDateTime windowStart = wo.getStartedAt() != null ? wo.getStartedAt() : LocalDateTime.now();

        List<Booking> overlapping = bookingRepository.findOverlappingBookingsForMaintenance(
                wo.getEquipmentId(),
                List.of("PENDING", "APPROVED", "CONFIRMED"),
                windowStart,
                proposedEndTime
        );

        List<Map<String, Object>> bookingList = new ArrayList<>();
        for (Booking b : overlapping) {
            Map<String, Object> item = new HashMap<>();
            item.put("bookingId", b.getId());
            item.put("equipmentId", b.getEquipmentId());
            item.put("equipmentName", eq.getName());
            item.put("startTime", b.getStartTime());
            item.put("endTime", b.getEndTime());
            item.put("status", b.getStatus());
            item.put("purpose", b.getPurpose());
            item.put("userId", b.getUserId());
            userRepository.findById(b.getUserId()).ifPresent(u -> {
                item.put("userName", u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail());
                item.put("userEmail", u.getEmail());
            });
            bookingList.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("workOrderId", wo.getId());
        result.put("workOrderNumber", wo.getWorkOrderNumber());
        result.put("equipmentId", eq.getId());
        result.put("equipmentName", eq.getName());
        result.put("proposedEndTime", proposedEndTime);
        result.put("affectedCount", bookingList.size());
        result.put("overlappingBookings", bookingList);
        return result;
    }

    @Transactional
    public WorkOrder startWorkOrder(Long workOrderId, LocalDateTime expectedEndTime, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));

        boolean isManager = roles.contains("LAB_MANAGER");
        boolean isAssignedTech = roles.contains("LAB_TECHNICIAN") && Objects.equals(wo.getAssignedTo(), user.getId());

        if (!isManager && !isAssignedTech) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Lab Manager or assigned Technician can start this work order.");
        }

        if (!List.of("OPEN", "ASSIGNED").contains(wo.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work order cannot be started from status " + wo.getStatus());
        }

        if (expectedEndTime == null || !expectedEndTime.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected maintenance end time must be in the future.");
        }

        LocalDateTime startedAt = LocalDateTime.now();
        String prevStatus = wo.getStatus();

        wo.setStatus("IN_PROGRESS");
        wo.setStartedAt(startedAt);
        wo.setExpectedEndTime(expectedEndTime);
        wo.setUpdatedAt(startedAt);
        WorkOrder saved = workOrderRepository.save(wo);

        // 1. Move equipment to UNDER_MAINTENANCE
        Equipment eq = equipmentRepository.findById(wo.getEquipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
        eq.setStatus("UNDER_MAINTENANCE");
        equipmentRepository.save(eq);

        // 2. Record downtime start log
        EquipmentDowntimeLog dLog = new EquipmentDowntimeLog();
        dLog.setEquipmentId(eq.getId());
        dLog.setWorkOrderId(saved.getId());
        dLog.setStartTime(startedAt);
        dLog.setExpectedEndTime(expectedEndTime);
        dLog.setReason("Work order " + saved.getWorkOrderNumber() + ": " + saved.getTitle());
        dLog.setCreatedAt(startedAt);
        downtimeLogRepository.save(dLog);

        // 3. Automatically cancel overlapping PENDING_APPROVAL and CONFIRMED bookings
        cancelOverlappingBookings(eq.getId(), startedAt, expectedEndTime, saved.getId(), user.getEmail());

        // 4. Expire open waitlist offers overlapping maintenance period
        expireOverlappingWaitlistOffers(eq.getId(), startedAt, expectedEndTime);

        // 5. Service log
        recordServiceLog(saved.getId(), user.getId(), "STATUS_CHANGE", prevStatus, "IN_PROGRESS",
                "Work order started. Equipment moved to UNDER_MAINTENANCE. Expected completion: " + expectedEndTime);

        enrichWorkOrder(saved);
        return saved;
    }

    @Transactional
    public WorkOrder extendWorkOrder(Long workOrderId, LocalDateTime newExpectedEndTime, String reason, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        if (!roles.contains("LAB_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers can extend work order completion times.");
        }

        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));

        if (!"IN_PROGRESS".equals(wo.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only IN_PROGRESS work orders can be extended.");
        }

        if (newExpectedEndTime == null || (wo.getExpectedEndTime() != null && !newExpectedEndTime.isAfter(wo.getExpectedEndTime()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New expected end time must be after the current expected end time.");
        }

        LocalDateTime previousExpected = wo.getExpectedEndTime() != null ? wo.getExpectedEndTime() : LocalDateTime.now();
        wo.setExpectedEndTime(newExpectedEndTime);
        wo.setUpdatedAt(LocalDateTime.now());
        WorkOrder saved = workOrderRepository.save(wo);

        // Update active downtime log
        downtimeLogRepository.findFirstByWorkOrderIdAndEndTimeIsNullOrderByStartTimeDesc(saved.getId())
                .ifPresent(dLog -> {
                    dLog.setExpectedEndTime(newExpectedEndTime);
                    downtimeLogRepository.save(dLog);
                });

        // Cancel newly overlapping bookings
        cancelOverlappingBookings(saved.getEquipmentId(), previousExpected, newExpectedEndTime, saved.getId(), user.getEmail());
        expireOverlappingWaitlistOffers(saved.getEquipmentId(), previousExpected, newExpectedEndTime);

        recordServiceLog(saved.getId(), user.getId(), "EXTENSION", "IN_PROGRESS", "IN_PROGRESS",
                "Maintenance extended to " + newExpectedEndTime + ". Reason: " + (reason != null ? reason.trim() : "None provided"));

        enrichWorkOrder(saved);
        return saved;
    }

    @Transactional
    public WorkOrder completeWorkOrder(Long workOrderId, String resolutionNotes, BigDecimal cost, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));

        boolean isManager = roles.contains("LAB_MANAGER");
        boolean isAssignedTech = roles.contains("LAB_TECHNICIAN") && Objects.equals(wo.getAssignedTo(), user.getId());

        if (!isManager && !isAssignedTech) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Lab Manager or assigned Technician can complete this work order.");
        }

        if (!"IN_PROGRESS".equals(wo.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only IN_PROGRESS work orders can be completed.");
        }

        if (resolutionNotes == null || resolutionNotes.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolution notes are required.");
        }

        LocalDateTime completedAt = LocalDateTime.now();
        wo.setStatus("COMPLETED");
        wo.setCompletedAt(completedAt);
        wo.setResolutionNotes(resolutionNotes.trim());
        if (cost != null && cost.signum() >= 0) {
            wo.setCost(cost);
        }

        // Calculate downtime
        long downtimeMinutes = 0;
        if (wo.getStartedAt() != null) {
            downtimeMinutes = Duration.between(wo.getStartedAt(), completedAt).toMinutes();
            wo.setDowntimeHours(Math.round((downtimeMinutes / 60.0) * 10.0) / 10.0);
        }
        wo.setUpdatedAt(completedAt);
        WorkOrder saved = workOrderRepository.save(wo);

        // Close downtime log & compute operating hours downtime
        downtimeLogRepository.findFirstByWorkOrderIdAndEndTimeIsNullOrderByStartTimeDesc(saved.getId())
                .ifPresent(dLog -> {
                    dLog.setEndTime(completedAt);
                    dLog.setTotalDowntimeMinutes((int) Duration.between(dLog.getStartTime(), completedAt).toMinutes());
                    int opMinutes = downtimeAnalyticsService.calculateOperatingHoursDowntime(
                            saved.getEquipmentId(), dLog.getStartTime(), completedAt
                    );
                    dLog.setOperatingDowntimeMinutes(opMinutes);
                    downtimeLogRepository.save(dLog);
                });

        // Restore equipment status back to AVAILABLE ONLY if no other work order is IN_PROGRESS
        long activeOthers = workOrderRepository.countByEquipmentIdAndStatusAndIdNot(saved.getEquipmentId(), "IN_PROGRESS", saved.getId());
        if (activeOthers == 0) {
            equipmentRepository.findById(saved.getEquipmentId()).ifPresent(eq -> {
                eq.setStatus("AVAILABLE");
                equipmentRepository.save(eq);
            });
        }

        // If linked to preventive schedule, update lastCompletedDate and nextDueDate
        if (saved.getScheduleId() != null) {
            scheduleRepository.findById(saved.getScheduleId()).ifPresent(s -> {
                s.setLastCompletedDate(LocalDate.now());
                s.setNextDueDate(LocalDate.now().plusDays(s.getRepeatPeriodDays()));
                s.setUpdatedAt(LocalDateTime.now());
                scheduleRepository.save(s);
            });
        }

        recordServiceLog(saved.getId(), user.getId(), "STATUS_CHANGE", "IN_PROGRESS", "COMPLETED",
                "Work order completed. Resolution: " + resolutionNotes.trim() + (cost != null ? " | Cost: ₹" + cost : ""));

        enrichWorkOrder(saved);
        return saved;
    }

    @Transactional
    public WorkOrder cancelWorkOrder(Long workOrderId, String reason, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        if (!roles.contains("LAB_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers can cancel work orders.");
        }

        if (reason == null || reason.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is required.");
        }

        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));

        if (List.of("COMPLETED", "CANCELLED").contains(wo.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work order is already " + wo.getStatus());
        }

        String prevStatus = wo.getStatus();
        LocalDateTime cancelledAt = LocalDateTime.now();

        wo.setStatus("CANCELLED");
        wo.setCancelledAt(cancelledAt);
        wo.setCancellationReason(reason.trim());
        wo.setUpdatedAt(cancelledAt);
        WorkOrder saved = workOrderRepository.save(wo);

        // If was IN_PROGRESS, end downtime log and check equipment status
        if ("IN_PROGRESS".equals(prevStatus)) {
            downtimeLogRepository.findFirstByWorkOrderIdAndEndTimeIsNullOrderByStartTimeDesc(saved.getId())
                    .ifPresent(dLog -> {
                        dLog.setEndTime(cancelledAt);
                        dLog.setTotalDowntimeMinutes((int) Duration.between(dLog.getStartTime(), cancelledAt).toMinutes());
                        int opMinutes = downtimeAnalyticsService.calculateOperatingHoursDowntime(
                                saved.getEquipmentId(), dLog.getStartTime(), cancelledAt
                        );
                        dLog.setOperatingDowntimeMinutes(opMinutes);
                        downtimeLogRepository.save(dLog);
                    });

            long activeOthers = workOrderRepository.countByEquipmentIdAndStatusAndIdNot(saved.getEquipmentId(), "IN_PROGRESS", saved.getId());
            if (activeOthers == 0) {
                equipmentRepository.findById(saved.getEquipmentId()).ifPresent(eq -> {
                    eq.setStatus("AVAILABLE");
                    equipmentRepository.save(eq);
                });
            }
        }

        // If linked to preventive schedule and overdue, update nextDueDate
        if (saved.getScheduleId() != null) {
            scheduleRepository.findById(saved.getScheduleId()).ifPresent(s -> {
                if (s.getNextDueDate().isBefore(LocalDate.now())) {
                    s.setNextDueDate(LocalDate.now().plusDays(s.getRepeatPeriodDays()));
                    s.setUpdatedAt(LocalDateTime.now());
                    scheduleRepository.save(s);
                }
            });
        }

        recordServiceLog(saved.getId(), user.getId(), "STATUS_CHANGE", prevStatus, "CANCELLED",
                "Work order cancelled. Reason: " + reason.trim());

        enrichWorkOrder(saved);
        return saved;
    }

    private void cancelOverlappingBookings(Long equipmentId, LocalDateTime start, LocalDateTime end, Long workOrderId, String actorEmail) {
        List<Booking> overlapping = bookingRepository.findOverlappingBookingsForMaintenance(
                equipmentId,
                List.of("PENDING", "APPROVED", "CONFIRMED"),
                start,
                end
        );

        for (Booking b : overlapping) {
            String prevStatus = b.getStatus();
            b.setStatus("CANCELLED");
            bookingRepository.save(b);

            // Record audit log
            if (bookingAuditLogRepository != null) {
                BookingAuditLog log = new BookingAuditLog(
                        b.getId(),
                        "STATUS_CHANGED",
                        prevStatus,
                        "CANCELLED",
                        actorEmail + " [Reason: Equipment under maintenance]"
                );
                bookingAuditLogRepository.save(log);
            }

            // Publish domain events
            eventPublisher.publishEvent(new BookingReleasedEvent(b.getEquipmentId(), b.getStartTime(), b.getEndTime()));
            eventPublisher.publishEvent(new BookingMaintenanceCancelledEvent(
                    b.getId(), b.getEquipmentId(), b.getUserId(), b.getStartTime(), b.getEndTime(),
                    "Equipment under maintenance", workOrderId
            ));
        }
    }

    private void expireOverlappingWaitlistOffers(Long equipmentId, LocalDateTime start, LocalDateTime end) {
        if (waitlistRepository == null) return;
        List<WaitlistEntry> offered = waitlistRepository.findByEquipmentIdAndStatusOrderByCreatedAtAsc(equipmentId, "OFFERED");
        for (WaitlistEntry w : offered) {
            if (w.getRequestedStartTime().isBefore(end) && w.getRequestedEndTime().isAfter(start)) {
                w.setStatus("EXPIRED");
                w.setNotes(w.getNotes() != null ? w.getNotes() + " | Expired due to equipment maintenance" : "Expired due to equipment maintenance");
                waitlistRepository.save(w);
            }
        }
    }

    @Transactional
    public WorkOrderServiceLog recordServiceLog(Long workOrderId, Long userId, String logType, String fromStatus, String toStatus, String notes) {
        WorkOrderServiceLog log = new WorkOrderServiceLog();
        log.setWorkOrderId(workOrderId);
        log.setUserId(userId);
        log.setLogType(logType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setNotes(notes);
        log.setCreatedAt(LocalDateTime.now());
        return serviceLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderServiceLog> getServiceLogs(Long workOrderId, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));

        boolean isTech = roles.contains("LAB_TECHNICIAN");
        boolean isManager = roles.contains("LAB_MANAGER");
        boolean isAdmin = roles.contains("INSTITUTION_ADMIN");

        if (isTech && !isManager && !isAdmin && !Objects.equals(wo.getAssignedTo(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to service logs.");
        }

        List<WorkOrderServiceLog> logs = serviceLogRepository.findByWorkOrderIdOrderByCreatedAtAsc(workOrderId);
        for (WorkOrderServiceLog l : logs) {
            userRepository.findById(l.getUserId()).ifPresent(u -> {
                l.setUserName(u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail());
                l.setUserEmail(u.getEmail());
            });
        }
        return logs;
    }

    @Transactional
    public WorkOrderServiceLog addServiceLogNote(Long workOrderId, String notes, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));

        boolean isManager = roles.contains("LAB_MANAGER");
        boolean isAssignedTech = roles.contains("LAB_TECHNICIAN") && Objects.equals(wo.getAssignedTo(), user.getId());

        if (!isManager && !isAssignedTech) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Lab Manager or assigned Technician can add service notes.");
        }

        if (notes == null || notes.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service note content is required.");
        }

        WorkOrderServiceLog log = recordServiceLog(wo.getId(), user.getId(), "NOTE", wo.getStatus(), wo.getStatus(), notes.trim());
        log.setUserName(user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getEmail());
        log.setUserEmail(user.getEmail());
        return log;
    }

    @Transactional(readOnly = true)
    public Page<WorkOrder> getEquipmentMaintenanceHistory(Long equipmentId, Pageable pageable, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        Page<WorkOrder> page = workOrderRepository.findCompletedByEquipmentId(equipmentId, pageable);
        page.getContent().forEach(this::enrichWorkOrder);
        return page;
    }

    public void enrichWorkOrder(WorkOrder wo) {
        if (wo == null) return;
        if (wo.getEquipmentId() != null) {
            equipmentRepository.findById(wo.getEquipmentId()).ifPresent(eq -> {
                wo.setEquipmentName(eq.getName());
                wo.setAssetTag(eq.getAssetTag());
                wo.setInstitutionId(eq.getInstitutionId());
            });
        }
        if (wo.getAssignedTo() != null) {
            userRepository.findById(wo.getAssignedTo()).ifPresent(u -> {
                wo.setAssignedTechnicianName(u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail());
                wo.setAssignedTechnicianEmail(u.getEmail());
            });
        }
        if (wo.getCreatedBy() != null) {
            userRepository.findById(wo.getCreatedBy()).ifPresent(u -> {
                wo.setCreatorName(u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail());
            });
        }
    }
}
