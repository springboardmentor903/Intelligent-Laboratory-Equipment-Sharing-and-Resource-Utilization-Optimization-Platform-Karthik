
package com.labresource.platform.equipment.repository;

import com.labresource.platform.equipment.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByInstitutionId(Long institutionId);

    List<Equipment> findByCategoryIgnoreCase(String category);

    List<Equipment> findByStatus(String status);

    List<Equipment> findByInstitutionIdAndStatus(
            Long institutionId,
            String status
    );
}
