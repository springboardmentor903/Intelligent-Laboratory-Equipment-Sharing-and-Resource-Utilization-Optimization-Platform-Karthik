package com.labresource.platform.dashboard.service;

import com.labresource.platform.analytics.entity.DailyUtilizationSnapshot;
import com.labresource.platform.analytics.repository.DailyUtilizationSnapshotRepository;
import com.labresource.platform.billing.entity.DepartmentBudget;
import com.labresource.platform.billing.repository.DepartmentBudgetRepository;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.booking.waitlist.entity.WaitlistEntry;
import com.labresource.platform.booking.waitlist.repository.WaitlistRepository;
import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.common.cache.ExpiringCache;
import com.labresource.platform.dashboard.dto.DashboardDTOs.*;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.maintenance.entity.EquipmentDowntimeLog;
import com.labresource.platform.maintenance.entity.PreventiveMaintenanceSchedule;
import com.labresource.platform.maintenance.entity.WorkOrder;
import com.labresource.platform.maintenance.repository.EquipmentDowntimeLogRepository;
import com.labresource.platform.maintenance.repository.PreventiveMaintenanceScheduleRepository;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import com.labresource.platform.notification.dto.NotificationDTO;
import com.labresource.platform.notification.model.DeliveryStatus;
import com.labresource.platform.notification.repository.NotificationDeliveryRepository;
import com.labresource.platform.notification.repository.NotificationRepository;
import com.labresource.platform.sharing.entity.SharingAgreement;
import com.labresource.platform.sharing.entity.SharingRequest;
import com.labresource.platform.sharing.repository.SharingAgreementRepository;
import com.labresource.platform.sharing.repository.SharingRequestRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final BookingRepository bookingRepository;
    private final WaitlistRepository waitlistRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final UsageChargeRepository usageChargeRepository;
    private final DepartmentBudgetRepository departmentBudgetRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final PreventiveMaintenanceScheduleRepository preventiveScheduleRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final EquipmentDowntimeLogRepository downtimeLogRepository;
    private final DailyUtilizationSnapshotRepository snapshotRepository;
    private final SharingRequestRepository sharingRequestRepository;
    private final SharingAgreementRepository sharingAgreementRepository;
    private final UserService userService;

    // 2-minute in-memory caches
    private final ExpiringCache<String, ManagerDashboardResponse> managerCache = new ExpiringCache<>(120_000);
    private final ExpiringCache<String, InstitutionDashboardResponse> institutionCache = new ExpiringCache<>(120_000);

    public DashboardService(
            UserRepository userRepository,
            EquipmentRepository equipmentRepository,
            BookingRepository bookingRepository,
            WaitlistRepository waitlistRepository,
            NotificationRepository notificationRepository,
            NotificationDeliveryRepository notificationDeliveryRepository,
            UsageChargeRepository usageChargeRepository,
            DepartmentBudgetRepository departmentBudgetRepository,
            DepartmentRepository departmentRepository,
            WorkOrderRepository workOrderRepository,
            PreventiveMaintenanceScheduleRepository preventiveScheduleRepository,
            CalibrationRecordRepository calibrationRecordRepository,
            EquipmentDowntimeLogRepository downtimeLogRepository,
            DailyUtilizationSnapshotRepository snapshotRepository,
            SharingRequestRepository sharingRequestRepository,
            SharingAgreementRepository sharingAgreementRepository,
            UserService userService) {
        this.userRepository = userRepository;
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
        this.waitlistRepository = waitlistRepository;
        this.notificationRepository = notificationRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.usageChargeRepository = usageChargeRepository;
        this.departmentBudgetRepository = departmentBudgetRepository;
        this.departmentRepository = departmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.preventiveScheduleRepository = preventiveScheduleRepository;
        this.calibrationRecordRepository = calibrationRecordRepository;
        this.downtimeLogRepository = downtimeLogRepository;
        this.snapshotRepository = snapshotRepository;
        this.sharingRequestRepository = sharingRequestRepository;
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.userService = userService;
    }

    // ==========================================
    // 1.1 RESEARCHER / STUDENT DASHBOARD
    // ==========================================
    public ResearcherDashboardResponse getResearcherDashboard(User currentUser) {
        Long userId = currentUser.getId();
        Long institutionId = currentUser.getInstitutionId();
        LocalDateTime now = LocalDateTime.now();

        // 1. Next 5 bookings with PENDING_APPROVAL, CONFIRMED, or IN_USE
        List<Booking> nextBookingsRaw = bookingRepository.findUpcomingUserBookings(
                userId,
                List.of("PENDING_APPROVAL", "CONFIRMED", "IN_USE"),
                now,
                PageRequest.of(0, 5)
        );
        Map<Long, Equipment> equipmentMap = fetchEquipmentMap(nextBookingsRaw.stream().map(Booking::getEquipmentId).toList());
        Map<Long, Department> departmentMap = fetchDepartmentMap(equipmentMap.values().stream().map(Equipment::getDepartmentId).filter(Objects::nonNull).toList());

        List<BookingSummaryDTO> nextBookings = nextBookingsRaw.stream().map(b -> {
            Equipment eq = equipmentMap.get(b.getEquipmentId());
            Department dept = eq != null && eq.getDepartmentId() != null ? departmentMap.get(eq.getDepartmentId()) : null;
            return new BookingSummaryDTO(
                    b.getId(),
                    b.getEquipmentId(),
                    eq != null ? eq.getName() : "Unknown Equipment",
                    eq != null ? eq.getAssetTag() : null,
                    dept != null ? dept.getName() : "General",
                    currentUser.getId(),
                    currentUser.getFullName(),
                    currentUser.getEmail(),
                    b.getStartTime(),
                    b.getEndTime(),
                    b.getStatus(),
                    b.getPurpose(),
                    b.getCreatedAt()
            );
        }).toList();

        // 2. Availability overview of bookable equipment in institution grouped by status
        List<Equipment> institutionEquipment = institutionId != null
                ? equipmentRepository.findByInstitutionId(institutionId)
                : Collections.emptyList();
        Map<String, Long> availabilityOverview = institutionEquipment.stream()
                .collect(Collectors.groupingBy(e -> e.getStatus() != null ? e.getStatus() : "UNKNOWN", Collectors.counting()));

        // 3. Usage summary for the last 90 days
        LocalDateTime ninetyDaysAgo = now.minusDays(90);
        List<Booking> userRecentBookings = bookingRepository.findUserBookingsSince(userId, ninetyDaysAgo);

        Map<String, Long> bookingsByStatus = userRecentBookings.stream()
                .collect(Collectors.groupingBy(b -> b.getStatus() != null ? b.getStatus() : "UNKNOWN", Collectors.counting()));

        double hoursUsed = userRecentBookings.stream()
                .filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus()))
                .mapToDouble(b -> {
                    long minutes = Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes());
                    return minutes / 60.0;
                })
                .sum();
        hoursUsed = Math.round(hoursUsed * 10.0) / 10.0;

        long noShowCount = userRecentBookings.stream()
                .filter(b -> "NO_SHOW".equalsIgnoreCase(b.getStatus()))
                .count();

        BigDecimal usageCost = usageChargeRepository.sumUserChargesSince(userId, ninetyDaysAgo);
        if (usageCost == null) usageCost = BigDecimal.ZERO;

        ResearcherUsageSummaryDTO usageSummary = new ResearcherUsageSummaryDTO(
                bookingsByStatus,
                hoursUsed,
                noShowCount,
                usageCost
        );

        // 4. Waitlist counts and active offers
        List<WaitlistEntry> userWaitlists = waitlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
        long waitingCount = userWaitlists.stream().filter(w -> "WAITING".equalsIgnoreCase(w.getStatus())).count();
        long offeredCount = userWaitlists.stream().filter(w -> "OFFERED".equalsIgnoreCase(w.getStatus())).count();

        List<WaitlistOfferDTO> activeOffers = userWaitlists.stream()
                .filter(w -> "OFFERED".equalsIgnoreCase(w.getStatus()) && w.getExpiresAt() != null && w.getExpiresAt().isAfter(now))
                .map(w -> {
                    Equipment eq = equipmentRepository.findById(w.getEquipmentId()).orElse(null);
                    return new WaitlistOfferDTO(
                            w.getId(),
                            w.getEquipmentId(),
                            eq != null ? eq.getName() : "Equipment #" + w.getEquipmentId(),
                            w.getRequestedStartTime(),
                            w.getRequestedEndTime(),
                            w.getExpiresAt(),
                            w.getStatus()
                    );
                })
                .toList();

        ResearcherWaitlistDTO waitlist = new ResearcherWaitlistDTO(waitingCount, offeredCount, activeOffers);

        // 5. Unread notification count and latest 3 notifications
        long unreadNotifs = notificationRepository.countByRecipientIdAndReadFalse(userId);
        List<NotificationDTO> latestNotifs = notificationRepository
                .findByRecipientId(userId, PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(NotificationDTO::fromEntity)
                .toList();

        // 6. 5 Rule-based equipment recommendations
        LocalDateTime oneEightyDaysAgo = now.minusDays(180);
        List<Booking> historyBookings = bookingRepository.findUserBookingsSince(userId, oneEightyDaysAgo);

        List<EquipmentRecommendationDTO> recommendations = new ArrayList<>();
        if (!historyBookings.isEmpty()) {
            Set<Long> bookedEquipmentIds = historyBookings.stream().map(Booking::getEquipmentId).collect(Collectors.toSet());
            Map<Long, Equipment> historyEquipment = fetchEquipmentMap(new ArrayList<>(bookedEquipmentIds));

            // Rank categories by booking count
            Map<String, Long> categoryCounts = historyBookings.stream()
                    .map(b -> {
                        Equipment eq = historyEquipment.get(b.getEquipmentId());
                        return eq != null ? eq.getCategory() : null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(c -> c, Collectors.counting()));

            List<String> rankedCategories = categoryCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .toList();

            for (String category : rankedCategories) {
                if (recommendations.size() >= 5) break;
                List<Equipment> categoryCandidates = institutionEquipment.stream()
                        .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                        .filter(e -> !bookedEquipmentIds.contains(e.getId()))
                        .filter(e -> "AVAILABLE".equalsIgnoreCase(e.getStatus()))
                        .toList();

                for (Equipment candidate : categoryCandidates) {
                    if (recommendations.size() >= 5) break;
                    recommendations.add(new EquipmentRecommendationDTO(
                            candidate.getId(),
                            candidate.getName(),
                            candidate.getAssetTag(),
                            candidate.getCategory(),
                            candidate.getHourlyRate(),
                            candidate.getStatus(),
                            "Popular in your top category: " + candidate.getCategory()
                    ));
                }
            }
        }

        // If no bookings or recommendations < 5, recommend institution's available equipment
        if (recommendations.size() < 5) {
            Set<Long> existingRecIds = recommendations.stream().map(EquipmentRecommendationDTO::equipmentId).collect(Collectors.toSet());
            List<Equipment> generalCandidates = institutionEquipment.stream()
                    .filter(e -> !existingRecIds.contains(e.getId()))
                    .filter(e -> "AVAILABLE".equalsIgnoreCase(e.getStatus()))
                    .limit(5 - recommendations.size())
                    .toList();

            for (Equipment c : generalCandidates) {
                recommendations.add(new EquipmentRecommendationDTO(
                        c.getId(),
                        c.getName(),
                        c.getAssetTag(),
                        c.getCategory(),
                        c.getHourlyRate(),
                        c.getStatus(),
                        "Institution recommended resource"
                ));
            }
        }

        return new ResearcherDashboardResponse(
                nextBookings,
                availabilityOverview,
                usageSummary,
                waitlist,
                unreadNotifs,
                latestNotifs,
                recommendations
        );
    }

    // ==========================================
    // 1.2 LAB MANAGER / DEPARTMENT HEAD DASHBOARD
    // ==========================================
    public ManagerDashboardResponse getManagerDashboard(User currentUser, Integer daysParam, Long departmentFilter) {
        int days = daysParam != null && daysParam > 0 ? daysParam : 30;
        Long institutionId = currentUser.getInstitutionId();
        List<String> roles = userService.getRoleNames(currentUser.getId());
        boolean isDeptHead = roles.contains("DEPARTMENT_HEAD") && !roles.contains("LAB_MANAGER") && !roles.contains("INSTITUTION_ADMIN") && !roles.contains("SYSTEM_ADMIN");

        Long effectiveDeptId = isDeptHead ? currentUser.getDepartmentId() : departmentFilter;
        String scope = effectiveDeptId != null ? "DEPARTMENT" : "INSTITUTION";

        String cacheKey = "manager:" + institutionId + ":" + effectiveDeptId + ":" + days + ":" + scope;
        ManagerDashboardResponse cached = managerCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = now.minusDays(days);

        // Fetch equipment in scope
        List<Equipment> equipmentInScope = (effectiveDeptId != null)
                ? equipmentRepository.findByDepartmentId(effectiveDeptId)
                : (institutionId != null ? equipmentRepository.findByInstitutionId(institutionId) : Collections.emptyList());

        List<Long> equipmentIds = equipmentInScope.stream().map(Equipment::getId).toList();

        // 1. Utilization against target
        List<Booking> periodBookings = equipmentIds.isEmpty() ? Collections.emptyList()
                : bookingRepository.findByEquipmentIdInAndPeriod(equipmentIds, periodStart, now);

        double totalUsedHours = periodBookings.stream()
                .filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus()) || "IN_USE".equalsIgnoreCase(b.getStatus()))
                .mapToDouble(b -> Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes()) / 60.0)
                .sum();

        // 40 operating hours per week per equipment
        double expectedOperatingHours = equipmentInScope.size() * (days / 7.0) * 40.0;
        double averageUtilization = expectedOperatingHours > 0
                ? Math.min(100.0, Math.round((totalUsedHours / expectedOperatingHours) * 1000.0) / 10.0)
                : 0.0;
        double targetUtilization = 70.0;
        double gap = Math.round((averageUtilization - targetUtilization) * 10.0) / 10.0;
        UtilizationTargetDTO utilizationAgainstTarget = new UtilizationTargetDTO(averageUtilization, targetUtilization, gap);

        // 2. Heatmap data: hourly slots (0-23)
        Map<Integer, Long> hourFrequency = periodBookings.stream()
                .collect(Collectors.groupingBy(b -> b.getStartTime().getHour(), Collectors.counting()));

        List<Map<String, Object>> heatmap = new ArrayList<>();
        for (int h = 8; h <= 20; h++) {
            long count = hourFrequency.getOrDefault(h, 0L);
            heatmap.add(Map.of(
                    "hour", h,
                    "label", String.format("%02d:00", h),
                    "bookingCount", count,
                    "intensity", count >= 10 ? "HIGH" : count >= 4 ? "MEDIUM" : "LOW"
            ));
        }

        // 3. Booking adoption percentage: (equipment with >= 1 booking / total equipment) * 100
        Set<Long> bookedEquipmentSet = periodBookings.stream().map(Booking::getEquipmentId).collect(Collectors.toSet());
        double adoptionPercentage = equipmentInScope.isEmpty() ? 0.0
                : Math.round(((double) bookedEquipmentSet.size() / equipmentInScope.size()) * 1000.0) / 10.0;

        // 4. No-show rate
        long totalBookings = periodBookings.size();
        long noShows = periodBookings.stream().filter(b -> "NO_SHOW".equalsIgnoreCase(b.getStatus())).count();
        double noShowRate = totalBookings > 0
                ? Math.round(((double) noShows / totalBookings) * 1000.0) / 10.0
                : 0.0;

        // 5. High demand equipment list
        Map<Long, Long> bookingCountByEq = periodBookings.stream()
                .collect(Collectors.groupingBy(Booking::getEquipmentId, Collectors.counting()));

        List<HighDemandEquipmentDTO> highDemandList = equipmentInScope.stream()
                .filter(e -> bookingCountByEq.getOrDefault(e.getId(), 0L) >= 2)
                .map(e -> {
                    long bCount = bookingCountByEq.getOrDefault(e.getId(), 0L);
                    long wCount = waitlistRepository.countByEquipmentIdAndStatus(e.getId(), "WAITING");
                    double uRate = expectedOperatingHours > 0 ? (bCount * 2.0 / (days * 6.0)) * 100.0 : 0.0;
                    return new HighDemandEquipmentDTO(
                            e.getId(),
                            e.getName(),
                            e.getCategory(),
                            bCount,
                            Math.min(100.0, Math.round(uRate * 10.0) / 10.0),
                            wCount
                    );
                })
                .sorted(Comparator.comparingLong(HighDemandEquipmentDTO::bookingCount).reversed())
                .limit(5)
                .toList();

        // 6. Open work orders by status
        List<WorkOrder> openWorkOrders = equipmentIds.isEmpty() ? Collections.emptyList()
                : workOrderRepository.findByEquipmentIdInAndStatusNotIn(equipmentIds, List.of("COMPLETED", "CANCELLED"));
        Map<String, Long> openWorkOrdersByStatus = openWorkOrders.stream()
                .collect(Collectors.groupingBy(WorkOrder::getStatus, Collectors.counting()));

        // 7. Equipment currently under maintenance
        List<MaintenanceEquipmentDTO> underMaintenance = equipmentInScope.stream()
                .filter(e -> "UNDER_MAINTENANCE".equalsIgnoreCase(e.getStatus()))
                .map(e -> new MaintenanceEquipmentDTO(
                        e.getId(),
                        e.getName(),
                        e.getAssetTag(),
                        e.getStatus(),
                        null,
                        e.getCreatedAt()
                ))
                .toList();

        // 8. Preventive schedules due soon or overdue
        List<PreventiveMaintenanceSchedule> schedules = equipmentIds.isEmpty() ? Collections.emptyList()
                : preventiveScheduleRepository.findByEquipmentIdIn(equipmentIds);

        LocalDate sevenDaysOut = LocalDate.now().plusDays(7);
        List<PreventiveScheduleAlertDTO> preventiveAlerts = schedules.stream()
                .filter(s -> Boolean.TRUE.equals(s.getActive()) && s.getNextDueDate() != null && !s.getNextDueDate().isAfter(sevenDaysOut))
                .map(s -> {
                    Equipment eq = equipmentRepository.findById(s.getEquipmentId()).orElse(null);
                    boolean overdue = s.getNextDueDate().isBefore(LocalDate.now());
                    long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), s.getNextDueDate());
                    return new PreventiveScheduleAlertDTO(
                            s.getId(),
                            s.getEquipmentId(),
                            eq != null ? eq.getName() : "Equipment #" + s.getEquipmentId(),
                            s.getRepeatPeriodDays() != null ? s.getRepeatPeriodDays() + " days" : "Periodic",
                            s.getNextDueDate(),
                            overdue,
                            daysUntil
                    );
                })
                .sorted(Comparator.comparing(PreventiveScheduleAlertDTO::nextDueDate))
                .toList();

        // 9. Calibration overview counts (VALID, DUE_SOON, EXPIRED, NONE)
        Map<String, Long> calibrationCounts = new HashMap<>();
        calibrationCounts.put("VALID", 0L);
        calibrationCounts.put("DUE_SOON", 0L);
        calibrationCounts.put("EXPIRED", 0L);
        calibrationCounts.put("NONE", 0L);

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysOut = today.plusDays(30);

        for (Equipment e : equipmentInScope) {
            Optional<CalibrationRecord> calOpt = calibrationRecordRepository.findFirstByEquipment_IdOrderByCalibrationDateDesc(e.getId());
            if (calOpt.isEmpty()) {
                calibrationCounts.put("NONE", calibrationCounts.get("NONE") + 1);
            } else {
                LocalDate dueDate = calOpt.get().getNextDueDate();
                if (dueDate == null || dueDate.isBefore(today)) {
                    calibrationCounts.put("EXPIRED", calibrationCounts.get("EXPIRED") + 1);
                } else if (!dueDate.isAfter(thirtyDaysOut)) {
                    calibrationCounts.put("DUE_SOON", calibrationCounts.get("DUE_SOON") + 1);
                } else {
                    calibrationCounts.put("VALID", calibrationCounts.get("VALID") + 1);
                }
            }
        }

        // 10. Role-specific approvals
        List<BookingSummaryDTO> pendingApprovals = Collections.emptyList();
        if (!isDeptHead) {
            List<Booking> pendingList = equipmentIds.isEmpty() ? Collections.emptyList()
                    : bookingRepository.findByEquipmentIdInAndStatus(equipmentIds, "PENDING_APPROVAL");
            Map<Long, Equipment> eqMap = fetchEquipmentMap(pendingList.stream().map(Booking::getEquipmentId).toList());
            pendingApprovals = pendingList.stream().map(b -> {
                Equipment eq = eqMap.get(b.getEquipmentId());
                return new BookingSummaryDTO(
                        b.getId(),
                        b.getEquipmentId(),
                        eq != null ? eq.getName() : null,
                        eq != null ? eq.getAssetTag() : null,
                        null,
                        b.getUserId(),
                        null,
                        null,
                        b.getStartTime(),
                        b.getEndTime(),
                        b.getStatus(),
                        b.getPurpose(),
                        b.getCreatedAt()
                );
            }).toList();
        }

        List<Map<String, Object>> deptSharingRequests = Collections.emptyList();
        if (isDeptHead && effectiveDeptId != null) {
            deptSharingRequests = sharingRequestRepository.findAll().stream()
                    .filter(r -> equipmentIds.contains(r.getEquipmentId()))
                    .map(r -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", r.getId());
                        m.put("equipmentId", r.getEquipmentId());
                        m.put("status", r.getStatus());
                        m.put("startDate", r.getStartDate());
                        m.put("endDate", r.getEndDate());
                        m.put("purpose", r.getPurpose());
                        return m;
                    })
                    .limit(10)
                    .toList();
        }

        ManagerDashboardResponse response = new ManagerDashboardResponse(
                days,
                scope,
                effectiveDeptId,
                utilizationAgainstTarget,
                heatmap,
                adoptionPercentage,
                noShowRate,
                highDemandList,
                openWorkOrdersByStatus,
                underMaintenance,
                preventiveAlerts,
                calibrationCounts,
                pendingApprovals,
                deptSharingRequests
        );

        managerCache.put(cacheKey, response);
        return response;
    }

    // ==========================================
    // 1.3 INSTITUTION ADMINISTRATOR DASHBOARD
    // ==========================================
    public InstitutionDashboardResponse getInstitutionDashboard(Long institutionId, Integer daysParam) {
        int days = daysParam != null && daysParam > 0 ? daysParam : 30;
        String cacheKey = "institution:" + institutionId + ":" + days;
        InstitutionDashboardResponse cached = institutionCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = now.minusDays(days);

        // 1. Department utilization against targets and gaps
        List<Department> departments = departmentRepository.findByInstitutionId(institutionId);
        List<DepartmentUtilizationItemDTO> deptUtilList = new ArrayList<>();

        for (Department d : departments) {
            List<Equipment> deptEquipment = equipmentRepository.findByDepartmentId(d.getId());
            List<Long> eqIds = deptEquipment.stream().map(Equipment::getId).toList();
            double usedHours = 0.0;
            if (!eqIds.isEmpty()) {
                List<Booking> bookings = bookingRepository.findByEquipmentIdInAndPeriod(eqIds, periodStart, now);
                usedHours = bookings.stream()
                        .filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus()) || "IN_USE".equalsIgnoreCase(b.getStatus()))
                        .mapToDouble(b -> Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes()) / 60.0)
                        .sum();
            }
            double expectedHours = deptEquipment.size() * (days / 7.0) * 40.0;
            double util = expectedHours > 0 ? Math.min(100.0, Math.round((usedHours / expectedHours) * 1000.0) / 10.0) : 0.0;
            double target = 70.0;
            deptUtilList.add(new DepartmentUtilizationItemDTO(
                    d.getId(),
                    d.getName(),
                    util,
                    target,
                    Math.round((util - target) * 10.0) / 10.0
            ));
        }

        // 2. Monthly utilization trends from snapshots
        LocalDate twelveMonthsAgo = LocalDate.now().minusMonths(12).withDayOfMonth(1);
        List<DailyUtilizationSnapshot> snapshots = snapshotRepository.findByInstitutionIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                institutionId, twelveMonthsAgo, LocalDate.now()
        );

        Map<String, List<DailyUtilizationSnapshot>> byMonth = snapshots.stream()
                .collect(Collectors.groupingBy(s -> s.getSnapshotDate().getYear() + "-" + String.format("%02d", s.getSnapshotDate().getMonthValue())));

        List<MonthlyTrendDTO> monthlyTrends = byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    String[] parts = e.getKey().split("-");
                    int year = Integer.parseInt(parts[0]);
                    int monthVal = Integer.parseInt(parts[1]);
                    List<DailyUtilizationSnapshot> list = e.getValue();
                    double avgUtil = list.stream().mapToDouble(s -> s.getUtilizationRate() != null ? s.getUtilizationRate() : 0.0).average().orElse(0.0);
                    int sessions = list.stream().mapToInt(DailyUtilizationSnapshot::getSessionCount).sum();
                    double opHours = list.stream().mapToInt(DailyUtilizationSnapshot::getOperatingMinutes).sum() / 60.0;
                    double usedHrs = list.stream().mapToInt(DailyUtilizationSnapshot::getUsedMinutes).sum() / 60.0;
                    return new MonthlyTrendDTO(
                            java.time.Month.of(monthVal).name(),
                            year,
                            Math.round(avgUtil * 10.0) / 10.0,
                            sessions,
                            Math.round(opHours * 10.0) / 10.0,
                            Math.round(usedHrs * 10.0) / 10.0
                    );
                })
                .toList();

        // 3. Idle equipment alerts (no bookings/usage in last 14+ days)
        List<Equipment> allInstEquipment = equipmentRepository.findByInstitutionId(institutionId);
        List<Long> allEqIds = allInstEquipment.stream().map(Equipment::getId).toList();

        LocalDateTime fourteenDaysAgo = now.minusDays(14);
        List<Booking> recentBookings = allEqIds.isEmpty() ? Collections.emptyList()
                : bookingRepository.findByEquipmentIdInAndCreatedAtAfter(allEqIds, fourteenDaysAgo);
        Set<Long> activeEqIds = recentBookings.stream().map(Booking::getEquipmentId).collect(Collectors.toSet());

        Map<Long, Department> deptMap = departments.stream().collect(Collectors.toMap(Department::getId, d -> d, (a, b) -> a));
        List<IdleEquipmentItemDTO> idleAlerts = allInstEquipment.stream()
                .filter(e -> !activeEqIds.contains(e.getId()))
                .filter(e -> "AVAILABLE".equalsIgnoreCase(e.getStatus()))
                .map(e -> {
                    Department d = e.getDepartmentId() != null ? deptMap.get(e.getDepartmentId()) : null;
                    return new IdleEquipmentItemDTO(
                            e.getId(),
                            e.getName(),
                            e.getAssetTag(),
                            d != null ? d.getName() : "General",
                            14L,
                            null
                    );
                })
                .limit(10)
                .toList();

        // 4. Inter-institution sharing analytics
        List<SharingAgreement> agreements = sharingAgreementRepository.findByOwnerInstitutionId(institutionId);
        long approvedAgreements = agreements.stream().filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus())).count();
        double sharedBookingHours = 0.0;
        BigDecimal accruedSharingRevenue = BigDecimal.ZERO;

        for (SharingAgreement a : agreements) {
            List<Booking> sharedBookings = bookingRepository.findBySharingAgreementId(a.getId());
            for (Booking b : sharedBookings) {
                if ("COMPLETED".equalsIgnoreCase(b.getStatus())) {
                    double hrs = Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes()) / 60.0;
                    sharedBookingHours += hrs;
                    if (a.getFeePerHour() != null) {
                        accruedSharingRevenue = accruedSharingRevenue.add(a.getFeePerHour().multiply(BigDecimal.valueOf(hrs)));
                    }
                }
            }
        }
        SharingAnalyticsSummaryDTO sharingAnalytics = new SharingAnalyticsSummaryDTO(
                agreements.size(),
                approvedAgreements,
                Math.round(sharedBookingHours * 10.0) / 10.0,
                accruedSharingRevenue.setScale(2, RoundingMode.HALF_UP)
        );

        // 5. Cross-department sharing hours and top 5 department pairs
        List<Booking> allInstBookings = allEqIds.isEmpty() ? Collections.emptyList()
                : bookingRepository.findByEquipmentIdInAndPeriod(allEqIds, periodStart, now);

        Map<Long, Equipment> eqLookup = allInstEquipment.stream().collect(Collectors.toMap(Equipment::getId, e -> e, (a, b) -> a));
        Map<Long, User> userLookup = fetchUserMap(allInstBookings.stream().map(Booking::getUserId).toList());

        double totalCrossDeptHours = 0.0;
        Map<String, DepartmentPairDTO> pairStats = new HashMap<>();

        for (Booking b : allInstBookings) {
            Equipment eq = eqLookup.get(b.getEquipmentId());
            User u = userLookup.get(b.getUserId());
            if (eq != null && u != null && eq.getDepartmentId() != null && u.getDepartmentId() != null) {
                if (!Objects.equals(eq.getDepartmentId(), u.getDepartmentId())) {
                    double hrs = Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes()) / 60.0;
                    totalCrossDeptHours += hrs;

                    String eqDept = deptMap.containsKey(eq.getDepartmentId()) ? deptMap.get(eq.getDepartmentId()).getName() : "Dept " + eq.getDepartmentId();
                    String uDept = deptMap.containsKey(u.getDepartmentId()) ? deptMap.get(u.getDepartmentId()).getName() : "Dept " + u.getDepartmentId();
                    String pairKey = eqDept + " -> " + uDept;

                    DepartmentPairDTO existing = pairStats.get(pairKey);
                    if (existing == null) {
                        pairStats.put(pairKey, new DepartmentPairDTO(eqDept, uDept, hrs, 1L));
                    } else {
                        pairStats.put(pairKey, new DepartmentPairDTO(eqDept, uDept, existing.hoursUsed() + hrs, existing.bookingCount() + 1));
                    }
                }
            }
        }

        List<DepartmentPairDTO> topPairs = pairStats.values().stream()
                .sorted(Comparator.comparingDouble(DepartmentPairDTO::hoursUsed).reversed())
                .limit(5)
                .map(p -> new DepartmentPairDTO(p.equipmentDepartmentName(), p.userDepartmentName(), Math.round(p.hoursUsed() * 10.0) / 10.0, p.bookingCount()))
                .toList();

        CrossDepartmentSharingDTO crossDeptSharing = new CrossDepartmentSharingDTO(
                Math.round(totalCrossDeptHours * 10.0) / 10.0,
                topPairs
        );

        // 6. Cost & budget summary
        BigDecimal totalUsageCost = usageChargeRepository.sumTotalChargesByInstitution(institutionId);
        if (totalUsageCost == null) totalUsageCost = BigDecimal.ZERO;

        int fiscalYear = LocalDate.now().getYear();
        List<DepartmentBudget> budgets = departmentBudgetRepository.findByInstitutionIdAndFiscalYear(institutionId, fiscalYear);

        LocalDateTime fiscalStart = LocalDate.of(fiscalYear, 1, 1).atStartOfDay();
        LocalDateTime fiscalEnd = LocalDate.of(fiscalYear, 12, 31).atTime(LocalTime.MAX);
        BigDecimal totalAllocated = budgets.stream().map(DepartmentBudget::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = BigDecimal.ZERO;
        for (DepartmentBudget budget : budgets) {
            BigDecimal spent = usageChargeRepository.sumDepartmentSpend(budget.getDepartmentId(), fiscalStart, fiscalEnd);
            if (spent != null) {
                totalSpent = totalSpent.add(spent);
            }
        }
        double budgetUtil = totalAllocated.compareTo(BigDecimal.ZERO) > 0
                ? totalSpent.divide(totalAllocated, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        CostAndBudgetSummaryDTO costSummary = new CostAndBudgetSummaryDTO(
                totalUsageCost,
                totalAllocated,
                totalSpent,
                Math.round(budgetUtil * 10.0) / 10.0
        );

        InstitutionDashboardResponse response = new InstitutionDashboardResponse(
                institutionId,
                deptUtilList,
                monthlyTrends,
                idleAlerts,
                sharingAnalytics,
                crossDeptSharing,
                costSummary
        );

        institutionCache.put(cacheKey, response);
        return response;
    }

    // ==========================================
    // 1.4 PROCUREMENT RECOMMENDATIONS
    // ==========================================
    public List<ProcurementRecommendationDTO> getProcurementRecommendations(Long institutionId) {
        List<ProcurementRecommendationDTO> results = new ArrayList<>();
        List<Equipment> equipmentList = equipmentRepository.findByInstitutionId(institutionId);
        List<Long> eqIds = equipmentList.stream().map(Equipment::getId).toList();
        if (eqIds.isEmpty()) return results;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime ninetyDaysAgo = now.minusDays(90);
        LocalDateTime twelveMonthsAgo = now.minusDays(365);

        // 1. HIGH_DEMAND: category average booking rate >= 80% and >= 3 waitlist entries in last 90 days
        Map<String, List<Equipment>> byCategory = equipmentList.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(Equipment::getCategory));

        for (Map.Entry<String, List<Equipment>> entry : byCategory.entrySet()) {
            String cat = entry.getKey();
            List<Equipment> catEqs = entry.getValue();
            List<Long> catIds = catEqs.stream().map(Equipment::getId).toList();

            List<Booking> catBookings = bookingRepository.findByEquipmentIdInAndPeriod(catIds, ninetyDaysAgo, now);
            long waitlistCount = catIds.stream().mapToLong(id -> waitlistRepository.countByEquipmentIdAndStatus(id, "WAITING")).sum();

            double catUsedHours = catBookings.stream()
                    .filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus()) || "IN_USE".equalsIgnoreCase(b.getStatus()))
                    .mapToDouble(b -> Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes()) / 60.0)
                    .sum();
            double catExpectedHours = catEqs.size() * (90 / 7.0) * 40.0;
            double bookingRate = catExpectedHours > 0 ? (catUsedHours / catExpectedHours) * 100.0 : 0.0;

            if (bookingRate >= 80.0 || waitlistCount >= 3) {
                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("bookingRate", Math.round(bookingRate * 10.0) / 10.0);
                metrics.put("waitlistEntries", waitlistCount);
                metrics.put("unitsInService", catEqs.size());

                results.add(new ProcurementRecommendationDTO(
                        "HIGH_DEMAND",
                        "CATEGORY",
                        cat,
                        null,
                        cat + " Category Fleet",
                        null,
                        metrics,
                        "Category shows sustained high demand (" + Math.round(bookingRate) + "% rate, " + waitlistCount + " waitlists). Procure additional units to relieve bottlenecks."
                ));
            }
        }

        // 2. LOW_UTILIZATION & HIGH_MAINTENANCE per equipment
        for (Equipment eq : equipmentList) {
            // Utilization in last 90 days
            List<Booking> eqBookings = bookingRepository.findByEquipmentIdInAndPeriod(List.of(eq.getId()), ninetyDaysAgo, now);
            double eqUsedHours = eqBookings.stream()
                    .filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus()) || "IN_USE".equalsIgnoreCase(b.getStatus()))
                    .mapToDouble(b -> Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes()) / 60.0)
                    .sum();
            double eqExpectedHours = (90 / 7.0) * 40.0;
            double util = eqExpectedHours > 0 ? (eqUsedHours / eqExpectedHours) * 100.0 : 0.0;

            if (util < 20.0 && eqBookings.size() <= 2) {
                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("utilizationRate", Math.round(util * 10.0) / 10.0);
                metrics.put("bookingsCount", eqBookings.size());
                metrics.put("purchaseCost", eq.getPurchaseCost());

                results.add(new ProcurementRecommendationDTO(
                        "LOW_UTILIZATION",
                        "EQUIPMENT",
                        eq.getCategory(),
                        eq.getId(),
                        eq.getName(),
                        eq.getAssetTag(),
                        metrics,
                        "Equipment has under 20% utilization (" + Math.round(util * 10.0) / 10.0 + "%). Consider repurposing, sharing externally, or deferring replacement."
                ));
            }

            // Maintenance cost in last 12 months >= 30% of purchase cost
            if (eq.getPurchaseCost() != null && eq.getPurchaseCost().compareTo(BigDecimal.ZERO) > 0) {
                List<Object[]> costObjs = workOrderRepository.sumCompletedCostByEquipmentIdsAndPeriod(List.of(eq.getId()), twelveMonthsAgo, now);
                BigDecimal maintCost = BigDecimal.ZERO;
                if (!costObjs.isEmpty() && costObjs.get(0).length > 1 && costObjs.get(0)[1] != null) {
                    maintCost = (BigDecimal) costObjs.get(0)[1];
                }

                BigDecimal ratio = maintCost.divide(eq.getPurchaseCost(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                if (ratio.compareTo(BigDecimal.valueOf(30.0)) >= 0) {
                    Map<String, Object> metrics = new LinkedHashMap<>();
                    metrics.put("maintenanceCostLast12m", maintCost);
                    metrics.put("purchaseCost", eq.getPurchaseCost());
                    metrics.put("maintenanceCostRatio", ratio.setScale(1, RoundingMode.HALF_UP));

                    results.add(new ProcurementRecommendationDTO(
                            "HIGH_MAINTENANCE",
                            "EQUIPMENT",
                            eq.getCategory(),
                            eq.getId(),
                            eq.getName(),
                            eq.getAssetTag(),
                            metrics,
                            "12-month maintenance cost (₹" + maintCost + ") has reached " + ratio.setScale(1, RoundingMode.HALF_UP) + "% of original purchase cost. Replacement is economically recommended."
                    ));
                }
            }
        }

        return results;
    }

    // ==========================================
    // 1.5 EQUIPMENT LIFECYCLE AND ROI
    // ==========================================
    public Page<EquipmentLifecycleDTO> getEquipmentLifecycleAndRoi(Long institutionId, Pageable pageable) {
        Page<Equipment> equipmentPage = equipmentRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("institutionId"), institutionId),
                pageable
        );

        List<Long> eqIds = equipmentPage.getContent().stream().map(Equipment::getId).toList();
        if (eqIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        Map<Long, BigDecimal> maintCostMap = new HashMap<>();
        List<Object[]> maintCosts = workOrderRepository.sumCompletedCostByEquipmentIdsAndPeriod(eqIds, null, null);
        for (Object[] row : maintCosts) {
            if (row.length > 1 && row[0] != null && row[1] != null) {
                maintCostMap.put((Long) row[0], (BigDecimal) row[1]);
            }
        }

        Map<Long, BigDecimal> usageChargeMap = new HashMap<>();
        List<Object[]> chargeCosts = usageChargeRepository.sumChargesByEquipmentIds(eqIds);
        for (Object[] row : chargeCosts) {
            if (row.length > 1 && row[0] != null && row[1] != null) {
                usageChargeMap.put((Long) row[0], (BigDecimal) row[1]);
            }
        }

        List<Department> depts = departmentRepository.findByInstitutionId(institutionId);
        Map<Long, String> deptNameMap = depts.stream().collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        LocalDate today = LocalDate.now();

        List<EquipmentLifecycleDTO> dtoList = equipmentPage.getContent().stream().map(eq -> {
            LocalDate pDate = eq.getPurchaseDate() != null ? eq.getPurchaseDate() : eq.getCreatedAt().toLocalDate();
            int ageMonths = Math.max(0, (int) ChronoUnit.MONTHS.between(pDate, today));

            long woCount = workOrderRepository.countByEquipmentId(eq.getId());

            List<EquipmentDowntimeLog> dLogs = downtimeLogRepository.findByEquipmentIdOrderByStartTimeDesc(eq.getId());
            double downtimeHours = dLogs.stream().mapToDouble(d -> {
                LocalDateTime end = d.getEndTime() != null ? d.getEndTime() : LocalDateTime.now();
                return Math.max(0, Duration.between(d.getStartTime(), end).toMinutes()) / 60.0;
            }).sum();

            Optional<CalibrationRecord> calOpt = calibrationRecordRepository.findFirstByEquipment_IdOrderByCalibrationDateDesc(eq.getId());
            String calState = "NONE";
            if (calOpt.isPresent()) {
                LocalDate due = calOpt.get().getNextDueDate();
                if (due == null || due.isBefore(today)) calState = "EXPIRED";
                else if (!due.isAfter(today.plusDays(30))) calState = "DUE_SOON";
                else calState = "VALID";
            }

            BigDecimal pCost = eq.getPurchaseCost();
            BigDecimal mCost = maintCostMap.getOrDefault(eq.getId(), BigDecimal.ZERO);
            BigDecimal uCharges = usageChargeMap.getOrDefault(eq.getId(), BigDecimal.ZERO);

            Double roi = null;
            if (pCost != null && pCost.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal netRevenue = uCharges.subtract(mCost);
                roi = netRevenue.divide(pCost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
            }

            return new EquipmentLifecycleDTO(
                    eq.getId(),
                    eq.getName(),
                    eq.getAssetTag(),
                    eq.getDepartmentId() != null ? deptNameMap.get(eq.getDepartmentId()) : "General",
                    pDate,
                    ageMonths,
                    eq.getStatus(),
                    calState,
                    woCount,
                    Math.round(downtimeHours * 10.0) / 10.0,
                    pCost,
                    mCost,
                    uCharges,
                    roi
            );
        }).toList();

        return new PageImpl<>(dtoList, pageable, equipmentPage.getTotalElements());
    }

    // ==========================================
    // 1.6 SYSTEM MONITORING & USER MANAGEMENT
    // ==========================================
    public SystemMonitoringResponse getSystemMonitoring(Long institutionId) {
        long totalUsers = userRepository.countByInstitutionId(institutionId);
        long totalEquipment = equipmentRepository.findByInstitutionId(institutionId).size();

        List<Equipment> instEquipment = equipmentRepository.findByInstitutionId(institutionId);
        List<Long> eqIds = instEquipment.stream().map(Equipment::getId).toList();
        long totalBookings = eqIds.isEmpty() ? 0 : bookingRepository.countByEquipmentIdIn(eqIds);

        long underMaintenance = instEquipment.stream().filter(e -> "UNDER_MAINTENANCE".equalsIgnoreCase(e.getStatus())).count();

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long failedNotifs = notificationDeliveryRepository.countByStatusAndCreatedAtAfter(DeliveryStatus.FAILED, sevenDaysAgo);

        List<User> pendingDeptHeads = userRepository.findByInstitutionIdAndRequestedRoleAndMembershipStatus(institutionId, "DEPARTMENT_HEAD", "PENDING");
        long pendingDeptHeadCount = pendingDeptHeads.size();

        LocalDate lastSnapshotDate = snapshotRepository.findTopByInstitutionIdOrderBySnapshotDateDesc(institutionId)
                .map(DailyUtilizationSnapshot::getSnapshotDate)
                .orElse(null);

        List<Department> departments = departmentRepository.findByInstitutionId(institutionId);
        Map<Long, String> deptMap = departments.stream().collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        List<User> userList = userRepository.findByInstitutionId(institutionId);
        List<InstitutionUserDTO> institutionUsers = userList.stream().map(u -> new InstitutionUserDTO(
                u.getId(),
                u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail(),
                u.getEmail(),
                u.getDepartmentId(),
                u.getDepartmentId() != null ? deptMap.get(u.getDepartmentId()) : null,
                userService.getRoleNames(u.getId()),
                u.getMembershipStatus(),
                u.getMembershipApproved(),
                u.getCreatedAt()
        )).toList();

        return new SystemMonitoringResponse(
                totalUsers,
                totalEquipment,
                totalBookings,
                underMaintenance,
                failedNotifs,
                pendingDeptHeadCount,
                lastSnapshotDate,
                institutionUsers
        );
    }

    // Helper lookups
    private Map<Long, Equipment> fetchEquipmentMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        return equipmentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e, (a, b) -> a));
    }

    private Map<Long, Department> fetchDepartmentMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        return departmentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Department::getId, d -> d, (a, b) -> a));
    }

    private Map<Long, User> fetchUserMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }
}
