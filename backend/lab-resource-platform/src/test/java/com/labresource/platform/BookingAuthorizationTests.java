
package com.labresource.platform;

import com.labresource.platform.booking.controller.BookingController;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(BookingAuthorizationTests.TestConfig.class)
class BookingAuthorizationTests {

    @Autowired
    private BookingController bookingController;

    @Autowired
    private BookingService bookingService;

    @BeforeEach
    void resetMocks() {
        reset(bookingService);
    }

    @Test
    @WithMockUser(
            username = "student@example.com",
            roles = "RESEARCHER_STUDENT"
    )
    void studentCanCreateBooking() {
        Booking request = new Booking();
        Booking created = new Booking();
        created.setId(10L);
        created.setUserId(1L);
        created.setStatus("PENDING");

        when(bookingService.createBooking(
                request, "student@example.com"
        )).thenReturn(created);

        var response = bookingController.createBooking(
                request,
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication()
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(bookingService).createBooking(
                request, "student@example.com"
        );
    }

    @Test
    @WithMockUser(
            username = "student@example.com",
            roles = "RESEARCHER_STUDENT"
    )
    void studentCannotViewAllBookings() {
        assertThrows(
                AccessDeniedException.class,
                () -> bookingController.getAllBookings()
        );

        verifyNoInteractions(bookingService);
    }

    @Test
    @WithMockUser(
            username = "student@example.com",
            roles = "RESEARCHER_STUDENT"
    )
    void studentCanCancelOwnBooking() {
        Booking booking = new Booking();
        booking.setId(42L);
        booking.setUserId(1L);
        booking.setStatus("CANCELLED");

        when(bookingService.isBookingOwner(
                42L, "student@example.com"
        )).thenReturn(true);

        when(bookingService.cancelBooking(42L))
                .thenReturn(booking);

        var response = bookingController.cancelBooking(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(bookingService).cancelBooking(42L);
    }

    @Test
    @WithMockUser(
            username = "student@example.com",
            roles = "RESEARCHER_STUDENT"
    )
    void studentCannotCancelAnotherUsersBooking() {
        when(bookingService.isBookingOwner(
                42L, "student@example.com"
        )).thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> bookingController.cancelBooking(42L)
        );

        verify(bookingService, never()).cancelBooking(42L);
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        BookingService bookingService() {
            return mock(BookingService.class);
        }

        @Bean
        BookingController bookingController(
                BookingService bookingService
        ) {
            return new BookingController(bookingService);
        }
    }
}
