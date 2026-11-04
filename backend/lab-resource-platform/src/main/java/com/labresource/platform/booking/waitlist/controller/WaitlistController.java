package com.labresource.platform.booking.waitlist.controller;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.waitlist.dto.RecurringBookingRequest;
import com.labresource.platform.booking.waitlist.dto.RecurringBookingResponse;
import com.labresource.platform.booking.waitlist.dto.SchedulingRecommendationDto;
import com.labresource.platform.booking.waitlist.dto.WaitlistRequest;
import com.labresource.platform.booking.waitlist.entity.WaitlistEntry;
import com.labresource.platform.booking.waitlist.service.RecurringBookingService;
import com.labresource.platform.booking.waitlist.service.WaitlistService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final RecurringBookingService recurringBookingService;
    private final ResourceAccessPolicy resourceAccess;

    public WaitlistController(
            WaitlistService waitlistService,
            RecurringBookingService recurringBookingService,
            ResourceAccessPolicy resourceAccess) {
        this.waitlistService = waitlistService;
        this.recurringBookingService = recurringBookingService;
        this.resourceAccess = resourceAccess;
    }

    @PostMapping("/api/waitlist")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<?> joinWaitlist(@RequestBody WaitlistRequest request, Authentication auth) {
        try {
            Long userId = resourceAccess.currentUserId(auth);
            WaitlistEntry entry = waitlistService.joinWaitlist(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(entry);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/api/waitlist/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WaitlistEntry>> getMyWaitlist(Authentication auth) {
        Long userId = resourceAccess.currentUserId(auth);
        return ResponseEntity.ok(waitlistService.getMyWaitlist(userId));
    }

    @GetMapping("/api/waitlist/equipment/{equipmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WaitlistEntry>> getEquipmentWaitlist(@PathVariable Long equipmentId) {
        return ResponseEntity.ok(waitlistService.getEquipmentWaitlist(equipmentId));
    }

    @DeleteMapping("/api/waitlist/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> cancelWaitlist(@PathVariable Long id, Authentication auth) {
        try {
            Long userId = resourceAccess.currentUserId(auth);
            boolean isStaff = resourceAccess.hasAnyRole(auth, "LAB_TECHNICIAN", "LAB_MANAGER", "DEPARTMENT_HEAD", "INSTITUTION_ADMIN", "SYSTEM_ADMIN");
            waitlistService.cancelWaitlist(id, userId, isStaff);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/api/waitlist/{id}/convert")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> convertWaitlistToBooking(@PathVariable Long id, Authentication auth) {
        try {
            Long userId = resourceAccess.currentUserId(auth);
            Booking booking = waitlistService.convertToBooking(id, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(booking);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/api/waitlist/{id}/claim")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> claimWaitlist(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        try {
            Long userId = resourceAccess.currentUserId(auth);
            String purpose = body != null ? body.get("purpose") : null;
            if (purpose == null || purpose.trim().isEmpty()) {
                purpose = "Waitlist Offer Claimed";
            }
            Booking booking = waitlistService.claimWaitlist(id, userId, purpose);
            return ResponseEntity.status(HttpStatus.CREATED).body(booking);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/api/waitlist/{id}/decline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> declineWaitlist(@PathVariable Long id, Authentication auth) {
        try {
            Long userId = resourceAccess.currentUserId(auth);
            waitlistService.declineWaitlist(id, userId);
            return ResponseEntity.ok(Map.of("message", "Waitlist offer declined successfully"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/api/bookings/recurring/preview")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<?> previewRecurringBooking(@RequestBody RecurringBookingRequest request, Authentication auth) {
        try {
            Long userId = resourceAccess.currentUserId(auth);
            RecurringBookingResponse response = recurringBookingService.previewRecurringBooking(userId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/api/bookings/recurring")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<?> createRecurringBooking(@RequestBody RecurringBookingRequest request, Authentication auth) {
        try {
            Long userId = resourceAccess.currentUserId(auth);
            RecurringBookingResponse response = recurringBookingService.createRecurringBooking(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/api/bookings/recurring/{id}/cancel-series")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> cancelRecurringSeries(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        try {
            Long userId = resourceAccess.currentUserId(auth);
            boolean isStaff = resourceAccess.hasAnyRole(auth, "LAB_TECHNICIAN", "LAB_MANAGER", "DEPARTMENT_HEAD", "INSTITUTION_ADMIN", "SYSTEM_ADMIN");
            String reason = body != null ? body.get("reason") : null;
            int count = recurringBookingService.cancelRecurringSeries(id, userId, isStaff, reason);
            return ResponseEntity.ok(Map.of("message", "Cancelled " + count + " bookings in recurring series.", "cancelledCount", count));
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/api/bookings/recurring/{id}/approve")
    @PreAuthorize("@resourceAccess.canApproveRecurringSeries(#id, authentication)")
    public ResponseEntity<?> approveRecurringSeries(@PathVariable Long id, Authentication auth) {
        try {
            Long userId = resourceAccess.currentUserId(auth);
            int count = recurringBookingService.approveRecurringSeries(id, userId, auth.getName());
            return ResponseEntity.ok(Map.of("message", "Approved " + count + " pending bookings in recurring series.", "approvedCount", count));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/api/bookings/recurring/{id}/reject")
    @PreAuthorize("@resourceAccess.canApproveRecurringSeries(#id, authentication)")
    public ResponseEntity<?> rejectRecurringSeries(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        try {
            Long userId = resourceAccess.currentUserId(auth);
            String reason = body != null ? body.get("reason") : null;
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Rejection reason is required."));
            }
            int count = recurringBookingService.rejectRecurringSeries(id, userId, auth.getName(), reason);
            return ResponseEntity.ok(Map.of("message", "Rejected " + count + " pending bookings in recurring series.", "rejectedCount", count));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/api/bookings/recommendations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getSchedulingRecommendations(
            @RequestParam Long equipmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            SchedulingRecommendationDto recommendations = recurringBookingService.getRecommendations(
                    equipmentId, startTime, endTime);
            return ResponseEntity.ok(recommendations);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
