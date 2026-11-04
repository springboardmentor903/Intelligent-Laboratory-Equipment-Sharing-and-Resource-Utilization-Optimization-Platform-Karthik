package com.labresource.platform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandAnalysisDto {
    private Long totalRequests;
    private Double totalRequestedHours;
    private Long fulfilledBookings;
    private Double fulfilledHours;
    private Long rejectedBookings;
    private Long cancelledBookings;
    private Long noShowBookings;
    private Long pendingBookings;
    private Double rejectionRate; // percentage
    private Double noShowRate; // percentage
    private Double cancellationRate; // percentage
    private Double fulfillmentRate; // percentage
    private Double bookingRate; // booked minutes / available operating minutes %
    private Double unmetDemandHours; // requested hours from rejected/cancelled bookings
    private Double peakContentionRatio; // peak requested concurrent bookings / capacity units
    private Boolean isHighDemand; // true if bookingRate >= 80.0% or contention >= 1.0
}
