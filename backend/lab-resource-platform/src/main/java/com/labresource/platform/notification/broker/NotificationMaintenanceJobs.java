package com.labresource.platform.notification.broker;

import com.labresource.platform.notification.entity.Notification;
import com.labresource.platform.notification.entity.NotificationDelivery;
import com.labresource.platform.notification.entity.UserNotificationPreferences;
import com.labresource.platform.notification.model.DeliveryStatus;
import com.labresource.platform.notification.repository.NotificationDeliveryRepository;
import com.labresource.platform.notification.repository.NotificationRepository;
import com.labresource.platform.notification.repository.UserNotificationPreferencesRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class NotificationMaintenanceJobs {
    private static final Logger log = LoggerFactory.getLogger(NotificationMaintenanceJobs.class);

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;
    private final NotificationDeliveryConsumer consumer;

    public NotificationMaintenanceJobs(NotificationRepository notificationRepository,
                                       NotificationDeliveryRepository deliveryRepository,
                                       UserNotificationPreferencesRepository preferencesRepository,
                                       UserRepository userRepository,
                                       NotificationDeliveryConsumer consumer) {
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.preferencesRepository = preferencesRepository;
        this.userRepository = userRepository;
        this.consumer = consumer;
    }

    /**
     * Retry job every 5 minutes: retries FAILED deliveries up to a maximum of 3 attempts.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void retryFailedDeliveries() {
        log.debug("Running notification delivery retry job");
        List<NotificationDelivery> failed = deliveryRepository.findByStatusAndAttemptsLessThan(DeliveryStatus.FAILED, 3);
        if (failed.isEmpty()) return;

        log.info("Found {} failed notification deliveries to retry", failed.size());
        for (NotificationDelivery delivery : failed) {
            try {
                Notification notification = notificationRepository.findById(delivery.getNotificationId()).orElse(null);
                if (notification == null) continue;

                User recipient = userRepository.findById(notification.getRecipientId()).orElse(null);
                if (recipient == null) continue;

                UserNotificationPreferences prefs = preferencesRepository.findByUserId(recipient.getId())
                        .orElseGet(() -> new UserNotificationPreferences(recipient.getId()));

                switch (delivery.getChannel()) {
                    case EMAIL -> consumer.deliverEmail(notification, recipient, prefs);
                    case SMS -> consumer.deliverSms(notification, recipient, prefs);
                    case PUSH -> consumer.deliverPush(notification, recipient, prefs);
                }
            } catch (Exception ex) {
                log.error("Exception during retry of delivery {}: {}", delivery.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Pickup job every 1 minute: republishes notifications older than 2 minutes if no delivery record exists.
     */
    @Scheduled(fixedDelay = 60000)
    public void pickupOrphanNotifications() {
        Instant cutoff = Instant.now().minus(2, ChronoUnit.MINUTES);
        List<Notification> orphans = notificationRepository.findOrphanNotificationsOlderThan(cutoff);
        if (orphans.isEmpty()) return;

        log.info("Found {} orphan notifications older than 2 minutes without delivery records", orphans.size());
        for (Notification orphan : orphans) {
            try {
                consumer.processDelivery(orphan.getId());
            } catch (Exception ex) {
                log.error("Failed to pickup orphan notification {}: {}", orphan.getId(), ex.getMessage());
            }
        }
    }
}
