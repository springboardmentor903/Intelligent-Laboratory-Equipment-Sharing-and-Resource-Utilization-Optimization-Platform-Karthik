
package com.labresource.platform;

import com.labresource.platform.booking.controller.BookingController;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.dto.BookingCreateRequest;
import com.labresource.platform.booking.service.BookingService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
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

    @Autowired
    private ResourceAccessPolicy resourceAccess;

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
        BookingCreateRequest request = new BookingCreateRequest();
        request.setEquipmentId(8L);
        Booking created = new Booking();
        created.setId(10L);
        created.setUserId(1L);
        created.setStatus("PENDING");

        when(bookingService.createBooking(
                org.mockito.ArgumentMatchers.any(Booking.class), eq("student@example.com")
        )).thenReturn(created);
        when(resourceAccess.canAccessEquipment(eq(8L), any())).thenReturn(true);

        var response = bookingController.createBooking(
                request,
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication()
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(bookingService).createBooking(
                argThat(booking -> booking.getEquipmentId().equals(8L)), eq("student@example.com")
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

        when(resourceAccess.ownsBooking(42L,
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication())).thenReturn(true);

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
        when(resourceAccess.ownsBooking(42L,
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication())).thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> bookingController.cancelBooking(42L)
        );

        verify(bookingService, never()).cancelBooking(42L);
    }

    @Test
    @WithMockUser(username = "manager@example.com", roles = "LAB_MANAGER")
    void managerCanReadInstitutionScopedBookingList() {
        when(bookingService.getAllBookings()).thenReturn(java.util.List.of());

        var response = bookingController.getAllBookings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(bookingService).getAllBookings();
    }

    @Test
    void unauthenticatedCallerCannotCreateBooking() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> bookingController.createBooking(new BookingCreateRequest(), null));
        verifyNoInteractions(bookingService);
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        BookingService bookingService() {
            return mock(BookingService.class);
        }

        @Bean
        ResourceAccessPolicy resourceAccess() {
            return mock(ResourceAccessPolicy.class);
        }

        @Bean
        BookingController bookingController(
                BookingService bookingService,
                ResourceAccessPolicy resourceAccess
        ) {
            return new BookingController(bookingService, resourceAccess);
        }
    }
}
