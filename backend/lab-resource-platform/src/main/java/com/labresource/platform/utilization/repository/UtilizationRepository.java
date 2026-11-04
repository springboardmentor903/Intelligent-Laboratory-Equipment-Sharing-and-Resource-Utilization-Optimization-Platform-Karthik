package com.labresource.platform.utilization.repository;

import com.labresource.platform.utilization.entity.Utilization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UtilizationRepository extends JpaRepository<Utilization, Long> {

    List<Utilization> findByEquipmentId(Long equipmentId);

    List<Utilization> findByUserId(Long userId);

    List<Utilization> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Utilization u where u.bookingId = :bookingId and u.endTime is null")
    Optional<Utilization> findOpenByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    List<Utilization> findByEquipmentIdInAndEndTimeIsNull(List<Long> equipmentIds);

    long countByEquipmentIdAndEndTimeIsNull(Long equipmentId);

    long countByEquipmentId(Long equipmentId);

    List<Utilization> findByStartTimeBetween(
            java.time.LocalDateTime start,
            java.time.LocalDateTime end
    );

    @Query("select u from Utilization u where u.equipmentId in :equipmentIds and u.startTime < :windowEnd and (u.endTime is null or u.endTime > :windowStart)")
    List<Utilization> findOverlappingSessions(
            @Param("equipmentIds") List<Long> equipmentIds,
            @Param("windowStart") java.time.LocalDateTime windowStart,
            @Param("windowEnd") java.time.LocalDateTime windowEnd
    );

    @Query("select u from Utilization u where u.equipmentId = :equipmentId and u.startTime < :windowEnd and (u.endTime is null or u.endTime > :windowStart)")
    List<Utilization> findOverlappingSessionsForEquipment(
            @Param("equipmentId") Long equipmentId,
            @Param("windowStart") java.time.LocalDateTime windowStart,
            @Param("windowEnd") java.time.LocalDateTime windowEnd
    );
}
