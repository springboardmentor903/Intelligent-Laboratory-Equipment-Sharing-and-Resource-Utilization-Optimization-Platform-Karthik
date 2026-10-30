package com.labresource.platform.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetDeliveryServiceTests {
    @Test
    void sendsResetTokenOnlyThroughConfiguredMailSender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        PasswordResetDeliveryService delivery = new PasswordResetDeliveryService(
                sender, true, "http://localhost:5173/", "noreply@example.test");

        assertTrue(delivery.deliver("researcher@example.test", "opaque-test-token"));
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals("noreply@example.test", message.getValue().getFrom());
        assertTrue(message.getValue().getText().contains("/password-reset#token=opaque-test-token"));
    }

    @Test
    void doesNotExposeOrAttemptDeliveryWhenDisabledOrUnavailable() {
        JavaMailSender sender = mock(JavaMailSender.class);
        PasswordResetDeliveryService disabled = new PasswordResetDeliveryService(
                sender, false, "http://localhost:5173", "");
        PasswordResetDeliveryService unavailable = new PasswordResetDeliveryService(
                (JavaMailSender) null, true, "http://localhost:5173", "");
        assertFalse(disabled.deliver("a@example.test", "test-token"));
        assertFalse(unavailable.deliver("a@example.test", "test-token"));
        verifyNoInteractions(sender);
    }
}
