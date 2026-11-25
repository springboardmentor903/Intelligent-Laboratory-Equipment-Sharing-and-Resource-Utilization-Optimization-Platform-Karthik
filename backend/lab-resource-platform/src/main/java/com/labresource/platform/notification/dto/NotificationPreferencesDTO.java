package com.labresource.platform.notification.dto;

import com.labresource.platform.notification.entity.UserNotificationPreferences;

public record NotificationPreferencesDTO(
        Long userId,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean pushEnabled,
        boolean inAppEnabled,
        boolean hasPhoneNumber
) {
    public static NotificationPreferencesDTO fromEntity(UserNotificationPreferences entity, boolean hasPhoneNumber) {
        return new NotificationPreferencesDTO(
                entity.getUserId(),
                entity.isEmailEnabled(),
                entity.isSmsEnabled(),
                entity.isPushEnabled(),
                true, // In-app notifications are always enabled
                hasPhoneNumber
        );
    }
}
