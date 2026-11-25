package com.labresource.platform.notification;

import com.labresource.platform.analytics.dto.IdleTimeReportDto;
import com.labresource.platform.analytics.service.UtilizationAnalyticsService;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.notification.entity.ReminderDeduplicationLog;
import com.labresource.platform.notification.model.NotificationType;
import com.labresource.platform.notification.model.ReferenceType;
import com.labresource.platform.notification.repository.ReminderDeduplicationLogRepository;
import com.labresource.platform.notification.service.NotificationScheduledService;
import com.labresource.platform.notification.service.NotificationService;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationScheduledServiceTests {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CalibrationRecordRepository calibrationRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ReminderDeduplicationLogRepository dedupRepository;

    @Mock
    private UtilizationAnalyticsService analyticsService;

    private NotificationScheduledService scheduledService;

    @BeforeEach
    void setUp() {
        scheduledService = new NotificationScheduledService(
                bookingRepository,
                calibrationRepository,
                equipmentRepository,
                userRepository,
                institutionRepository,
                notificationService,
                dedupRepository,
                analyticsService
        );
    }

    @Test
    void sendBookingReminders_createsNotificationAndPreventsDuplicate() {
        Booking b = new Booking();
        b.setId(201L);
        b.setUserId(10L);
        b.setEquipmentId(5L);
        b.setStatus("CONFIRMED");
        b.setStartTime(LocalDateTime.now(NotificationScheduledService.PLATFORM_ZONE).plusMinutes(60));

        Equipment eq = new Equipment();
        eq.setId(5L);
        eq.setName("HPLC Analyzer");

        when(bookingRepository.findOverlappingBookingsForMaintenance(isNull(), anyList(), any(), any()))
                .thenReturn(List.of(b));
        when(dedupRepository.existsByDedupKey("BOOKING_REMINDER:201")).thenReturn(false);
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(eq));

        scheduledService.sendBookingReminders();

        verify(notificationService).createNotification(
                eq(10L),
                eq(NotificationType.BOOKING_REMINDER),
                eq("Upcoming Booking Reminder"),
                contains("HPLC Analyzer"),
                eq(ReferenceType.BOOKING),
                eq(201L)
        );
        verify(dedupRepository).save(any(ReminderDeduplicationLog.class));

        // When dedupKey already exists on subsequent run
        reset(notificationService);
        when(dedupRepository.existsByDedupKey("BOOKING_REMINDER:201")).thenReturn(true);

        scheduledService.sendBookingReminders();
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendCalibrationExpiryAlerts_notifiesLabManagersAndPreventsDuplicate() {
        Equipment eq = new Equipment();
        eq.setId(5L);
        eq.setName("Gas Chromatograph");
        eq.setInstitutionId(1L);

        CalibrationRecord cr = new CalibrationRecord();
        cr.setId(88L);
        cr.setEquipment(eq);
        cr.setNextDueDate(LocalDate.now(NotificationScheduledService.PLATFORM_ZONE).minusDays(2));
        cr.setStatus("EXPIRED");

        User mgr = new User();
        mgr.setId(15L);
        mgr.setEmail("manager@mrec.ac.in");

        when(calibrationRepository.findByNextDueDateBeforeAndStatusNot(any(), eq("COMPLETED")))
                .thenReturn(List.of(cr));
        when(dedupRepository.existsByDedupKey(startsWith("CALIBRATION_EXPIRED:5:"))).thenReturn(false);
        when(userRepository.findByInstitutionIdAndRequestedRoleAndMembershipStatus(1L, "LAB_MANAGER", "APPROVED"))
                .thenReturn(List.of(mgr));

        scheduledService.sendCalibrationExpiryAlerts();

        verify(notificationService).createNotification(
                eq(15L),
                eq(NotificationType.CALIBRATION_EXPIRED),
                eq("Calibration Expired Alert"),
                contains("Gas Chromatograph"),
                eq(ReferenceType.CALIBRATION),
                eq(88L)
        );
        verify(dedupRepository).save(any(ReminderDeduplicationLog.class));
    }
}
