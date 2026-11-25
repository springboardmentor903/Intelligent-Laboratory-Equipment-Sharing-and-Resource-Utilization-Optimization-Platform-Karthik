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
@Table(name = "maintenance_requests", indexes = {
        @Index(name = "idx_maint_req_equipment", columnList = "equipment_id"),
        @Index(name = "idx_maint_req_requester", columnList = "requester_id"),
        @Index(name = "idx_maint_req_institution", columnList = "institution_id"),
        @Index(name = "idx_maint_req_status", columnList = "status")
})
public class MaintenanceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_remarks", length = 500)
    private String reviewRemarks;

    @Column(name = "work_order_id")
    private Long workOrderId;

    @Transient
    private String equipmentName;

    @Transient
    private String assetTag;

    @Transient
    private String requesterName;

    @Transient
    private String requesterEmail;

    @Transient
    private String reviewerName;

    @Transient
    private String workOrderNumber;
}
