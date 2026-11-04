package com.labresource.platform.booking.waitlist.dto;

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
public class SchedulingRecommendationDto {
    private Long requestedEquipmentId;
    private String requestedEquipmentName;
    private Boolean isAvailableAtRequestedTime;
    private List<TimeSlotRecommendation> alternativeTimeSlots;
    private List<AlternativeEquipmentRecommendation> alternativeEquipment;
    private List<TimeSlotRecommendation> quietTimeSlots;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSlotRecommendation {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String reason; // e.g., "Adjacent slot on same day with 0 conflicts"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlternativeEquipmentRecommendation {
        private Long equipmentId;
        private String equipmentName;
        private String assetTag;
        private String category;
        private Integer quantity;
        private Double currentUtilizationRate;
        private String recommendationReason;
    }
}
