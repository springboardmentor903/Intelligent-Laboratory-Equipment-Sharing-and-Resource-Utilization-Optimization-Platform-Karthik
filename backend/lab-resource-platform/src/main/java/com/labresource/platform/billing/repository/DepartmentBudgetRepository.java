package com.labresource.platform.billing.repository;

import com.labresource.platform.billing.entity.DepartmentBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentBudgetRepository extends JpaRepository<DepartmentBudget, Long> {

    Optional<DepartmentBudget> findByDepartmentIdAndFiscalYear(Long departmentId, Integer fiscalYear);

    boolean existsByDepartmentIdAndFiscalYear(Long departmentId, Integer fiscalYear);

    List<DepartmentBudget> findByInstitutionIdAndFiscalYear(Long institutionId, Integer fiscalYear);

    List<DepartmentBudget> findByDepartmentIdOrderByFiscalYearDesc(Long departmentId);
}
