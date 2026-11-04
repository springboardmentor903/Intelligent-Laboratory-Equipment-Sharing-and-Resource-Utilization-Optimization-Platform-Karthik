package com.labresource.platform.booking.waitlist.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "equipment_waitlist", indexes = {
        @Index(name = "ix_waitlist_equipment_status", columnList = "equipment_id, status")
})
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "requested_start_time", nullable = false)
    private LocalDateTime requestedStartTime;

    @Column(name = "requested_end_time", nullable = false)
    private LocalDateTime requestedEndTime;

    @Column(nullable = false)
    private Integer priority = 1;

    @Column(nullable = false, length = 30)
    private String status = "WAITING"; // WAITING, OFFERED, CONVERTED, CANCELLED, EXPIRED

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public WaitlistEntry(Long equipmentId, Long userId, LocalDateTime start, LocalDateTime end, String notes) {
        this.equipmentId = equipmentId;
        this.userId = userId;
        this.requestedStartTime = start;
        this.requestedEndTime = end;
        this.notes = notes;
        this.status = "WAITING";
        this.priority = 1;
        this.createdAt = LocalDateTime.now();
    }
}
