package com.labresource.platform.analytics.repository;

import com.labresource.platform.analytics.entity.DailyUtilizationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyUtilizationSnapshotRepository extends JpaRepository<DailyUtilizationSnapshot, Long> {

    Optional<DailyUtilizationSnapshot> findBySnapshotDateAndEquipmentId(LocalDate snapshotDate, Long equipmentId);

    List<DailyUtilizationSnapshot> findByEquipmentIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long equipmentId, LocalDate start, LocalDate end);

    List<DailyUtilizationSnapshot> findByInstitutionIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long institutionId, LocalDate start, LocalDate end);

    List<DailyUtilizationSnapshot> findByDepartmentIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long departmentId, LocalDate start, LocalDate end);

    boolean existsBySnapshotDateAndEquipmentId(LocalDate snapshotDate, Long equipmentId);
}
