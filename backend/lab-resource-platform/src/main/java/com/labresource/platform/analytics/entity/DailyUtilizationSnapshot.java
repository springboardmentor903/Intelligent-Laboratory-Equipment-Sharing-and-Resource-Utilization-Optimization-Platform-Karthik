package com.labresource.platform.analytics.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_utilization_snapshots", uniqueConstraints = {
        @UniqueConstraint(name = "ux_snapshot_date_equipment", columnNames = {"snapshot_date", "equipment_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyUtilizationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "operating_minutes", nullable = false)
    @Builder.Default
    private Integer operatingMinutes = 0;

    @Column(name = "used_minutes", nullable = false)
    @Builder.Default
    private Integer usedMinutes = 0;

    @Column(name = "utilization_rate")
    private Double utilizationRate;

    @Column(name = "session_count", nullable = false)
    @Builder.Default
    private Integer sessionCount = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
