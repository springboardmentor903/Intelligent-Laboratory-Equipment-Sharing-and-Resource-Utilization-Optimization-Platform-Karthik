package com.labresource.platform.notification.service;

import com.labresource.platform.analytics.dto.IdleTimeReportDto;
import com.labresource.platform.analytics.service.UtilizationAnalyticsService;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.notification.entity.ReminderDeduplicationLog;
import com.labresource.platform.notification.model.NotificationType;
import com.labresource.platform.notification.model.ReferenceType;
import com.labresource.platform.notification.repository.ReminderDeduplicationLogRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.List;

@Service
public class NotificationScheduledService {
    private static final Logger log = LoggerFactory.getLogger(NotificationScheduledService.class);
    public static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BookingRepository bookingRepository;
    private final CalibrationRecordRepository calibrationRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final NotificationService notificationService;
    private final ReminderDeduplicationLogRepository dedupRepository;
    private final UtilizationAnalyticsService analyticsService;

    public NotificationScheduledService(BookingRepository bookingRepository,
                                        CalibrationRecordRepository calibrationRepository,
                                        EquipmentRepository equipmentRepository,
                                        UserRepository userRepository,
                                        InstitutionRepository institutionRepository,
                                        NotificationService notificationService,
                                        ReminderDeduplicationLogRepository dedupRepository,
                                        @Autowired(required = false) UtilizationAnalyticsService analyticsService) {
        this.bookingRepository = bookingRepository;
        this.calibrationRepository = calibrationRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.institutionRepository = institutionRepository;
        this.notificationService = notificationService;
        this.dedupRepository = dedupRepository;
        this.analyticsService = analyticsService;
    }

