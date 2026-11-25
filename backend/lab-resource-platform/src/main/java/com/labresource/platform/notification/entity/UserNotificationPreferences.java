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
@Table(name = "user_notification_preferences")
public class UserNotificationPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled = false;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UserNotificationPreferences(Long userId) {
        this.userId = userId;
        this.emailEnabled = true;
        this.smsEnabled = false;
        this.pushEnabled = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
