package com.labresource.platform.booking.controller;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.entity.BookingAuditLog;
import com.labresource.platform.booking.dto.BookingCreateRequest;
import com.labresource.platform.booking.service.BookingService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final ResourceAccessPolicy resourceAccess;

    public BookingController(BookingService bookingService,
                             ResourceAccessPolicy resourceAccess) {
        this.bookingService = bookingService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<Booking>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings().stream()
                .filter(booking -> resourceAccess.canAccessEquipment(
                        booking.getEquipmentId(),
                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication()))
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAccess.canAccessBooking(#id, authentication)")
    public ResponseEntity<?> getBookingById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(bookingService.getBookingById(id));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("@resourceAccess.canAccessBooking(#id, authentication)")
    public ResponseEntity<List<BookingAuditLog>> getBookingAuditHistory(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingAuditHistory(id));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("(@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessUser(#userId, authentication)) or @resourceAccess.ownsUser(#userId, authentication)")
    public ResponseEntity<List<Booking>> getBookingsByUser(
            @PathVariable Long userId) {
        return ResponseEntity.ok(bookingService.getBookingsByUser(userId).stream()
                .filter(booking -> resourceAccess.canAccessEquipment(
                        booking.getEquipmentId(),
                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication()))
                .collect(Collectors.toList()));
    }

    @GetMapping("/equipment/{equipmentId}")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessEquipment(#equipmentId, authentication)")
    public ResponseEntity<List<Booking>> getBookingsByEquipment(
            @PathVariable Long equipmentId) {
        return ResponseEntity.ok(
                bookingService.getBookingsByEquipment(equipmentId));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<Booking>> getBookingsByStatus(
            @PathVariable String status) {
        return ResponseEntity.ok(bookingService.getBookingsByStatus(status).stream()
                .filter(booking -> resourceAccess.canAccessEquipment(
                        booking.getEquipmentId(),
                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication()))
                .collect(Collectors.toList()));
    }

    @PostMapping
    @PreAuthorize("hasRole('RESEARCHER_STUDENT') and @resourceAccess.canAccessEquipment(#request.equipmentId, authentication)")
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody BookingCreateRequest request,
            Authentication authentication) {
        try {
            Booking booking = new Booking();
            booking.setEquipmentId(request.getEquipmentId());
            booking.setStartTime(request.getStartTime());
            booking.setEndTime(request.getEndTime());
            booking.setPurpose(request.getPurpose());
            Booking createdBooking = bookingService.createBooking(
                    booking, authentication.getName());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(createdBooking);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    /** Advisory capacity check; POST repeats this calculation while holding an equipment row lock. */
    @GetMapping("/availability")
    @PreAuthorize("@resourceAccess.canAccessEquipment(#equipmentId, authentication)")
    public ResponseEntity<?> getAvailability(
            @RequestParam Long equipmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    java.time.LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    java.time.LocalDateTime endTime) {
        try {
            return ResponseEntity.ok(bookingService.getAvailability(
                    equipmentId, startTime, endTime));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessBooking(#id, authentication)")
    public ResponseEntity<?> updateBookingStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            Booking updatedBooking = bookingService.updateBookingStatus(
                    id, request.get("status"));

            return ResponseEntity.ok(updatedBooking);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessBooking(#id, authentication)")
    public ResponseEntity<?> deleteBooking(@PathVariable Long id) {
        try {
            bookingService.deleteBooking(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("(@resourceAccess.hasAnyRole(authentication, 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessBooking(#id, authentication)) or (hasRole('RESEARCHER_STUDENT') and @resourceAccess.ownsBooking(#id, authentication))")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(bookingService.cancelBooking(id));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", exception.getMessage()));
        }
    }
}
