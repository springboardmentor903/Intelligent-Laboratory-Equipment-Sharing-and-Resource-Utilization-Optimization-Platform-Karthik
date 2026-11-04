package com.labresource.platform.utilization.service;

import com.labresource.platform.booking.service.BookingService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@EnableScheduling
public class UtilizationScheduler {
    private final BookingService bookings;

    public UtilizationScheduler(BookingService bookings) {
        this.bookings = bookings;
    }

    @Scheduled(fixedDelayString = "${app.utilization.sweep-ms:60000}")
    public void processBookingUsageLifecycle() {
        LocalDateTime now = LocalDateTime.now();
        bookings.markNoShows(now);
        bookings.automaticallyCompleteExpiredBookings(now);
    }
}
