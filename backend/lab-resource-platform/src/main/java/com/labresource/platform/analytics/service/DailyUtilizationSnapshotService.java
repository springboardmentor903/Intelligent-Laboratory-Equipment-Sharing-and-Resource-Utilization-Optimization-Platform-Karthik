package com.labresource.platform.analytics.service;

import com.labresource.platform.analytics.entity.DailyUtilizationSnapshot;
import com.labresource.platform.analytics.repository.DailyUtilizationSnapshotRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.entity.EquipmentOperatingHours;
import com.labresource.platform.equipment.repository.EquipmentOperatingHoursRepository;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Slf4j
@Service
public class DailyUtilizationSnapshotService {

    private final DailyUtilizationSnapshotRepository snapshotRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentOperatingHoursRepository operatingHoursRepository;
    private final UtilizationRepository utilizationRepository;
    private final ZoneId platformZoneId;

    public DailyUtilizationSnapshotService(
            DailyUtilizationSnapshotRepository snapshotRepository,
            EquipmentRepository equipmentRepository,
            EquipmentOperatingHoursRepository operatingHoursRepository,
            UtilizationRepository utilizationRepository,
            @Value("${app.platform.timezone:Asia/Kolkata}") String platformTimezone) {
        this.snapshotRepository = snapshotRepository;
        this.equipmentRepository = equipmentRepository;
        this.operatingHoursRepository = operatingHoursRepository;
        this.utilizationRepository = utilizationRepository;
        ZoneId zone;
        try {
            zone = ZoneId.of(platformTimezone);
        } catch (Exception ex) {
            zone = ZoneId.of("Asia/Kolkata");
        }
        this.platformZoneId = zone;
    }

    /**
     * Daily snapshot run at 00:30 in platform timezone.
     */
    @Scheduled(cron = "${app.analytics.snapshot-cron:0 30 0 * * *}")
    @Transactional
    public void runDailySnapshot() {
        LocalDate yesterday = LocalDate.now(platformZoneId).minusDays(1);
        log.info("Starting daily utilization snapshot for date: {}", yesterday);
        captureSnapshotsForDate(yesterday);
        log.info("Daily utilization snapshot completed for date: {}", yesterday);
    }

    /**
     * Backfills historical daily snapshots up to 90 days.
     */
    @Transactional
    public int backfillSnapshots(int days) {
        int boundedDays = Math.min(Math.max(1, days), 90);
        LocalDate today = LocalDate.now(platformZoneId);
        int totalCreated = 0;
        for (int i = boundedDays; i >= 1; i--) {
            LocalDate targetDate = today.minusDays(i);
            totalCreated += captureSnapshotsForDate(targetDate);
        }
        return totalCreated;
    }

    @Transactional
    public int captureSnapshotsForDate(LocalDate date) {
        List<Equipment> allEquipment = equipmentRepository.findAll();
        int savedCount = 0;

        for (Equipment eq : allEquipment) {
            DailyUtilizationSnapshot snapshot = computeEquipmentDaySnapshot(eq, date);
            if (snapshot != null) {
                // Upsert behavior: update if already exists for equipment and date
                Optional<DailyUtilizationSnapshot> existing = snapshotRepository
                        .findBySnapshotDateAndEquipmentId(date, eq.getId());
                if (existing.isPresent()) {
                    DailyUtilizationSnapshot current = existing.get();
                    current.setOperatingMinutes(snapshot.getOperatingMinutes());
                    current.setUsedMinutes(snapshot.getUsedMinutes());
                    current.setUtilizationRate(snapshot.getUtilizationRate());
                    current.setSessionCount(snapshot.getSessionCount());
                    snapshotRepository.save(current);
                } else {
                    snapshotRepository.save(snapshot);
                }
                savedCount++;
            }
        }
        return savedCount;
    }

