package com.labresource.platform.notification.dto;

public record RegisterDeviceTokenRequest(
        String token,
        String platform
) {}
