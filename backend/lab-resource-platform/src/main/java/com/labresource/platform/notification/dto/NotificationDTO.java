package com.labresource.platform.notification.dto;

import com.labresource.platform.notification.entity.Notification;
import com.labresource.platform.notification.model.NotificationType;
import com.labresource.platform.notification.model.ReferenceType;

import java.time.Instant;

public record NotificationDTO(
        Long id,
        Long recipientId,
        NotificationType notificationType,
        String title,
        String message,
        ReferenceType referenceType,
        Long referenceId,
        boolean read,
        Instant createdAt
) {
    public static NotificationDTO fromEntity(Notification entity) {
        return new NotificationDTO(
                entity.getId(),
                entity.getRecipientId(),
                entity.getNotificationType(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getReferenceType(),
                entity.getReferenceId(),
                entity.isRead(),
                entity.getCreatedAt()
        );
    }
}
