package com.labresource.platform.analytics.service;

import com.labresource.platform.analytics.entity.DailyUtilizationSnapshot;
import com.labresource.platform.analytics.repository.DailyUtilizationSnapshotRepository;
import com.labresource.platform.analytics.dto.*;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.entity.EquipmentOperatingHours;
import com.labresource.platform.equipment.repository.EquipmentOperatingHoursRepository;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UtilizationAnalyticsService {

    private final UtilizationRepository utilizationRepository;
    private final BookingRepository bookingRepository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final InstitutionRepository institutionRepository;
    private final EquipmentOperatingHoursRepository operatingHoursRepository;
    private final ZoneId platformZoneId;

    @Autowired(required = false)
    private DailyUtilizationSnapshotRepository snapshotRepository;

    public void setSnapshotRepository(DailyUtilizationSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    @Autowired
    public UtilizationAnalyticsService(
            UtilizationRepository utilizationRepository,
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository,
            DepartmentRepository departmentRepository,
            InstitutionRepository institutionRepository,
            @Autowired(required = false) EquipmentOperatingHoursRepository operatingHoursRepository,
            @Value("${app.platform.timezone:UTC}") String timezoneStr) {
        this.utilizationRepository = utilizationRepository;
        this.bookingRepository = bookingRepository;
        this.equipmentRepository = equipmentRepository;
        this.departmentRepository = departmentRepository;
        this.institutionRepository = institutionRepository;
        this.operatingHoursRepository = operatingHoursRepository;
        ZoneId parsedZone;
        try {
            parsedZone = (timezoneStr != null && !timezoneStr.isBlank()) ? ZoneId.of(timezoneStr) : ZoneOffset.UTC;
        } catch (Exception ex) {
            parsedZone = ZoneOffset.UTC;
        }
        this.platformZoneId = parsedZone;
    }

    public UtilizationAnalyticsService(
            UtilizationRepository utilizationRepository,
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository,
            DepartmentRepository departmentRepository,
            InstitutionRepository institutionRepository,
            EquipmentOperatingHoursRepository operatingHoursRepository) {
        this(utilizationRepository, bookingRepository, equipmentRepository, departmentRepository, institutionRepository, operatingHoursRepository, "UTC");
    }

    public UtilizationAnalyticsService(
            UtilizationRepository utilizationRepository,
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository,
            DepartmentRepository departmentRepository,
            InstitutionRepository institutionRepository) {
        this(utilizationRepository, bookingRepository, equipmentRepository, departmentRepository, institutionRepository, null, "UTC");
    }

    public ZoneId getPlatformZoneId() {
        return platformZoneId;
    }

    public LocalDateTime toPlatformTime(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return null;
        return utcDateTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(platformZoneId)
                .toLocalDateTime();
    }

    public LocalDateTime toUtcInstant(LocalDateTime platformDateTime) {
        if (platformDateTime == null) return null;
        return platformDateTime.atZone(platformZoneId)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    // --- Schedule Resolution ---
    public record DailySchedule(boolean isOpen, LocalTime openTime, LocalTime closeTime) {}

    public Map<Integer, DailySchedule> resolveSchedule(Equipment equipment) {
        Map<Integer, DailySchedule> scheduleMap = new HashMap<>();
        List<EquipmentOperatingHours> customRows = Collections.emptyList();
        if (operatingHoursRepository != null && equipment != null) {
            if (equipment.getId() != null) {
                customRows = operatingHoursRepository.findByEquipmentIdOrderByDayOfWeekAsc(equipment.getId());
            }
            if (customRows.isEmpty() && equipment.getInstitutionId() != null) {
                customRows = operatingHoursRepository.findByInstitutionIdAndEquipmentIdIsNullOrderByDayOfWeekAsc(equipment.getInstitutionId());
            }
        }

        if (!customRows.isEmpty()) {
            // Rule 3: If custom operating-hour rows exist for an equipment schedule,
            // only configured days are open. Missing configured days are closed.
            for (int d = 1; d <= 7; d++) {
                final int day = d;
                Optional<EquipmentOperatingHours> row = customRows.stream()
                        .filter(r -> r.getDayOfWeek() != null && r.getDayOfWeek() == day)
                        .findFirst();
                if (row.isPresent()) {
                    EquipmentOperatingHours h = row.get();
                    boolean open = !Boolean.TRUE.equals(h.getIsClosed())
                            && h.getOpenTime() != null && h.getCloseTime() != null
                            && h.getOpenTime().isBefore(h.getCloseTime());
                    scheduleMap.put(day, new DailySchedule(open, h.getOpenTime(), h.getCloseTime()));
                } else {
                    scheduleMap.put(day, new DailySchedule(false, null, null));
                }
            }
        } else {
            // Rules 1 & 2: Default Monday-Friday 09:00-17:00, Saturday-Sunday closed.
            for (int d = 1; d <= 7; d++) {
                if (d <= 5) {
                    scheduleMap.put(d, new DailySchedule(true, LocalTime.of(9, 0), LocalTime.of(17, 0)));
                } else {
                    scheduleMap.put(d, new DailySchedule(false, null, null));
                }
            }
        }
        return scheduleMap;
    }

    // --- Available Operating Minutes ---
    public long calculateAvailableMinutes(Equipment equipment, LocalDateTime windowStart, LocalDateTime windowEnd) {
        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart)) return 0L;
        Map<Integer, DailySchedule> schedule = resolveSchedule(equipment);
        int quantity = equipment != null && equipment.getQuantity() != null && equipment.getQuantity() > 0 ? equipment.getQuantity() : 1;

        LocalDate startDate = windowStart.toLocalDate();
        LocalDate endDate = windowEnd.toLocalDate();
        long totalMinutes = 0L;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DailySchedule daySchedule = schedule.get(date.getDayOfWeek().getValue());
            if (daySchedule == null || !daySchedule.isOpen()) continue;

            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            LocalDateTime winSliceStart = windowStart.isAfter(dayStart) ? windowStart : dayStart;
            LocalDateTime winSliceEnd = windowEnd.isBefore(dayEnd) ? windowEnd : dayEnd;
            if (!winSliceEnd.isAfter(winSliceStart)) continue;

            LocalDateTime opStart = date.atTime(daySchedule.openTime());
            LocalDateTime opEnd = date.atTime(daySchedule.closeTime());

            LocalDateTime activeStart = winSliceStart.isAfter(opStart) ? winSliceStart : opStart;
            LocalDateTime activeEnd = winSliceEnd.isBefore(opEnd) ? winSliceEnd : opEnd;

            if (activeEnd.isAfter(activeStart)) {
                long mins = Duration.between(activeStart, activeEnd).toMinutes();
                totalMinutes += mins * quantity;
            }
        }
        return totalMinutes;
    }

    // --- Usage Breakdown & Day-by-Day Clipping ---
    public record UsageBreakdownMinutes(long totalMinutes, long sharedMinutes, long exclusiveMinutes) {}

    public UsageBreakdownMinutes calculateActualUsageMinutes(
            Equipment equipment, List<Utilization> sessions,
            LocalDateTime windowStart, LocalDateTime windowEnd) {

        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart) || sessions == null || sessions.isEmpty()) {
            return new UsageBreakdownMinutes(0L, 0L, 0L);
        }
        Map<Integer, DailySchedule> schedule = resolveSchedule(equipment);
        long totalMins = 0L;
        long sharedMins = 0L;
        long exclusiveMins = 0L;
        LocalDateTime now = LocalDateTime.now(platformZoneId);

        for (Utilization s : sessions) {
            LocalDateTime rawStart = toPlatformTime(s.getStartTime());
            LocalDateTime rawEnd = s.getEndTime() != null ? toPlatformTime(s.getEndTime()) : now;
            if (rawStart == null || rawEnd == null || !rawEnd.isAfter(rawStart)) continue;

            LocalDateTime effStart = rawStart.isBefore(windowStart) ? windowStart : rawStart;
            LocalDateTime effEnd = rawEnd.isAfter(windowEnd) ? windowEnd : rawEnd;
            if (!effEnd.isAfter(effStart)) continue;

            LocalDate startDay = effStart.toLocalDate();
            LocalDate endDay = effEnd.toLocalDate();

            long sessionClippedMinutes = 0L;

            for (LocalDate date = startDay; !date.isAfter(endDay); date = date.plusDays(1)) {
                DailySchedule daySchedule = schedule.get(date.getDayOfWeek().getValue());
                if (daySchedule == null || !daySchedule.isOpen()) continue;

                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

                LocalDateTime sliceStart = effStart.isAfter(dayStart) ? effStart : dayStart;
                LocalDateTime sliceEnd = effEnd.isBefore(dayEnd) ? effEnd : dayEnd;
                if (!sliceEnd.isAfter(sliceStart)) continue;

                LocalDateTime opStart = date.atTime(daySchedule.openTime());
                LocalDateTime opEnd = date.atTime(daySchedule.closeTime());

                LocalDateTime clipStart = sliceStart.isAfter(opStart) ? sliceStart : opStart;
                LocalDateTime clipEnd = sliceEnd.isBefore(opEnd) ? sliceEnd : opEnd;

                if (clipEnd.isAfter(clipStart)) {
                    sessionClippedMinutes += Duration.between(clipStart, clipEnd).toMinutes();
                }
            }

            totalMins += sessionClippedMinutes;
            if ("SHARED".equalsIgnoreCase(s.getUsageType())) {
                sharedMins += sessionClippedMinutes;
            } else {
                exclusiveMins += sessionClippedMinutes;
            }
        }

        return new UsageBreakdownMinutes(totalMins, sharedMins, exclusiveMins);
    }

    public List<EquipmentUtilizationRateDto> getEquipmentUtilizationRates(
            Long institutionId, Long departmentId,
            LocalDateTime start, LocalDateTime end, Double operatingHoursPerDay) {
        return getEquipmentUtilizationRates(institutionId, departmentId, start, end, operatingHoursPerDay, null);
    }

    public List<EquipmentUtilizationRateDto> getEquipmentUtilizationRates(
            Long institutionId, Long departmentId,
            LocalDateTime start, LocalDateTime end, Double operatingHoursPerDay, Double targetUtilizationRate) {

        final WindowParams window = resolveWindow(start, end, operatingHoursPerDay);
        List<Equipment> equipmentList = getEquipmentInScope(institutionId, departmentId, null);
        if (equipmentList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> equipmentIds = equipmentList.stream().map(Equipment::getId).toList();
        List<Utilization> currentSessions = utilizationRepository.findOverlappingSessions(
                equipmentIds, toUtcInstant(window.start), toUtcInstant(window.end));

        // Rule 10: Correct the benchmark to compare against the preceding fixed 28-day period
        LocalDateTime benchmarkEnd = window.start;
        LocalDateTime benchmarkStart = window.start.minusDays(28);
        List<Utilization> benchmarkSessions = utilizationRepository.findOverlappingSessions(
                equipmentIds, toUtcInstant(benchmarkStart), toUtcInstant(benchmarkEnd));

        Map<Long, List<Utilization>> currentByEquipment = currentSessions.stream()
                .collect(Collectors.groupingBy(Utilization::getEquipmentId));
        Map<Long, List<Utilization>> benchmarkByEquipment = benchmarkSessions.stream()
                .collect(Collectors.groupingBy(Utilization::getEquipmentId));

        Map<Long, String> departmentNames = getDepartmentNames(equipmentList);

        List<EquipmentUtilizationRateDto> results = new ArrayList<>();
        for (Equipment eq : equipmentList) {
            int quantity = eq.getQuantity() != null && eq.getQuantity() > 0 ? eq.getQuantity() : 1;

            long availMins = calculateAvailableMinutes(eq, window.start, window.end);
            double capacityHours = round(availMins / 60.0);

            List<Utilization> eqSessions = currentByEquipment.getOrDefault(eq.getId(), Collections.emptyList());
            UsageBreakdownMinutes currentUsage = calculateActualUsageMinutes(eq, eqSessions, window.start, window.end);
            double actualHours = round(currentUsage.totalMinutes / 60.0);

            // Rule 7: utilization from actual usage minutes divided by available minutes; if available minutes are zero, return null
            Double rate = availMins > 0
                    ? Math.min(100.0, round(((double) currentUsage.totalMinutes / (double) availMins) * 100.0))
                    : null;

            // 28-day benchmark: use snapshots if available, otherwise dynamic
            Double benchRate = null;
            if (snapshotRepository != null) {
                List<DailyUtilizationSnapshot> snaps = snapshotRepository
                        .findByEquipmentIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                                eq.getId(), benchmarkStart.toLocalDate(), benchmarkEnd.toLocalDate());
                if (!snaps.isEmpty()) {
                    long snapOperating = snaps.stream().mapToLong(DailyUtilizationSnapshot::getOperatingMinutes).sum();
                    long snapUsed = snaps.stream().mapToLong(DailyUtilizationSnapshot::getUsedMinutes).sum();
                    if (snapOperating > 0) {
                        benchRate = Math.min(100.0, round(((double) snapUsed / (double) snapOperating) * 100.0));
                    }
                }
            }
            if (benchRate == null) {
                long benchAvailMins = calculateAvailableMinutes(eq, benchmarkStart, benchmarkEnd);
                List<Utilization> benchSessions = benchmarkByEquipment.getOrDefault(eq.getId(), Collections.emptyList());
                UsageBreakdownMinutes benchUsage = calculateActualUsageMinutes(eq, benchSessions, benchmarkStart, benchmarkEnd);
                benchRate = benchAvailMins > 0
                        ? Math.min(100.0, round(((double) benchUsage.totalMinutes / (double) benchAvailMins) * 100.0))
                        : null;
            }

            Double trend = (rate != null && benchRate != null) ? round(rate - benchRate) : null;

            // Rule 9: utilization target gap using explicit target without inventing undocumented default
            Double targetGap = (rate != null && targetUtilizationRate != null)
                    ? round(rate - targetUtilizationRate)
                    : null;

            results.add(EquipmentUtilizationRateDto.builder()
                    .equipmentId(eq.getId())
                    .equipmentName(eq.getName())
                    .assetTag(eq.getAssetTag())
                    .departmentId(eq.getDepartmentId())
                    .departmentName(eq.getDepartmentId() != null ? departmentNames.get(eq.getDepartmentId()) : "Unassigned")
                    .institutionId(eq.getInstitutionId())
                    .quantity(quantity)
                    .totalCapacityHours(capacityHours)
                    .actualUsageHours(actualHours)
                    .totalAvailableMinutes(availMins)
                    .actualUsageMinutes(currentUsage.totalMinutes)
                    .utilizationRate(rate)
                    .exclusiveUsageHours(round(currentUsage.exclusiveMinutes / 60.0))
                    .sharedUsageHours(round(currentUsage.sharedMinutes / 60.0))
                    .sessionCount((long) eqSessions.size())
                    .historicalBenchmarkRate(benchRate)
                    .trendPercentage(trend)
                    .targetUtilizationRate(targetUtilizationRate)
                    .targetGap(targetGap)
                    .build());
        }

        return results;
    }

    public List<DepartmentUtilizationSummaryDto> getDepartmentUtilization(
            Long institutionId, LocalDateTime start, LocalDateTime end, Double operatingHoursPerDay) {
        return getDepartmentUtilization(institutionId, start, end, operatingHoursPerDay, null);
    }

    public List<DepartmentUtilizationSummaryDto> getDepartmentUtilization(
            Long institutionId, LocalDateTime start, LocalDateTime end, Double operatingHoursPerDay, Double targetUtilizationRate) {

        List<EquipmentUtilizationRateDto> equipmentRates = getEquipmentUtilizationRates(
                institutionId, null, start, end, operatingHoursPerDay, targetUtilizationRate);

        Map<Long, List<EquipmentUtilizationRateDto>> byDept = equipmentRates.stream()
                .collect(Collectors.groupingBy(dto -> dto.getDepartmentId() != null ? dto.getDepartmentId() : -1L));

        List<DepartmentUtilizationSummaryDto> summaries = new ArrayList<>();
        for (Map.Entry<Long, List<EquipmentUtilizationRateDto>> entry : byDept.entrySet()) {
            Long deptId = entry.getKey();
            List<EquipmentUtilizationRateDto> deptRates = entry.getValue();

            String deptName = deptId > 0 && !deptRates.isEmpty()
                    ? deptRates.getFirst().getDepartmentName()
                    : "Unassigned";

            // Rule 8: Department percentages must be calculated by summing raw used minutes and available minutes first
            long totalAvailMins = deptRates.stream().mapToLong(dto -> dto.getTotalAvailableMinutes() != null ? dto.getTotalAvailableMinutes() : 0L).sum();
            long totalUsedMins = deptRates.stream().mapToLong(dto -> dto.getActualUsageMinutes() != null ? dto.getActualUsageMinutes() : 0L).sum();

            Double avgRate = totalAvailMins > 0
                    ? Math.min(100.0, round(((double) totalUsedMins / (double) totalAvailMins) * 100.0))
                    : null;

            double sharedUsage = round(deptRates.stream().mapToDouble(EquipmentUtilizationRateDto::getSharedUsageHours).sum());
            double exclusiveUsage = round(deptRates.stream().mapToDouble(EquipmentUtilizationRateDto::getExclusiveUsageHours).sum());

            Double targetGap = (avgRate != null && targetUtilizationRate != null)
                    ? round(avgRate - targetUtilizationRate)
                    : null;

            summaries.add(DepartmentUtilizationSummaryDto.builder()
                    .departmentId(deptId > 0 ? deptId : null)
                    .departmentName(deptName)
                    .institutionId(institutionId)
                    .equipmentCount(deptRates.size())
                    .totalCapacityHours(round(totalAvailMins / 60.0))
                    .actualUsageHours(round(totalUsedMins / 60.0))
                    .totalAvailableMinutes(totalAvailMins)
                    .actualUsageMinutes(totalUsedMins)
                    .averageUtilizationRate(avgRate)
                    .sharedUsageHours(sharedUsage)
                    .exclusiveUsageHours(exclusiveUsage)
                    .targetUtilizationRate(targetUtilizationRate)
                    .targetGap(targetGap)
                    .build());
        }

        summaries.sort(Comparator.comparing(
                DepartmentUtilizationSummaryDto::getAverageUtilizationRate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return summaries;
    }

    public InstitutionUtilizationSummaryDto getInstitutionUtilization(
            Long institutionId, LocalDateTime start, LocalDateTime end, Double operatingHoursPerDay) {
        return getInstitutionUtilization(institutionId, start, end, operatingHoursPerDay, null);
    }

    public InstitutionUtilizationSummaryDto getInstitutionUtilization(
            Long institutionId, LocalDateTime start, LocalDateTime end, Double operatingHoursPerDay, Double targetUtilizationRate) {

        List<EquipmentUtilizationRateDto> equipmentRates = getEquipmentUtilizationRates(
                institutionId, null, start, end, operatingHoursPerDay, targetUtilizationRate);

        String institutionName = institutionId != null
                ? institutionRepository.findById(institutionId).map(Institution::getName).orElse("Institution #" + institutionId)
                : "All Institutions";

        Set<Long> deptIds = equipmentRates.stream()
                .map(EquipmentUtilizationRateDto::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Rule 8: Institution percentages calculated by summing raw used minutes and available minutes first
        long totalAvailMins = equipmentRates.stream().mapToLong(dto -> dto.getTotalAvailableMinutes() != null ? dto.getTotalAvailableMinutes() : 0L).sum();
        long totalUsedMins = equipmentRates.stream().mapToLong(dto -> dto.getActualUsageMinutes() != null ? dto.getActualUsageMinutes() : 0L).sum();

        Double overallRate = totalAvailMins > 0
                ? Math.min(100.0, round(((double) totalUsedMins / (double) totalAvailMins) * 100.0))
                : null;

        double avgBenchmark = equipmentRates.isEmpty() ? 0.0 : round(
                equipmentRates.stream()
                        .filter(dto -> dto.getHistoricalBenchmarkRate() != null)
                        .mapToDouble(EquipmentUtilizationRateDto::getHistoricalBenchmarkRate)
                        .average().orElse(0.0));
        Double trend = overallRate != null ? round(overallRate - avgBenchmark) : null;

        double sharedUsage = round(equipmentRates.stream().mapToDouble(EquipmentUtilizationRateDto::getSharedUsageHours).sum());
        double exclusiveUsage = round(equipmentRates.stream().mapToDouble(EquipmentUtilizationRateDto::getExclusiveUsageHours).sum());

        Double targetGap = (overallRate != null && targetUtilizationRate != null)
                ? round(overallRate - targetUtilizationRate)
                : null;

        return InstitutionUtilizationSummaryDto.builder()
                .institutionId(institutionId)
                .institutionName(institutionName)
                .departmentCount(deptIds.size())
                .equipmentCount(equipmentRates.size())
                .totalCapacityHours(round(totalAvailMins / 60.0))
                .actualUsageHours(round(totalUsedMins / 60.0))
                .totalAvailableMinutes(totalAvailMins)
                .actualUsageMinutes(totalUsedMins)
                .overallUtilizationRate(overallRate)
                .historicalBenchmarkRate(avgBenchmark)
                .trendPercentage(trend)
                .sharedUsageHours(sharedUsage)
                .exclusiveUsageHours(exclusiveUsage)
                .targetUtilizationRate(targetUtilizationRate)
                .targetGap(targetGap)
                .build();
    }

    // --- Demand Analysis ---
    public DemandAnalysisDto getDemandAnalysis(
            Long institutionId, Long departmentId, Long equipmentId,
            LocalDateTime start, LocalDateTime end) {

        final WindowParams window = resolveWindow(start, end, 24.0);
        List<Equipment> equipmentList = getEquipmentInScope(institutionId, departmentId, equipmentId);
        if (equipmentList.isEmpty()) {
            return DemandAnalysisDto.builder()
                    .totalRequests(0L).totalRequestedHours(0.0)
                    .fulfilledBookings(0L).fulfilledHours(0.0)
                    .rejectedBookings(0L).cancelledBookings(0L).noShowBookings(0L).pendingBookings(0L)
                    .rejectionRate(0.0).noShowRate(0.0).cancellationRate(0.0).fulfillmentRate(0.0)
                    .bookingRate(null).unmetDemandHours(0.0).peakContentionRatio(0.0)
                    .isHighDemand(false)
                    .build();
        }

        List<Long> equipmentIds = equipmentList.stream().map(Equipment::getId).toList();
        List<Booking> bookings = bookingRepository.findOverlappingBookings(
                equipmentIds, toUtcInstant(window.start), toUtcInstant(window.end));

        long totalRequests = bookings.size();
        double totalRequestedHours = 0.0;
        long fulfilled = 0;
        double fulfilledHours = 0.0;
        long rejected = 0;
        long cancelled = 0;
        long noShow = 0;
        long pending = 0;
        long completed = 0;
        long inUse = 0;
        double unmetHours = 0.0;

        for (Booking b : bookings) {
            LocalDateTime rawStart = toPlatformTime(b.getStartTime());
            LocalDateTime rawEnd = toPlatformTime(b.getEndTime());
            LocalDateTime effectiveStart = rawStart.isBefore(window.start) ? window.start : rawStart;
            LocalDateTime effectiveEnd = rawEnd.isAfter(window.end) ? window.end : rawEnd;
            double durationHours = effectiveEnd.isAfter(effectiveStart)
                    ? Duration.between(effectiveStart, effectiveEnd).toMinutes() / 60.0
                    : 0.0;

            totalRequestedHours += durationHours;
            String status = b.getStatus() != null ? b.getStatus().toUpperCase() : "PENDING";

            if (status.equals("COMPLETED")) {
                completed++;
                fulfilled++;
                fulfilledHours += durationHours;
            } else if (status.equals("IN_USE")) {
                inUse++;
                fulfilled++;
                fulfilledHours += durationHours;
            } else if (status.equals("CONFIRMED") || status.equals("APPROVED")) {
                fulfilled++;
                fulfilledHours += durationHours;
            } else if (status.equals("REJECTED")) {
                rejected++;
                unmetHours += durationHours;
            } else if (status.equals("CANCELLED")) {
                cancelled++;
                unmetHours += durationHours;
            } else if (status.equals("NO_SHOW")) {
                noShow++;
            } else if (status.equals("PENDING") || status.equals("PENDING_APPROVAL")) {
                pending++;
            }
        }

        // Rule 11: Exact rates
        double rejectionRate = totalRequests > 0 ? round(((double) rejected / totalRequests) * 100.0) : 0.0;
        long attendanceOpportunities = completed + inUse + noShow;
        double noShowRate = attendanceOpportunities > 0 ? round(((double) noShow / (double) attendanceOpportunities) * 100.0) : 0.0;
        double cancellationRate = totalRequests > 0 ? round(((double) cancelled / totalRequests) * 100.0) : 0.0;
        double fulfillmentRate = totalRequests > 0 ? round(((double) fulfilled / totalRequests) * 100.0) : 0.0;

        // Calculate booked minutes clipped to operating hours
        Map<Long, List<Booking>> bookingsByEquipment = bookings.stream().collect(Collectors.groupingBy(Booking::getEquipmentId));
        long totalBookedMinutes = 0L;
        long totalAvailableMinutes = 0L;
        for (Equipment eq : equipmentList) {
            totalAvailableMinutes += calculateAvailableMinutes(eq, window.start, window.end);
            List<Booking> eqBookings = bookingsByEquipment.getOrDefault(eq.getId(), Collections.emptyList());
            totalBookedMinutes += calculateBookedMinutes(eq, eqBookings, window.start, window.end);
        }

        Double bookingRate = totalAvailableMinutes > 0
                ? Math.min(100.0, round(((double) totalBookedMinutes / (double) totalAvailableMinutes) * 100.0))
                : null;

        // Peak contention ratio
        int totalCapacityUnits = equipmentList.stream().mapToInt(e -> e.getQuantity() != null ? e.getQuantity() : 1).sum();
        int maxConcurrentBookings = calculatePeakBookingConcurrency(bookings);
        double peakContention = totalCapacityUnits > 0
                ? round(((double) maxConcurrentBookings / totalCapacityUnits))
                : 0.0;

        // Rule 11: 80% high-demand threshold
        boolean highDemand = bookingRate != null && bookingRate >= 80.0;

        return DemandAnalysisDto.builder()
                .totalRequests(totalRequests)
                .totalRequestedHours(round(totalRequestedHours))
                .fulfilledBookings(fulfilled)
                .fulfilledHours(round(fulfilledHours))
                .rejectedBookings(rejected)
                .cancelledBookings(cancelled)
                .noShowBookings(noShow)
                .pendingBookings(pending)
                .rejectionRate(rejectionRate)
                .noShowRate(noShowRate)
                .cancellationRate(cancellationRate)
                .fulfillmentRate(fulfillmentRate)
                .bookingRate(bookingRate)
                .unmetDemandHours(round(unmetHours))
                .peakContentionRatio(peakContention)
                .isHighDemand(highDemand)
                .build();
    }

    private static final Set<String> COUNTED_BOOKING_STATUSES = Set.of(
            "CONFIRMED", "APPROVED", "IN_USE", "COMPLETED", "NO_SHOW"
    );

    public long calculateBookedMinutes(Equipment equipment, List<Booking> bookings, LocalDateTime windowStart, LocalDateTime windowEnd) {
        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart) || bookings == null || bookings.isEmpty()) {
            return 0L;
        }
        Map<Integer, DailySchedule> schedule = resolveSchedule(equipment);
        int quantity = equipment != null && equipment.getQuantity() != null && equipment.getQuantity() > 0 ? equipment.getQuantity() : 1;

        Map<LocalDateTime, Integer> eventPoints = new TreeMap<>();

        for (Booking b : bookings) {
            if (b == null || b.getStatus() == null) continue;
            String status = b.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!COUNTED_BOOKING_STATUSES.contains(status)) {
                continue;
            }
            LocalDateTime rawStart = toPlatformTime(b.getStartTime());
            LocalDateTime rawEnd = toPlatformTime(b.getEndTime());
            if (rawStart == null || rawEnd == null || !rawEnd.isAfter(rawStart)) continue;

            LocalDateTime effStart = rawStart.isBefore(windowStart) ? windowStart : rawStart;
            LocalDateTime effEnd = rawEnd.isAfter(windowEnd) ? windowEnd : rawEnd;
            if (!effEnd.isAfter(effStart)) continue;

            LocalDate startDay = effStart.toLocalDate();
            LocalDate endDay = effEnd.toLocalDate();

            for (LocalDate date = startDay; !date.isAfter(endDay); date = date.plusDays(1)) {
                DailySchedule daySchedule = schedule.get(date.getDayOfWeek().getValue());
                if (daySchedule == null || !daySchedule.isOpen()) continue;

                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

                LocalDateTime sliceStart = effStart.isAfter(dayStart) ? effStart : dayStart;
                LocalDateTime sliceEnd = effEnd.isBefore(dayEnd) ? effEnd : dayEnd;
                if (!sliceEnd.isAfter(sliceStart)) continue;

                LocalDateTime opStart = date.atTime(daySchedule.openTime());
                LocalDateTime opEnd = date.atTime(daySchedule.closeTime());

                LocalDateTime clipStart = sliceStart.isAfter(opStart) ? sliceStart : opStart;
                LocalDateTime clipEnd = sliceEnd.isBefore(opEnd) ? sliceEnd : opEnd;

                if (clipEnd.isAfter(clipStart)) {
                    eventPoints.merge(clipStart, 1, Integer::sum);
                    eventPoints.merge(clipEnd, -1, Integer::sum);
                }
            }
        }

        if (eventPoints.isEmpty()) {
            return 0L;
        }

        LocalDateTime prevTime = null;
        int activeCount = 0;
        long totalBookedMinutes = 0L;

        for (Map.Entry<LocalDateTime, Integer> entry : eventPoints.entrySet()) {
            LocalDateTime currTime = entry.getKey();
            if (prevTime != null && activeCount > 0) {
                long sliceMinutes = Duration.between(prevTime, currTime).toMinutes();
                int effectiveUnits = Math.min(activeCount, quantity);
                totalBookedMinutes += sliceMinutes * effectiveUnits;
            }
            activeCount += entry.getValue();
            prevTime = currTime;
        }

        return totalBookedMinutes;
    }

    // --- Idle Time Reports ---
    public List<IdleTimeReportDto> getIdleTimeReports(
            Long institutionId, Long departmentId,
            LocalDateTime start, LocalDateTime end, Double operatingHoursPerDay, Double thresholdPercent) {

        // Enforce the mandatory last 7 completed calendar days ending yesterday in platform timezone (Asia/Kolkata)
        LocalDate today = LocalDate.now(platformZoneId);
        LocalDate endCompleted = today.minusDays(1);
        LocalDate startCompleted = endCompleted.minusDays(6);
        LocalDateTime mandatoryStart = startCompleted.atStartOfDay();
        LocalDateTime mandatoryEnd = endCompleted.atTime(23, 59, 59);

        LocalDateTime resolvedStart;
        LocalDateTime resolvedEnd;

        // If either parameter is null, safely use the mandatory 7 completed days window (never a 30-day window)
        if (start == null || end == null) {
            resolvedStart = mandatoryStart;
            resolvedEnd = mandatoryEnd;
        } else {
            // For callers passing date windows (e.g. historical tests), ensure end never extends into today or future
            resolvedEnd = end.isAfter(mandatoryEnd) ? mandatoryEnd : end;
            resolvedStart = start.isAfter(resolvedEnd) ? mandatoryStart : start;
        }

        final WindowParams window = new WindowParams(resolvedStart, resolvedEnd, 7.0, 24.0);
        final double alertThreshold = thresholdPercent != null && thresholdPercent > 0 ? thresholdPercent : 90.0;

        List<Equipment> equipmentList = getEquipmentInScope(institutionId, departmentId, null).stream()
                .filter(eq -> eq.getStatus() != null && (
                        "AVAILABLE".equalsIgnoreCase(eq.getStatus().trim())
                        || "BOOKED".equalsIgnoreCase(eq.getStatus().trim())))
                .toList();

        if (equipmentList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> equipmentIds = equipmentList.stream().map(Equipment::getId).toList();
        List<Booking> bookings = Collections.emptyList();
        if (bookingRepository != null && !equipmentIds.isEmpty()) {
            bookings = bookingRepository.findOverlappingBookings(
                    equipmentIds, toUtcInstant(window.start), toUtcInstant(window.end));
        }
        Map<Long, List<Booking>> bookingsByEquipment = bookings.stream()
                .collect(Collectors.groupingBy(Booking::getEquipmentId));

        Map<Long, String> departmentNames = getDepartmentNames(equipmentList);
        List<IdleTimeReportDto> reports = new ArrayList<>();

        for (Equipment eq : equipmentList) {
            long availMins = calculateAvailableMinutes(eq, window.start, window.end);
            double operatingHours = round(availMins / 60.0);

            List<Booking> eqBookings = bookingsByEquipment.getOrDefault(eq.getId(), Collections.emptyList());
            long bookedMins = calculateBookedMinutes(eq, eqBookings, window.start, window.end);

            // Ensure idle minutes cannot become negative
            long idleMins = Math.max(0L, availMins - bookedMins);
            double idleHours = round(idleMins / 60.0);
            double idlePercentage = availMins > 0 ? Math.min(100.0, round(((double) idleMins / availMins) * 100.0)) : 0.0;

            LocalDateTime lastUsed = eqBookings.stream()
                    .filter(b -> b.getStatus() != null && COUNTED_BOOKING_STATUSES.contains(b.getStatus().trim().toUpperCase(Locale.ROOT)))
                    .map(b -> b.getEndTime() != null ? toPlatformTime(b.getEndTime()) : toPlatformTime(b.getStartTime()))
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            boolean isAlert = idlePercentage >= alertThreshold;
            String level = "NORMAL";
            String msg = "Utilization is within normal operational parameters.";

            if (idlePercentage >= 90.0) {
                level = "CRITICAL";
                msg = "Critical underutilization: idle rate exceeds 90%. Review resource sharing or reallocation.";
            } else if (idlePercentage >= alertThreshold) {
                level = "WARNING";
                msg = "High idle time: idle rate exceeds " + alertThreshold + "%.";
            }

            reports.add(IdleTimeReportDto.builder()
                    .equipmentId(eq.getId())
                    .equipmentName(eq.getName())
                    .assetTag(eq.getAssetTag())
                    .departmentId(eq.getDepartmentId())
                    .departmentName(eq.getDepartmentId() != null ? departmentNames.get(eq.getDepartmentId()) : "Unassigned")
                    .totalOperatingHours(operatingHours)
                    .actualUsageHours(round(bookedMins / 60.0))
                    .idleHours(idleHours)
                    .idlePercentage(idlePercentage)
                    .lastUsedAt(lastUsed)
                    .idleAlert(isAlert)
                    .alertLevel(level)
                    .alertMessage(msg)
                    .build());
        }

        reports.sort(Comparator.comparing(IdleTimeReportDto::getIdlePercentage).reversed());
        return reports;
    }

    // --- Peak Usage Patterns ---
    public PeakUsagePatternDto getPeakUsagePattern(
            Long institutionId, Long departmentId, Long equipmentId,
            LocalDateTime start, LocalDateTime end) {

        final WindowParams window = resolveWindow(start, end, 24.0);
        List<Equipment> equipmentList = getEquipmentInScope(institutionId, departmentId, equipmentId);
        int totalCapacityUnits = equipmentList.stream().mapToInt(e -> e.getQuantity() != null ? e.getQuantity() : 1).sum();

        if (equipmentList.isEmpty()) {
            return PeakUsagePatternDto.builder()
                    .timeSlots(Collections.emptyList())
                    .busiestDayOfWeek("NONE")
                    .busiestHourOfDay(0)
                    .maxPeakConcurrency(0)
                    .totalCapacityUnits(0)
                    .capacitySaturationRate(0.0)
                    .build();
        }

        List<Long> equipmentIds = equipmentList.stream().map(Equipment::getId).toList();
        List<Utilization> sessions = utilizationRepository.findOverlappingSessions(
                equipmentIds, toUtcInstant(window.start), toUtcInstant(window.end));

        double[] hourlyHours = new double[24];
        int[] hourlyPeakConcurrency = new int[24];
        int[] hourlySessionCount = new int[24];
        double[] dayOfWeekUsage = new double[8]; // 1..7 for Mon..Sun

        for (Utilization s : sessions) {
            LocalDateTime sStart = toPlatformTime(s.getStartTime());
            LocalDateTime sEnd = s.getEndTime() == null ? LocalDateTime.now(platformZoneId) : toPlatformTime(s.getEndTime());
            if (sStart == null || sEnd == null) continue;
            if (sStart.isBefore(window.start)) sStart = window.start;
            if (sEnd.isAfter(window.end)) sEnd = window.end;
            if (!sEnd.isAfter(sStart)) continue;

            LocalDateTime cursor = sStart;
            while (cursor.isBefore(sEnd)) {
                int hour = cursor.getHour();
                int dayOfWeek = cursor.getDayOfWeek().getValue();

                LocalDateTime nextHour = cursor.withMinute(0).withSecond(0).withNano(0).plusHours(1);
                LocalDateTime stepEnd = sEnd.isBefore(nextHour) ? sEnd : nextHour;

                double sliceHours = Duration.between(cursor, stepEnd).toMinutes() / 60.0;
                hourlyHours[hour] += sliceHours;
                hourlySessionCount[hour]++;
                dayOfWeekUsage[dayOfWeek] += sliceHours;

                cursor = stepEnd;
            }
        }

        for (int h = 0; h < 24; h++) {
            final int hour = h;
            List<Utilization> matchingInHour = sessions.stream().filter(s -> {
                LocalDateTime sStart = toPlatformTime(s.getStartTime());
                LocalDateTime sEnd = s.getEndTime() != null ? toPlatformTime(s.getEndTime()) : LocalDateTime.now(platformZoneId);
                return sStart != null && sEnd != null && sStart.getHour() <= hour && sEnd.getHour() >= hour;
            }).toList();
            hourlyPeakConcurrency[h] = calculatePeakUtilizationConcurrency(matchingInHour);
        }

        List<HourlyPeakSlotDto> slots = new ArrayList<>(24);
        int busiestHour = 0;
        double maxScore = -1.0;
        double maxUsage = -1.0;
        int maxPeak = 0;

        for (int h = 0; h < 24; h++) {
            double usage = round(hourlyHours[h]);
            if (hourlyPeakConcurrency[h] > maxPeak) {
                maxPeak = hourlyPeakConcurrency[h];
            }

            double totalSlotCapacity = window.days * totalCapacityUnits;
            double score = totalSlotCapacity > 0 ? Math.min(100.0, round((usage / totalSlotCapacity) * 100.0)) : 0.0;

            // Mentor Tie-Breaker: 1) Utilization percentage, 2) Used minutes/hours, 3) Earlier hour
            if (score > maxScore || (Math.abs(score - maxScore) < 0.0001 && usage > maxUsage)) {
                maxScore = score;
                maxUsage = usage;
                busiestHour = h;
            }

            slots.add(HourlyPeakSlotDto.builder()
                    .hour(h)
                    .hourLabel(String.format("%02d:00", h))
                    .totalUsageHours(usage)
                    .peakConcurrency(hourlyPeakConcurrency[h])
                    .averageConcurrency(window.days > 0 ? round(usage / window.days) : 0.0)
                    .utilizationScore(score)
                    .build());
        }

        // Busiest day of week with mentor tie-breakers:
        // 1) Utilization percentage, 2) Used minutes, 3) Earlier weekday (Mon=1 < Tue=2 ... < Sun=7)
        long[] dayAvailableMinutes = new long[8];
        for (LocalDate date = window.start.toLocalDate(); !date.isAfter(window.end.toLocalDate()); date = date.plusDays(1)) {
            int dow = date.getDayOfWeek().getValue();
            for (Equipment eq : equipmentList) {
                dayAvailableMinutes[dow] += calculateAvailableMinutes(eq, date.atStartOfDay(), date.atTime(23, 59, 59));
            }
        }

        int busiestDayIdx = 1;
        double maxDayPct = -1.0;
        double maxDayUsage = -1.0;
        for (int d = 1; d <= 7; d++) {
            double usedMins = dayOfWeekUsage[d] * 60.0;
            double availMins = dayAvailableMinutes[d];
            double dayPct = availMins > 0 ? Math.min(100.0, (usedMins / availMins) * 100.0) : 0.0;

            if (dayPct > maxDayPct || (Math.abs(dayPct - maxDayPct) < 0.0001 && usedMins > maxDayUsage)) {
                maxDayPct = dayPct;
                maxDayUsage = usedMins;
                busiestDayIdx = d;
            }
        }
        String busiestDayName = DayOfWeek.of(busiestDayIdx).getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase();

        double saturationRate = totalCapacityUnits > 0
                ? Math.min(100.0, round(((double) maxPeak / totalCapacityUnits) * 100.0))
                : 0.0;

        // Top 5 peak slots sorted by: 1) Utilization score DESC, 2) Usage hours DESC, 3) Earlier hour ASC
        List<HourlyPeakSlotDto> topFive = slots.stream()
                .sorted(Comparator.comparing(HourlyPeakSlotDto::getUtilizationScore, Comparator.reverseOrder())
                        .thenComparing(HourlyPeakSlotDto::getTotalUsageHours, Comparator.reverseOrder())
                        .thenComparing(HourlyPeakSlotDto::getHour))
                .limit(5)
                .toList();

        return PeakUsagePatternDto.builder()
                .timeSlots(slots)
                .topFivePeakSlots(topFive)
                .busiestDayOfWeek(busiestDayName)
                .busiestHourOfDay(busiestHour)
                .maxPeakConcurrency(maxPeak)
                .totalCapacityUnits(totalCapacityUnits)
                .capacitySaturationRate(saturationRate)
                .build();
    }

    public PeakUsagePatternDto getPeakUsagePatterns(
            Long institutionId, Long departmentId, Long equipmentId,
            LocalDateTime start, LocalDateTime end) {
        return getPeakUsagePattern(institutionId, departmentId, equipmentId, start, end);
    }

    // --- Heatmap Data ---
    public HeatmapDataDto getHeatmapData(
            Long institutionId, Long departmentId, Long equipmentId,
            LocalDateTime start, LocalDateTime end) {

        final WindowParams window = resolveWindow(start, end, 24.0);
        List<Equipment> equipmentList = getEquipmentInScope(institutionId, departmentId, equipmentId);
        int totalUnits = equipmentList.stream().mapToInt(e -> e.getQuantity() != null ? e.getQuantity() : 1).sum();

        if (equipmentList.isEmpty() || totalUnits == 0) {
            return HeatmapDataDto.builder()
                    .matrix(buildEmptyMatrix())
                    .averageUtilizationRate(0.0)
                    .totalUsageHours(0L)
                    .totalActiveBookings(0)
                    .build();
        }

        List<Long> equipmentIds = equipmentList.stream().map(Equipment::getId).toList();
        List<Utilization> sessions = utilizationRepository.findOverlappingSessions(
                equipmentIds, toUtcInstant(window.start), toUtcInstant(window.end));
        List<Booking> bookings = bookingRepository.findOverlappingBookings(
                equipmentIds, toUtcInstant(window.start), toUtcInstant(window.end));

        // Aggregate operating schedule across scoped equipment
        Map<Integer, DailySchedule> representativeSchedule = resolveSchedule(equipmentList.getFirst());

        long[][] cellMinutes = new long[8][24];
        int[][] cellBookingCounts = new int[8][24];

        for (Utilization s : sessions) {
            LocalDateTime sStart = toPlatformTime(s.getStartTime());
            LocalDateTime sEnd = s.getEndTime() == null ? LocalDateTime.now(platformZoneId) : toPlatformTime(s.getEndTime());
            if (sStart == null || sEnd == null) continue;
            if (sStart.isBefore(window.start)) sStart = window.start;
            if (sEnd.isAfter(window.end)) sEnd = window.end;
            if (!sEnd.isAfter(sStart)) continue;

            LocalDateTime cursor = sStart;
            while (cursor.isBefore(sEnd)) {
                int day = cursor.getDayOfWeek().getValue();
                int hour = cursor.getHour();

                LocalDateTime nextHour = cursor.withMinute(0).withSecond(0).withNano(0).plusHours(1);
                LocalDateTime stepEnd = sEnd.isBefore(nextHour) ? sEnd : nextHour;

                long mins = Duration.between(cursor, stepEnd).toMinutes();
                cellMinutes[day][hour] += mins;

                cursor = stepEnd;
            }
        }

        for (Booking b : bookings) {
            LocalDateTime bStart = toPlatformTime(b.getStartTime());
            LocalDateTime bEnd = toPlatformTime(b.getEndTime());
            if (bStart == null || bEnd == null) continue;
            if (bStart.isBefore(window.start)) bStart = window.start;
            if (bEnd.isAfter(window.end)) bEnd = window.end;
            if (!bEnd.isAfter(bStart)) continue;

            LocalDateTime cursor = bStart;
            while (cursor.isBefore(bEnd)) {
                int day = cursor.getDayOfWeek().getValue();
                int hour = cursor.getHour();
                cellBookingCounts[day][hour]++;
                cursor = cursor.withMinute(0).withSecond(0).withNano(0).plusHours(1);
            }
        }

        double daysPerWeekday = Math.max(1.0, window.days / 7.0);
        double cellCapacityMinutes = daysPerWeekday * 60.0 * totalUnits;

        List<HeatmapCellDto> matrix = new ArrayList<>(168);
        double totalUtilizationSum = 0.0;
        int openCellsCount = 0;
        long totalMins = 0;

        for (int d = 1; d <= 7; d++) {
            String dayName = DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            DailySchedule daySchedule = representativeSchedule.get(d);
            boolean dayIsOpen = daySchedule != null && daySchedule.isOpen();

            for (int h = 0; h < 24; h++) {
                boolean slotIsOpen = dayIsOpen
                        && daySchedule.openTime().getHour() <= h
                        && daySchedule.closeTime().getHour() > h;

                long usageMins = cellMinutes[d][h];
                totalMins += usageMins;

                Double score;
                int intensity = 0;

                if (!slotIsOpen) {
                    // Mentor Requirement: Closed slots return null; open unused return 0%
                    score = null;
                    intensity = 0;
                } else {
                    openCellsCount++;
                    score = cellCapacityMinutes > 0
                            ? Math.min(100.0, round(((double) usageMins / cellCapacityMinutes) * 100.0))
                            : 0.0;
                    totalUtilizationSum += score;

                    if (score > 75.0) intensity = 4;
                    else if (score > 50.0) intensity = 3;
                    else if (score > 25.0) intensity = 2;
                    else if (score > 0.0) intensity = 1;
                }

                matrix.add(HeatmapCellDto.builder()
                        .dayOfWeek(d)
                        .dayName(dayName)
                        .hour(h)
                        .usageMinutes(usageMins)
                        .utilizationScore(score)
                        .intensityLevel(intensity)
                        .bookingCount(cellBookingCounts[d][h])
                        .build());
            }
        }

        double avgRate = openCellsCount > 0 ? round(totalUtilizationSum / (double) openCellsCount) : 0.0;

        return HeatmapDataDto.builder()
                .matrix(matrix)
                .averageUtilizationRate(avgRate)
                .totalUsageHours(totalMins / 60)
                .totalActiveBookings(bookings.size())
                .build();
    }

    // --- Helpers ---
    private List<Equipment> getEquipmentInScope(Long institutionId, Long departmentId, Long equipmentId) {
        if (equipmentId != null) {
            return equipmentRepository.findById(equipmentId).stream()
                    .filter(eq -> institutionId == null || Objects.equals(eq.getInstitutionId(), institutionId))
                    .filter(eq -> departmentId == null || Objects.equals(eq.getDepartmentId(), departmentId))
                    .toList();
        }
        if (departmentId != null) {
            return equipmentRepository.findAll().stream()
                    .filter(eq -> (institutionId == null || Objects.equals(eq.getInstitutionId(), institutionId))
                            && Objects.equals(eq.getDepartmentId(), departmentId))
                    .toList();
        }
        if (institutionId != null) {
            return equipmentRepository.findAll().stream()
                    .filter(eq -> Objects.equals(eq.getInstitutionId(), institutionId))
                    .toList();
        }
        return equipmentRepository.findAll();
    }

    private Map<Long, String> getDepartmentNames(List<Equipment> equipmentList) {
        Set<Long> deptIds = equipmentList.stream()
                .map(Equipment::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (deptIds.isEmpty()) return Collections.emptyMap();
        return departmentRepository.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    private int calculatePeakUtilizationConcurrency(List<Utilization> sessions) {
        List<Endpoint> endpoints = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(platformZoneId);
        for (Utilization s : sessions) {
            LocalDateTime start = toPlatformTime(s.getStartTime());
            LocalDateTime end = s.getEndTime() != null ? toPlatformTime(s.getEndTime()) : now;
            if (start != null && end != null && end.isAfter(start)) {
                endpoints.add(new Endpoint(start, true));
                endpoints.add(new Endpoint(end, false));
            }
        }
        endpoints.sort((a, b) -> {
            int cmp = a.time.compareTo(b.time);
            return cmp != 0 ? cmp : (a.isStart ? 1 : -1);
        });

        int peak = 0;
        int active = 0;
        for (Endpoint ep : endpoints) {
            if (ep.isStart) {
                active++;
                if (active > peak) peak = active;
            } else {
                active--;
            }
        }
        return peak;
    }

    private int calculatePeakBookingConcurrency(List<Booking> bookings) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (Booking b : bookings) {
            LocalDateTime start = toPlatformTime(b.getStartTime());
            LocalDateTime end = toPlatformTime(b.getEndTime());
            if (start != null && end != null && end.isAfter(start)) {
                endpoints.add(new Endpoint(start, true));
                endpoints.add(new Endpoint(end, false));
            }
        }
        endpoints.sort((a, b) -> {
            int cmp = a.time.compareTo(b.time);
            return cmp != 0 ? cmp : (a.isStart ? 1 : -1);
        });

        int peak = 0;
        int active = 0;
        for (Endpoint ep : endpoints) {
            if (ep.isStart) {
                active++;
                if (active > peak) peak = active;
            } else {
                active--;
            }
        }
        return peak;
    }

    private List<HeatmapCellDto> buildEmptyMatrix() {
        List<HeatmapCellDto> list = new ArrayList<>(168);
        for (int d = 1; d <= 7; d++) {
            String dayName = DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            for (int h = 0; h < 24; h++) {
                list.add(HeatmapCellDto.builder()
                        .dayOfWeek(d)
                        .dayName(dayName)
                        .hour(h)
                        .usageMinutes(0L)
                        .utilizationScore(0.0)
                        .intensityLevel(0)
                        .bookingCount(0)
                        .build());
            }
        }
        return list;
    }

    private WindowParams resolveWindow(LocalDateTime start, LocalDateTime end, Double operatingHoursPerDay) {
        LocalDateTime resolvedEnd = end != null ? end : LocalDateTime.now(platformZoneId);
        LocalDateTime resolvedStart = start != null ? start : resolvedEnd.minusDays(30);
        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new IllegalArgumentException("Start date must not be after end date");
        }
        if (Duration.between(resolvedStart, resolvedEnd).toDays() > 90) {
            throw new IllegalArgumentException("Custom date range must not exceed 90 days");
        }
        double operatingHours = operatingHoursPerDay != null && operatingHoursPerDay > 0 && operatingHoursPerDay <= 24.0
                ? operatingHoursPerDay
                : 24.0;
        double days = Math.max(1.0, Duration.between(resolvedStart, resolvedEnd).toMinutes() / 1440.0);
        return new WindowParams(resolvedStart, resolvedEnd, days, operatingHours);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record WindowParams(LocalDateTime start, LocalDateTime end, double days, double operatingHoursPerDay) {}
    private record Endpoint(LocalDateTime time, boolean isStart) {}
}
