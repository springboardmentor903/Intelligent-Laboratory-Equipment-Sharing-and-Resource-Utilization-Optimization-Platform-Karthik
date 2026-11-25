package com.labresource.platform.notification.broker;

import com.labresource.platform.notification.channel.EmailNotificationSender;
import com.labresource.platform.notification.channel.PushNotificationSender;
import com.labresource.platform.notification.channel.SmsNotificationSender;
import com.labresource.platform.notification.entity.Notification;
import com.labresource.platform.notification.entity.NotificationDelivery;
import com.labresource.platform.notification.entity.PushDeviceToken;
import com.labresource.platform.notification.entity.UserNotificationPreferences;
import com.labresource.platform.notification.model.DeliveryChannel;
import com.labresource.platform.notification.model.DeliveryStatus;
import com.labresource.platform.notification.repository.NotificationDeliveryRepository;
import com.labresource.platform.notification.repository.NotificationRepository;
import com.labresource.platform.notification.repository.PushDeviceTokenRepository;
import com.labresource.platform.notification.repository.UserNotificationPreferencesRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationDeliveryConsumer {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryConsumer.class);

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final UserRepository userRepository;
    private final EmailNotificationSender emailSender;
    private final SmsNotificationSender smsSender;
    private final PushNotificationSender pushSender;

    public NotificationDeliveryConsumer(NotificationRepository notificationRepository,
                                        NotificationDeliveryRepository deliveryRepository,
                                        UserNotificationPreferencesRepository preferencesRepository,
                                        PushDeviceTokenRepository pushDeviceTokenRepository,
                                        UserRepository userRepository,
                                        EmailNotificationSender emailSender,
                                        SmsNotificationSender smsSender,
                                        PushNotificationSender pushSender) {
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.preferencesRepository = preferencesRepository;
        this.pushDeviceTokenRepository = pushDeviceTokenRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.pushSender = pushSender;
    }

    @Async
    @Transactional
    public void processDelivery(Long notificationId) {
        if (notificationId == null) return;

        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            log.warn("Notification {} not found for delivery", notificationId);
            return;
        }

        User recipient = userRepository.findById(notification.getRecipientId()).orElse(null);
        if (recipient == null) {
            log.warn("Recipient user {} not found for notification {}", notification.getRecipientId(), notificationId);
            return;
        }

        UserNotificationPreferences preferences = preferencesRepository.findByUserId(recipient.getId())
                .orElseGet(() -> new UserNotificationPreferences(recipient.getId()));

        deliverEmail(notification, recipient, preferences);
        deliverSms(notification, recipient, preferences);
        deliverPush(notification, recipient, preferences);
    }

    public void deliverEmail(Notification notification, User recipient, UserNotificationPreferences preferences) {
        NotificationDelivery delivery = deliveryRepository
                .findByNotificationIdAndChannel(notification.getId(), DeliveryChannel.EMAIL)
                .orElseGet(() -> new NotificationDelivery(notification.getId(), DeliveryChannel.EMAIL, DeliveryStatus.PENDING));

        // Idempotency: skip if already sent or skipped
        if (delivery.getStatus() == DeliveryStatus.SENT || delivery.getStatus() == DeliveryStatus.SKIPPED) {
            return;
        }

        if (!preferences.isEmailEnabled()) {
            delivery.setStatus(DeliveryStatus.SKIPPED);
            delivery.setErrorMessage("Email disabled in preferences");
            deliveryRepository.save(delivery);
            return;
        }

        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastAttemptAt(Instant.now());
        try {
            emailSender.sendEmail(notification, recipient);
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setErrorMessage(null);
        } catch (Exception ex) {
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setErrorMessage(ex.getMessage());
            log.warn("Failed email delivery attempt {} for notification {}: {}",
                    delivery.getAttempts(), notification.getId(), ex.getMessage());
        }
        deliveryRepository.save(delivery);
    }

    public void deliverSms(Notification notification, User recipient, UserNotificationPreferences preferences) {
        NotificationDelivery delivery = deliveryRepository
                .findByNotificationIdAndChannel(notification.getId(), DeliveryChannel.SMS)
                .orElseGet(() -> new NotificationDelivery(notification.getId(), DeliveryChannel.SMS, DeliveryStatus.PENDING));

        if (delivery.getStatus() == DeliveryStatus.SENT || delivery.getStatus() == DeliveryStatus.SKIPPED) {
            return;
        }

        if (!preferences.isSmsEnabled()) {
            delivery.setStatus(DeliveryStatus.SKIPPED);
            delivery.setErrorMessage("SMS disabled in preferences");
            deliveryRepository.save(delivery);
            return;
        }

        if (recipient.getPhoneNumber() == null || recipient.getPhoneNumber().isBlank()) {
            delivery.setStatus(DeliveryStatus.SKIPPED);
            delivery.setErrorMessage("Missing phone number");
            deliveryRepository.save(delivery);
            return;
        }

        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastAttemptAt(Instant.now());
        try {
            smsSender.sendSms(notification, recipient);
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setErrorMessage(null);
        } catch (Exception ex) {
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setErrorMessage(ex.getMessage());
            log.warn("Failed SMS delivery attempt {} for notification {}: {}",
                    delivery.getAttempts(), notification.getId(), ex.getMessage());
        }
        deliveryRepository.save(delivery);
    }

    public void deliverPush(Notification notification, User recipient, UserNotificationPreferences preferences) {
        NotificationDelivery delivery = deliveryRepository
                .findByNotificationIdAndChannel(notification.getId(), DeliveryChannel.PUSH)
                .orElseGet(() -> new NotificationDelivery(notification.getId(), DeliveryChannel.PUSH, DeliveryStatus.PENDING));

        if (delivery.getStatus() == DeliveryStatus.SENT || delivery.getStatus() == DeliveryStatus.SKIPPED) {
            return;
        }

        if (!preferences.isPushEnabled()) {
            delivery.setStatus(DeliveryStatus.SKIPPED);
            delivery.setErrorMessage("Push disabled in preferences");
            deliveryRepository.save(delivery);
            return;
        }

        List<PushDeviceToken> tokens = pushDeviceTokenRepository.findByUserId(recipient.getId());
        if (tokens.isEmpty()) {
            delivery.setStatus(DeliveryStatus.SKIPPED);
            delivery.setErrorMessage("No active device token");
            deliveryRepository.save(delivery);
            return;
        }

        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastAttemptAt(Instant.now());
        try {
            pushSender.sendPush(notification, tokens);
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setErrorMessage(null);
        } catch (Exception ex) {
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setErrorMessage(ex.getMessage());
            log.warn("Failed Push delivery attempt {} for notification {}: {}",
                    delivery.getAttempts(), notification.getId(), ex.getMessage());
        }
        deliveryRepository.save(delivery);
    }
}
