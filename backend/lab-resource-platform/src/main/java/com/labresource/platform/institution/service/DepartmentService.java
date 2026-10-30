package com.labresource.platform.institution.service;

import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                             InstitutionRepository institutionRepository) {
        this(departmentRepository, institutionRepository, null, null);
    }

    @Autowired
    public DepartmentService(DepartmentRepository departmentRepository,
                             InstitutionRepository institutionRepository,
                             @Autowired(required = false) UserRepository userRepository,
                             @Autowired(required = false) EquipmentRepository equipmentRepository) {
        this.departmentRepository = departmentRepository;
        this.institutionRepository = institutionRepository;
        this.userRepository = userRepository;
        this.equipmentRepository = equipmentRepository;
    }

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public List<Department> getDepartmentsByInstitution(Long institutionId) {
        if (institutionId == null) {
            return departmentRepository.findAll();
        }
        return departmentRepository.findByInstitutionId(institutionId);
    }

    public Department getDepartmentById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found with ID: " + id));
    }

    @Transactional
    public Department createDepartment(Department department) {
        if (department.getInstitutionId() == null
                || !institutionRepository.existsById(department.getInstitutionId())) {
            throw new IllegalArgumentException("The specified institution does not exist.");
        }
        if (department.getName() == null || department.getName().isBlank()) {
            throw new IllegalArgumentException("Department name is required.");
        }
        String trimmedName = department.getName().trim();
        if (trimmedName.length() > 150) {
            throw new IllegalArgumentException("Department name must be 150 characters or fewer.");
        }
        if (departmentRepository.existsByInstitutionIdAndNameIgnoreCase(
                department.getInstitutionId(), trimmedName)) {
            throw new IllegalArgumentException(
                    "Department with this name already exists in this institution.");
        }

        department.setId(null);
        department.setName(trimmedName);
        department.setCreatedAt(LocalDateTime.now());
        return departmentRepository.save(department);
    }

    @Transactional
    public Department updateDepartment(Long id, String newName) {
        Department department = getDepartmentById(id);
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Department name is required.");
        }
        String trimmedName = newName.trim();
        if (trimmedName.length() > 150) {
            throw new IllegalArgumentException("Department name must be 150 characters or fewer.");
        }
        if (departmentRepository.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(
                department.getInstitutionId(), trimmedName, id)) {
            throw new IllegalArgumentException("Department with this name already exists in this institution.");
        }
        department.setName(trimmedName);
        return departmentRepository.save(department);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        Department department = getDepartmentById(id);
        if (userRepository != null) {
            userRepository.clearDepartmentId(id);
        }
        if (equipmentRepository != null) {
            equipmentRepository.clearDepartmentId(id);
        }
        departmentRepository.delete(department);
    }
}
