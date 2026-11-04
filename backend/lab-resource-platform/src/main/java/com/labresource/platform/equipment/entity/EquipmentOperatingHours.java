package com.labresource.platform.equipment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "equipment_operating_hours")
public class EquipmentOperatingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek; // 1 = Monday, ..., 7 = Sunday (matching java.time.DayOfWeek.getValue())

    @Builder.Default
    @Column(name = "open_time", nullable = false)
    private LocalTime openTime = LocalTime.of(9, 0);

    @Builder.Default
    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime = LocalTime.of(17, 0);

    @Builder.Default
    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed = false;

    public EquipmentOperatingHours(Long equipmentId, Long institutionId, Integer dayOfWeek, LocalTime openTime, LocalTime closeTime, Boolean isClosed) {
        this.equipmentId = equipmentId;
        this.institutionId = institutionId;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime != null ? openTime : LocalTime.of(9, 0);
        this.closeTime = closeTime != null ? closeTime : LocalTime.of(17, 0);
        this.isClosed = isClosed != null ? isClosed : false;
    }
}
