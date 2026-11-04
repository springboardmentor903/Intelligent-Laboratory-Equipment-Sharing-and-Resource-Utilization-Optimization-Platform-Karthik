package com.labresource.platform.booking.waitlist.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurringBookingRequest {
    private Long equipmentId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String recurrencePattern; // DAILY, WEEKLY, BIWEEKLY, MONTHLY
    private Integer occurrences = 4; // default 4 occurrences
    private String purpose;
    private Boolean requireAllOrNothing = true;

    public RecurringBookingRequest(Long equipmentId, LocalDateTime startTime, LocalDateTime endTime, String recurrencePattern, Integer occurrences, String purpose) {
        this.equipmentId = equipmentId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.recurrencePattern = recurrencePattern;
        this.occurrences = occurrences;
        this.purpose = purpose;
        this.requireAllOrNothing = true;
    }

    public Boolean isRequireAllOrNothing() {
        return requireAllOrNothing != null ? requireAllOrNothing : true;
    }

    public void setRecurrenceRule(String rule) {
        this.recurrencePattern = rule;
    }

    public void setRecurrenceCount(Integer count) {
        this.occurrences = count;
    }
}