    public DailyUtilizationSnapshot computeEquipmentDaySnapshot(Equipment eq, LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        DailySchedule schedule = resolveScheduleForEquipment(eq, dayOfWeek);

        int totalUnits = eq.getQuantity() != null && eq.getQuantity() > 0 ? eq.getQuantity() : 1;
        int operatingMinutes = 0;

        if (schedule.isOpen()) {
            operatingMinutes = (int) Duration.between(schedule.openTime(), schedule.closeTime()).toMinutes() * totalUnits;
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(LocalTime.MAX);
        LocalDateTime utcStart = toUtcInstant(dayStart);
        LocalDateTime utcEnd = toUtcInstant(dayEnd);

        List<Utilization> sessions = utilizationRepository.findOverlappingSessions(
                List.of(eq.getId()), utcStart, utcEnd);

        int usedMinutes = 0;
        int sessionCount = sessions.size();

        if (schedule.isOpen()) {
            LocalDateTime windowOpen = date.atTime(schedule.openTime());
            LocalDateTime windowClose = date.atTime(schedule.closeTime());

            for (Utilization session : sessions) {
                LocalDateTime sStart = toPlatformTime(session.getStartTime());
                LocalDateTime sEnd = session.getEndTime() != null ? toPlatformTime(session.getEndTime()) : dayEnd;
                if (sStart == null || sEnd == null) continue;

                LocalDateTime clippedStart = sStart.isBefore(windowOpen) ? windowOpen : sStart;
                LocalDateTime clippedEnd = sEnd.isAfter(windowClose) ? windowClose : sEnd;

                if (clippedEnd.isAfter(clippedStart)) {
                    usedMinutes += (int) Duration.between(clippedStart, clippedEnd).toMinutes();
                }
            }
        }

        Double utilizationRate = null;
        if (operatingMinutes > 0) {
            double rate = ((double) usedMinutes / (double) operatingMinutes) * 100.0;
            utilizationRate = Math.round(Math.min(100.0, rate) * 10.0) / 10.0;
        }

        return DailyUtilizationSnapshot.builder()
                .snapshotDate(date)
                .equipmentId(eq.getId())
                .institutionId(eq.getInstitutionId())
                .departmentId(eq.getDepartmentId())
                .operatingMinutes(operatingMinutes)
                .usedMinutes(usedMinutes)
                .utilizationRate(utilizationRate)
                .sessionCount(sessionCount)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private DailySchedule resolveScheduleForEquipment(Equipment equipment, int dayOfWeek) {
        List<EquipmentOperatingHours> customRows = operatingHoursRepository
                .findByEquipmentIdOrderByDayOfWeekAsc(equipment.getId());

        if (customRows.isEmpty() && equipment.getInstitutionId() != null) {
            customRows = operatingHoursRepository
                    .findByInstitutionIdAndEquipmentIdIsNullOrderByDayOfWeekAsc(equipment.getInstitutionId());
        }

        if (customRows.isEmpty()) {
            // Default Mon-Fri 09:00 - 17:00, Sat-Sun closed
            if (dayOfWeek >= 1 && dayOfWeek <= 5) {
                return new DailySchedule(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
            } else {
                return new DailySchedule(false, LocalTime.of(9, 0), LocalTime.of(17, 0));
            }
        }

        Optional<EquipmentOperatingHours> match = customRows.stream()
                .filter(r -> r.getDayOfWeek() == dayOfWeek)
                .findFirst();

        if (match.isEmpty() || Boolean.TRUE.equals(match.get().getIsClosed())) {
            return new DailySchedule(false, LocalTime.of(9, 0), LocalTime.of(17, 0));
        }

        EquipmentOperatingHours row = match.get();
        LocalTime open = row.getOpenTime() != null ? row.getOpenTime() : LocalTime.of(9, 0);
        LocalTime close = row.getCloseTime() != null ? row.getCloseTime() : LocalTime.of(17, 0);
        return new DailySchedule(true, open, close);
    }

    private LocalDateTime toPlatformTime(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return null;
        return utcDateTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(platformZoneId)
                .toLocalDateTime();
    }

    private LocalDateTime toUtcInstant(LocalDateTime platformDateTime) {
        if (platformDateTime == null) return null;
        return platformDateTime.atZone(platformZoneId)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    public record DailySchedule(boolean isOpen, LocalTime openTime, LocalTime closeTime) {}
}
