package com.labresource.platform.equipment.service;

import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTests {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @InjectMocks
    private EquipmentService equipmentService;

    @Test
    void equipmentSearchForwardsTagAlongWithSupportedFilters() {
        when(equipmentRepository.search("scope", "category", null, null,
                "AVAILABLE", "optical", 7L)).thenReturn(List.of());

        equipmentService.searchEquipment("scope", "category", null, null,
                "AVAILABLE", "optical", 7L);

        verify(equipmentRepository).search("scope", "category", null, null,
                "AVAILABLE", "optical", 7L);
    }

    @Test
    void createEquipmentNormalizesSpecificationsAndTags() {
        when(institutionRepository.existsById(7L)).thenReturn(true);
        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment request = validEquipment();
        request.setSpecifications(Map.of("  power  ", "1200 W"));
        request.setTags(new LinkedHashSet<>(
                Set.of("  Microscope ", "OPTICAL")));
        request.setCategory("  Optics ");
        request.setStatus(" available ");

        Equipment saved = equipmentService.createEquipment(request);

        assertEquals(Map.of("power", "1200 W"), saved.getSpecifications());
        assertEquals(Set.of("microscope", "optical"), saved.getTags());
        assertEquals("Optics", saved.getCategory());
        assertEquals("AVAILABLE", saved.getStatus());
        verify(equipmentRepository).save(request);
    }

    @Test
    void updateEquipmentPreservesFieldsOmittedFromExistingPutPayloads() {
        Equipment existing = validEquipment();
        existing.setId(12L);
        existing.setSpecifications(Map.of("power", "1200 W"));
        existing.setTags(Set.of("optical"));

        when(equipmentRepository.findById(12L)).thenReturn(Optional.of(existing));
        when(institutionRepository.existsById(7L)).thenReturn(true);
        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment update = validEquipment();
        equipmentService.updateEquipment(12L, update);

        assertEquals(Map.of("power", "1200 W"), existing.getSpecifications());
        assertEquals(Set.of("optical"), existing.getTags());
    }

    @Test
    void createEquipmentRejectsBlankTags() {
        when(institutionRepository.existsById(7L)).thenReturn(true);
        Equipment request = validEquipment();
        request.setTags(Set.of(" "));

        assertThrows(IllegalArgumentException.class,
                () -> equipmentService.createEquipment(request));

        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void createEquipmentRejectsCategoryLongerThanMappedColumn() {
        when(institutionRepository.existsById(7L)).thenReturn(true);
        Equipment request = validEquipment();
        request.setCategory("x".repeat(101));

        assertThrows(IllegalArgumentException.class,
                () -> equipmentService.createEquipment(request));

        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void createEquipmentRejectsStatusLongerThanMappedColumn() {
        when(institutionRepository.existsById(7L)).thenReturn(true);
        Equipment request = validEquipment();
        request.setStatus("x".repeat(31));

        assertThrows(IllegalArgumentException.class,
                () -> equipmentService.createEquipment(request));

        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void createEquipmentRejectsStatusOutsideMentorVocabulary() {
        when(institutionRepository.existsById(7L)).thenReturn(true);
        Equipment request = validEquipment();
        request.setStatus("BROKEN");

        assertThrows(IllegalArgumentException.class,
                () -> equipmentService.createEquipment(request));

        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    private Equipment validEquipment() {
        Equipment equipment = new Equipment();
        equipment.setName("Microscope");
        equipment.setInstitutionId(7L);
        equipment.setQuantity(1);
        return equipment;
    }
}
