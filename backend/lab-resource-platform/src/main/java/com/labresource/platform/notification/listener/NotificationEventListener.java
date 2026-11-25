package com.labresource.platform.notification.listener;

import com.labresource.platform.booking.event.BookingMaintenanceCancelledEvent;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.notification.model.NotificationType;
import com.labresource.platform.notification.model.ReferenceType;
import com.labresource.platform.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final EquipmentRepository equipmentRepository;

    public NotificationEventListener(NotificationService notificationService,
                                     EquipmentRepository equipmentRepository) {
        this.notificationService = notificationService;
        this.equipmentRepository = equipmentRepository;
    }

    @EventListener
    public void onBookingMaintenanceCancelled(BookingMaintenanceCancelledEvent event) {
        log.info("Received BookingMaintenanceCancelledEvent for booking: {}", event.bookingId());
        try {
            Equipment eq = equipmentRepository.findById(event.equipmentId()).orElse(null);
            String eqName = eq != null ? eq.getName() : "Equipment #" + event.equipmentId();

            notificationService.createNotification(
                    event.userId(),
                    NotificationType.BOOKING_CANCELLED,
                    "Booking Cancelled Due to Maintenance",
                    "Your booking #" + event.bookingId() + " for " + eqName + " scheduled from " + event.startTime()
                            + " has been cancelled because the equipment is undergoing maintenance (Work Order #" + event.workOrderId() + ").",
                    ReferenceType.BOOKING,
                    event.bookingId()
            );
        } catch (Exception ex) {
            log.error("Failed to process BookingMaintenanceCancelledEvent: {}", ex.getMessage(), ex);
        }
    }
}
