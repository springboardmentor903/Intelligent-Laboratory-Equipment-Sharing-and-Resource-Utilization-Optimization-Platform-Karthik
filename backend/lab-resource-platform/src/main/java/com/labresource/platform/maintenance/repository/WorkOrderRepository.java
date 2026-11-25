package com.labresource.platform.maintenance.repository;

import com.labresource.platform.maintenance.entity.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Optional<WorkOrder> findByWorkOrderNumber(String workOrderNumber);

    @Query("SELECT MAX(w.workOrderNumber) FROM WorkOrder w WHERE w.workOrderNumber LIKE CONCAT(:prefix, '%')")
    Optional<String> findMaxWorkOrderNumber(@Param("prefix") String prefix);

    long countByEquipmentIdAndStatusAndIdNot(Long equipmentId, String status, Long id);

    long countByEquipmentIdAndStatus(Long equipmentId, String status);

    boolean existsByScheduleIdAndStatusIn(Long scheduleId, Collection<String> statuses);

    List<WorkOrder> findByEquipmentIdOrderByCreatedAtDesc(Long equipmentId);

    List<WorkOrder> findByEquipmentIdAndStatus(Long equipmentId, String status);

    @Query("""
            SELECT w FROM WorkOrder w
            WHERE w.equipmentId = :equipmentId
              AND w.status = 'COMPLETED'
            ORDER BY w.completedAt DESC
            """)
    Page<WorkOrder> findCompletedByEquipmentId(
            @Param("equipmentId") Long equipmentId,
            Pageable pageable
    );

    @Query("""
            SELECT w FROM WorkOrder w
            WHERE w.equipmentId IN :equipmentIds
              AND (:status IS NULL OR w.status = :status)
              AND (:workOrderType IS NULL OR w.workOrderType = :workOrderType)
              AND (:priority IS NULL OR w.priority = :priority)
              AND (:equipmentId IS NULL OR w.equipmentId = :equipmentId)
              AND (:assignedTo IS NULL OR w.assignedTo = :assignedTo)
            ORDER BY w.createdAt DESC
            """)
    Page<WorkOrder> findWorkOrdersWithFilters(
            @Param("equipmentIds") List<Long> equipmentIds,
            @Param("status") String status,
            @Param("workOrderType") String workOrderType,
            @Param("priority") String priority,
            @Param("equipmentId") Long equipmentId,
            @Param("assignedTo") Long assignedTo,
            Pageable pageable
    );

    @Query("""
            SELECT w FROM WorkOrder w
            WHERE w.equipmentId IN :equipmentIds
              AND w.createdAt >= :startDate AND w.createdAt <= :endDate
            """)
    List<WorkOrder> findWorkOrdersInPeriod(
            @Param("equipmentIds") List<Long> equipmentIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
