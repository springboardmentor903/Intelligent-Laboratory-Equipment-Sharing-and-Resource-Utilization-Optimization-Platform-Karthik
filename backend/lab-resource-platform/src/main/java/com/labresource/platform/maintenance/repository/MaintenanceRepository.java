
package com.labresource.platform.maintenance.repository;

import com.labresource.platform.maintenance.entity.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

    List<Maintenance> findByEquipmentId(Long equipmentId);

    List<Maintenance> findByReportedBy(Long reportedBy);

    List<Maintenance> findByAssignedTo(Long assignedTo);

    List<Maintenance> findByStatus(String status);

    List<Maintenance> findByPriority(String priority);

    List<Maintenance> findByMaintenanceType(String maintenanceType);

    long countByStatus(String status);

    long countByPriority(String priority);

    long countByMaintenanceType(String maintenanceType);

    List<Maintenance> findByAssignedToOrderByReportedAtDesc(Long assignedTo);

    List<Maintenance> findByReportedAtBetween(
            java.time.LocalDateTime start,
            java.time.LocalDateTime end
    );

    List<Maintenance> findByScheduledDateBetween(
            java.time.LocalDateTime start,
            java.time.LocalDateTime end
    );
}
