package com.labresource.platform.equipment.service;

import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.equipment.search.EquipmentSearchService;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.labresource.platform.equipment.event.EquipmentStatusChangedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class EquipmentService {

    private static final int MAX_SPECIFICATIONS = 100;
    private static final int MAX_TAGS = 30;
    private static final int MAX_TAG_LENGTH = 50;
    private static final Set<String> EQUIPMENT_STATUSES = Set.of(
            "AVAILABLE", "BOOKED", "UNDER_MAINTENANCE", "OUT_OF_SERVICE", "RETIRED");

    private final EquipmentRepository equipmentRepository;
    private final InstitutionRepository institutionRepository;
    private final DepartmentRepository departmentRepository;
    private final EquipmentSearchService searchService;
    private final ApplicationEventPublisher eventPublisher;

    public EquipmentService(
            EquipmentRepository equipmentRepository,
            InstitutionRepository institutionRepository) {
        this(equipmentRepository, institutionRepository, null, null);
    }

    public EquipmentService(
            EquipmentRepository equipmentRepository,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository) {
        this(equipmentRepository, institutionRepository, departmentRepository, null);
    }

    public EquipmentService(
            EquipmentRepository equipmentRepository,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository,
            EquipmentSearchService searchService) {
        this(equipmentRepository, institutionRepository, departmentRepository, searchService, null);
    }

    @Autowired
    public EquipmentService(
            EquipmentRepository equipmentRepository,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository,
            EquipmentSearchService searchService,
            ApplicationEventPublisher eventPublisher) {
        this.equipmentRepository = equipmentRepository;
        this.institutionRepository = institutionRepository;
        this.departmentRepository = departmentRepository;
        this.searchService = searchService;
        this.eventPublisher = eventPublisher;
    }

    public List<Equipment> getAllEquipment() {
        return equipmentRepository.findAll();
    }

    public List<Equipment> searchEquipment(
            String name,
            String category,
            String description,
            String labLocation,
            String status,
            String tag,
            Long institutionId) {
        if (searchService != null && searchService.isEnabled()) {
            Optional<List<Long>> matchIds = searchService.searchEquipmentIds(
                    name, category, description, labLocation, status, institutionId, null, tag);
            if (matchIds.isPresent()) {
                return hydrateInOrder(matchIds.get());
            }
        }
        return equipmentRepository.search(
                name,
                category,
                description,
                labLocation,
                status,
                tag,
                institutionId);
    }

    public List<Equipment> searchEquipment(
            String name,
            String category,
            String description,
            String labLocation,
            String status,
            String tag,
            Long institutionId,
            Long departmentId) {
        if (searchService != null && searchService.isEnabled()) {
            Optional<List<Long>> matchIds = searchService.searchEquipmentIds(
                    name, category, description, labLocation, status, institutionId, departmentId, tag);
            if (matchIds.isPresent()) {
                return hydrateInOrder(matchIds.get());
            }
        }
        if (departmentId == null) {
            return searchEquipment(name, category, description, labLocation, status, tag, institutionId);
        }
        return equipmentRepository.search(
                name,
                category,
                description,
                labLocation,
                status,
                tag,
                institutionId,
                departmentId);
    }

    private List<Equipment> hydrateInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Equipment> entities = equipmentRepository.findAllById(ids);
        Map<Long, Equipment> map = new HashMap<>();
        for (Equipment eq : entities) {
            if (eq != null && eq.getId() != null) {
                map.put(eq.getId(), eq);
            }
        }
        List<Equipment> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Equipment eq = map.get(id);
            if (eq != null) {
                ordered.add(eq);
            }
        }
        return ordered;
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

    public List<Equipment> getEquipmentByDepartment(Long departmentId) {
        return equipmentRepository.findByDepartmentId(departmentId);
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

        if (equipment.getDepartmentId() != null && departmentRepository != null) {
            Department dept = departmentRepository.findById(equipment.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("The specified department does not exist."));
            if (!equipment.getInstitutionId().equals(dept.getInstitutionId())) {
                throw new IllegalArgumentException("Department does not belong to the specified institution.");
            }
        }

        normalizeInventoryFields(equipment, true, null);
        if (!"AVAILABLE".equals(equipment.getStatus())) {
            throw new IllegalArgumentException(
                    "New equipment must start as AVAILABLE. Booking and maintenance workflows manage later status changes.");
        }

        if (equipment.getQuantity() == null
                || equipment.getQuantity() < 1) {
            throw new IllegalArgumentException(
                    "Equipment quantity must be at least 1."
            );
        }

        equipment.setSpecifications(normalizeSpecifications(
                equipment.getSpecifications()));
        equipment.setTags(normalizeTags(equipment.getTags()));

        equipment.setId(null);
        equipment.setCreatedAt(java.time.LocalDateTime.now());

        Equipment saved = equipmentRepository.save(equipment);
        publishEquipmentChange(saved.getId());
        if (searchService != null) {
            searchService.indexEquipment(saved);
        }
        return saved;
    }

    @Transactional
    public Equipment updateEquipment(Long id, Equipment updatedEquipment) {
        Equipment existing = getEquipmentById(id);

        normalizeInventoryFields(updatedEquipment, false, id);

        if (updatedEquipment.getStatus() != null && !updatedEquipment.getStatus().isBlank()) {
            validateManualStatusTransition(existing.getStatus(), updatedEquipment.getStatus());
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

        if (updatedEquipment.getDepartmentId() != null && departmentRepository != null) {
            Department dept = departmentRepository.findById(updatedEquipment.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("The specified department does not exist."));
            if (!updatedEquipment.getInstitutionId().equals(dept.getInstitutionId())) {
                throw new IllegalArgumentException("Department does not belong to the specified institution.");
            }
        }

        if (updatedEquipment.getSpecifications() != null) {
            existing.setSpecifications(normalizeSpecifications(
                    updatedEquipment.getSpecifications()));
        }

        if (updatedEquipment.getTags() != null) {
            existing.setTags(normalizeTags(updatedEquipment.getTags()));
        }

        existing.setName(updatedEquipment.getName().trim());
        if (updatedEquipment.getAssetTag() != null) {
            existing.setAssetTag(updatedEquipment.getAssetTag());
        }
        existing.setCategory(updatedEquipment.getCategory());
        existing.setDescription(updatedEquipment.getDescription());
        existing.setInstitutionId(updatedEquipment.getInstitutionId());
        existing.setDepartmentId(updatedEquipment.getDepartmentId());
        existing.setLabLocation(updatedEquipment.getLabLocation());
        existing.setQuantity(updatedEquipment.getQuantity());

        if (updatedEquipment.getStatus() != null
                && !updatedEquipment.getStatus().isBlank()) {
            existing.setStatus(normalizeStatus(updatedEquipment.getStatus()));
        }

        Equipment saved = equipmentRepository.save(existing);
        publishEquipmentChange(saved.getId());
        if (searchService != null) {
            searchService.indexEquipment(saved);
        }
        return saved;
    }

    private void normalizeInventoryFields(Equipment equipment, boolean creating, Long existingId) {
        if (equipment.getName() == null || equipment.getName().isBlank()) {
            throw new IllegalArgumentException("Equipment name is required.");
        }
        String name = equipment.getName().trim();
        if (name.length() > 150) {
            throw new IllegalArgumentException("Equipment name must be 150 characters or fewer.");
        }
        equipment.setName(name);
        equipment.setCategory(trimAndCheck(equipment.getCategory(), 100, "category"));
        equipment.setDescription(trimAndCheck(equipment.getDescription(), 500, "description"));
        equipment.setLabLocation(trimAndCheck(equipment.getLabLocation(), 150, "lab location"));
        equipment.setAssetTag(trimAndCheck(equipment.getAssetTag(), 100, "asset tag"));

        if (equipment.getAssetTag() != null) {
            boolean duplicate = creating
                    ? equipmentRepository.existsByAssetTagIgnoreCase(equipment.getAssetTag())
                    : equipmentRepository.existsByAssetTagIgnoreCaseAndIdNot(
                            equipment.getAssetTag(), existingId);
            if (duplicate) {
                throw new IllegalArgumentException("Equipment asset tag is already in use.");
            }
        }

        if (creating && (equipment.getStatus() == null || equipment.getStatus().isBlank())) {
            equipment.setStatus("AVAILABLE");
        } else if (equipment.getStatus() != null && !equipment.getStatus().isBlank()) {
            equipment.setStatus(normalizeStatus(equipment.getStatus()));
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!EQUIPMENT_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid equipment status: " + status);
        }
        return normalized;
    }

    private void validateManualStatusTransition(String currentStatus, String requestedStatus) {
        String current = normalizeStatus(currentStatus == null ? "AVAILABLE" : currentStatus);
        String requested = normalizeStatus(requestedStatus);
        if (current.equals(requested)) return;
        if ("RETIRED".equals(current)) {
            throw new IllegalArgumentException("Retired equipment cannot be reactivated or change status.");
        }
        if ("BOOKED".equals(requested)) {
            throw new IllegalArgumentException("BOOKED status is set by booking check-in and cannot be set manually.");
        }
        if ("BOOKED".equals(current) && !"RETIRED".equals(requested)) {
            throw new IllegalArgumentException(
                    "Equipment in BOOKED status can only be changed by the booking workflow or retired.");
        }
        if (!Set.of("AVAILABLE", "UNDER_MAINTENANCE", "OUT_OF_SERVICE", "RETIRED").contains(requested)) {
            throw new IllegalArgumentException("Invalid equipment status transition.");
        }
    }

    private String trimAndCheck(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("Equipment " + fieldName + " must be "
                    + maxLength + " characters or fewer.");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> normalizeSpecifications(Map<String, Object> specifications) {
        if (specifications == null) {
            return new LinkedHashMap<>();
        }
        if (specifications.size() > MAX_SPECIFICATIONS) {
            throw new IllegalArgumentException(
                    "At most 100 equipment specifications are allowed.");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : specifications.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Specification keys must be nonblank and values are required.");
            }
            String normalizedKey = key.trim();
            if (normalized.putIfAbsent(normalizedKey, entry.getValue()) != null) {
                throw new IllegalArgumentException(
                        "Specification keys must be unique after trimming.");
            }
        }
        return normalized;
    }

    private void publishEquipmentChange(Long equipmentId) {
        if (eventPublisher != null && equipmentId != null) {
            eventPublisher.publishEvent(new EquipmentStatusChangedEvent(equipmentId));
        }
    }

    private Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) {
            return new LinkedHashSet<>();
        }
        if (tags.size() > MAX_TAGS) {
            throw new IllegalArgumentException(
                    "At most 30 equipment tags are allowed.");
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                throw new IllegalArgumentException(
                        "Equipment tags must not be blank.");
            }
            String normalizedTag = tag.trim().toLowerCase(Locale.ROOT);
            if (normalizedTag.length() > MAX_TAG_LENGTH) {
                throw new IllegalArgumentException(
                        "Equipment tags must be 50 characters or fewer.");
            }
            normalized.add(normalizedTag);
        }
        return normalized;
    }

    @Transactional
    public void deleteEquipment(Long id) {
        Equipment equipment = getEquipmentById(id);
        if ("RETIRED".equalsIgnoreCase(equipment.getStatus())) return;
        equipment.setStatus("RETIRED");
        Equipment retired = equipmentRepository.save(equipment);
        publishEquipmentChange(retired.getId());
        if (searchService != null) {
            searchService.indexEquipment(retired);
        }
    }
}
