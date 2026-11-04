package com.labresource.platform.booking.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingAuditLogRepository;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingUsageLifecycleTests {
    @Mock BookingRepository bookings;
    @Mock EquipmentRepository equipmentRepository;
    @Mock UserRepository users;
    @Mock BookingAuditLogRepository auditLogs;
    @Mock UtilizationRepository usages;
    @Mock ApplicationEventPublisher events;

    BookingService service;
    Booking booking;
    Equipment equipment;
    User owner;
    LocalDateTime start;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookings, equipmentRepository, users, auditLogs, usages, events);
        start = LocalDateTime.of(2026, 10, 31, 10, 0);
        booking = new Booking();
        booking.setId(8L); booking.setEquipmentId(12L); booking.setUserId(4L);
        booking.setStartTime(start); booking.setEndTime(start.plusHours(2)); booking.setStatus("APPROVED");
        equipment = new Equipment(); equipment.setId(12L); equipment.setStatus("AVAILABLE");
        owner = new User(); owner.setId(4L); owner.setEmail("owner@example.test");
        lenient().when(bookings.findByIdForUpdate(8L)).thenReturn(Optional.of(booking));
        lenient().when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        lenient().when(equipmentRepository.findByIdForBooking(12L)).thenReturn(Optional.of(equipment));
        lenient().when(bookings.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(equipmentRepository.save(any(Equipment.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(usages.save(any(Utilization.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void checkInAtOpeningBoundaryCreatesUsageAndMarksBookingInUse() {
        var result = service.checkIn(8L, owner.getEmail(), start.minusMinutes(15));
        assertEquals("IN_USE", result.getStatus());
        assertEquals("BOOKED", equipment.getStatus());
        verify(usages).save(argThat(usage -> usage.getUserId().equals(owner.getId())
                && usage.getBookingId().equals(8L) && usage.getStartTime().equals(start.minusMinutes(15))));
    }

    @Test
    void checkInRejectsWrongOwnerAndOutsideWindow() {
        User other = new User(); other.setId(9L); other.setEmail("other@example.test");
        when(users.findByEmail(other.getEmail())).thenReturn(Optional.of(other));
        BookingActionException wrongOwner = assertThrows(BookingActionException.class,
                () -> service.checkIn(8L, other.getEmail(), start));
        assertEquals(HttpStatus.FORBIDDEN, wrongOwner.getStatus());
        BookingActionException early = assertThrows(BookingActionException.class,
                () -> service.checkIn(8L, owner.getEmail(), start.minusMinutes(16)));
        assertEquals(HttpStatus.CONFLICT, early.getStatus());
        verify(usages, never()).save(any());
    }

    @Test
    void checkOutClosesUsageAndRecordsActualDuration() {
        booking.setStatus("IN_USE");
        equipment.setStatus("BOOKED");
        Utilization active = new Utilization(); active.setStartTime(start); active.setBookingId(8L);
        active.setEquipmentId(12L); active.setUserId(4L);
        when(usages.findOpenByBookingIdForUpdate(8L)).thenReturn(Optional.of(active));
        when(bookings.findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(12L), anyList(), any(), any())).thenReturn(List.of());

        var result = service.checkOut(8L, owner.getEmail(), start.plusHours(1));

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(start.plusHours(1), active.getEndTime());
        assertEquals(1.0, active.getActualHours());
        assertEquals("AVAILABLE", equipment.getStatus());
    }

    @Test
    void noShowSweepChangesOnlyConfirmedBookingsPastGracePeriod() {
        booking.setStartTime(start);
        when(bookings.findByStatusInAndStartTimeLessThanEqual(List.of("APPROVED", "CONFIRMED"),
                start)).thenReturn(List.of(booking));
        when(bookings.findByIdForUpdate(8L)).thenReturn(Optional.of(booking));

        assertEquals(1, service.markNoShows(start.plusMinutes(15)));
        assertEquals("NO_SHOW", booking.getStatus());
    }
}
