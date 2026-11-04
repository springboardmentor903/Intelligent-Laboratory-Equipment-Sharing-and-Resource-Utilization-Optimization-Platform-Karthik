package com.labresource.platform.booking.waitlist;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.waitlist.controller.WaitlistController;
import com.labresource.platform.booking.waitlist.dto.RecurringBookingRequest;
import com.labresource.platform.booking.waitlist.dto.RecurringBookingResponse;
import com.labresource.platform.booking.waitlist.dto.SchedulingRecommendationDto;
import com.labresource.platform.booking.waitlist.dto.WaitlistRequest;
import com.labresource.platform.booking.waitlist.entity.WaitlistEntry;
import com.labresource.platform.booking.waitlist.service.RecurringBookingService;
import com.labresource.platform.booking.waitlist.service.WaitlistService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WaitlistControllerTests {

    @Mock private WaitlistService waitlistService;
    @Mock private RecurringBookingService recurringBookingService;
    @Mock private ResourceAccessPolicy resourceAccess;

    private WaitlistController controller;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        controller = new WaitlistController(waitlistService, recurringBookingService, resourceAccess);
        auth = mock(Authentication.class);
    }

    @Test
    void joinWaitlistReturns201Created() {
        when(resourceAccess.currentUserId(auth)).thenReturn(10L);
        WaitlistRequest req = new WaitlistRequest(
                5L,
                LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 12, 0),
                "Project testing"
        );
        WaitlistEntry entry = new WaitlistEntry(5L, 10L, req.getRequestedStartTime(), req.getRequestedEndTime(), "Project testing");
        entry.setId(1L);
        entry.setStatus("WAITING");

        when(waitlistService.joinWaitlist(10L, req)).thenReturn(entry);

        ResponseEntity<?> response = controller.joinWaitlist(req, auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(entry);
    }

    @Test
    void getMyWaitlistReturnsCurrentUserEntries() {
        when(resourceAccess.currentUserId(auth)).thenReturn(10L);
        when(waitlistService.getMyWaitlist(10L)).thenReturn(Collections.emptyList());

        ResponseEntity<List<WaitlistEntry>> response = controller.getMyWaitlist(auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void convertWaitlistToBookingReturns201() {
        when(resourceAccess.currentUserId(auth)).thenReturn(10L);
        Booking booking = new Booking();
        booking.setId(99L);
        when(waitlistService.convertToBooking(1L, 10L)).thenReturn(booking);

        ResponseEntity<?> response = controller.convertWaitlistToBooking(1L, auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(booking);
    }

    @Test
    void createRecurringBookingReturnsSummary() {
        when(resourceAccess.currentUserId(auth)).thenReturn(10L);
        RecurringBookingRequest req = new RecurringBookingRequest(
                5L,
                LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 12, 0),
                "WEEKLY",
                4,
                "Recurring lab"
        );
        RecurringBookingResponse resp = RecurringBookingResponse.builder()
                .successCount(4)
                .conflictCount(0)
                .summary("Booked 4 slots")
                .build();
        when(recurringBookingService.createRecurringBooking(10L, req)).thenReturn(resp);

        ResponseEntity<?> response = controller.createRecurringBooking(req, auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(resp);
    }

    @Test
    void getSchedulingRecommendationsReturnsOptions() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 5, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 5, 12, 0);
        SchedulingRecommendationDto dto = SchedulingRecommendationDto.builder()
                .requestedEquipmentId(5L)
                .isAvailableAtRequestedTime(false)
                .alternativeTimeSlots(List.of(new SchedulingRecommendationDto.TimeSlotRecommendation(start.plusHours(2), end.plusHours(2), "Available slot")))
                .build();

        when(recurringBookingService.getRecommendations(5L, start, end)).thenReturn(dto);

        ResponseEntity<?> response = controller.getSchedulingRecommendations(5L, start, end);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
    }

    @Test
    void previewRecurringBookingReturns200WithAvailability() {
        when(resourceAccess.currentUserId(auth)).thenReturn(10L);
        RecurringBookingRequest req = new RecurringBookingRequest(
                5L,
                LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 12, 0),
                "WEEKLY",
                4,
                "Preview test"
        );
        RecurringBookingResponse resp = RecurringBookingResponse.builder()
                .successCount(3)
                .conflictCount(1)
                .summary("Preview: 3 of 4 available (1 conflict)")
                .build();
        when(recurringBookingService.previewRecurringBooking(10L, req)).thenReturn(resp);

        ResponseEntity<?> response = controller.previewRecurringBooking(req, auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resp);
    }

    @Test
    void cancelRecurringSeriesReturnsCancelledCount() {
        when(resourceAccess.currentUserId(auth)).thenReturn(10L);
        when(resourceAccess.hasAnyRole(eq(auth), any())).thenReturn(false);
        when(recurringBookingService.cancelRecurringSeries(99L, 10L, false, "Test cancel")).thenReturn(3);

        ResponseEntity<?> response = controller.cancelRecurringSeries(99L, java.util.Map.of("reason", "Test cancel"), auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((java.util.Map<?, ?>) response.getBody()).get("cancelledCount")).isEqualTo(3);
    }

    @Test
    void claimWaitlistReturns201Created() {
        when(resourceAccess.currentUserId(auth)).thenReturn(10L);
        Booking booking = new Booking();
        booking.setId(101L);
        when(waitlistService.claimWaitlist(1L, 10L, "Claim test")).thenReturn(booking);

        ResponseEntity<?> response = controller.claimWaitlist(1L, java.util.Map.of("purpose", "Claim test"), auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(booking);
    }

    @Test
    void declineWaitlistReturns200Ok() {
        when(resourceAccess.currentUserId(auth)).thenReturn(10L);
        doNothing().when(waitlistService).declineWaitlist(1L, 10L);

        ResponseEntity<?> response = controller.declineWaitlist(1L, auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void approveRecurringSeriesReturnsCount() {
        when(resourceAccess.currentUserId(auth)).thenReturn(10L);
        when(auth.getName()).thenReturn("admin@lab.edu");
        when(recurringBookingService.approveRecurringSeries(99L, 10L, "admin@lab.edu")).thenReturn(4);

        ResponseEntity<?> response = controller.approveRecurringSeries(99L, auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((java.util.Map<?, ?>) response.getBody()).get("approvedCount")).isEqualTo(4);
    }

    @Test
    void rejectRecurringSeriesReturnsCount() {
        when(resourceAccess.currentUserId(auth)).thenReturn(10L);
        when(auth.getName()).thenReturn("admin@lab.edu");
        when(recurringBookingService.rejectRecurringSeries(99L, 10L, "admin@lab.edu", "Lab unavailable")).thenReturn(4);

        ResponseEntity<?> response = controller.rejectRecurringSeries(99L, java.util.Map.of("reason", "Lab unavailable"), auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((java.util.Map<?, ?>) response.getBody()).get("rejectedCount")).isEqualTo(4);
    }
}
