package com.labresource.platform.notification.repository;

import com.labresource.platform.notification.entity.NotificationDelivery;
import com.labresource.platform.notification.model.DeliveryChannel;
import com.labresource.platform.notification.model.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    Optional<NotificationDelivery> findByNotificationIdAndChannel(Long notificationId, DeliveryChannel channel);

    List<NotificationDelivery> findByNotificationId(Long notificationId);

    List<NotificationDelivery> findByStatusAndAttemptsLessThan(DeliveryStatus status, int maxAttempts);

    long countByStatusAndCreatedAtAfter(DeliveryStatus status, java.time.Instant after);

    boolean existsByNotificationIdAndChannel(Long notificationId, DeliveryChannel channel);
}
