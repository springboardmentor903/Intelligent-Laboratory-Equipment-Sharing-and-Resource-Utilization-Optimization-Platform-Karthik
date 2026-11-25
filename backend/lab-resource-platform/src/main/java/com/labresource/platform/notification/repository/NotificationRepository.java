package com.labresource.platform.notification.repository;

import com.labresource.platform.notification.entity.Notification;
import com.labresource.platform.notification.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndRead(Long recipientId, boolean read, Pageable pageable);

    Page<Notification> findByRecipientIdAndNotificationType(Long recipientId, NotificationType notificationType, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadAndNotificationType(Long recipientId, boolean read, NotificationType notificationType, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientId = :recipientId AND n.read = false")
    int markAllAsRead(@Param("recipientId") Long recipientId);

    @Query("SELECT n FROM Notification n WHERE n.createdAt < :cutoff AND NOT EXISTS (SELECT d FROM NotificationDelivery d WHERE d.notificationId = n.id)")
    List<Notification> findOrphanNotificationsOlderThan(@Param("cutoff") Instant cutoff);
}