    /**
     * Booking reminder job: runs every 5 minutes and notifies booking owners 60 minutes before start.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void sendBookingReminders() {
        LocalDateTime now = LocalDateTime.now(PLATFORM_ZONE);
        // Look ahead 55 to 65 minutes
        LocalDateTime windowStart = now.plusMinutes(55);
        LocalDateTime windowEnd = now.plusMinutes(65);

        List<Booking> upcoming = bookingRepository.findOverlappingBookingsForMaintenance(
                null, List.of("CONFIRMED", "APPROVED"), windowStart, windowEnd);

        // Also query direct time between if equipmentId is not specified
        if (upcoming.isEmpty()) {
            upcoming = bookingRepository.findByStatusInAndStartTimeLessThanEqual(
                    List.of("CONFIRMED", "APPROVED"), windowEnd).stream()
                    .filter(b -> b.getStartTime() != null && !b.getStartTime().isBefore(windowStart))
                    .toList();
        }

        for (Booking booking : upcoming) {
            try {
                String dedupKey = "BOOKING_REMINDER:" + booking.getId();
                if (dedupRepository.existsByDedupKey(dedupKey)) {
                    continue;
                }

                Equipment eq = equipmentRepository.findById(booking.getEquipmentId()).orElse(null);
                String eqName = eq != null ? eq.getName() : "Equipment #" + booking.getEquipmentId();
                String formattedStart = booking.getStartTime().format(TIME_FMT);

                notificationService.createNotification(
                        booking.getUserId(),
                        NotificationType.BOOKING_REMINDER,
                        "Upcoming Booking Reminder",
                        "Your booking for " + eqName + " starts in approximately 60 minutes at " + formattedStart + " (" + PLATFORM_ZONE.getId() + ").",
                        ReferenceType.BOOKING,
                        booking.getId()
                );

                dedupRepository.save(new ReminderDeduplicationLog(dedupKey));
                log.info("Sent booking reminder for booking {}", booking.getId());
            } catch (Exception ex) {
                log.error("Failed to send booking reminder for booking {}: {}", booking.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Calibration expiry job: runs daily at 6:00 AM Asia/Kolkata and notifies Lab Managers once per expired record.
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void sendCalibrationExpiryAlerts() {
        LocalDate today = LocalDate.now(PLATFORM_ZONE);
        List<CalibrationRecord> expired = calibrationRepository.findByNextDueDateBeforeAndStatusNot(today.plusDays(1), "COMPLETED");

        for (CalibrationRecord record : expired) {
            try {
                Long equipmentId = record.getEquipment() != null ? record.getEquipment().getId() : null;
                if (equipmentId == null) continue;

                String dueDateStr = record.getNextDueDate() != null ? record.getNextDueDate().toString() : today.toString();
                String dedupKey = "CALIBRATION_EXPIRED:" + equipmentId + ":" + dueDateStr;
                if (dedupRepository.existsByDedupKey(dedupKey)) {
                    continue;
                }

                Equipment eq = record.getEquipment();
                Long institutionId = eq.getInstitutionId();
                String eqName = eq.getName();

                List<User> labManagers = findLabManagers(institutionId);
                for (User manager : labManagers) {
                    notificationService.createNotification(
                            manager.getId(),
                            NotificationType.CALIBRATION_EXPIRED,
                            "Calibration Expired Alert",
                            "Calibration for equipment '" + eqName + "' (Tag: " + (eq.getAssetTag() != null ? eq.getAssetTag() : "—") + ") expired on " + dueDateStr + ". Please schedule calibration.",
                            ReferenceType.CALIBRATION,
                            record.getId()
                    );
                }

                dedupRepository.save(new ReminderDeduplicationLog(dedupKey));
                log.info("Sent calibration expired alert for equipment {}", equipmentId);
            } catch (Exception ex) {
                log.error("Failed to process calibration alert for record {}: {}", record.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Idle equipment alert job: runs daily at 6:30 AM Asia/Kolkata and notifies Lab Managers at most once every 7 days per idle equipment.
     */
    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void sendIdleEquipmentAlerts() {
        if (analyticsService == null) return;

        LocalDate today = LocalDate.now(PLATFORM_ZONE);
        int year = today.getYear();
        int week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        var institutions = institutionRepository.findAll();
        for (var institution : institutions) {
            try {
                List<IdleTimeReportDto> reports = analyticsService.getIdleTimeReports(
                        institution.getId(), null, null, null, null, 90.0);

                for (IdleTimeReportDto report : reports) {
                    if (!Boolean.TRUE.equals(report.getIdleAlert())) continue;

                    Long equipmentId = report.getEquipmentId();
                    String dedupKey = "IDLE_ALERT:" + equipmentId + ":" + year + "-W" + week;
                    if (dedupRepository.existsByDedupKey(dedupKey)) {
                        continue;
                    }

                    List<User> labManagers = findLabManagers(institution.getId());
                    String eqName = report.getEquipmentName() != null ? report.getEquipmentName() : "Equipment #" + equipmentId;
                    String message = String.format("Equipment '%s' has been idle for %.1f%% of available operating time (Idle: %.1fh). Consider listing for inter-institution sharing.",
                            eqName, report.getIdlePercentage(), report.getIdleHours());

                    for (User manager : labManagers) {
                        notificationService.createNotification(
                                manager.getId(),
                                NotificationType.IDLE_EQUIPMENT_ALERT,
                                "High Equipment Idle Time Alert",
                                message,
                                ReferenceType.EQUIPMENT,
                                equipmentId
                        );
                    }

                    dedupRepository.save(new ReminderDeduplicationLog(dedupKey));
                    log.info("Sent idle alert for equipment {}", equipmentId);
                }
            } catch (Exception ex) {
                log.error("Failed to process idle alerts for institution {}: {}", institution.getId(), ex.getMessage());
            }
        }
    }

    private List<User> findLabManagers(Long institutionId) {
        if (institutionId == null) {
            return userRepository.findByRequestedRoleAndMembershipStatus("LAB_MANAGER", "APPROVED");
        }
        return userRepository.findByInstitutionIdAndRequestedRoleAndMembershipStatus(institutionId, "LAB_MANAGER", "APPROVED");
    }
}
