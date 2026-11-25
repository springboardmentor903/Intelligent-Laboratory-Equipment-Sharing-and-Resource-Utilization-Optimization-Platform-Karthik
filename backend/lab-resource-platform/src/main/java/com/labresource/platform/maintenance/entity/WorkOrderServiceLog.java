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
@Table(name = "work_order_service_logs", indexes = {
        @Index(name = "idx_wosl_work_order", columnList = "work_order_id"),
        @Index(name = "idx_wosl_created_at", columnList = "created_at")
})
public class WorkOrderServiceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "log_type", nullable = false, length = 50)
    private String logType; // STATUS_CHANGE, NOTE, EXTENSION, ASSIGNMENT

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(nullable = false, length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private String userName;

    @Transient
    private String userEmail;
}
