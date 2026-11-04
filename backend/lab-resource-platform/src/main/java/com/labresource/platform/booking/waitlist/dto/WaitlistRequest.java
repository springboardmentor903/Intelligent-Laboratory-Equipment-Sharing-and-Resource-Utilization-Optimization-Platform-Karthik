package com.labresource.platform.booking.waitlist.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistRequest {
    private Long equipmentId;
    private LocalDateTime requestedStartTime;
    private LocalDateTime requestedEndTime;
    private String notes;
}
