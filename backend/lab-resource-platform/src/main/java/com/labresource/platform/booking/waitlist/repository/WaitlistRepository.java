package com.labresource.platform.booking.waitlist.repository;

import com.labresource.platform.booking.waitlist.entity.WaitlistEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WaitlistEntry w where w.id = :id")
    Optional<WaitlistEntry> findByIdForUpdate(@Param("id") Long id);

    List<WaitlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WaitlistEntry> findByEquipmentIdAndStatusOrderByCreatedAtAsc(Long equipmentId, String status);

    List<WaitlistEntry> findByEquipmentIdOrderByCreatedAtAsc(Long equipmentId);

    @Query("select w from WaitlistEntry w where w.equipmentId = :equipmentId and w.status = 'WAITING' and w.requestedStartTime < :endTime and w.requestedEndTime > :startTime order by w.createdAt asc")
    List<WaitlistEntry> findEligibleForPromotion(
            @Param("equipmentId") Long equipmentId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    boolean existsByUserIdAndEquipmentIdAndStatus(Long userId, Long equipmentId, String status);

    long countByEquipmentIdAndStatus(Long equipmentId, String status);

    List<WaitlistEntry> findByStatusAndExpiresAtBefore(String status, LocalDateTime threshold);

    @Query("select count(w) from WaitlistEntry w where w.equipmentId = :equipmentId and w.status = 'OFFERED' and w.expiresAt > :now and w.requestedStartTime < :endTime and w.requestedEndTime > :startTime")
    long countActiveOffersOverlapping(
            @Param("equipmentId") Long equipmentId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("now") LocalDateTime now
    );
}
