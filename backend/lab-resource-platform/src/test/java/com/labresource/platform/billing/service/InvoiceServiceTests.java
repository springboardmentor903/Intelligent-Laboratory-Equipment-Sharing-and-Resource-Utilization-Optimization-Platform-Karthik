package com.labresource.platform.billing.service;

import com.labresource.platform.billing.entity.Invoice;
import com.labresource.platform.billing.entity.UsageCharge;
import com.labresource.platform.billing.model.*;
import com.labresource.platform.billing.repository.InvoiceItemRepository;
import com.labresource.platform.billing.repository.InvoiceRepository;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTests {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceItemRepository invoiceItemRepository;
    @Mock
    private UsageChargeRepository usageChargeRepository;
    @Mock
    private InstitutionRepository institutionRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UsageChargeService usageChargeService;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(
                invoiceRepository,
                invoiceItemRepository,
                usageChargeRepository,
                institutionRepository,
                departmentRepository,
                equipmentRepository,
                userRepository,
                usageChargeService,
                new BigDecimal("18.00"),
                30
        );
    }

    @Test
    @DisplayName("Should generate invoice numbers strictly in INV-YYYY-XXXXXX format")
    void generatesInvoiceNumberInStandardFormat() {
        when(invoiceRepository.findAll()).thenReturn(List.of());
        String invNum1 = invoiceService.generateNextInvoiceNumber(2026);
        assertThat(invNum1).isEqualTo("INV-2026-000001");

        Invoice existing = Invoice.builder().invoiceNumber("INV-2026-000042").build();
        when(invoiceRepository.findAll()).thenReturn(List.of(existing));
        String invNum2 = invoiceService.generateNextInvoiceNumber(2026);
        assertThat(invNum2).isEqualTo("INV-2026-000043");
    }

    @Test
    @DisplayName("Should generate INTER_INSTITUTION invoice with 18% tax and link charges")
    void generatesInterInstitutionInvoice() {
        UsageCharge c1 = UsageCharge.builder()
                .id(1L)
                .institutionId(1L)
                .userInstitutionId(2L)
                .equipmentId(10L)
                .userId(20L)
                .amount(new BigDecimal("1000.00"))
                .usageMinutes(60L)
                .hourlyRate(new BigDecimal("1000.00"))
                .status(ChargeStatus.PENDING)
                .build();

        when(usageChargeRepository.findPendingExternalChargesForInvoicing(eq(1L), eq(ChargeType.EXTERNAL), any(), any()))
                .thenReturn(List.of(c1));
        when(invoiceRepository.findAll()).thenReturn(List.of());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(50L);
            return i;
        });
        when(equipmentRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        GenerateInvoiceRequest req = new GenerateInvoiceRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                InvoiceType.INTER_INSTITUTION,
                null,
                null
        );

        List<InvoiceDTO> results = invoiceService.generateInvoices(1L, req);

        assertThat(results).hasSize(1);
        InvoiceDTO invoice = results.get(0);
        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-2026-000001");
        assertThat(invoice.getInvoiceType()).isEqualTo(InvoiceType.INTER_INSTITUTION);
        assertThat(invoice.getRecipientInstitutionId()).isEqualTo(2L);
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("1000.00");
        assertThat(invoice.getTaxRate()).isEqualByComparingTo("18.00");
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("180.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1180.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);

        // Verify charge status updated and linked
        assertThat(c1.getInvoiceId()).isEqualTo(50L);
        assertThat(c1.getStatus()).isEqualTo(ChargeStatus.INVOICED);
        verify(usageChargeRepository).save(c1);
    }

    @Test
    @DisplayName("Should generate DEPARTMENT_CHARGEBACK invoice with 0% tax")
    void generatesDepartmentChargebackInvoice() {
        UsageCharge c1 = UsageCharge.builder()
                .id(2L)
                .institutionId(1L)
                .userInstitutionId(1L)
                .departmentId(5L)
                .equipmentId(10L)
                .userId(20L)
                .amount(new BigDecimal("500.00"))
                .usageMinutes(60L)
                .hourlyRate(new BigDecimal("500.00"))
                .status(ChargeStatus.PENDING)
                .build();

        when(usageChargeRepository.findPendingInternalChargesForInvoicing(eq(1L), eq(ChargeType.INTERNAL), any(), any()))
                .thenReturn(List.of(c1));
        when(invoiceRepository.findAll()).thenReturn(List.of());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(60L);
            return i;
        });

        GenerateInvoiceRequest req = new GenerateInvoiceRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                InvoiceType.DEPARTMENT_CHARGEBACK,
                null,
                null
        );

        List<InvoiceDTO> results = invoiceService.generateInvoices(1L, req);

        assertThat(results).hasSize(1);
        InvoiceDTO invoice = results.get(0);
        assertThat(invoice.getInvoiceType()).isEqualTo(InvoiceType.DEPARTMENT_CHARGEBACK);
        assertThat(invoice.getRecipientDepartmentId()).isEqualTo(5L);
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("500.00");
        assertThat(invoice.getTaxRate()).isEqualByComparingTo("0.00");
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("0.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Should handle status transitions: issue, pay, cancel with linked charges")
    void handlesInvoiceStatusTransitionsAndSynchronizesCharges() {
        Invoice invoice = Invoice.builder()
                .id(100L)
                .invoiceNumber("INV-2026-000001")
                .institutionId(1L)
                .status(InvoiceStatus.DRAFT)
                .dueDate(LocalDate.now().plusDays(30))
                .build();

        UsageCharge charge = UsageCharge.builder()
                .id(500L)
                .invoiceId(100L)
                .status(ChargeStatus.INVOICED)
                .build();

        when(invoiceRepository.findById(100L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(usageChargeRepository.findByInvoiceId(100L)).thenReturn(List.of(charge));

        // 1. Issue invoice
        InvoiceDTO issued = invoiceService.issueInvoice(100L, 1L);
        assertThat(issued.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getIssuedAt()).isNotNull();

        // 2. Pay invoice
        InvoiceDTO paid = invoiceService.markInvoiceAsPaid(100L, 1L);
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PAID);

        // 3. Cannot cancel PAID invoice
        assertThatThrownBy(() -> invoiceService.cancelInvoice(100L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot cancel a PAID invoice");

        // 4. Cancel DRAFT or ISSUED invoice releases charges back to PENDING
        invoice.setStatus(InvoiceStatus.ISSUED);
        InvoiceDTO cancelled = invoiceService.cancelInvoice(100L, 1L);
        assertThat(cancelled.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(charge.getInvoiceId()).isNull();
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PENDING);
    }

    @Test
    @DisplayName("Dynamic overdue calculation: overdue when unpaid and due date in past, not overdue when paid")
    void calculatesDynamicOverdueStatus() {
        Invoice unpaidPastDue = Invoice.builder()
                .id(10L)
                .status(InvoiceStatus.ISSUED)
                .dueDate(LocalDate.now().minusDays(5))
                .build();
        assertThat(unpaidPastDue.isOverdue()).isTrue();

        Invoice unpaidFutureDue = Invoice.builder()
                .id(11L)
                .status(InvoiceStatus.ISSUED)
                .dueDate(LocalDate.now().plusDays(5))
                .build();
        assertThat(unpaidFutureDue.isOverdue()).isFalse();

        Invoice paidPastDue = Invoice.builder()
                .id(12L)
                .status(InvoiceStatus.PAID)
                .dueDate(LocalDate.now().minusDays(5))
                .build();
        assertThat(paidPastDue.isOverdue()).isFalse();
    }
}
