package com.labresource.platform.notification.channel;

import com.labresource.platform.notification.entity.Notification;
import com.labresource.platform.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(SmsNotificationSender.class);

    public void sendSms(Notification notification, User recipient) {
        if (recipient == null || recipient.getPhoneNumber() == null || recipient.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException("Recipient phone number is missing");
        }

        String phone = recipient.getPhoneNumber().trim();
        String message = "[LabResource] " + notification.getTitle() + ": " + notification.getMessage();

        // Default mock / provider adapter
        log.info("[SMS DISPATCH] To: {} | Body: {}", phone, message);
    }
}
