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
@Table(name = "push_device_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_device_token", columnNames = {"user_id", "token"})
})
public class PushDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(nullable = false, length = 64)
    private String platform;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public PushDeviceToken(Long userId, String token, String platform) {
        this.userId = userId;
        this.token = token;
        this.platform = platform != null ? platform : "WEB";
        this.lastUsedAt = Instant.now();
        this.createdAt = Instant.now();
    }
}
