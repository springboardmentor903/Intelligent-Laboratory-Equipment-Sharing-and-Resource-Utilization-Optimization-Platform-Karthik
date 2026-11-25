package com.labresource.platform.maintenance.repository;

import com.labresource.platform.maintenance.entity.MaintenanceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    Page<MaintenanceRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId, Pageable pageable);

    List<MaintenanceRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    @Query("""
            SELECT r FROM MaintenanceRequest r
            WHERE r.requesterId = :requesterId
              AND (:status IS NULL OR r.status = :status)
              AND (:equipmentId IS NULL OR r.equipmentId = :equipmentId)
              AND (:priority IS NULL OR r.priority = :priority)
            ORDER BY r.createdAt DESC
            """)
    Page<MaintenanceRequest> findMyRequestsWithFilters(
            @Param("requesterId") Long requesterId,
            @Param("status") String status,
            @Param("equipmentId") Long equipmentId,
            @Param("priority") String priority,
            Pageable pageable
    );

    @Query("""
            SELECT r FROM MaintenanceRequest r
            WHERE r.institutionId = :institutionId
              AND (:status IS NULL OR r.status = :status)
              AND (:equipmentId IS NULL OR r.equipmentId = :equipmentId)
              AND (:priority IS NULL OR r.priority = :priority)
            ORDER BY r.createdAt DESC
            """)
    Page<MaintenanceRequest> findQueueWithFilters(
            @Param("institutionId") Long institutionId,
            @Param("status") String status,
            @Param("equipmentId") Long equipmentId,
            @Param("priority") String priority,
            Pageable pageable
    );

    List<MaintenanceRequest> findByEquipmentIdOrderByCreatedAtDesc(Long equipmentId);
}
