package com.labresource.platform.maintenance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "preventive_maintenance_schedules", indexes = {
        @Index(name = "idx_pms_equipment", columnList = "equipment_id"),
        @Index(name = "idx_pms_next_due", columnList = "next_due_date"),
        @Index(name = "idx_pms_active", columnList = "active")
})
public class PreventiveMaintenanceSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "repeat_period_days", nullable = false)
    private Integer repeatPeriodDays;

    @Column(name = "last_completed_date")
    private LocalDate lastCompletedDate;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Transient
    private String status; // OK, DUE_SOON, OVERDUE, INACTIVE

    @Transient
    private String equipmentName;

    @Transient
    private String assetTag;

    @Transient
    private Long institutionId;
}
