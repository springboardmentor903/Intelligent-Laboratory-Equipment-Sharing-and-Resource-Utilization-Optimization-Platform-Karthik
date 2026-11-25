package com.labresource.platform.maintenance.repository;

import com.labresource.platform.maintenance.entity.PreventiveMaintenanceSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PreventiveMaintenanceScheduleRepository extends JpaRepository<PreventiveMaintenanceSchedule, Long> {

    List<PreventiveMaintenanceSchedule> findByEquipmentId(Long equipmentId);

    List<PreventiveMaintenanceSchedule> findByEquipmentIdIn(List<Long> equipmentIds);

    @Query("""
            SELECT s FROM PreventiveMaintenanceSchedule s
            WHERE s.equipmentId IN :equipmentIds
              AND (:active IS NULL OR s.active = :active)
            ORDER BY s.nextDueDate ASC
            """)
    Page<PreventiveMaintenanceSchedule> findByEquipmentIds(
            @Param("equipmentIds") List<Long> equipmentIds,
            @Param("active") Boolean active,
            Pageable pageable
    );

    @Query("""
            SELECT s FROM PreventiveMaintenanceSchedule s
            WHERE s.active = true
              AND s.nextDueDate <= :threshold
            ORDER BY s.nextDueDate ASC
            """)
    List<PreventiveMaintenanceSchedule> findDueSchedules(@Param("threshold") LocalDate threshold);

    @Query("""
            SELECT s FROM PreventiveMaintenanceSchedule s
            WHERE s.equipmentId = :equipmentId
              AND s.active = true
            ORDER BY s.nextDueDate ASC
            """)
    List<PreventiveMaintenanceSchedule> findActiveByEquipmentId(@Param("equipmentId") Long equipmentId);
}
