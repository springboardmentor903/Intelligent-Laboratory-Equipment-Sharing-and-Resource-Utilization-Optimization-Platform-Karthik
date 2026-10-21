
package com.labresource.platform.maintenance.service;

import com.labresource.platform.maintenance.entity.Maintenance;
import com.labresource.platform.maintenance.repository.MaintenanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;

    public MaintenanceService(MaintenanceRepository maintenanceRepository) {
        this.maintenanceRepository = maintenanceRepository;
    }

    public List<Maintenance> getAllMaintenanceRecords() {
        return maintenanceRepository.findAll();
    }

    public Maintenance getMaintenanceById(Long id) {
        return maintenanceRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Maintenance record not found with id: " + id
                        ));
    }

    public List<Maintenance> getByEquipment(Long equipmentId) {
        return maintenanceRepository.findByEquipmentId(equipmentId);
    }

    public List<Maintenance> getByReporter(Long reportedBy) {
        return maintenanceRepository.findByReportedBy(reportedBy);
    }

    public List<Maintenance> getByTechnician(Long assignedTo) {
        return maintenanceRepository.findByAssignedTo(assignedTo);
    }

    public List<Maintenance> getByStatus(String status) {
        return maintenanceRepository.findByStatus(status.toUpperCase());
    }

    public List<Maintenance> getByPriority(String priority) {
        return maintenanceRepository.findByPriority(priority.toUpperCase());
    }

    public Maintenance createMaintenance(Maintenance maintenance) {
        if (maintenance.getEquipmentId() == null
                || maintenance.getReportedBy() == null
                || maintenance.getDescription() == null
                || maintenance.getDescription().isBlank()) {
            throw new IllegalArgumentException(
                    "Equipment, reporting user and description are required"
            );
        }

        if (maintenance.getPriority() == null
                || maintenance.getPriority().isBlank()) {
            maintenance.setPriority("MEDIUM");
        }

        String priority = maintenance.getPriority().trim().toUpperCase();

        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(priority)) {
            throw new IllegalArgumentException("Invalid maintenance priority");
        }

        maintenance.setPriority(priority);
        maintenance.setId(null);
        maintenance.setStatus("REPORTED");
        maintenance.setReportedAt(LocalDateTime.now());
        maintenance.setStartedAt(null);
        maintenance.setCompletedAt(null);
        maintenance.setAssignedTo(null);
        maintenance.setResolutionNotes(null);

        return maintenanceRepository.save(maintenance);
    }

    public Maintenance updateStatus(Long id, String status) {
        Maintenance maintenance = getMaintenanceById(id);

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }

        String normalizedStatus = status.trim().toUpperCase();

        if (!List.of(
                "REPORTED",
                "ASSIGNED",
                "IN_PROGRESS",
                "COMPLETED",
                "CANCELLED"
        ).contains(normalizedStatus)) {
            throw new IllegalArgumentException("Invalid maintenance status");
        }


        if ("COMPLETED".equals(normalizedStatus)) {
            LocalDateTime completedAt = LocalDateTime.now();
            maintenance.setCompletedAt(completedAt);

            if (maintenance.getStartedAt() != null) {
                double downtime = java.time.Duration.between(
                        maintenance.getStartedAt(),
                        completedAt
                ).toMinutes() / 60.0;

                maintenance.setDowntimeHours(downtime);
            }
        }


        if ("IN_PROGRESS".equals(normalizedStatus)
                && maintenance.getStartedAt() == null) {
            maintenance.setStartedAt(LocalDateTime.now());
        }

        return maintenanceRepository.save(
                updateMaintenanceStatus(maintenance, normalizedStatus)
        );
    }

    private Maintenance updateMaintenanceStatus(
            Maintenance maintenance, String status) {
        maintenance.setStatus(status);
        return maintenance;
    }

    public void deleteMaintenance(Long id) {
        Maintenance maintenance = getMaintenanceById(id);
        maintenanceRepository.delete(maintenance);
    }


    public Maintenance assignTechnician(Long id, Long technicianId) {
        Maintenance maintenance = getMaintenanceById(id);

        if (technicianId == null) {
            throw new IllegalArgumentException("Technician ID is required");
        }

        maintenance.setAssignedTo(technicianId);

        if ("REPORTED".equals(maintenance.getStatus())) {
            maintenance.setStatus("ASSIGNED");
        }

        return maintenanceRepository.save(maintenance);
    }


    public long getTotalMaintenanceRecords() {
        return maintenanceRepository.count();
    }

    public long getCountByStatus(String status) {
        return maintenanceRepository.countByStatus(status.toUpperCase());
    }

    public long getCountByPriority(String priority) {
        return maintenanceRepository.countByPriority(priority.toUpperCase());
    }


    public Maintenance updateResolutionNotes(Long id, String resolutionNotes) {
        Maintenance maintenance = getMaintenanceById(id);

        if (resolutionNotes == null || resolutionNotes.isBlank()) {
            throw new IllegalArgumentException(
                    "Resolution notes are required"
            );
        }

        maintenance.setResolutionNotes(resolutionNotes.trim());

        return maintenanceRepository.save(maintenance);
    }


    public List<Maintenance> getMaintenanceBetween(
            LocalDateTime start,
            LocalDateTime end) {

        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "A valid start and end time are required"
            );
        }

        return maintenanceRepository.findByReportedAtBetween(start, end);
    }

}
