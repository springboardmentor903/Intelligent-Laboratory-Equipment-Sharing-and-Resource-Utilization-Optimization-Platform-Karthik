package com.labresource.platform.notification.channel;

import com.labresource.platform.notification.entity.Notification;
import com.labresource.platform.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${spring.mail.username:noreply@labresource.local}")
    private String fromEmail;

    public EmailNotificationSender(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(Notification notification, User recipient) {
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            throw new IllegalArgumentException("Recipient email is missing");
        }

        String recipientEmail = recipient.getEmail().trim();
        String appLink = resolveLink(notification);
        String subject = "[LabResource] " + notification.getTitle();
        String content = notification.getMessage() + "\n\nView details: " + appLink;

        if (mailSender == null) {
            log.info("[MOCK EMAIL] To: {} | Subject: {} | Link: {}", recipientEmail, subject, appLink);
            return;
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(fromEmail);
            mailMessage.setTo(recipientEmail);
            mailMessage.setSubject(subject);
            mailMessage.setText(content);
            mailSender.send(mailMessage);
            log.info("Sent email notification to: {}", recipientEmail);
        } catch (Exception ex) {
            log.error("Failed to deliver email to {}: {}", recipientEmail, ex.getMessage());
            throw new RuntimeException("Email delivery failed: " + ex.getMessage(), ex);
        }
    }

    private String resolveLink(Notification notification) {
        if (notification.getReferenceType() == null) {
            return frontendUrl + "/notifications";
        }
        return switch (notification.getReferenceType()) {
            case BOOKING -> frontendUrl + "/bookings";
            case WAITLIST -> frontendUrl + "/bookings";
            case WORK_ORDER -> frontendUrl + "/maintenance";
            case CALIBRATION -> frontendUrl + "/calibration";
            case EQUIPMENT -> frontendUrl + "/equipment";
            case SHARING_REQUEST -> frontendUrl + "/sharing";
            case SHARING_AGREEMENT -> frontendUrl + "/sharing";
        };
    }
}
