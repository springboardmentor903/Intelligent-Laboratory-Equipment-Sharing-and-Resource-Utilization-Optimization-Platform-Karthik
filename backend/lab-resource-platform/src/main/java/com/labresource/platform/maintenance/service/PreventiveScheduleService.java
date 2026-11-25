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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PreventiveScheduleService {

    private final PreventiveMaintenanceScheduleRepository scheduleRepository;
    private final EquipmentRepository equipmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final ZoneId platformZoneId;

    public PreventiveScheduleService(
            PreventiveMaintenanceScheduleRepository scheduleRepository,
            EquipmentRepository equipmentRepository,
            WorkOrderRepository workOrderRepository,
            @Lazy WorkOrderService workOrderService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            @Value("${app.platform.timezone:Asia/Kolkata}") String platformTimezone) {
        this.scheduleRepository = scheduleRepository;
        this.equipmentRepository = equipmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.workOrderService = workOrderService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        ZoneId zone;
        try {
            zone = ZoneId.of(platformTimezone);
        } catch (Exception ex) {
            zone = ZoneId.of("Asia/Kolkata");
        }
        this.platformZoneId = zone;
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

    public String computeScheduleStatus(PreventiveMaintenanceSchedule schedule) {
        if (schedule == null) return "UNKNOWN";
        if (!Boolean.TRUE.equals(schedule.getActive())) {
            return "INACTIVE";
        }
        LocalDate today = LocalDate.now(platformZoneId);
        if (schedule.getNextDueDate() == null) {
            return "OK";
        }
        if (today.isAfter(schedule.getNextDueDate())) {
            return "OVERDUE";
        }
        if (!schedule.getNextDueDate().isAfter(today.plusDays(7))) {
            return "DUE_SOON";
        }
        return "OK";
    }

    @Transactional
    public PreventiveMaintenanceSchedule createSchedule(
            Long equipmentId,
            String title,
            String description,
            Integer repeatPeriodDays,
            LocalDate nextDueDate,
            Boolean active,
            String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        if (!roles.contains("LAB_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers can manage preventive schedules.");
        }

        if (equipmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment ID is required.");
        }
        if (title == null || title.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required.");
        }
        if (repeatPeriodDays == null || repeatPeriodDays < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repeat period must be at least 1 day.");
        }
        if (nextDueDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Next due date is required.");
        }

        Equipment eq = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        if (!Objects.equals(user.getInstitutionId(), eq.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Equipment must belong to your institution.");
        }

        PreventiveMaintenanceSchedule schedule = new PreventiveMaintenanceSchedule();
        schedule.setEquipmentId(eq.getId());
        schedule.setTitle(title.trim());
        schedule.setDescription(description != null ? description.trim() : null);
        schedule.setRepeatPeriodDays(repeatPeriodDays);
        schedule.setNextDueDate(nextDueDate);
        schedule.setActive(active != null ? active : true);
        schedule.setCreatedAt(LocalDateTime.now());
        schedule.setUpdatedAt(LocalDateTime.now());

        PreventiveMaintenanceSchedule saved = scheduleRepository.save(schedule);
        enrichSchedule(saved);
        return saved;
    }

    @Transactional
    public PreventiveMaintenanceSchedule updateSchedule(
            Long id,
            String title,
            String description,
            Integer repeatPeriodDays,
            LocalDate nextDueDate,
            Boolean active,
            String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        if (!roles.contains("LAB_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers can update preventive schedules.");
        }

        PreventiveMaintenanceSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        Equipment eq = equipmentRepository.findById(schedule.getEquipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        if (!Objects.equals(user.getInstitutionId(), eq.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Schedule does not belong to your institution.");
        }

        if (title != null && !title.trim().isBlank()) {
            schedule.setTitle(title.trim());
        }
        if (description != null) {
            schedule.setDescription(description.trim());
        }
        if (repeatPeriodDays != null && repeatPeriodDays >= 1) {
            schedule.setRepeatPeriodDays(repeatPeriodDays);
        }
        if (nextDueDate != null) {
            schedule.setNextDueDate(nextDueDate);
        }
        if (active != null) {
            schedule.setActive(active);
        }
        schedule.setUpdatedAt(LocalDateTime.now());

        PreventiveMaintenanceSchedule saved = scheduleRepository.save(schedule);
        enrichSchedule(saved);
        return saved;
    }

    @Transactional
    public PreventiveMaintenanceSchedule setScheduleActive(Long id, boolean active, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        if (!roles.contains("LAB_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers can activate/deactivate schedules.");
        }

        PreventiveMaintenanceSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        schedule.setActive(active);
        schedule.setUpdatedAt(LocalDateTime.now());
        PreventiveMaintenanceSchedule saved = scheduleRepository.save(schedule);
        enrichSchedule(saved);
        return saved;
    }

    @Transactional
    public void deleteSchedule(Long id, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        if (!roles.contains("LAB_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers can delete preventive schedules.");
        }

        PreventiveMaintenanceSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        scheduleRepository.delete(schedule);
    }

    @Transactional(readOnly = true)
    public Page<PreventiveMaintenanceSchedule> getSchedules(
            Long equipmentId,
            Boolean active,
            Pageable pageable,
            String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        boolean canAccess = roles.contains("LAB_MANAGER") || roles.contains("INSTITUTION_ADMIN");
        if (!canAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers and Institution Administrators can view schedules.");
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

        if (equipmentId != null) {
            if (!equipmentIds.contains(equipmentId)) {
                return Page.empty(pageable);
            }
            equipmentIds = List.of(equipmentId);
        }

        Page<PreventiveMaintenanceSchedule> page = scheduleRepository.findByEquipmentIds(equipmentIds, active, pageable);
        page.getContent().forEach(this::enrichSchedule);
        return page;
    }

    @Transactional(readOnly = true)
    public PreventiveMaintenanceSchedule getScheduleById(Long id, String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        PreventiveMaintenanceSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        enrichSchedule(schedule);
        return schedule;
    }

    /**
     * Daily @Scheduled job at 1:00 AM (0 0 1 * * *) in platform timezone (Asia/Kolkata).
     * Automatically creates PREVENTIVE work orders when nextDueDate is within the next 7 days,
     * preventing duplicates if an active work order already exists.
     */
    @Scheduled(cron = "${app.maintenance.schedule-cron:0 0 1 * * *}")
    @Transactional
    public void runDailyPreventiveMaintenanceJob() {
        LocalDate today = LocalDate.now(platformZoneId);
        LocalDate threshold = today.plusDays(7);
        log.info("Running daily preventive maintenance schedule check for due date <= {}", threshold);

        List<PreventiveMaintenanceSchedule> dueSchedules = scheduleRepository.findDueSchedules(threshold);
        int createdCount = 0;

        for (PreventiveMaintenanceSchedule schedule : dueSchedules) {
            boolean hasActive = workOrderRepository.existsByScheduleIdAndStatusIn(
                    schedule.getId(),
                    List.of("OPEN", "ASSIGNED", "IN_PROGRESS")
            );

            if (!hasActive) {
                try {
                    createPreventiveWorkOrder(schedule, today);
                    createdCount++;
                } catch (Exception e) {
                    log.error("Failed to generate preventive work order for schedule {}: {}", schedule.getId(), e.getMessage());
                }
            }
        }
        log.info("Daily preventive maintenance check finished. Generated {} work orders.", createdCount);
    }

    public WorkOrder createPreventiveWorkOrder(PreventiveMaintenanceSchedule schedule, LocalDate referenceDate) {
        WorkOrder wo = new WorkOrder();
        wo.setWorkOrderNumber(workOrderService.generateNextWorkOrderNumber());
        wo.setEquipmentId(schedule.getEquipmentId());
        wo.setScheduleId(schedule.getId());
        wo.setTitle(schedule.getTitle());
        wo.setDescription(schedule.getDescription() != null ? schedule.getDescription() : "Scheduled preventive maintenance");
        wo.setWorkOrderType("PREVENTIVE");

        boolean overdue = schedule.getNextDueDate() != null && schedule.getNextDueDate().isBefore(referenceDate);
        wo.setPriority(overdue ? "HIGH" : "MEDIUM");
        wo.setStatus("OPEN");
        wo.setScheduledDate(schedule.getNextDueDate() != null ? schedule.getNextDueDate().atTime(9, 0) : referenceDate.atTime(9, 0));
        wo.setCreatedAt(LocalDateTime.now());
        wo.setUpdatedAt(LocalDateTime.now());

        WorkOrder saved = workOrderRepository.save(wo);

        workOrderService.recordServiceLog(saved.getId(), 0L, "STATUS_CHANGE", null, "OPEN",
                "Work order automatically generated by preventive maintenance schedule #" + schedule.getId());

        return saved;
    }

    public void enrichSchedule(PreventiveMaintenanceSchedule schedule) {
        if (schedule == null) return;
        schedule.setStatus(computeScheduleStatus(schedule));
        if (schedule.getEquipmentId() != null) {
            equipmentRepository.findById(schedule.getEquipmentId()).ifPresent(eq -> {
                schedule.setEquipmentName(eq.getName());
                schedule.setAssetTag(eq.getAssetTag());
                schedule.setInstitutionId(eq.getInstitutionId());
            });
        }
    }
}
