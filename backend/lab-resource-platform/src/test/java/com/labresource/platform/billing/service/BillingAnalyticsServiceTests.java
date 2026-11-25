package com.labresource.platform.billing.service;

import com.labresource.platform.billing.entity.Invoice;
import com.labresource.platform.billing.entity.UsageCharge;
import com.labresource.platform.billing.model.*;
import com.labresource.platform.billing.repository.InvoiceRepository;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingAnalyticsServiceTests {

    @Mock
    private UsageChargeRepository usageChargeRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private InvoiceRepository invoiceRepository;

    private BillingAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new BillingAnalyticsService(
                usageChargeRepository,
                equipmentRepository,
                departmentRepository,
                workOrderRepository,
                invoiceRepository
        );
    }

    @Test
    @DisplayName("Should return equipment costs with separate maintenance and usage costs")
    void returnsEquipmentCostsWithSeparateMaintenanceCost() {
        Equipment eq = new Equipment();
        eq.setId(10L);
        eq.setName("Electron Microscope");
        eq.setAssetTag("MIC-001");
        eq.setInstitutionId(1L);

        when(equipmentRepository.findByInstitutionId(1L)).thenReturn(List.of(eq));

        UsageCharge internalCharge = UsageCharge.builder()
                .equipmentId(10L)
                .institutionId(1L)
                .chargeType(ChargeType.INTERNAL)
                .usageMinutes(60L)
                .amount(new BigDecimal("100.00"))
                .status(ChargeStatus.PENDING)
                .build();

        UsageCharge externalCharge = UsageCharge.builder()
                .equipmentId(10L)
                .institutionId(1L)
                .chargeType(ChargeType.EXTERNAL)
                .usageMinutes(120L)
                .amount(new BigDecimal("300.00"))
                .status(ChargeStatus.PENDING)
                .build();

        when(usageChargeRepository.findAll(any(Specification.class))).thenReturn(List.of(internalCharge, externalCharge));

        // Maintenance cost for equipment 10 is 450.00
        when(workOrderRepository.sumCompletedCostByEquipmentIdsAndPeriod(eq(List.of(10L)), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{10L, new BigDecimal("450.00")}));

        List<EquipmentCostDTO> result = analyticsService.getEquipmentCosts(1L, null, null, null);

        assertThat(result).hasSize(1);
        EquipmentCostDTO dto = result.get(0);
        assertThat(dto.getEquipmentName()).isEqualTo("Electron Microscope");
        assertThat(dto.getInternalCost()).isEqualByComparingTo("100.00");
        assertThat(dto.getExternalCost()).isEqualByComparingTo("300.00");
        assertThat(dto.getTotalUsageCost()).isEqualByComparingTo("400.00");
        assertThat(dto.getMaintenanceCost()).isEqualByComparingTo("450.00");
        assertThat(dto.getUsageHours()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("Should return department allocations with percentages and unallocated charges")
    void returnsDepartmentAllocations() {
        Department dept1 = new Department();
        dept1.setId(1L);
        dept1.setName("Bio");
        dept1.setInstitutionId(1L);

        when(departmentRepository.findByInstitutionId(1L)).thenReturn(List.of(dept1));

        UsageCharge allocatedCharge = UsageCharge.builder()
                .departmentId(1L)
                .chargeType(ChargeType.INTERNAL)
                .amount(new BigDecimal("600.00"))
                .status(ChargeStatus.PENDING)
                .build();

        UsageCharge unallocatedCharge = UsageCharge.builder()
                .departmentId(null) // No department
                .chargeType(ChargeType.INTERNAL)
                .amount(new BigDecimal("400.00"))
                .status(ChargeStatus.PENDING)
                .build();

        when(usageChargeRepository.findAll(any(Specification.class))).thenReturn(List.of(allocatedCharge, unallocatedCharge));

        DepartmentAllocationResponse response = analyticsService.getDepartmentAllocations(1L, null, null);

        assertThat(response.getGrandTotalCost()).isEqualByComparingTo("1000.00");
        assertThat(response.getAllocations()).hasSize(1);

        DepartmentAllocationDTO bioAlloc = response.getAllocations().get(0);
        assertThat(bioAlloc.getDepartmentName()).isEqualTo("Bio");
        assertThat(bioAlloc.getTotalCost()).isEqualByComparingTo("600.00");
        assertThat(bioAlloc.getPercentageShare()).isEqualByComparingTo("60.00");

        DepartmentAllocationDTO unallocated = response.getUnallocated();
        assertThat(unallocated.getTotalCost()).isEqualByComparingTo("400.00");
        assertThat(unallocated.getPercentageShare()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("Should return accurate financial summary for issued, received, and pending charges")
    void returnsFinancialSummary() {
        Invoice issuedIssued = Invoice.builder()
                .id(1L)
                .institutionId(1L)
                .status(InvoiceStatus.ISSUED)
                .totalAmount(new BigDecimal("2000.00"))
                .dueDate(LocalDate.now().plusDays(10))
                .build();

        Invoice issuedOverdue = Invoice.builder()
                .id(2L)
                .institutionId(1L)
                .status(InvoiceStatus.ISSUED)
                .totalAmount(new BigDecimal("1500.00"))
                .dueDate(LocalDate.now().minusDays(5)) // overdue
                .build();

        Invoice issuedPaid = Invoice.builder()
                .id(3L)
                .institutionId(1L)
                .status(InvoiceStatus.PAID)
                .totalAmount(new BigDecimal("3000.00"))
                .dueDate(LocalDate.now().minusDays(10))
                .build();

        when(invoiceRepository.findIssuedForSummary(eq(1L), any(), any()))
                .thenReturn(List.of(issuedIssued, issuedOverdue, issuedPaid));

        when(invoiceRepository.findReceivedForSummary(eq(1L), any(), any()))
                .thenReturn(List.of());

        when(usageChargeRepository.sumPendingChargesAmount(1L)).thenReturn(new BigDecimal("800.00"));
        when(usageChargeRepository.countPendingCharges(1L)).thenReturn(4L);

        FinancialSummaryDTO summary = analyticsService.getFinancialSummary(1L, null, null);

        // Issued invoiced = 2000 + 1500 + 3000 = 6500.00
        assertThat(summary.getIssuedInvoiced()).isEqualByComparingTo("6500.00");
        assertThat(summary.getIssuedPaid()).isEqualByComparingTo("3000.00");
        // Outstanding = 2000 + 1500 = 3500.00
        assertThat(summary.getIssuedOutstanding()).isEqualByComparingTo("3500.00");
        assertThat(summary.getIssuedOverdue()).isEqualByComparingTo("1500.00");
        assertThat(summary.getPendingChargesAmount()).isEqualByComparingTo("800.00");
        assertThat(summary.getPendingChargesCount()).isEqualTo(4L);
    }
}
