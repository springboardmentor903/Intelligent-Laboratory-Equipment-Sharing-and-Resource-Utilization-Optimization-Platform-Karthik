package com.labresource.platform.maintenance.service;

import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.entity.EquipmentOperatingHours;
import com.labresource.platform.equipment.repository.EquipmentOperatingHoursRepository;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.maintenance.entity.EquipmentDowntimeLog;
import com.labresource.platform.maintenance.entity.WorkOrder;
import com.labresource.platform.maintenance.repository.EquipmentDowntimeLogRepository;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DowntimeAnalyticsService {

    private final EquipmentDowntimeLogRepository downtimeLogRepository;
    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentOperatingHoursRepository operatingHoursRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public DowntimeAnalyticsService(
            EquipmentDowntimeLogRepository downtimeLogRepository,
            WorkOrderRepository workOrderRepository,
            EquipmentRepository equipmentRepository,
            EquipmentOperatingHoursRepository operatingHoursRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository) {
        this.downtimeLogRepository = downtimeLogRepository;
        this.workOrderRepository = workOrderRepository;
        this.equipmentRepository = equipmentRepository;
        this.operatingHoursRepository = operatingHoursRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
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

    public record OperatingSchedule(boolean isOpen, LocalTime openTime, LocalTime closeTime) {}

    public OperatingSchedule resolveScheduleForEquipment(Long equipmentId, Long institutionId, int dayOfWeek) {
        List<EquipmentOperatingHours> customRows = Collections.emptyList();
        if (equipmentId != null) {
            customRows = operatingHoursRepository.findByEquipmentIdOrderByDayOfWeekAsc(equipmentId);
        }
        if (customRows.isEmpty() && institutionId != null) {
            customRows = operatingHoursRepository.findByInstitutionIdAndEquipmentIdIsNullOrderByDayOfWeekAsc(institutionId);
        }

        if (customRows.isEmpty()) {
            // Default Mon-Fri 09:00 - 17:00, Sat-Sun closed
            if (dayOfWeek >= 1 && dayOfWeek <= 5) {
                return new OperatingSchedule(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
            } else {
                return new OperatingSchedule(false, LocalTime.of(9, 0), LocalTime.of(17, 0));
            }
        }

        Optional<EquipmentOperatingHours> match = customRows.stream()
                .filter(r -> r.getDayOfWeek() == dayOfWeek)
                .findFirst();

        if (match.isEmpty() || Boolean.TRUE.equals(match.get().getIsClosed())) {
            return new OperatingSchedule(false, LocalTime.of(9, 0), LocalTime.of(17, 0));
        }

        EquipmentOperatingHours row = match.get();
        LocalTime open = row.getOpenTime() != null ? row.getOpenTime() : LocalTime.of(9, 0);
        LocalTime close = row.getCloseTime() != null ? row.getCloseTime() : LocalTime.of(17, 0);
        return new OperatingSchedule(true, open, close);
    }

    public int calculateOperatingHoursDowntime(Long equipmentId, LocalDateTime start, LocalDateTime end) {
        if (equipmentId == null || start == null || end == null || !end.isAfter(start)) {
            return 0;
        }

        Equipment eq = equipmentRepository.findById(equipmentId).orElse(null);
        Long instId = eq != null ? eq.getInstitutionId() : null;

        int totalOperatingMinutes = 0;
        LocalDate startDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            int dayOfWeek = d.getDayOfWeek().getValue();
            OperatingSchedule schedule = resolveScheduleForEquipment(equipmentId, instId, dayOfWeek);
            if (!schedule.isOpen()) continue;

            LocalDateTime windowOpen = d.atTime(schedule.openTime());
            LocalDateTime windowClose = d.atTime(schedule.closeTime());

            LocalDateTime effStart = start.isBefore(windowOpen) ? windowOpen : start;
            LocalDateTime effEnd = end.isAfter(windowClose) ? windowClose : end;

            if (effEnd.isAfter(effStart)) {
                totalOperatingMinutes += (int) Duration.between(effStart, effEnd).toMinutes();
            }
        }
        return totalOperatingMinutes;
    }

    public int calculateTotalAvailableOpenMinutes(Long equipmentId, Long institutionId, LocalDate fromDate, LocalDate toDate) {
        int totalOpenMinutes = 0;
        for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
            int dayOfWeek = d.getDayOfWeek().getValue();
            OperatingSchedule schedule = resolveScheduleForEquipment(equipmentId, institutionId, dayOfWeek);
            if (schedule.isOpen()) {
                totalOpenMinutes += (int) Duration.between(schedule.openTime(), schedule.closeTime()).toMinutes();
            }
        }
        return totalOpenMinutes;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDowntimeSummary(
            Long equipmentId,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String userEmail) {
        return getDowntimeSummary(equipmentId, null, null, startDateTime, endDateTime, userEmail);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDowntimeSummary(
            Long equipmentId,
            Long departmentId,
            Long institutionId,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String userEmail) {
        User user = getActor(userEmail);
        Set<String> roles = getUserRoles(user.getId());
        checkNotSystemAdmin(roles);

        boolean canAccess = roles.contains("LAB_MANAGER") || roles.contains("INSTITUTION_ADMIN");
        if (!canAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Lab Managers and Institution Administrators can view downtime analytics.");
        }

        Long userInstId = user.getInstitutionId();
        if (userInstId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Institution affiliation required");
        }

        LocalDateTime start = startDateTime != null ? startDateTime : LocalDateTime.now().minusDays(30);
        LocalDateTime end = endDateTime != null ? endDateTime : LocalDateTime.now();
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be after start date.");
        }

        List<Equipment> equipmentList;
        if (equipmentId != null) {
            Equipment eq = equipmentRepository.findById(equipmentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
            if (!Objects.equals(userInstId, eq.getInstitutionId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Equipment does not belong to your institution.");
            }
            equipmentList = List.of(eq);
        } else if (departmentId != null) {
            equipmentList = equipmentRepository.findByDepartmentId(departmentId).stream()
                    .filter(e -> Objects.equals(userInstId, e.getInstitutionId()))
                    .toList();
        } else {
            equipmentList = equipmentRepository.findByInstitutionId(userInstId);
        }

        List<Long> equipmentIds = equipmentList.stream().map(Equipment::getId).toList();
        if (equipmentIds.isEmpty()) {
            return Map.of(
                    "totalRawDowntimeMinutes", 0,
                    "totalRawDowntimeHours", 0.0,
                    "totalOperatingDowntimeMinutes", 0,
                    "totalOperatingDowntimeHours", 0.0,
                    "totalWorkOrders", 0,
                    "averageDowntimeMinutes", 0.0,
                    "averageDowntimeHours", 0.0,
                    "totalAvailableOpenHours", 0.0,
                    "percentageOpenHoursLost", 0.0,
                    "equipmentBreakdown", List.of()
            );
        }

        List<EquipmentDowntimeLog> logs = downtimeLogRepository.findOverlappingDowntimes(equipmentIds, start, end);
        List<WorkOrder> workOrders = workOrderRepository.findWorkOrdersInPeriod(equipmentIds, start, end);

        Map<Long, List<EquipmentDowntimeLog>> logsByEquipment = logs.stream()
                .collect(Collectors.groupingBy(EquipmentDowntimeLog::getEquipmentId));
        Map<Long, List<WorkOrder>> woByEquipment = workOrders.stream()
                .collect(Collectors.groupingBy(WorkOrder::getEquipmentId));

        int grandRawDowntimeMinutes = 0;
        int grandOperatingDowntimeMinutes = 0;
        int grandAvailableOpenMinutes = 0;
        int grandWorkOrdersCount = workOrders.size();

        List<Map<String, Object>> breakdown = new ArrayList<>();

        for (Equipment eq : equipmentList) {
            List<EquipmentDowntimeLog> eqLogs = logsByEquipment.getOrDefault(eq.getId(), Collections.emptyList());
            List<WorkOrder> eqWos = woByEquipment.getOrDefault(eq.getId(), Collections.emptyList());

            int eqRawMinutes = 0;
            int eqOperatingMinutes = 0;

            for (EquipmentDowntimeLog log : eqLogs) {
                LocalDateTime logStart = log.getStartTime().isBefore(start) ? start : log.getStartTime();
                LocalDateTime logEnd = log.getEndTime() != null
                        ? (log.getEndTime().isAfter(end) ? end : log.getEndTime())
                        : (LocalDateTime.now().isAfter(end) ? end : LocalDateTime.now());

                if (logEnd.isAfter(logStart)) {
                    int raw = (int) Duration.between(logStart, logEnd).toMinutes();
                    eqRawMinutes += raw;
                    eqOperatingMinutes += calculateOperatingHoursDowntime(eq.getId(), logStart, logEnd);
                }
            }

            int eqOpenMinutes = calculateTotalAvailableOpenMinutes(eq.getId(), eq.getInstitutionId(), start.toLocalDate(), end.toLocalDate());
            double eqOpenHours = Math.round((eqOpenMinutes / 60.0) * 10.0) / 10.0;
            double eqRawHours = Math.round((eqRawMinutes / 60.0) * 10.0) / 10.0;
            double eqOperatingHours = Math.round((eqOperatingMinutes / 60.0) * 10.0) / 10.0;
            double eqPercentLost = eqOpenMinutes > 0
                    ? Math.round((((double) eqOperatingMinutes / (double) eqOpenMinutes) * 100.0) * 10.0) / 10.0
                    : 0.0;

            grandRawDowntimeMinutes += eqRawMinutes;
            grandOperatingDowntimeMinutes += eqOperatingMinutes;
            grandAvailableOpenMinutes += eqOpenMinutes;

            Map<String, Object> item = new HashMap<>();
            item.put("equipmentId", eq.getId());
            item.put("equipmentName", eq.getName());
            item.put("assetTag", eq.getAssetTag());
            item.put("workOrderCount", eqWos.size());
            item.put("rawDowntimeMinutes", eqRawMinutes);
            item.put("rawDowntimeHours", eqRawHours);
            item.put("operatingDowntimeMinutes", eqOperatingMinutes);
            item.put("operatingDowntimeHours", eqOperatingHours);
            item.put("availableOpenHours", eqOpenHours);
            item.put("percentageOpenHoursLost", eqPercentLost);
            breakdown.add(item);
        }

        double totalRawHours = Math.round((grandRawDowntimeMinutes / 60.0) * 10.0) / 10.0;
        double totalOperatingHours = Math.round((grandOperatingDowntimeMinutes / 60.0) * 10.0) / 10.0;
        double totalOpenHours = Math.round((grandAvailableOpenMinutes / 60.0) * 10.0) / 10.0;
        double avgDowntimeMinutes = grandWorkOrdersCount > 0 ? (double) grandRawDowntimeMinutes / grandWorkOrdersCount : 0.0;
        double avgDowntimeHours = Math.round((avgDowntimeMinutes / 60.0) * 10.0) / 10.0;
        double grandPercentLost = grandAvailableOpenMinutes > 0
                ? Math.round((((double) grandOperatingDowntimeMinutes / (double) grandAvailableOpenMinutes) * 100.0) * 10.0) / 10.0
                : 0.0;

        Map<String, Object> response = new HashMap<>();
        response.put("totalRawDowntimeMinutes", grandRawDowntimeMinutes);
        response.put("totalRawDowntimeHours", totalRawHours);
        response.put("totalOperatingDowntimeMinutes", grandOperatingDowntimeMinutes);
        response.put("totalOperatingDowntimeHours", totalOperatingHours);
        response.put("totalWorkOrders", grandWorkOrdersCount);
        response.put("averageDowntimeMinutes", Math.round(avgDowntimeMinutes * 10.0) / 10.0);
        response.put("averageDowntimeHours", avgDowntimeHours);
        response.put("totalAvailableOpenHours", totalOpenHours);
        response.put("percentageOpenHoursLost", grandPercentLost);
        response.put("equipmentBreakdown", breakdown);

        return response;
    }
}
