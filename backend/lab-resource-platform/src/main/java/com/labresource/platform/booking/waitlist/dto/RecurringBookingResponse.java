package com.labresource.platform.booking.waitlist.dto;

import com.labresource.platform.booking.entity.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringBookingResponse {
    private List<Booking> createdBookings;
    private List<LocalDateTime> conflictedSlots;
    private Integer successCount;
    private Integer conflictCount;
    private String summary;
}
