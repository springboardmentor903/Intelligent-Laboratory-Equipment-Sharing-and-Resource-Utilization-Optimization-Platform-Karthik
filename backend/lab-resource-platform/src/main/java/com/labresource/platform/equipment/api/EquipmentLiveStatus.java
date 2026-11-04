package com.labresource.platform.equipment.api;

import java.time.LocalDateTime;

public record EquipmentLiveStatus(
        Long id,
        String name,
        String assetTag,
        String status,
        boolean inUse,
        LocalDateTime currentUsageStartTime,
        LocalDateTime scheduledBookingEndTime,
        Long elapsedUsageSeconds,
        BookingWindow nextConfirmedBooking,
        ManagerUsageDetails managerUsageDetails
) {
    public EquipmentLiveStatus withoutManagerUsageDetails() {
        return new EquipmentLiveStatus(
                id, name, assetTag, status, inUse, currentUsageStartTime,
                scheduledBookingEndTime, elapsedUsageSeconds,
                nextConfirmedBooking, null);
    }

    public record BookingWindow(Long bookingId, LocalDateTime startTime, LocalDateTime endTime) {
    }

    public record ManagerUsageDetails(Long usageId, String usageType, String remarks) {
    }
}
