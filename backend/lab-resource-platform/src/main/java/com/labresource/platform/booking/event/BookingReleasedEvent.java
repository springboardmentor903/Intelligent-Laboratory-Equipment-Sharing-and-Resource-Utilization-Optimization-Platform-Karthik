package com.labresource.platform.booking.event;

import java.time.LocalDateTime;

/**
 * Event fired when an equipment time slot is released due to cancellation,
 * early check-out, or no-show.
 */
public record BookingReleasedEvent(Long equipmentId, LocalDateTime startTime, LocalDateTime endTime) {
}
