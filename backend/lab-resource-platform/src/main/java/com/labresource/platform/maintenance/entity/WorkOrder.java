package com.labresource.platform.maintenance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "work_orders", indexes = {
        @Index(name = "idx_wo_number", columnList = "work_order_number", unique = true),
        @Index(name = "idx_wo_equipment", columnList = "equipment_id"),
        @Index(name = "idx_wo_assigned_to", columnList = "assigned_to"),
        @Index(name = "idx_wo_status", columnList = "status"),
        @Index(name = "idx_wo_type", columnList = "work_order_type"),
        @Index(name = "idx_wo_schedule", columnList = "schedule_id")
})
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_number", nullable = false, unique = true, length = 50)
    private String workOrderNumber; // WO-YYYY-XXXXXX

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "work_order_type", nullable = false, length = 30)
    private String workOrderType = "CORRECTIVE"; // PREVENTIVE, CORRECTIVE

    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(nullable = false, length = 30)
    private String status = "OPEN"; // OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "expected_end_time")
    private LocalDateTime expectedEndTime;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "downtime_hours")
    private Double downtimeHours;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Transient
    private String equipmentName;

    @Transient
    private String assetTag;

    @Transient
    private Long institutionId;

    @Transient
    private String assignedTechnicianName;

    @Transient
    private String assignedTechnicianEmail;

    @Transient
    private String creatorName;
}
