package com.labresource.platform.notification.service;

import com.labresource.platform.equipment.websocket.EquipmentWebSocketHandler;
import com.labresource.platform.notification.broker.NotificationDeliveryEvent;
import com.labresource.platform.notification.dto.NotificationDTO;
import com.labresource.platform.notification.dto.NotificationPreferencesDTO;
import com.labresource.platform.notification.dto.RegisterDeviceTokenRequest;
import com.labresource.platform.notification.dto.UpdatePreferencesRequest;
import com.labresource.platform.notification.entity.Notification;
import com.labresource.platform.notification.entity.PushDeviceToken;
import com.labresource.platform.notification.entity.UserNotificationPreferences;
import com.labresource.platform.notification.model.NotificationType;
import com.labresource.platform.notification.model.ReferenceType;
import com.labresource.platform.notification.repository.NotificationRepository;
import com.labresource.platform.notification.repository.PushDeviceTokenRepository;
import com.labresource.platform.notification.repository.UserNotificationPreferencesRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EquipmentWebSocketHandler webSocketHandler;

    public NotificationService(NotificationRepository notificationRepository,
                               UserNotificationPreferencesRepository preferencesRepository,
                               PushDeviceTokenRepository pushDeviceTokenRepository,
                               UserRepository userRepository,
                               ApplicationEventPublisher eventPublisher,
                               @Autowired(required = false) EquipmentWebSocketHandler webSocketHandler) {
        this.notificationRepository = notificationRepository;
        this.preferencesRepository = preferencesRepository;
        this.pushDeviceTokenRepository = pushDeviceTokenRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Always generates an in-app notification inside the SAME transaction as the triggering operation.
     */
    @Transactional
    public Notification createNotification(Long recipientId, NotificationType type, String title,
                                           String message, ReferenceType referenceType, Long referenceId) {
        if (recipientId == null) return null;

        Notification notification = new Notification(recipientId, type, title, message, referenceType, referenceId);
        notification = notificationRepository.save(notification);

        // Publish event for delivery (transactional event listener dispatches after commit)
        eventPublisher.publishEvent(new NotificationDeliveryEvent(notification.getId()));

        // Push real-time unread count update
        pushUnreadCount(recipientId);

        log.debug("Created notification {} for user {}", notification.getId(), recipientId);
        return notification;
    }

    @Transactional
    public List<Notification> createNotifications(Collection<Long> recipientIds, NotificationType type,
                                                  String title, String message, ReferenceType referenceType, Long referenceId) {
        if (recipientIds == null || recipientIds.isEmpty()) return Collections.emptyList();

        return recipientIds.stream()
                .distinct()
                .map(id -> createNotification(id, type, title, message, referenceType, referenceId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<NotificationDTO> getNotifications(Long userId, Boolean unreadOnly, NotificationType type, Pageable pageable) {
        Page<Notification> page;
        if (Boolean.TRUE.equals(unreadOnly)) {
            if (type != null) {
                page = notificationRepository.findByRecipientIdAndReadAndNotificationType(userId, false, type, pageable);
            } else {
                page = notificationRepository.findByRecipientIdAndRead(userId, false, pageable);
            }
        } else {
            if (type != null) {
                page = notificationRepository.findByRecipientIdAndNotificationType(userId, type, pageable);
            } else {
                page = notificationRepository.findByRecipientId(userId, pageable);
            }
        }
        return page.map(NotificationDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationDTO markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
            pushUnreadCount(userId);
        }

        return NotificationDTO.fromEntity(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
        pushUnreadCount(userId);
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesDTO getPreferences(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean hasPhone = user.getPhoneNumber() != null && !user.getPhoneNumber().trim().isBlank();
        UserNotificationPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> new UserNotificationPreferences(userId));

        return NotificationPreferencesDTO.fromEntity(preferences, hasPhone);
    }

    @Transactional
    public NotificationPreferencesDTO updatePreferences(Long userId, UpdatePreferencesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean hasPhone = user.getPhoneNumber() != null && !user.getPhoneNumber().trim().isBlank();

        // Enforce phone requirement if enabling SMS
        if (Boolean.TRUE.equals(request.smsEnabled()) && !hasPhone) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A registered phone number is required to enable SMS notifications. Please update your profile.");
        }

        UserNotificationPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> new UserNotificationPreferences(userId));

        if (request.emailEnabled() != null) {
            preferences.setEmailEnabled(request.emailEnabled());
        }
        if (request.smsEnabled() != null) {
            preferences.setSmsEnabled(request.smsEnabled());
        }
        if (request.pushEnabled() != null) {
            preferences.setPushEnabled(request.pushEnabled());
        }
        preferences.setUpdatedAt(Instant.now());
        preferences = preferencesRepository.save(preferences);

        return NotificationPreferencesDTO.fromEntity(preferences, hasPhone);
    }

    @Transactional
    public void registerDeviceToken(Long userId, RegisterDeviceTokenRequest request) {
        if (request.token() == null || request.token().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device token cannot be empty");
        }

        String token = request.token().trim();
        String platform = request.platform() != null && !request.platform().trim().isBlank()
                ? request.platform().trim() : "WEB";

        PushDeviceToken deviceToken = pushDeviceTokenRepository.findByUserIdAndToken(userId, token)
                .orElseGet(() -> new PushDeviceToken(userId, token, platform));

        deviceToken.setPlatform(platform);
        deviceToken.setLastUsedAt(Instant.now());
        pushDeviceTokenRepository.save(deviceToken);
    }

    @Transactional
    public void deleteDeviceToken(Long userId, String token) {
        if (token == null || token.trim().isBlank()) return;
        pushDeviceTokenRepository.deleteByUserIdAndToken(userId, token.trim());
    }

    private void pushUnreadCount(Long userId) {
        if (webSocketHandler != null) {
            long count = notificationRepository.countByRecipientIdAndReadFalse(userId);
            webSocketHandler.sendUnreadCountUpdate(userId, count);
        }
    }
}
