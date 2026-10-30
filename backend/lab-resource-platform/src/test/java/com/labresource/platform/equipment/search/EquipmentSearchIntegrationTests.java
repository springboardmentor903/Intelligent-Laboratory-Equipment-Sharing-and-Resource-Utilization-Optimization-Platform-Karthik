package com.labresource.platform.equipment.search;

import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.equipment.service.EquipmentService;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentSearchIntegrationTests {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EquipmentSearchService searchService;

    private EquipmentService equipmentService;

    @BeforeEach
    void setUp() {
        equipmentService = new EquipmentService(
                equipmentRepository,
                institutionRepository,
                departmentRepository,
                searchService
        );
    }

    @Test
    void searchEquipmentUsesElasticsearchWhenEnabledAndMatchesFound() {
        when(searchService.isEnabled()).thenReturn(true);
        when(searchService.searchEquipmentIds(
                eq("Laser"), eq("Optics"), eq(null), eq(null), eq("AVAILABLE"), eq(1L), eq(2L), eq(null)
        )).thenReturn(Optional.of(List.of(10L, 20L)));

        Equipment eq1 = new Equipment();
        eq1.setId(10L);
        Equipment eq2 = new Equipment();
        eq2.setId(20L);
        // Return reversed from JPA to verify order-preservation based on search relevance
        when(equipmentRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(eq2, eq1));

        List<Equipment> results = equipmentService.searchEquipment(
                "Laser", "Optics", null, null, "AVAILABLE", null, 1L, 2L
        );

        assertEquals(2, results.size());
        assertEquals(10L, results.get(0).getId());
        assertEquals(20L, results.get(1).getId());
        verify(searchService).searchEquipmentIds("Laser", "Optics", null, null, "AVAILABLE", 1L, 2L, null);
        verify(equipmentRepository).findAllById(List.of(10L, 20L));
        verify(equipmentRepository, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchEquipmentFallsBackToRelationalSearchWhenElasticsearchDisabled() {
        when(searchService.isEnabled()).thenReturn(false);

        Equipment eq1 = new Equipment();
        eq1.setId(5L);
        when(equipmentRepository.search(
                "Microscope", null, null, null, null, null, 1L, 3L
        )).thenReturn(List.of(eq1));

        List<Equipment> results = equipmentService.searchEquipment(
                "Microscope", null, null, null, null, null, 1L, 3L
        );

        assertEquals(1, results.size());
        assertEquals(5L, results.get(0).getId());
        verify(equipmentRepository).search("Microscope", null, null, null, null, null, 1L, 3L);
        verify(searchService, never()).searchEquipmentIds(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchEquipmentFallsBackToRelationalSearchWhenElasticsearchReturnsEmptyOptional() {
        when(searchService.isEnabled()).thenReturn(true);
        when(searchService.searchEquipmentIds(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(Optional.empty()); // e.g. Elasticsearch failure or timeout

        Equipment eq1 = new Equipment();
        eq1.setId(8L);
        when(equipmentRepository.search(
                "Centrifuge", null, null, null, null, null, 1L, 4L
        )).thenReturn(List.of(eq1));

        List<Equipment> results = equipmentService.searchEquipment(
                "Centrifuge", null, null, null, null, null, 1L, 4L
        );

        assertEquals(1, results.size());
        assertEquals(8L, results.get(0).getId());
        verify(equipmentRepository).search("Centrifuge", null, null, null, null, null, 1L, 4L);
    }

    @Test
    void createEquipmentIndexesInElasticsearch() {
        when(institutionRepository.existsById(1L)).thenReturn(true);
        Department dept = new Department();
        dept.setId(2L);
        dept.setInstitutionId(1L);
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(dept));

        Equipment input = new Equipment();
        input.setName("Spectrophotometer");
        input.setInstitutionId(1L);
        input.setDepartmentId(2L);
        input.setQuantity(1);

        Equipment saved = new Equipment();
        saved.setId(50L);
        saved.setName("Spectrophotometer");
        saved.setInstitutionId(1L);
        saved.setDepartmentId(2L);
        when(equipmentRepository.save(any(Equipment.class))).thenReturn(saved);

        Equipment result = equipmentService.createEquipment(input);

        assertEquals(50L, result.getId());
        verify(equipmentRepository).save(any(Equipment.class));
        verify(searchService).indexEquipment(saved);
    }

    @Test
    void deleteEquipmentRemovesFromElasticsearch() {
        Equipment existing = new Equipment();
        existing.setId(75L);
        when(equipmentRepository.findById(75L)).thenReturn(Optional.of(existing));

        equipmentService.deleteEquipment(75L);

        verify(equipmentRepository).delete(existing);
        verify(searchService).deleteEquipment(75L);
    }
}
