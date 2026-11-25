package com.labresource.platform.notification.entity;

import com.labresource.platform.notification.model.DeliveryChannel;
import com.labresource.platform.notification.model.DeliveryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_deliveries", uniqueConstraints = {
        @UniqueConstraint(name = "uq_notification_delivery_channel", columnNames = {"notification_id", "channel"})
})
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public NotificationDelivery(Long notificationId, DeliveryChannel channel, DeliveryStatus status) {
        this.notificationId = notificationId;
        this.channel = channel;
        this.status = status;
        this.attempts = 0;
        this.createdAt = Instant.now();
    }
}
