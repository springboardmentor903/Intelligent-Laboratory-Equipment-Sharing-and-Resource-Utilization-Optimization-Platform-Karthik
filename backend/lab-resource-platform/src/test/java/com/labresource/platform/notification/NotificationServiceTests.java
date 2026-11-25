package com.labresource.platform.notification;

import com.labresource.platform.equipment.websocket.EquipmentWebSocketHandler;
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
import com.labresource.platform.notification.service.NotificationService;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserNotificationPreferencesRepository preferencesRepository;

    @Mock
    private PushDeviceTokenRepository pushDeviceTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EquipmentWebSocketHandler webSocketHandler;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                preferencesRepository,
                pushDeviceTokenRepository,
                userRepository,
                eventPublisher,
                webSocketHandler
        );
    }

    @Test
    void createNotification_persistsInAppNotificationAndPushesWebSocketUpdate() {
        Long recipientId = 101L;
        Notification saved = new Notification(recipientId, NotificationType.BOOKING_CONFIRMED,
                "Title", "Message", ReferenceType.BOOKING, 1L);
        saved.setId(50L);

        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(notificationRepository.countByRecipientIdAndReadFalse(recipientId)).thenReturn(3L);

        Notification result = notificationService.createNotification(
                recipientId,
                NotificationType.BOOKING_CONFIRMED,
                "Title",
                "Message",
                ReferenceType.BOOKING,
                1L
        );

        assertNotNull(result);
        assertEquals(50L, result.getId());
        verify(notificationRepository).save(any(Notification.class));
        verify(eventPublisher).publishEvent(any(com.labresource.platform.notification.broker.NotificationDeliveryEvent.class));
        verify(webSocketHandler).sendUnreadCountUpdate(recipientId, 3L);
    }

    @Test
    void markAsRead_enforcesOwnershipAndUpdatesUnreadCount() {
        Long userId = 101L;
        Long notificationId = 50L;
        Notification n = new Notification(userId, NotificationType.BOOKING_CONFIRMED, "T", "M", ReferenceType.BOOKING, 1L);
        n.setId(notificationId);
        n.setRead(false);

        when(notificationRepository.findByIdAndRecipientId(notificationId, userId)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
        when(notificationRepository.countByRecipientIdAndReadFalse(userId)).thenReturn(0L);

        NotificationDTO dto = notificationService.markAsRead(userId, notificationId);

        assertTrue(dto.read());
        verify(webSocketHandler).sendUnreadCountUpdate(userId, 0L);
    }

    @Test
    void markAsRead_throwsNotFound_whenNotificationDoesNotBelongToUser() {
        when(notificationRepository.findByIdAndRecipientId(50L, 999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                notificationService.markAsRead(999L, 50L));
    }

    @Test
    void updatePreferences_rejectsEnablingSmsWithoutPhoneNumber() {
        Long userId = 101L;
        User userWithoutPhone = new User();
        userWithoutPhone.setId(userId);
        userWithoutPhone.setPhoneNumber(null); // No phone number!

        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithoutPhone));

        UpdatePreferencesRequest request = new UpdatePreferencesRequest(true, true, false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                notificationService.updatePreferences(userId, request));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("registered phone number is required"));
    }

    @Test
    void registerDeviceToken_updatesLastUsedAtWhenTokenExists() {
        Long userId = 101L;
        PushDeviceToken existing = new PushDeviceToken(userId, "token-123", "WEB");
        existing.setId(1L);
        Instant before = existing.getLastUsedAt();

        when(pushDeviceTokenRepository.findByUserIdAndToken(userId, "token-123"))
                .thenReturn(Optional.of(existing));

        notificationService.registerDeviceToken(userId, new RegisterDeviceTokenRequest("token-123", "ANDROID"));

        verify(pushDeviceTokenRepository).save(existing);
        assertEquals("ANDROID", existing.getPlatform());
    }
}
