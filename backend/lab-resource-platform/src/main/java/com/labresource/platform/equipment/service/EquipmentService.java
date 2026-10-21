
package com.labresource.platform.equipment.service;

import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final InstitutionRepository institutionRepository;

    public EquipmentService(
            EquipmentRepository equipmentRepository,
            InstitutionRepository institutionRepository) {
        this.equipmentRepository = equipmentRepository;
        this.institutionRepository = institutionRepository;
    }

    public List<Equipment> getAllEquipment() {
        return equipmentRepository.findAll();
    }

    public Equipment getEquipmentById(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Equipment not found with ID: " + id
                        ));
    }

    public List<Equipment> getEquipmentByInstitution(Long institutionId) {
        return equipmentRepository.findByInstitutionId(institutionId);
    }

    @Transactional
    public Equipment createEquipment(Equipment equipment) {
        if (equipment.getInstitutionId() == null
                || !institutionRepository.existsById(
                equipment.getInstitutionId())) {
            throw new IllegalArgumentException(
                    "The specified institution does not exist."
            );
        }

        if (equipment.getName() == null
                || equipment.getName().isBlank()) {
            throw new IllegalArgumentException(
                    "Equipment name is required."
            );
        }

        if (equipment.getQuantity() == null
                || equipment.getQuantity() < 1) {
            throw new IllegalArgumentException(
                    "Equipment quantity must be at least 1."
            );
        }

        if (equipment.getStatus() == null
                || equipment.getStatus().isBlank()) {
            equipment.setStatus("AVAILABLE");
        }

        equipment.setId(null);
        equipment.setCreatedAt(java.time.LocalDateTime.now());

        return equipmentRepository.save(equipment);
    }

    @Transactional
    public Equipment updateEquipment(Long id, Equipment updatedEquipment) {
        Equipment existing = getEquipmentById(id);

        if (updatedEquipment.getName() == null
                || updatedEquipment.getName().isBlank()) {
            throw new IllegalArgumentException(
                    "Equipment name is required."
            );
        }

        if (updatedEquipment.getQuantity() == null
                || updatedEquipment.getQuantity() < 1) {
            throw new IllegalArgumentException(
                    "Equipment quantity must be at least 1."
            );
        }

        if (updatedEquipment.getInstitutionId() == null
                || !institutionRepository.existsById(
                updatedEquipment.getInstitutionId())) {
            throw new IllegalArgumentException(
                    "The specified institution does not exist."
            );
        }

        existing.setName(updatedEquipment.getName());
        existing.setCategory(updatedEquipment.getCategory());
        existing.setDescription(updatedEquipment.getDescription());
        existing.setInstitutionId(updatedEquipment.getInstitutionId());
        existing.setLabLocation(updatedEquipment.getLabLocation());
        existing.setQuantity(updatedEquipment.getQuantity());

        if (updatedEquipment.getStatus() != null
                && !updatedEquipment.getStatus().isBlank()) {
            existing.setStatus(updatedEquipment.getStatus());
        }

        return equipmentRepository.save(existing);
    }

    @Transactional
    public void deleteEquipment(Long id) {
        Equipment equipment = getEquipmentById(id);
        equipmentRepository.delete(equipment);
    }
}
