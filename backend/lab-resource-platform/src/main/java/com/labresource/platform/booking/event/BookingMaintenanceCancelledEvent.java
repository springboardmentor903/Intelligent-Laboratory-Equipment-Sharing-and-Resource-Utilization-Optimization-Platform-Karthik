package com.labresource.platform.booking.event;

import java.time.LocalDateTime;

/**
 * Event fired when a booking is cancelled due to equipment maintenance downtime.
 * Consumed by notification listeners in Session 4.
 */
public record BookingMaintenanceCancelledEvent(
        Long bookingId,
        Long equipmentId,
        Long userId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String reason,
        Long workOrderId
) {}
