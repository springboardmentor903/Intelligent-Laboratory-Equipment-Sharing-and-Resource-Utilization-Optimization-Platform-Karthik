package com.labresource.platform.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_audit_logs", indexes = {
        @Index(name = "ix_booking_audit_booking_id", columnList = "booking_id"),
        @Index(name = "ix_booking_audit_occurred_at", columnList = "occurred_at")
})
public class BookingAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;

    @Column(name = "actor_email", length = 180)
    private String actorEmail;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    public BookingAuditLog(Long bookingId, String eventType, String previousStatus, String newStatus, String actorEmail) {
        this.bookingId = bookingId;
        this.eventType = eventType;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.actorEmail = actorEmail;
        this.occurredAt = LocalDateTime.now();
    }
}
