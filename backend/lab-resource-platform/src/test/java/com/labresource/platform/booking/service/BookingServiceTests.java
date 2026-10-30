package com.labresource.platform.booking.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.entity.BookingAuditLog;
import com.labresource.platform.booking.repository.BookingAuditLogRepository;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTests {
    @Mock private BookingRepository bookings;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private UserRepository users;
    @Mock private BookingAuditLogRepository auditLogs;

    private BookingService service;
    private Equipment equipment;
    private User user;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookings, equipmentRepository, users, auditLogs);
        equipment = new Equipment();
        equipment.setId(11L);
        equipment.setStatus("AVAILABLE");
        user = new User();
        user.setId(21L);
        user.setEmail("researcher@example.test");
    }

    @Test
    void createsPendingReservationWithAuthenticatedUserAfterAvailabilityCheck() {
        Booking request = request();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(equipmentRepository.findByIdForBooking(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), eq(List.of("PENDING", "APPROVED", "IN_USE")), any(), any()))
                .thenReturn(List.of());
        when(bookings.save(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Booking saved = service.createBooking(request, user.getEmail());

        assertEquals(21L, saved.getUserId());
        assertEquals("PENDING", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        verify(bookings).save(request);
    }

    @Test
    void rejectsOverlappingReservation() {
        Booking request = request();
        Booking existing = new Booking();
        existing.setStartTime(request.getStartTime());
        existing.setEndTime(request.getEndTime());
        existing.setStatus("APPROVED");

        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(equipmentRepository.findByIdForBooking(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), eq(List.of("PENDING", "APPROVED", "IN_USE")), any(), any()))
                .thenReturn(List.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(request, user.getEmail()));
        verify(bookings, never()).save(any());
    }

    @Test
    void rejectsUnavailableEquipment() {
        Booking request = request();
        equipment.setStatus("UNDER_MAINTENANCE");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(equipmentRepository.findByIdForBooking(11L)).thenReturn(Optional.of(equipment));

        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(request, user.getEmail()));
        verify(bookings, never()).save(any());
    }

    @Test
    void honorsEquipmentQuantityForOverlappingReservations() {
        equipment.setQuantity(2);
        Booking request = request();
        Booking existing = new Booking();
        existing.setStartTime(request.getStartTime());
        existing.setEndTime(request.getEndTime());
        existing.setStatus("APPROVED");

        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(equipmentRepository.findByIdForBooking(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), anyList(), any(), any())).thenReturn(List.of(existing));
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("PENDING", service.createBooking(request, user.getEmail()).getStatus());
        verify(equipmentRepository).findByIdForBooking(11L);
    }

    @Test
    void permitsBookingWhenEquipmentIsBookedButAnotherUnitIsFree() {
        equipment.setQuantity(2);
        equipment.setStatus("BOOKED");
        Booking request = request();
        Booking existing = new Booking();
        existing.setStartTime(request.getStartTime());
        existing.setEndTime(request.getEndTime());
        existing.setStatus("APPROVED");

        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(equipmentRepository.findByIdForBooking(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), anyList(), any(), any())).thenReturn(List.of(existing));
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service.createBooking(request(), user.getEmail()));
    }

    @Test
    void reportsRemainingCapacityForAnAvailabilityWindow() {
        equipment.setQuantity(3);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);
        Booking b1 = new Booking();
        b1.setStartTime(start);
        b1.setEndTime(end);
        Booking b2 = new Booking();
        b2.setStartTime(start);
        b2.setEndTime(end);

        when(equipmentRepository.findById(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), anyList(), any(), any())).thenReturn(List.of(b1, b2));
        var result = service.getAvailability(11L, start, end);
        assertTrue(result.available());
        assertEquals(3, result.capacity());
        assertEquals(1, result.remainingUnits());
    }

    @Test
    void permitsBookingWhenDisjointReservationsFallWithinWiderRequestedWindow() {
        equipment.setQuantity(2);
        LocalDateTime base = LocalDateTime.now().plusDays(1);

        Booking b1 = new Booking();
        b1.setStartTime(base.withHour(9).withMinute(0));
        b1.setEndTime(base.withHour(10).withMinute(0));
        b1.setStatus("APPROVED");

        Booking b2 = new Booking();
        b2.setStartTime(base.withHour(14).withMinute(0));
        b2.setEndTime(base.withHour(15).withMinute(0));
        b2.setStatus("APPROVED");

        Booking fullDayRequest = new Booking();
        fullDayRequest.setEquipmentId(11L);
        fullDayRequest.setStartTime(base.withHour(8).withMinute(0));
        fullDayRequest.setEndTime(base.withHour(17).withMinute(0));
        fullDayRequest.setPurpose("Full day reservation across multiple disjoint slots");

        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(equipmentRepository.findByIdForBooking(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), eq(List.of("PENDING", "APPROVED", "IN_USE")), any(), any()))
                .thenReturn(List.of(b1, b2));
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking created = service.createBooking(fullDayRequest, user.getEmail());
        assertEquals("PENDING", created.getStatus());
        verify(bookings).save(fullDayRequest);
    }

    @Test
    void rejectsBookingWhenSimultaneousOverlapsExceedCapacity() {
        equipment.setQuantity(2);
        LocalDateTime base = LocalDateTime.now().plusDays(1);

        Booking b1 = new Booking();
        b1.setStartTime(base.withHour(9).withMinute(0));
        b1.setEndTime(base.withHour(12).withMinute(0));
        b1.setStatus("APPROVED");

        Booking b2 = new Booking();
        b2.setStartTime(base.withHour(11).withMinute(0));
        b2.setEndTime(base.withHour(14).withMinute(0));
        b2.setStatus("APPROVED");

        Booking overlappingRequest = new Booking();
        overlappingRequest.setEquipmentId(11L);
        overlappingRequest.setStartTime(base.withHour(10).withMinute(0));
        overlappingRequest.setEndTime(base.withHour(13).withMinute(0));

        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(equipmentRepository.findByIdForBooking(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), eq(List.of("PENDING", "APPROVED", "IN_USE")), any(), any()))
                .thenReturn(List.of(b1, b2));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(overlappingRequest, user.getEmail()));
        assertEquals("No equipment units are available during the requested time", thrown.getMessage());
        verify(bookings, never()).save(any());
    }

    @Test
    void permitsAdjacentBookingsWhereOneEndsExactlyWhenAnotherStarts() {
        equipment.setQuantity(1);
        LocalDateTime base = LocalDateTime.now().plusDays(1);

        Booking b1 = new Booking();
        b1.setStartTime(base.withHour(9).withMinute(0));
        b1.setEndTime(base.withHour(10).withMinute(0));
        b1.setStatus("APPROVED");

        Booking b2 = new Booking();
        b2.setStartTime(base.withHour(11).withMinute(0));
        b2.setEndTime(base.withHour(12).withMinute(0));
        b2.setStatus("APPROVED");

        Booking adjacentRequest = new Booking();
        adjacentRequest.setEquipmentId(11L);
        adjacentRequest.setStartTime(base.withHour(10).withMinute(0));
        adjacentRequest.setEndTime(base.withHour(11).withMinute(0));

        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(equipmentRepository.findByIdForBooking(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), eq(List.of("PENDING", "APPROVED", "IN_USE")), any(), any()))
                .thenReturn(List.of(b1, b2));
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking created = service.createBooking(adjacentRequest, user.getEmail());
        assertEquals("PENDING", created.getStatus());
        verify(bookings).save(adjacentRequest);
    }

    @Test
    void reportsCorrectRemainingUnitsWhenWindowContainsDisjointBookings() {
        equipment.setQuantity(3);
        LocalDateTime base = LocalDateTime.now().plusDays(1);
        LocalDateTime start = base.withHour(8).withMinute(0);
        LocalDateTime end = base.withHour(18).withMinute(0);

        Booking b1 = new Booking();
        b1.setStartTime(base.withHour(9).withMinute(0));
        b1.setEndTime(base.withHour(10).withMinute(0));

        Booking b2 = new Booking();
        b2.setStartTime(base.withHour(11).withMinute(0));
        b2.setEndTime(base.withHour(12).withMinute(0));

        Booking b3 = new Booking();
        b3.setStartTime(base.withHour(14).withMinute(0));
        b3.setEndTime(base.withHour(15).withMinute(0));

        when(equipmentRepository.findById(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), anyList(), any(), any())).thenReturn(List.of(b1, b2, b3));

        var result = service.getAvailability(11L, start, end);
        assertTrue(result.available());
        assertEquals(3, result.capacity());
        assertEquals(2, result.remainingUnits()); // 3 capacity - peak 1 = 2 remaining units
    }

    @Test
    void reportsZeroRemainingUnitsWhenPeakReachesCapacity() {
        equipment.setQuantity(2);
        LocalDateTime base = LocalDateTime.now().plusDays(1);
        LocalDateTime start = base.withHour(10).withMinute(0);
        LocalDateTime end = base.withHour(13).withMinute(0);

        Booking b1 = new Booking();
        b1.setStartTime(base.withHour(9).withMinute(0));
        b1.setEndTime(base.withHour(12).withMinute(0));

        Booking b2 = new Booking();
        b2.setStartTime(base.withHour(11).withMinute(0));
        b2.setEndTime(base.withHour(14).withMinute(0));

        when(equipmentRepository.findById(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), anyList(), any(), any())).thenReturn(List.of(b1, b2));

        var result = service.getAvailability(11L, start, end);
        assertFalse(result.available());
        assertEquals(2, result.capacity());
        assertEquals(0, result.remainingUnits());
    }

    @Test
    void rejectsAvailabilityRequestsWithInvalidTimeRange() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        assertThrows(IllegalArgumentException.class,
                () -> service.getAvailability(11L, start, start));
        verifyNoInteractions(equipmentRepository, bookings);
    }

    @Test
    void pendingBookingCanBeApprovedThenCancelledButNotRejectedAfterApproval() {
        Booking booking = new Booking();
        booking.setId(31L);
        booking.setStatus("PENDING");
        when(bookings.findById(31L)).thenReturn(Optional.of(booking));
        when(bookings.save(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("APPROVED", service.updateBookingStatus(31L, "approved").getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> service.updateBookingStatus(31L, "REJECTED"));
        assertEquals("CANCELLED", service.cancelBooking(31L).getStatus());
    }

    @Test
    void allowsMentorLifecycleProgressionAndKeepsTerminalStatesTerminal() {
        Booking booking = new Booking();
        booking.setId(32L);
        booking.setStatus("PENDING");
        when(bookings.findById(32L)).thenReturn(Optional.of(booking));
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("APPROVED", service.updateBookingStatus(32L, "APPROVED").getStatus());
        assertEquals("IN_USE", service.updateBookingStatus(32L, "IN_USE").getStatus());
        assertEquals("COMPLETED", service.updateBookingStatus(32L, "COMPLETED").getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> service.updateBookingStatus(32L, "CANCELLED"));
    }

    @Test
    void createBookingRecordsAuditLog() {
        Booking request = request();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(equipmentRepository.findByIdForBooking(11L)).thenReturn(Optional.of(equipment));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(11L), eq(List.of("PENDING", "APPROVED", "IN_USE")), any(), any()))
                .thenReturn(List.of());
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createBooking(request, user.getEmail());

        verify(auditLogs).save(argThat(log ->
                "CREATED".equals(log.getEventType())
                && "PENDING".equals(log.getNewStatus())
                && user.getEmail().equals(log.getActorEmail())));
    }

    @Test
    void updateBookingStatusRecordsAuditLogWithActor() {
        Booking booking = new Booking();
        booking.setId(35L);
        booking.setStatus("PENDING");
        when(bookings.findById(35L)).thenReturn(Optional.of(booking));
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateBookingStatus(35L, "APPROVED", "manager@example.test");

        verify(auditLogs).save(argThat(log ->
                "STATUS_CHANGED".equals(log.getEventType())
                && "PENDING".equals(log.getPreviousStatus())
                && "APPROVED".equals(log.getNewStatus())
                && "manager@example.test".equals(log.getActorEmail())));
    }

    @Test
    void getBookingAuditHistoryDelegatesToRepository() {
        BookingAuditLog log = new BookingAuditLog(36L, "CREATED", null, "PENDING", "user@test.org");
        when(auditLogs.findByBookingIdOrderByOccurredAtAsc(36L)).thenReturn(List.of(log));

        List<BookingAuditLog> history = service.getBookingAuditHistory(36L);

        assertEquals(1, history.size());
        assertEquals("CREATED", history.get(0).getEventType());
        verify(auditLogs).findByBookingIdOrderByOccurredAtAsc(36L);
    }

    private Booking request() {
        Booking booking = new Booking();
        booking.setEquipmentId(11L);
        booking.setUserId(999L); // Must be replaced by authenticated identity.
        booking.setStartTime(LocalDateTime.now().plusDays(1));
        booking.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        return booking;
    }
}
