package com.labresource.platform.maintenance.repository;

import com.labresource.platform.maintenance.entity.EquipmentDowntimeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentDowntimeLogRepository extends JpaRepository<EquipmentDowntimeLog, Long> {

    Optional<EquipmentDowntimeLog> findFirstByWorkOrderIdAndEndTimeIsNullOrderByStartTimeDesc(Long workOrderId);

    List<EquipmentDowntimeLog> findByEquipmentIdOrderByStartTimeDesc(Long equipmentId);

    @Query("""
            SELECT d FROM EquipmentDowntimeLog d
            WHERE d.equipmentId IN :equipmentIds
              AND d.startTime <= :endDate
              AND (d.endTime IS NULL OR d.endTime >= :startDate)
            ORDER BY d.startTime DESC
            """)
    List<EquipmentDowntimeLog> findOverlappingDowntimes(
            @Param("equipmentIds") List<Long> equipmentIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
