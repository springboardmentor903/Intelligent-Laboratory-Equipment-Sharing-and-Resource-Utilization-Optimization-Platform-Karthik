package com.labresource.platform.billing.service;

import com.labresource.platform.billing.entity.DepartmentBudget;
import com.labresource.platform.billing.model.BudgetOverviewItem;
import com.labresource.platform.billing.model.BudgetStatus;
import com.labresource.platform.billing.model.CreateBudgetRequest;
import com.labresource.platform.billing.repository.DepartmentBudgetRepository;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BudgetService {

    private final DepartmentBudgetRepository budgetRepository;
    private final UsageChargeRepository usageChargeRepository;
    private final DepartmentRepository departmentRepository;
    private final int fiscalYearStartMonth;

    @Autowired
    public BudgetService(
            DepartmentBudgetRepository budgetRepository,
            UsageChargeRepository usageChargeRepository,
            DepartmentRepository departmentRepository,
            @Value("${app.billing.fiscal-year-start-month:1}") int fiscalYearStartMonth) {
        this.budgetRepository = budgetRepository;
        this.usageChargeRepository = usageChargeRepository;
        this.departmentRepository = departmentRepository;
        this.fiscalYearStartMonth = fiscalYearStartMonth <= 0 || fiscalYearStartMonth > 12 ? 1 : fiscalYearStartMonth;
    }

    public int getCurrentFiscalYear() {
        return getFiscalYear(LocalDate.now());
    }

    public int getFiscalYear(LocalDate date) {
        if (date == null) date = LocalDate.now();
        if (fiscalYearStartMonth <= 1) {
            return date.getYear();
        }
        return date.getMonthValue() >= fiscalYearStartMonth ? date.getYear() : date.getYear() - 1;
    }

    public LocalDate getFiscalYearStartDate(int fiscalYear) {
        return LocalDate.of(fiscalYear, fiscalYearStartMonth, 1);
    }

    public LocalDate getFiscalYearEndDate(int fiscalYear) {
        return getFiscalYearStartDate(fiscalYear).plusYears(1).minusDays(1);
    }

    @Transactional(readOnly = true)
    public List<BudgetOverviewItem> getBudgetsOverview(Long institutionId, Integer fiscalYear) {
        int year = (fiscalYear != null && fiscalYear > 1900) ? fiscalYear : getCurrentFiscalYear();
        List<Department> departments = departmentRepository.findByInstitutionId(institutionId);
        List<DepartmentBudget> budgets = budgetRepository.findByInstitutionIdAndFiscalYear(institutionId, year);
        Map<Long, DepartmentBudget> budgetMap = budgets.stream()
                .collect(Collectors.toMap(DepartmentBudget::getDepartmentId, b -> b));

        LocalDateTime start = getFiscalYearStartDate(year).atStartOfDay();
        LocalDateTime end = getFiscalYearEndDate(year).atTime(LocalTime.MAX);

        return departments.stream().map(dept -> {
            DepartmentBudget budget = budgetMap.get(dept.getId());
            BigDecimal spent = usageChargeRepository.sumDepartmentSpend(dept.getId(), start, end);
            if (spent == null) spent = BigDecimal.ZERO;
            spent = spent.setScale(2, RoundingMode.HALF_UP);

            if (budget == null) {
                return BudgetOverviewItem.builder()
                        .departmentId(dept.getId())
                        .departmentName(dept.getName())
                        .fiscalYear(year)
                        .budgetId(null)
                        .allocatedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .spentAmount(spent)
                        .remainingAmount(BigDecimal.ZERO.subtract(spent).setScale(2, RoundingMode.HALF_UP))
                        .utilizationPercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .status(BudgetStatus.NO_BUDGET)
                        .updatedAt(null)
                        .build();
            }

            BigDecimal allocated = budget.getAllocatedAmount() != null
                    ? budget.getAllocatedAmount().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal remaining = allocated.subtract(spent).setScale(2, RoundingMode.HALF_UP);

            BigDecimal utilization = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (allocated.compareTo(BigDecimal.ZERO) > 0) {
                utilization = spent.multiply(BigDecimal.valueOf(100))
                        .divide(allocated, 2, RoundingMode.HALF_UP);
            }

            BudgetStatus status;
            if (utilization.compareTo(BigDecimal.valueOf(100)) > 0) {
                status = BudgetStatus.EXCEEDED;
            } else if (utilization.compareTo(BigDecimal.valueOf(80)) >= 0) {
                status = BudgetStatus.WARNING;
            } else {
                status = BudgetStatus.OK;
            }

            return BudgetOverviewItem.builder()
                    .departmentId(dept.getId())
                    .departmentName(dept.getName())
                    .fiscalYear(year)
                    .budgetId(budget.getId())
                    .allocatedAmount(allocated)
                    .spentAmount(spent)
                    .remainingAmount(remaining)
                    .utilizationPercentage(utilization)
                    .status(status)
                    .updatedAt(budget.getUpdatedAt())
                    .build();
        }).sorted(Comparator.comparing(BudgetOverviewItem::getDepartmentName, String.CASE_INSENSITIVE_ORDER))
          .toList();
    }

    @Transactional(readOnly = true)
    public BudgetOverviewItem getDepartmentBudget(Long departmentId, Integer fiscalYear) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found."));

        int year = (fiscalYear != null && fiscalYear > 1900) ? fiscalYear : getCurrentFiscalYear();
        Optional<DepartmentBudget> budgetOpt = budgetRepository.findByDepartmentIdAndFiscalYear(departmentId, year);

        LocalDateTime start = getFiscalYearStartDate(year).atStartOfDay();
        LocalDateTime end = getFiscalYearEndDate(year).atTime(LocalTime.MAX);
        BigDecimal spent = usageChargeRepository.sumDepartmentSpend(departmentId, start, end);
        if (spent == null) spent = BigDecimal.ZERO;
        spent = spent.setScale(2, RoundingMode.HALF_UP);

        if (budgetOpt.isEmpty()) {
            return BudgetOverviewItem.builder()
                    .departmentId(dept.getId())
                    .departmentName(dept.getName())
                    .fiscalYear(year)
                    .budgetId(null)
                    .allocatedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .spentAmount(spent)
                    .remainingAmount(BigDecimal.ZERO.subtract(spent).setScale(2, RoundingMode.HALF_UP))
                    .utilizationPercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .status(BudgetStatus.NO_BUDGET)
                    .updatedAt(null)
                    .build();
        }

        DepartmentBudget budget = budgetOpt.get();
        BigDecimal allocated = budget.getAllocatedAmount() != null
                ? budget.getAllocatedAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = allocated.subtract(spent).setScale(2, RoundingMode.HALF_UP);

        BigDecimal utilization = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (allocated.compareTo(BigDecimal.ZERO) > 0) {
            utilization = spent.multiply(BigDecimal.valueOf(100))
                    .divide(allocated, 2, RoundingMode.HALF_UP);
        }

        BudgetStatus status;
        if (utilization.compareTo(BigDecimal.valueOf(100)) > 0) {
            status = BudgetStatus.EXCEEDED;
        } else if (utilization.compareTo(BigDecimal.valueOf(80)) >= 0) {
            status = BudgetStatus.WARNING;
        } else {
            status = BudgetStatus.OK;
        }

        return BudgetOverviewItem.builder()
                .departmentId(dept.getId())
                .departmentName(dept.getName())
                .fiscalYear(year)
                .budgetId(budget.getId())
                .allocatedAmount(allocated)
                .spentAmount(spent)
                .remainingAmount(remaining)
                .utilizationPercentage(utilization)
                .status(status)
                .updatedAt(budget.getUpdatedAt())
                .build();
    }

    @Transactional
    public DepartmentBudget createBudget(Long institutionId, CreateBudgetRequest request) {
        if (request.getDepartmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department ID is required.");
        }
        Department dept = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found."));
        if (!dept.getInstitutionId().equals(institutionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Department does not belong to your institution.");
        }

        int year = (request.getFiscalYear() != null && request.getFiscalYear() > 1900)
                ? request.getFiscalYear() : getCurrentFiscalYear();

        if (budgetRepository.existsByDepartmentIdAndFiscalYear(request.getDepartmentId(), year)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Budget already exists for department " + dept.getName() + " in fiscal year " + year);
        }

        BigDecimal allocated = request.getAllocatedAmount() != null ? request.getAllocatedAmount() : BigDecimal.ZERO;
        if (allocated.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allocated amount cannot be negative.");
        }

        DepartmentBudget budget = DepartmentBudget.builder()
                .institutionId(institutionId)
                .departmentId(request.getDepartmentId())
                .fiscalYear(year)
                .allocatedAmount(allocated.setScale(2, RoundingMode.HALF_UP))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return budgetRepository.save(budget);
    }

    @Transactional
    public DepartmentBudget updateBudget(Long budgetId, BigDecimal newAllocatedAmount, Long institutionId) {
        DepartmentBudget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget record not found."));
        if (!budget.getInstitutionId().equals(institutionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Budget does not belong to your institution.");
        }

        if (newAllocatedAmount == null || newAllocatedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allocated amount cannot be negative.");
        }

        budget.setAllocatedAmount(newAllocatedAmount.setScale(2, RoundingMode.HALF_UP));
        budget.setUpdatedAt(LocalDateTime.now());
        return budgetRepository.save(budget);
    }
}
