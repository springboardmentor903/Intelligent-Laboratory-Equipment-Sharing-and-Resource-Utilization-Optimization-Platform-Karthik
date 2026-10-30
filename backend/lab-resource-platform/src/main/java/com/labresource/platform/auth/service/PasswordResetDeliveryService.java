package com.labresource.platform.auth.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
@Service
public class PasswordResetDeliveryService {
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String frontendUrl;
    private final String from;
    public PasswordResetDeliveryService() {
        this((JavaMailSender) null, false, "http://localhost:5173", "");
    }

    @Autowired
    public PasswordResetDeliveryService(
            ObjectProvider<JavaMailSender> senders,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${app.mail.from:}") String from) {
        this(senders.getIfAvailable(), enabled, frontendUrl, from);
    }

    PasswordResetDeliveryService(JavaMailSender sender, boolean enabled,
                                 String frontendUrl, String from) {
        this.sender = sender;
        this.enabled = enabled;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.from = from;
    }

    /** Returns false when SMTP is not configured or delivery fails; never logs the token. */
    public boolean deliver(String email, String token) {
        if (!enabled || sender == null || email == null || token == null) return false;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) message.setFrom(from);
            message.setTo(email);
            message.setSubject("Lab Resource Platform password reset");
            message.setText("Use this one-time reset link within 15 minutes:\n\n"
                    + frontendUrl + "/password-reset#token=" + token
                    + "\n\nIf you did not request a password reset, ignore this message.");
            sender.send(message);
            return true;
        } catch (MailException exception) {
            return false;
        }
    }
}
