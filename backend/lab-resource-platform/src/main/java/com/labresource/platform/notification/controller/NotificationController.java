package com.labresource.platform.notification.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.notification.dto.NotificationDTO;
import com.labresource.platform.notification.dto.NotificationPreferencesDTO;
import com.labresource.platform.notification.dto.RegisterDeviceTokenRequest;
import com.labresource.platform.notification.dto.UnreadCountResponse;
import com.labresource.platform.notification.dto.UpdatePreferencesRequest;
import com.labresource.platform.notification.model.NotificationType;
import com.labresource.platform.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final ResourceAccessPolicy resourceAccess;

    public NotificationController(NotificationService notificationService,
                                  ResourceAccessPolicy resourceAccess) {
        this.notificationService = notificationService;
        this.resourceAccess = resourceAccess;
    }

    private Long getAuthUserId(Authentication auth) {
        Long userId = resourceAccess.currentUserId(auth);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User authentication required");
        }
        return userId;
    }

    @GetMapping
    public Page<NotificationDTO> getNotifications(
            @RequestParam(required = false) Boolean unread,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            Authentication auth) {
        Long userId = getAuthUserId(auth);
        return notificationService.getNotifications(
                userId, unread, type, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount(Authentication auth) {
        Long userId = getAuthUserId(auth);
        return new UnreadCountResponse(notificationService.getUnreadCount(userId));
    }

    @PatchMapping("/{id}/read")
    public NotificationDTO markAsRead(@PathVariable Long id, Authentication auth) {
        Long userId = getAuthUserId(auth);
        return notificationService.markAsRead(userId, id);
    }

    @PatchMapping("/read-all")
    public void markAllAsRead(Authentication auth) {
        Long userId = getAuthUserId(auth);
        notificationService.markAllAsRead(userId);
    }

    @GetMapping("/preferences")
    public NotificationPreferencesDTO getPreferences(Authentication auth) {
        Long userId = getAuthUserId(auth);
        return notificationService.getPreferences(userId);
    }

    @PutMapping("/preferences")
    public NotificationPreferencesDTO updatePreferences(
            @RequestBody UpdatePreferencesRequest request,
            Authentication auth) {
        Long userId = getAuthUserId(auth);
        return notificationService.updatePreferences(userId, request);
    }

    @PostMapping("/devices")
    public void registerDevice(
            @RequestBody RegisterDeviceTokenRequest request,
            Authentication auth) {
        Long userId = getAuthUserId(auth);
        notificationService.registerDeviceToken(userId, request);
    }

    @DeleteMapping("/devices/{token}")
    public void deleteDevice(
            @PathVariable String token,
            Authentication auth) {
        Long userId = getAuthUserId(auth);
        notificationService.deleteDeviceToken(userId, token);
    }
}
