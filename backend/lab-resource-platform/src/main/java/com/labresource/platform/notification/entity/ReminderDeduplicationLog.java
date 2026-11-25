package com.labresource.platform.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reminder_deduplication_logs")
public class ReminderDeduplicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dedup_key", nullable = false, unique = true, length = 255)
    private String dedupKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ReminderDeduplicationLog(String dedupKey) {
        this.dedupKey = dedupKey;
        this.createdAt = Instant.now();
    }
}
