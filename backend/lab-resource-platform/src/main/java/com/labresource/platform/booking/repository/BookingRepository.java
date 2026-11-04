package com.labresource.platform.booking.repository;

import com.labresource.platform.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    java.util.Optional<Booking> findByIdForUpdate(@Param("id") Long id);

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

    List<Booking> findByEquipmentIdInAndStatusInAndStartTimeAfterOrderByStartTimeAsc(
            List<Long> equipmentIds, List<String> statuses, LocalDateTime startTime);

    List<Booking> findByStatusInAndStartTimeLessThanEqual(List<String> statuses, LocalDateTime startTime);

    List<Booking> findByStatusInAndEndTimeLessThanEqual(List<String> statuses, LocalDateTime endTime);

    @Query("select b from Booking b where b.equipmentId in :equipmentIds and b.startTime < :windowEnd and b.endTime > :windowStart")
    List<Booking> findOverlappingBookings(
            @Param("equipmentIds") List<Long> equipmentIds,
            @Param("windowStart") java.time.LocalDateTime windowStart,
            @Param("windowEnd") java.time.LocalDateTime windowEnd
    );

    @Query("select b from Booking b where b.equipmentId = :equipmentId and b.startTime < :windowEnd and b.endTime > :windowStart")
    List<Booking> findOverlappingBookingsForEquipment(
            @Param("equipmentId") Long equipmentId,
            @Param("windowStart") java.time.LocalDateTime windowStart,
            @Param("windowEnd") java.time.LocalDateTime windowEnd
    );
}
