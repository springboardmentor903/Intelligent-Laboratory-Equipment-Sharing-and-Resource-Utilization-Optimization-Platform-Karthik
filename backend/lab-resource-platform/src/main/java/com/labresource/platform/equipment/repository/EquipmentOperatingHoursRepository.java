package com.labresource.platform.equipment.repository;

import com.labresource.platform.equipment.entity.EquipmentOperatingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EquipmentOperatingHoursRepository extends JpaRepository<EquipmentOperatingHours, Long> {

    List<EquipmentOperatingHours> findByEquipmentIdOrderByDayOfWeekAsc(Long equipmentId);

    List<EquipmentOperatingHours> findByInstitutionIdAndEquipmentIdIsNullOrderByDayOfWeekAsc(Long institutionId);

    List<EquipmentOperatingHours> findByEquipmentIdIn(List<Long> equipmentIds);
}
