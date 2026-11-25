package com.labresource.platform.report.service;

import com.labresource.platform.billing.entity.DepartmentBudget;
import com.labresource.platform.billing.repository.DepartmentBudgetRepository;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.maintenance.entity.EquipmentDowntimeLog;
import com.labresource.platform.maintenance.entity.WorkOrder;
import com.labresource.platform.maintenance.repository.EquipmentDowntimeLogRepository;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import com.labresource.platform.report.model.ReportColumn;
import com.labresource.platform.report.model.ReportResult;
import com.labresource.platform.report.model.ReportType;
import com.labresource.platform.sharing.entity.SharingAgreement;
import com.labresource.platform.sharing.repository.SharingAgreementRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ReportBuilder {

    private static final int MAX_ROWS = 10_000;

    private final EquipmentRepository equipmentRepository;
    private final BookingRepository bookingRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final EquipmentDowntimeLogRepository downtimeLogRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final SharingAgreementRepository sharingAgreementRepository;
    private final UsageChargeRepository usageChargeRepository;
    private final DepartmentBudgetRepository departmentBudgetRepository;

    public ReportBuilder(
            EquipmentRepository equipmentRepository,
            BookingRepository bookingRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            WorkOrderRepository workOrderRepository,
            EquipmentDowntimeLogRepository downtimeLogRepository,
            CalibrationRecordRepository calibrationRecordRepository,
            SharingAgreementRepository sharingAgreementRepository,
            UsageChargeRepository usageChargeRepository,
            DepartmentBudgetRepository departmentBudgetRepository) {
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.downtimeLogRepository = downtimeLogRepository;
        this.calibrationRecordRepository = calibrationRecordRepository;
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.usageChargeRepository = usageChargeRepository;
        this.departmentBudgetRepository = departmentBudgetRepository;
    }

    public ReportResult build(
            ReportType type,
            Long institutionId,
            Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            String category) {

        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be after end date.");
        }

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("institutionId", institutionId);
        if (departmentId != null) filters.put("departmentId", departmentId);
        filters.put("startDate", effectiveStart.toString());
        filters.put("endDate", effectiveEnd.toString());
        if (category != null && !category.isBlank()) filters.put("category", category);

        ReportResult result = switch (type) {
            case UTILIZATION -> buildUtilizationReport(institutionId, departmentId, effectiveStart, effectiveEnd, category, filters);
            case DEPARTMENT_USAGE -> buildDepartmentUsageReport(institutionId, departmentId, effectiveStart, effectiveEnd, filters);
            case MAINTENANCE_DOWNTIME -> buildMaintenanceDowntimeReport(institutionId, departmentId, effectiveStart, effectiveEnd, filters);
            case INTER_INSTITUTION_SHARING -> buildInterInstitutionSharingReport(institutionId, effectiveStart, effectiveEnd, filters);
            case PROCUREMENT_COST -> buildProcurementCostReport(institutionId, category, filters);
        };

        if (result.rows().size() > MAX_ROWS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Report exceeds the maximum allowed limit of 10,000 rows. Please narrow your date range or filters."
            );
        }

        return result;
    }

    // 1. UTILIZATION REPORT
    private ReportResult buildUtilizationReport(
            Long institutionId,
            Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            String category,
            Map<String, Object> filters) {

        List<ReportColumn> columns = List.of(
                ReportColumn.number("equipmentId", "Equipment ID"),
                ReportColumn.text("equipmentName", "Equipment Name"),
                ReportColumn.text("assetTag", "Asset Tag"),
                ReportColumn.text("category", "Category"),
                ReportColumn.text("departmentName", "Department"),
                ReportColumn.number("operatingHours", "Operating Hours"),
                ReportColumn.number("usedHours", "Used Hours"),
                ReportColumn.percentage("utilizationRate", "Utilization %"),
                ReportColumn.number("bookingCount", "Bookings")
        );

        List<Equipment> equipmentList = departmentId != null
                ? equipmentRepository.findByDepartmentId(departmentId)
                : equipmentRepository.findByInstitutionId(institutionId);

        if (category != null && !category.isBlank()) {
            equipmentList = equipmentList.stream()
                    .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                    .toList();
        }

        List<Long> eqIds = equipmentList.stream().map(Equipment::getId).toList();
        LocalDateTime pStart = startDate.atStartOfDay();
        LocalDateTime pEnd = endDate.atTime(23, 59, 59);

        List<Booking> bookings = eqIds.isEmpty() ? Collections.emptyList()
                : bookingRepository.findByEquipmentIdInAndPeriod(eqIds, pStart, pEnd);

        Map<Long, List<Booking>> bookingsByEq = bookings.stream().collect(Collectors.groupingBy(Booking::getEquipmentId));
        Map<Long, Department> deptMap = departmentRepository.findByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(Department::getId, d -> d, (a, b) -> a));

        long days = Math.max(1, ChronoUnit.DAYS.between(startDate, endDate) + 1);
        double expectedHoursPerUnit = (days / 7.0) * 40.0;

        List<Map<String, Object>> rows = new ArrayList<>();
        double totalUsedHours = 0.0;
        double totalOpHours = 0.0;
        long totalBookings = 0;

        for (Equipment eq : equipmentList) {
            List<Booking> eqBookings = bookingsByEq.getOrDefault(eq.getId(), Collections.emptyList());
            double usedHrs = eqBookings.stream()
                    .filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus()) || "IN_USE".equalsIgnoreCase(b.getStatus()))
                    .mapToDouble(b -> Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes()) / 60.0)
                    .sum();
            usedHrs = Math.round(usedHrs * 10.0) / 10.0;

            double utilRate = expectedHoursPerUnit > 0 ? Math.min(100.0, Math.round((usedHrs / expectedHoursPerUnit) * 1000.0) / 10.0) : 0.0;

            Department d = eq.getDepartmentId() != null ? deptMap.get(eq.getDepartmentId()) : null;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("equipmentId", eq.getId());
            row.put("equipmentName", eq.getName());
            row.put("assetTag", eq.getAssetTag() != null ? eq.getAssetTag() : "-");
            row.put("category", eq.getCategory() != null ? eq.getCategory() : "General");
            row.put("departmentName", d != null ? d.getName() : "General");
            row.put("operatingHours", Math.round(expectedHoursPerUnit * 10.0) / 10.0);
            row.put("usedHours", usedHrs);
            row.put("utilizationRate", utilRate);
            row.put("bookingCount", eqBookings.size());

            rows.add(row);

            totalUsedHours += usedHrs;
            totalOpHours += expectedHoursPerUnit;
            totalBookings += eqBookings.size();
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("totalEquipment", equipmentList.size());
        totals.put("totalOperatingHours", Math.round(totalOpHours * 10.0) / 10.0);
        totals.put("totalUsedHours", Math.round(totalUsedHours * 10.0) / 10.0);
        double overallUtil = totalOpHours > 0 ? Math.min(100.0, Math.round((totalUsedHours / totalOpHours) * 1000.0) / 10.0) : 0.0;
        totals.put("averageUtilization", overallUtil);
        totals.put("totalBookings", totalBookings);

        List<Map<String, Object>> chartData = rows.stream()
                .sorted(Comparator.comparingDouble((Map<String, Object> m) -> (Double) m.get("utilizationRate")).reversed())
                .limit(10)
                .map(m -> Map.<String, Object>of(
                        "name", m.get("equipmentName"),
                        "utilization", m.get("utilizationRate"),
                        "usedHours", m.get("usedHours")
                ))
                .toList();

        return new ReportResult(
                ReportType.UTILIZATION.name(),
                ReportType.UTILIZATION.getTitle(),
                LocalDateTime.now(),
                filters,
                columns,
                rows,
                totals,
                chartData
        );
    }

    // 2. DEPARTMENT USAGE REPORT
    private ReportResult buildDepartmentUsageReport(
            Long institutionId,
            Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, Object> filters) {

        List<ReportColumn> columns = List.of(
                ReportColumn.text("departmentName", "Department"),
                ReportColumn.number("activeUsers", "Active Users"),
                ReportColumn.number("totalBookings", "Total Bookings"),
                ReportColumn.number("hoursUsed", "Hours Used"),
                ReportColumn.currency("internalSpend", "Internal Spend (₹)"),
                ReportColumn.currency("externalSpend", "External Spend (₹)"),
                ReportColumn.currency("totalSpend", "Total Spend (₹)"),
                ReportColumn.currency("allocatedBudget", "Allocated Budget (₹)"),
                ReportColumn.percentage("budgetUtilization", "Budget Util %")
        );

        List<Department> departments = departmentRepository.findByInstitutionId(institutionId);
        if (departmentId != null) {
            departments = departments.stream().filter(d -> d.getId().equals(departmentId)).toList();
        }

        LocalDateTime pStart = startDate.atStartOfDay();
        LocalDateTime pEnd = endDate.atTime(23, 59, 59);

        List<Map<String, Object>> rows = new ArrayList<>();
        long totalInstBookings = 0;
        double totalInstHours = 0.0;
        BigDecimal totalInstSpend = BigDecimal.ZERO;
        BigDecimal totalInstBudget = BigDecimal.ZERO;

        for (Department d : departments) {
            List<User> deptUsers = userRepository.findByDepartmentId(d.getId());
            Set<Long> userIds = deptUsers.stream().map(User::getId).collect(Collectors.toSet());

            List<Equipment> deptEquipment = equipmentRepository.findByDepartmentId(d.getId());
            List<Long> eqIds = deptEquipment.stream().map(Equipment::getId).toList();

            List<Booking> deptBookings = eqIds.isEmpty() ? Collections.emptyList()
                    : bookingRepository.findByEquipmentIdInAndPeriod(eqIds, pStart, pEnd);

            double hoursUsed = deptBookings.stream()
                    .filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus()))
                    .mapToDouble(b -> Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes()) / 60.0)
                    .sum();
            hoursUsed = Math.round(hoursUsed * 10.0) / 10.0;

            BigDecimal deptSpend = usageChargeRepository.sumDepartmentSpend(d.getId(), pStart, pEnd);
            if (deptSpend == null) deptSpend = BigDecimal.ZERO;

            Optional<DepartmentBudget> budgetOpt = departmentBudgetRepository.findByDepartmentIdAndFiscalYear(d.getId(), startDate.getYear());
            BigDecimal budget = budgetOpt.map(DepartmentBudget::getAllocatedAmount).orElse(BigDecimal.ZERO);

            double budgetUtil = budget.compareTo(BigDecimal.ZERO) > 0
                    ? deptSpend.divide(budget, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("departmentName", d.getName());
            row.put("activeUsers", deptUsers.size());
            row.put("totalBookings", deptBookings.size());
            row.put("hoursUsed", hoursUsed);
            row.put("internalSpend", deptSpend);
            row.put("externalSpend", BigDecimal.ZERO);
            row.put("totalSpend", deptSpend);
            row.put("allocatedBudget", budget);
            row.put("budgetUtilization", Math.round(budgetUtil * 10.0) / 10.0);

            rows.add(row);

            totalInstBookings += deptBookings.size();
            totalInstHours += hoursUsed;
            totalInstSpend = totalInstSpend.add(deptSpend);
            totalInstBudget = totalInstBudget.add(budget);
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("totalBookings", totalInstBookings);
        totals.put("totalHoursUsed", Math.round(totalInstHours * 10.0) / 10.0);
        totals.put("totalSpend", totalInstSpend);
        totals.put("totalBudget", totalInstBudget);

        List<Map<String, Object>> chartData = rows.stream().map(m -> Map.<String, Object>of(
                "name", m.get("departmentName"),
                "spend", m.get("totalSpend"),
                "hours", m.get("hoursUsed")
        )).toList();

        return new ReportResult(
                ReportType.DEPARTMENT_USAGE.name(),
                ReportType.DEPARTMENT_USAGE.getTitle(),
                LocalDateTime.now(),
                filters,
                columns,
                rows,
                totals,
                chartData
        );
    }

    // 3. MAINTENANCE AND DOWNTIME REPORT
    private ReportResult buildMaintenanceDowntimeReport(
            Long institutionId,
            Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, Object> filters) {

        List<ReportColumn> columns = List.of(
                ReportColumn.text("equipmentName", "Equipment Name"),
                ReportColumn.text("assetTag", "Asset Tag"),
                ReportColumn.text("departmentName", "Department"),
                ReportColumn.number("totalWorkOrders", "Work Orders"),
                ReportColumn.number("preventiveOrders", "Preventive"),
                ReportColumn.number("correctiveOrders", "Corrective"),
                ReportColumn.number("totalDowntimeHours", "Downtime (Hours)"),
                ReportColumn.currency("maintenanceCost", "Maintenance Cost (₹)"),
                ReportColumn.text("latestCalibrationState", "Calibration Status")
        );

        List<Equipment> equipmentList = departmentId != null
                ? equipmentRepository.findByDepartmentId(departmentId)
                : equipmentRepository.findByInstitutionId(institutionId);

        List<Long> eqIds = equipmentList.stream().map(Equipment::getId).toList();
        LocalDateTime pStart = startDate.atStartOfDay();
        LocalDateTime pEnd = endDate.atTime(23, 59, 59);

        List<WorkOrder> workOrders = eqIds.isEmpty() ? Collections.emptyList()
                : workOrderRepository.findWorkOrdersInPeriod(eqIds, pStart, pEnd);

        Map<Long, List<WorkOrder>> woByEq = workOrders.stream().collect(Collectors.groupingBy(WorkOrder::getEquipmentId));
        Map<Long, Department> deptMap = departmentRepository.findByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(Department::getId, d -> d, (a, b) -> a));

        List<Map<String, Object>> rows = new ArrayList<>();
        long totalOrders = 0;
        double totalDowntime = 0.0;
        BigDecimal totalCost = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();

        for (Equipment eq : equipmentList) {
            List<WorkOrder> eqOrders = woByEq.getOrDefault(eq.getId(), Collections.emptyList());
            long prevCount = eqOrders.stream().filter(w -> "PREVENTIVE".equalsIgnoreCase(w.getWorkOrderType())).count();
            long corrCount = eqOrders.stream().filter(w -> "CORRECTIVE".equalsIgnoreCase(w.getWorkOrderType())).count();

            BigDecimal cost = eqOrders.stream()
                    .map(w -> w.getCost() != null ? w.getCost() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<EquipmentDowntimeLog> logs = downtimeLogRepository.findByEquipmentIdOrderByStartTimeDesc(eq.getId());
            double downtimeHrs = logs.stream().mapToDouble(d -> {
                LocalDateTime end = d.getEndTime() != null ? d.getEndTime() : LocalDateTime.now();
                return Math.max(0, Duration.between(d.getStartTime(), end).toMinutes()) / 60.0;
            }).sum();
            downtimeHrs = Math.round(downtimeHrs * 10.0) / 10.0;

            Optional<CalibrationRecord> calOpt = calibrationRecordRepository.findFirstByEquipment_IdOrderByCalibrationDateDesc(eq.getId());
            String calState = "NONE";
            if (calOpt.isPresent()) {
                LocalDate due = calOpt.get().getNextDueDate();
                if (due == null || due.isBefore(today)) calState = "EXPIRED";
                else if (!due.isAfter(today.plusDays(30))) calState = "DUE_SOON";
                else calState = "VALID";
            }

            Department d = eq.getDepartmentId() != null ? deptMap.get(eq.getDepartmentId()) : null;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("equipmentName", eq.getName());
            row.put("assetTag", eq.getAssetTag() != null ? eq.getAssetTag() : "-");
            row.put("departmentName", d != null ? d.getName() : "General");
            row.put("totalWorkOrders", eqOrders.size());
            row.put("preventiveOrders", prevCount);
            row.put("correctiveOrders", corrCount);
            row.put("totalDowntimeHours", downtimeHrs);
            row.put("maintenanceCost", cost);
            row.put("latestCalibrationState", calState);

            rows.add(row);

            totalOrders += eqOrders.size();
            totalDowntime += downtimeHrs;
            totalCost = totalCost.add(cost);
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("totalWorkOrders", totalOrders);
        totals.put("totalDowntimeHours", Math.round(totalDowntime * 10.0) / 10.0);
        totals.put("totalMaintenanceCost", totalCost);

        List<Map<String, Object>> chartData = rows.stream()
                .sorted(Comparator.comparingDouble((Map<String, Object> m) -> (Double) m.get("totalDowntimeHours")).reversed())
                .limit(10)
                .map(m -> Map.<String, Object>of(
                        "name", m.get("equipmentName"),
                        "downtime", m.get("totalDowntimeHours"),
                        "cost", m.get("maintenanceCost")
                ))
                .toList();

        return new ReportResult(
                ReportType.MAINTENANCE_DOWNTIME.name(),
                ReportType.MAINTENANCE_DOWNTIME.getTitle(),
                LocalDateTime.now(),
                filters,
                columns,
                rows,
                totals,
                chartData
        );
    }

    // 4. INTER-INSTITUTION SHARING REPORT
    private ReportResult buildInterInstitutionSharingReport(
            Long institutionId,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, Object> filters) {

        List<ReportColumn> columns = List.of(
                ReportColumn.number("agreementId", "Agreement ID"),
                ReportColumn.text("equipmentName", "Equipment Name"),
                ReportColumn.text("partnerInstitution", "Partner Institution"),
                ReportColumn.date("startDate", "Start Date"),
                ReportColumn.date("endDate", "End Date"),
                ReportColumn.currency("hourlyRate", "Hourly Rate (₹)"),
                ReportColumn.number("externalBookings", "External Bookings"),
                ReportColumn.number("sharedHours", "Shared Hours"),
                ReportColumn.currency("accruedRevenue", "Accrued Revenue (₹)"),
                ReportColumn.text("status", "Status")
        );

        List<SharingAgreement> agreements = sharingAgreementRepository.findByOwnerInstitutionId(institutionId);
        List<Map<String, Object>> rows = new ArrayList<>();
        double totalSharedHours = 0.0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalBookings = 0;

        for (SharingAgreement a : agreements) {
            Equipment eq = equipmentRepository.findById(a.getEquipmentId()).orElse(null);
            List<Booking> bookings = bookingRepository.findBySharingAgreementId(a.getId());

            double hrs = bookings.stream()
                    .filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus()))
                    .mapToDouble(b -> Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes()) / 60.0)
                    .sum();
            hrs = Math.round(hrs * 10.0) / 10.0;

            BigDecimal rev = a.getFeePerHour() != null
                    ? a.getFeePerHour().multiply(BigDecimal.valueOf(hrs)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agreementId", a.getId());
            row.put("equipmentName", eq != null ? eq.getName() : "Equipment #" + a.getEquipmentId());
            row.put("partnerInstitution", "Partner Inst #" + a.getPartnerInstitutionId());
            row.put("startDate", a.getStartDate());
            row.put("endDate", a.getEndDate());
            row.put("hourlyRate", a.getFeePerHour() != null ? a.getFeePerHour() : BigDecimal.ZERO);
            row.put("externalBookings", bookings.size());
            row.put("sharedHours", hrs);
            row.put("accruedRevenue", rev);
            row.put("status", a.getStatus());

            rows.add(row);

            totalSharedHours += hrs;
            totalRevenue = totalRevenue.add(rev);
            totalBookings += bookings.size();
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("totalAgreements", agreements.size());
        totals.put("totalExternalBookings", totalBookings);
        totals.put("totalSharedHours", Math.round(totalSharedHours * 10.0) / 10.0);
        totals.put("totalAccruedRevenue", totalRevenue);

        List<Map<String, Object>> chartData = rows.stream().map(m -> Map.<String, Object>of(
                "name", m.get("equipmentName"),
                "revenue", m.get("accruedRevenue"),
                "hours", m.get("sharedHours")
        )).toList();

        return new ReportResult(
                ReportType.INTER_INSTITUTION_SHARING.name(),
                ReportType.INTER_INSTITUTION_SHARING.getTitle(),
                LocalDateTime.now(),
                filters,
                columns,
                rows,
                totals,
                chartData
        );
    }

    // 5. PROCUREMENT AND COST ANALYSIS REPORT
    private ReportResult buildProcurementCostReport(
            Long institutionId,
            String category,
            Map<String, Object> filters) {

        List<ReportColumn> columns = List.of(
                ReportColumn.text("equipmentName", "Equipment Name"),
                ReportColumn.text("category", "Category"),
                ReportColumn.date("purchaseDate", "Purchase Date"),
                ReportColumn.currency("purchaseCost", "Purchase Cost (₹)"),
                ReportColumn.currency("maintenanceCost", "Maintenance Cost (₹)"),
                ReportColumn.currency("usageRevenue", "Usage Revenue (₹)"),
                ReportColumn.percentage("roiPercentage", "ROI %"),
                ReportColumn.text("recommendation", "Procurement Flag")
        );

        List<Equipment> equipmentList = equipmentRepository.findByInstitutionId(institutionId);
        if (category != null && !category.isBlank()) {
            equipmentList = equipmentList.stream()
                    .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                    .toList();
        }

        List<Long> eqIds = equipmentList.stream().map(Equipment::getId).toList();
        Map<Long, BigDecimal> maintCosts = new HashMap<>();
        if (!eqIds.isEmpty()) {
            List<Object[]> rows = workOrderRepository.sumCompletedCostByEquipmentIdsAndPeriod(eqIds, null, null);
            for (Object[] r : rows) {
                if (r.length > 1 && r[0] != null && r[1] != null) {
                    maintCosts.put((Long) r[0], (BigDecimal) r[1]);
                }
            }
        }

        Map<Long, BigDecimal> usageCharges = new HashMap<>();
        if (!eqIds.isEmpty()) {
            List<Object[]> rows = usageChargeRepository.sumChargesByEquipmentIds(eqIds);
            for (Object[] r : rows) {
                if (r.length > 1 && r[0] != null && r[1] != null) {
                    usageCharges.put((Long) r[0], (BigDecimal) r[1]);
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalPurchase = BigDecimal.ZERO;
        BigDecimal totalMaintenance = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (Equipment eq : equipmentList) {
            BigDecimal pCost = eq.getPurchaseCost() != null ? eq.getPurchaseCost() : BigDecimal.ZERO;
            BigDecimal mCost = maintCosts.getOrDefault(eq.getId(), BigDecimal.ZERO);
            BigDecimal uRev = usageCharges.getOrDefault(eq.getId(), BigDecimal.ZERO);

            Double roi = null;
            if (pCost.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal net = uRev.subtract(mCost);
                roi = net.divide(pCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
            }

            String flag = "OPTIMAL";
            if (pCost.compareTo(BigDecimal.ZERO) > 0 && mCost.divide(pCost, 2, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(0.3)) >= 0) {
                flag = "HIGH_MAINTENANCE";
            } else if (roi != null && roi < 0) {
                flag = "LOW_ROI";
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("equipmentName", eq.getName());
            row.put("category", eq.getCategory() != null ? eq.getCategory() : "General");
            row.put("purchaseDate", eq.getPurchaseDate() != null ? eq.getPurchaseDate() : eq.getCreatedAt().toLocalDate());
            row.put("purchaseCost", pCost);
            row.put("maintenanceCost", mCost);
            row.put("usageRevenue", uRev);
            row.put("roiPercentage", roi != null ? roi : 0.0);
            row.put("recommendation", flag);

            rows.add(row);

            totalPurchase = totalPurchase.add(pCost);
            totalMaintenance = totalMaintenance.add(mCost);
            totalRevenue = totalRevenue.add(uRev);
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("totalPurchaseCost", totalPurchase);
        totals.put("totalMaintenanceCost", totalMaintenance);
        totals.put("totalUsageRevenue", totalRevenue);
        double overallRoi = totalPurchase.compareTo(BigDecimal.ZERO) > 0
                ? totalRevenue.subtract(totalMaintenance).divide(totalPurchase, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;
        totals.put("averageRoi", Math.round(overallRoi * 100.0) / 100.0);

        List<Map<String, Object>> chartData = rows.stream().map(m -> Map.<String, Object>of(
                "name", m.get("equipmentName"),
                "roi", m.get("roiPercentage"),
                "cost", m.get("purchaseCost")
        )).toList();

        return new ReportResult(
                ReportType.PROCUREMENT_COST.name(),
                ReportType.PROCUREMENT_COST.getTitle(),
                LocalDateTime.now(),
                filters,
                columns,
                rows,
                totals,
                chartData
        );
    }
}
