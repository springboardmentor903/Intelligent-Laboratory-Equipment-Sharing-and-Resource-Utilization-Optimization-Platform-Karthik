package com.labresource.platform.institution.repository;

import com.labresource.platform.institution.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByInstitutionId(Long institutionId);
    Optional<Department> findByInstitutionIdAndNameIgnoreCase(Long institutionId, String name);
    boolean existsByInstitutionIdAndNameIgnoreCase(Long institutionId, String name);
    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);
}
