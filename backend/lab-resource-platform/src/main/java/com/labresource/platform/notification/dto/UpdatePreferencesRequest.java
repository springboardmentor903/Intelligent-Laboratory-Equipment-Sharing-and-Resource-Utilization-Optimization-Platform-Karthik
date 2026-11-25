package com.labresource.platform.notification.dto;

public record UpdatePreferencesRequest(
        Boolean emailEnabled,
        Boolean smsEnabled,
        Boolean pushEnabled
) {}
