package com.labresource.platform.equipment.search;

import com.labresource.platform.equipment.entity.Equipment;

import java.util.List;
import java.util.Optional;

public interface EquipmentSearchService {

    boolean isEnabled();

    void indexEquipment(Equipment equipment);

    void deleteEquipment(Long equipmentId);

    Optional<List<Long>> searchEquipmentIds(
            String name,
            String category,
            String description,
            String labLocation,
            String status,
            Long institutionId,
            Long departmentId,
            String tag
    );
}
