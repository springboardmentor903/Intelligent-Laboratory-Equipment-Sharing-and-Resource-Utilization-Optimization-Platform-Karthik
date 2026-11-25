package com.labresource.platform.billing.service;

import com.labresource.platform.billing.entity.DepartmentBudget;
import com.labresource.platform.billing.model.BudgetOverviewItem;
import com.labresource.platform.billing.model.BudgetStatus;
import com.labresource.platform.billing.model.CreateBudgetRequest;
import com.labresource.platform.billing.repository.DepartmentBudgetRepository;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTests {

    @Mock
    private DepartmentBudgetRepository budgetRepository;
    @Mock
    private UsageChargeRepository usageChargeRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgetRepository, usageChargeRepository, departmentRepository, 1);
    }

    @Test
    @DisplayName("Should correctly calculate fiscal year date boundaries with configurable start month")
    void calculatesFiscalYearBoundaries() {
        // Calendar year (starts in January)
        BudgetService calService = new BudgetService(budgetRepository, usageChargeRepository, departmentRepository, 1);
        assertThat(calService.getFiscalYear(LocalDate.of(2026, 6, 15))).isEqualTo(2026);
        assertThat(calService.getFiscalYearStartDate(2026)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(calService.getFiscalYearEndDate(2026)).isEqualTo(LocalDate.of(2026, 12, 31));

        // April fiscal year (starts in April)
        BudgetService aprService = new BudgetService(budgetRepository, usageChargeRepository, departmentRepository, 4);
        assertThat(aprService.getFiscalYear(LocalDate.of(2026, 3, 31))).isEqualTo(2025);
        assertThat(aprService.getFiscalYear(LocalDate.of(2026, 4, 1))).isEqualTo(2026);
        assertThat(aprService.getFiscalYearStartDate(2026)).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(aprService.getFiscalYearEndDate(2026)).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    @DisplayName("Should calculate OK status when spend is below 80%")
    void calculatesOkStatusWhenSpendBelowEightyPercent() {
        Department dept = new Department();
        dept.setId(10L);
        dept.setName("Biotech");
        dept.setInstitutionId(1L);

        DepartmentBudget budget = DepartmentBudget.builder()
                .id(1L)
                .institutionId(1L)
                .departmentId(10L)
                .fiscalYear(2026)
                .allocatedAmount(new BigDecimal("100000.00"))
                .build();

        when(departmentRepository.findByInstitutionId(1L)).thenReturn(List.of(dept));
        when(budgetRepository.findByInstitutionIdAndFiscalYear(1L, 2026)).thenReturn(List.of(budget));
        // 50,000 spend = 50%
        when(usageChargeRepository.sumDepartmentSpend(eq(10L), any(), any()))
                .thenReturn(new BigDecimal("50000.00"));

        List<BudgetOverviewItem> overview = budgetService.getBudgetsOverview(1L, 2026);

        assertThat(overview).hasSize(1);
        BudgetOverviewItem item = overview.get(0);
        assertThat(item.getStatus()).isEqualTo(BudgetStatus.OK);
        assertThat(item.getAllocatedAmount()).isEqualByComparingTo("100000.00");
        assertThat(item.getSpentAmount()).isEqualByComparingTo("50000.00");
        assertThat(item.getRemainingAmount()).isEqualByComparingTo("50000.00");
        assertThat(item.getUtilizationPercentage()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Should calculate WARNING status when spend is 80% to 100%")
    void calculatesWarningStatusWhenSpendEightyToHundredPercent() {
        Department dept = new Department();
        dept.setId(10L);
        dept.setName("Physics");
        dept.setInstitutionId(1L);

        DepartmentBudget budget = DepartmentBudget.builder()
                .id(2L)
                .institutionId(1L)
                .departmentId(10L)
                .fiscalYear(2026)
                .allocatedAmount(new BigDecimal("100000.00"))
                .build();

        when(departmentRepository.findById(10L)).thenReturn(Optional.of(dept));
        when(budgetRepository.findByDepartmentIdAndFiscalYear(10L, 2026)).thenReturn(Optional.of(budget));
        // 85,000 spend = 85%
        when(usageChargeRepository.sumDepartmentSpend(eq(10L), any(), any()))
                .thenReturn(new BigDecimal("85000.00"));

        BudgetOverviewItem item = budgetService.getDepartmentBudget(10L, 2026);

        assertThat(item.getStatus()).isEqualTo(BudgetStatus.WARNING);
        assertThat(item.getUtilizationPercentage()).isEqualByComparingTo("85.00");
        assertThat(item.getRemainingAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("Should calculate EXCEEDED status when spend exceeds 100%")
    void calculatesExceededStatusWhenSpendExceedsHundredPercent() {
        Department dept = new Department();
        dept.setId(10L);
        dept.setName("Chemistry");
        dept.setInstitutionId(1L);

        DepartmentBudget budget = DepartmentBudget.builder()
                .id(3L)
                .institutionId(1L)
                .departmentId(10L)
                .fiscalYear(2026)
                .allocatedAmount(new BigDecimal("100000.00"))
                .build();

        when(departmentRepository.findById(10L)).thenReturn(Optional.of(dept));
        when(budgetRepository.findByDepartmentIdAndFiscalYear(10L, 2026)).thenReturn(Optional.of(budget));
        // 110,000 spend = 110%
        when(usageChargeRepository.sumDepartmentSpend(eq(10L), any(), any()))
                .thenReturn(new BigDecimal("110000.00"));

        BudgetOverviewItem item = budgetService.getDepartmentBudget(10L, 2026);

        assertThat(item.getStatus()).isEqualTo(BudgetStatus.EXCEEDED);
        assertThat(item.getUtilizationPercentage()).isEqualByComparingTo("110.00");
        assertThat(item.getRemainingAmount()).isEqualByComparingTo("-10000.00");
    }

    @Test
    @DisplayName("Should calculate NO_BUDGET status when department has no budget allocated")
    void calculatesNoBudgetStatusWhenNotAllocated() {
        Department dept = new Department();
        dept.setId(15L);
        dept.setName("Mathematics");
        dept.setInstitutionId(1L);

        when(departmentRepository.findById(15L)).thenReturn(Optional.of(dept));
        when(budgetRepository.findByDepartmentIdAndFiscalYear(15L, 2026)).thenReturn(Optional.empty());
        when(usageChargeRepository.sumDepartmentSpend(eq(15L), any(), any()))
                .thenReturn(new BigDecimal("1500.00"));

        BudgetOverviewItem item = budgetService.getDepartmentBudget(15L, 2026);

        assertThat(item.getStatus()).isEqualTo(BudgetStatus.NO_BUDGET);
        assertThat(item.getAllocatedAmount()).isEqualByComparingTo("0.00");
        assertThat(item.getSpentAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Should prevent duplicate budget for same department and fiscal year")
    void preventsDuplicateBudgetForSameDeptAndYear() {
        Department dept = new Department();
        dept.setId(20L);
        dept.setName("Computer Science");
        dept.setInstitutionId(1L);

        when(departmentRepository.findById(20L)).thenReturn(Optional.of(dept));
        when(budgetRepository.existsByDepartmentIdAndFiscalYear(20L, 2026)).thenReturn(true);

        CreateBudgetRequest request = new CreateBudgetRequest(20L, 2026, new BigDecimal("500000.00"));

        assertThatThrownBy(() -> budgetService.createBudget(1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Budget already exists");

        verify(budgetRepository, never()).save(any(DepartmentBudget.class));
    }
}
