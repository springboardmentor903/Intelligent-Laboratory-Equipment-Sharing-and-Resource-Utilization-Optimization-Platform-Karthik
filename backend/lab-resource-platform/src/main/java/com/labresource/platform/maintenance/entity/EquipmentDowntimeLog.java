package com.labresource.platform.maintenance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "equipment_downtime_logs", indexes = {
        @Index(name = "idx_edl_equipment", columnList = "equipment_id"),
        @Index(name = "idx_edl_work_order", columnList = "work_order_id"),
        @Index(name = "idx_edl_start_time", columnList = "start_time")
})
public class EquipmentDowntimeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "work_order_id")
    private Long workOrderId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "expected_end_time")
    private LocalDateTime expectedEndTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "total_downtime_minutes")
    private Integer totalDowntimeMinutes;

    @Column(name = "operating_downtime_minutes")
    private Integer operatingDowntimeMinutes;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
