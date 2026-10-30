package com.labresource.platform.booking.repository;

import com.labresource.platform.booking.entity.BookingAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingAuditLogRepository extends JpaRepository<BookingAuditLog, Long> {
    List<BookingAuditLog> findByBookingIdOrderByOccurredAtAsc(Long bookingId);
    void deleteByBookingId(Long bookingId);
}
