package com.labresource.platform.institution.service;

import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTests {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private Department department;

    @BeforeEach
    void setUp() {
        department = new Department(10L, "Physics");
        department.setId(101L);
    }

    @Test
    void getDepartmentsByInstitutionReturnsMatchingDepartments() {
        when(departmentRepository.findByInstitutionId(10L)).thenReturn(List.of(department));

        List<Department> result = departmentService.getDepartmentsByInstitution(10L);

        assertEquals(1, result.size());
        assertEquals("Physics", result.get(0).getName());
        verify(departmentRepository).findByInstitutionId(10L);
    }

    @Test
    void createDepartmentValidatesInstitutionAndUniqueness() {
        when(institutionRepository.existsById(10L)).thenReturn(true);
        when(departmentRepository.existsByInstitutionIdAndNameIgnoreCase(10L, "Physics")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> i.getArgument(0));

        Department created = departmentService.createDepartment(new Department(10L, "  Physics  "));

        assertEquals("Physics", created.getName());
        assertEquals(10L, created.getInstitutionId());
        assertNotNull(created.getCreatedAt());
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    void createDepartmentRejectsDuplicateNameInSameInstitution() {
        when(institutionRepository.existsById(10L)).thenReturn(true);
        when(departmentRepository.existsByInstitutionIdAndNameIgnoreCase(10L, "Physics")).thenReturn(true);

        Department duplicate = new Department(10L, "Physics");
        assertThrows(IllegalArgumentException.class, () -> departmentService.createDepartment(duplicate));
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void createDepartmentRejectsMissingInstitution() {
        when(institutionRepository.existsById(99L)).thenReturn(false);

        Department missing = new Department(99L, "Chemistry");
        assertThrows(IllegalArgumentException.class, () -> departmentService.createDepartment(missing));
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void updateDepartmentRenamesSuccessfullyWhenValid() {
        when(departmentRepository.findById(101L)).thenReturn(Optional.of(department));
        when(departmentRepository.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(10L, "Astrophysics", 101L)).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> i.getArgument(0));

        Department updated = departmentService.updateDepartment(101L, "  Astrophysics  ");

        assertEquals("Astrophysics", updated.getName());
        verify(departmentRepository).save(department);
    }

    @Test
    void updateDepartmentRejectsDuplicateNameInSameInstitution() {
        when(departmentRepository.findById(101L)).thenReturn(Optional.of(department));
        when(departmentRepository.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(10L, "Chemistry", 101L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> departmentService.updateDepartment(101L, "Chemistry"));
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void deleteDepartmentSafelyClearsUserAndEquipmentReferences() {
        when(departmentRepository.findById(101L)).thenReturn(Optional.of(department));

        departmentService.deleteDepartment(101L);

        verify(userRepository).clearDepartmentId(101L);
        verify(equipmentRepository).clearDepartmentId(101L);
        verify(departmentRepository).delete(department);
    }
}
