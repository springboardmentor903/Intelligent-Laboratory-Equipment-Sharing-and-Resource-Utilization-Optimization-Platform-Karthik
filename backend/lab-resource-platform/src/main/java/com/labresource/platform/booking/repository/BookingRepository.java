
package com.labresource.platform.booking.repository;

import com.labresource.platform.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByEquipmentId(Long equipmentId);

    List<Booking> findByStatus(String status);

    List<Booking> findByUserIdAndStatus(Long userId, String status);

    List<Booking> findByEquipmentIdAndStatus(Long equipmentId, String status);

    boolean existsByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long equipmentId,
            List<String> statuses,
            java.time.LocalDateTime endTime,
            java.time.LocalDateTime startTime
    );

    long countByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long equipmentId,
            List<String> statuses,
            java.time.LocalDateTime endTime,
            java.time.LocalDateTime startTime
    );

    List<Booking> findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long equipmentId,
            List<String> statuses,
            java.time.LocalDateTime endTime,
            java.time.LocalDateTime startTime
    );
}
