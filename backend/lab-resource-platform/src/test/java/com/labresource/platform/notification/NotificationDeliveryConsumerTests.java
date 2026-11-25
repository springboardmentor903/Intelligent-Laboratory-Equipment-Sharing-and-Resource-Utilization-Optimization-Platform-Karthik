package com.labresource.platform.notification;

import com.labresource.platform.notification.broker.NotificationDeliveryConsumer;
import com.labresource.platform.notification.channel.EmailNotificationSender;
import com.labresource.platform.notification.channel.PushNotificationSender;
import com.labresource.platform.notification.channel.SmsNotificationSender;
import com.labresource.platform.notification.entity.Notification;
import com.labresource.platform.notification.entity.NotificationDelivery;
import com.labresource.platform.notification.entity.PushDeviceToken;
import com.labresource.platform.notification.entity.UserNotificationPreferences;
import com.labresource.platform.notification.model.DeliveryChannel;
import com.labresource.platform.notification.model.DeliveryStatus;
import com.labresource.platform.notification.model.NotificationType;
import com.labresource.platform.notification.model.ReferenceType;
import com.labresource.platform.notification.repository.NotificationDeliveryRepository;
import com.labresource.platform.notification.repository.NotificationRepository;
import com.labresource.platform.notification.repository.PushDeviceTokenRepository;
import com.labresource.platform.notification.repository.UserNotificationPreferencesRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryConsumerTests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private UserNotificationPreferencesRepository preferencesRepository;

    @Mock
    private PushDeviceTokenRepository pushDeviceTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailNotificationSender emailSender;

    @Mock
    private SmsNotificationSender smsSender;

    @Mock
    private PushNotificationSender pushSender;

    private NotificationDeliveryConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationDeliveryConsumer(
                notificationRepository,
                deliveryRepository,
                preferencesRepository,
                pushDeviceTokenRepository,
                userRepository,
                emailSender,
                smsSender,
                pushSender
        );
    }

    @Test
    void processDelivery_idempotentWhenDeliveryAlreadySent() {
        Long notificationId = 10L;
        Notification n = new Notification(1L, NotificationType.BOOKING_CONFIRMED, "T", "M", ReferenceType.BOOKING, 1L);
        n.setId(notificationId);
        User u = new User();
        u.setId(1L);
        u.setEmail("test@mrec.ac.in");

        NotificationDelivery alreadySent = new NotificationDelivery(notificationId, DeliveryChannel.EMAIL, DeliveryStatus.SENT);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(n));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(deliveryRepository.findByNotificationIdAndChannel(notificationId, DeliveryChannel.EMAIL))
                .thenReturn(Optional.of(alreadySent));

        consumer.processDelivery(notificationId);

        // emailSender should not be called again!
        verify(emailSender, never()).sendEmail(any(), any());
    }

    @Test
    void deliverSms_marksSkippedWhenUserHasNoPhoneNumber() {
        Notification n = new Notification(1L, NotificationType.BOOKING_CONFIRMED, "T", "M", ReferenceType.BOOKING, 1L);
        n.setId(10L);
        User u = new User();
        u.setId(1L);
        u.setPhoneNumber(null); // No phone!

        UserNotificationPreferences prefs = new UserNotificationPreferences(1L);
        prefs.setSmsEnabled(true); // User wants SMS, but has no phone

        when(deliveryRepository.findByNotificationIdAndChannel(10L, DeliveryChannel.SMS))
                .thenReturn(Optional.empty());

        consumer.deliverSms(n, u, prefs);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(captor.capture());

        NotificationDelivery saved = captor.getValue();
        assertEquals(DeliveryStatus.SKIPPED, saved.getStatus());
        assertTrue(saved.getErrorMessage().contains("Missing phone number"));
        verify(smsSender, never()).sendSms(any(), any());
    }

    @Test
    void deliverPush_marksSkippedWhenNoActiveDeviceTokens() {
        Notification n = new Notification(1L, NotificationType.BOOKING_CONFIRMED, "T", "M", ReferenceType.BOOKING, 1L);
        n.setId(10L);
        User u = new User();
        u.setId(1L);

        UserNotificationPreferences prefs = new UserNotificationPreferences(1L);
        prefs.setPushEnabled(true); // User wants push

        when(deliveryRepository.findByNotificationIdAndChannel(10L, DeliveryChannel.PUSH))
                .thenReturn(Optional.empty());
        when(pushDeviceTokenRepository.findByUserId(1L)).thenReturn(Collections.emptyList()); // No tokens

        consumer.deliverPush(n, u, prefs);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(captor.capture());

        NotificationDelivery saved = captor.getValue();
        assertEquals(DeliveryStatus.SKIPPED, saved.getStatus());
        assertTrue(saved.getErrorMessage().contains("No active device token"));
        verify(pushSender, never()).sendPush(any(), any());
    }

    @Test
    void deliverEmail_marksSentOnSuccess() {
        Notification n = new Notification(1L, NotificationType.BOOKING_CONFIRMED, "T", "M", ReferenceType.BOOKING, 1L);
        n.setId(10L);
        User u = new User();
        u.setId(1L);
        u.setEmail("test@mrec.ac.in");

        UserNotificationPreferences prefs = new UserNotificationPreferences(1L);
        prefs.setEmailEnabled(true);

        when(deliveryRepository.findByNotificationIdAndChannel(10L, DeliveryChannel.EMAIL))
                .thenReturn(Optional.empty());

        consumer.deliverEmail(n, u, prefs);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(captor.capture());

        NotificationDelivery saved = captor.getValue();
        assertEquals(DeliveryStatus.SENT, saved.getStatus());
        assertEquals(1, saved.getAttempts());
        assertNotNull(saved.getLastAttemptAt());
        verify(emailSender).sendEmail(n, u);
    }
}
