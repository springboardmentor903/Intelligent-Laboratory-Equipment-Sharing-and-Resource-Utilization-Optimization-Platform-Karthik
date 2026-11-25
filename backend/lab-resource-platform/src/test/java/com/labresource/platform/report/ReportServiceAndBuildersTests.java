package com.labresource.platform.report;

import com.labresource.platform.billing.model.InvoiceDetailDTO;
import com.labresource.platform.billing.model.InvoiceItemDTO;
import com.labresource.platform.billing.model.InvoiceStatus;
import com.labresource.platform.billing.model.InvoiceType;
import com.labresource.platform.billing.repository.DepartmentBudgetRepository;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.maintenance.repository.EquipmentDowntimeLogRepository;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import com.labresource.platform.report.model.ReportMetadata;
import com.labresource.platform.report.model.ReportResult;
import com.labresource.platform.report.model.ReportType;
import com.labresource.platform.report.service.*;
import com.labresource.platform.sharing.repository.SharingAgreementRepository;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceAndBuildersTests {

    @Mock private EquipmentRepository equipmentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private EquipmentDowntimeLogRepository downtimeLogRepository;
    @Mock private CalibrationRecordRepository calibrationRecordRepository;
    @Mock private SharingAgreementRepository sharingAgreementRepository;
    @Mock private UsageChargeRepository usageChargeRepository;
    @Mock private DepartmentBudgetRepository departmentBudgetRepository;

    private ReportRegistry reportRegistry;
    private ReportBuilder reportBuilder;
    private PdfExportService pdfExportService;
    private ExcelExportService excelExportService;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportRegistry = new ReportRegistry();
        reportBuilder = new ReportBuilder(
                equipmentRepository,
                bookingRepository,
                departmentRepository,
                userRepository,
                workOrderRepository,
                downtimeLogRepository,
                calibrationRecordRepository,
                sharingAgreementRepository,
                usageChargeRepository,
                departmentBudgetRepository
        );
        pdfExportService = new PdfExportService();
        excelExportService = new ExcelExportService();
        reportService = new ReportService(
                reportRegistry,
                reportBuilder,
                pdfExportService,
                excelExportService
        );
    }

    @Test
    void reportRegistry_enforcesRolePermissions() {
        assertTrue(reportRegistry.isPermitted(ReportType.UTILIZATION, List.of("LAB_MANAGER")));
        assertTrue(reportRegistry.isPermitted(ReportType.MAINTENANCE_DOWNTIME, List.of("LAB_MANAGER")));
        assertFalse(reportRegistry.isPermitted(ReportType.INTER_INSTITUTION_SHARING, List.of("LAB_MANAGER")));

        assertTrue(reportRegistry.isPermitted(ReportType.DEPARTMENT_USAGE, List.of("DEPARTMENT_HEAD")));
        assertFalse(reportRegistry.isPermitted(ReportType.MAINTENANCE_DOWNTIME, List.of("DEPARTMENT_HEAD")));

        for (ReportType type : ReportType.values()) {
            assertTrue(reportRegistry.isPermitted(type, List.of("INSTITUTION_ADMIN")));
            assertTrue(reportRegistry.isPermitted(type, List.of("SYSTEM_ADMIN")));
            assertFalse(reportRegistry.isPermitted(type, List.of("RESEARCHER_STUDENT")));
        }

        List<ReportMetadata> managerReports = reportRegistry.getAvailableReports(List.of("LAB_MANAGER"));
        assertEquals(2, managerReports.size());
    }

    @Test
    void reportBuilder_buildsUtilizationReportAccurately() {
        Long institutionId = 1L;
        Equipment eq = new Equipment();
        eq.setId(10L);
        eq.setName("Autoclave");
        eq.setCategory("Sterilization");
        eq.setInstitutionId(1L);

        when(equipmentRepository.findByInstitutionId(1L)).thenReturn(List.of(eq));
        when(departmentRepository.findByInstitutionId(1L)).thenReturn(List.of());
        when(bookingRepository.findByEquipmentIdInAndPeriod(anyList(), any(), any())).thenReturn(List.of());

        ReportResult result = reportBuilder.build(
                ReportType.UTILIZATION,
                institutionId,
                null,
                LocalDate.now().minusDays(30),
                LocalDate.now(),
                null
        );

        assertNotNull(result);
        assertEquals("UTILIZATION", result.reportType());
        assertEquals(1, result.rows().size());
        assertEquals("Autoclave", result.rows().get(0).get("equipmentName"));
        assertNotNull(result.totals());
        assertNotNull(result.chartData());
    }

    @Test
    void reportService_throwsForbiddenForUnauthorizedRole() {
        assertThrows(ResponseStatusException.class, () ->
                reportService.generateReportPreview(
                        "INTER_INSTITUTION_SHARING",
                        List.of("RESEARCHER_STUDENT"),
                        1L,
                        null,
                        LocalDate.now().minusDays(30),
                        LocalDate.now(),
                        null
                )
        );
    }

    @Test
    void pdfExportService_generatesValidPdfBytesWithPdfHeader() {
        ReportResult mockReport = new ReportResult(
                "UTILIZATION",
                "Utilization Report",
                LocalDateTime.now(),
                Map.of("institutionId", 1L),
                List.of(
                        com.labresource.platform.report.model.ReportColumn.text("name", "Equipment"),
                        com.labresource.platform.report.model.ReportColumn.number("used", "Hours")
                ),
                List.of(Map.of("name", "Spectrophotometer", "used", 12.5)),
                Map.of("used", 12.5),
                List.of()
        );

        byte[] pdfBytes = pdfExportService.generateReportPdf(mockReport);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 100);
        // Standard PDF magic header: %PDF
        assertEquals('%', (char) pdfBytes[0]);
        assertEquals('P', (char) pdfBytes[1]);
        assertEquals('D', (char) pdfBytes[2]);
        assertEquals('F', (char) pdfBytes[3]);
    }

    @Test
    void pdfExportService_generatesPrintableInvoicePdf() {
        com.labresource.platform.billing.model.InvoiceDTO invoiceDTO = com.labresource.platform.billing.model.InvoiceDTO.builder()
                .id(1001L)
                .invoiceNumber("INV-2026-0042")
                .institutionId(1L)
                .institutionName("MIT Central Labs")
                .recipientInstitutionId(2L)
                .recipientInstitutionName("Stanford Nanofab")
                .invoiceType(InvoiceType.INTER_INSTITUTION)
                .status(InvoiceStatus.ISSUED)
                .periodStart(LocalDate.of(2026, 11, 1))
                .periodEnd(LocalDate.of(2026, 11, 30))
                .dueDate(LocalDate.of(2026, 12, 15))
                .subtotal(BigDecimal.valueOf(12500.0))
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(12500.0))
                .issuedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        InvoiceItemDTO item = InvoiceItemDTO.builder()
                .id(1L)
                .invoiceId(1001L)
                .chargeId(50L)
                .equipmentId(10L)
                .equipmentName("NMR Spectrometer 4h usage")
                .usageMinutes(240L)
                .hourlyRate(BigDecimal.valueOf(3125.0))
                .amount(BigDecimal.valueOf(12500.0))
                .createdAt(LocalDateTime.now())
                .build();

        InvoiceDetailDTO invoice = InvoiceDetailDTO.builder()
                .invoice(invoiceDTO)
                .items(List.of(item))
                .charges(List.of())
                .build();

        byte[] pdfBytes = pdfExportService.generateInvoicePdf(invoice);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 100);
        assertEquals('%', (char) pdfBytes[0]);
        assertEquals('P', (char) pdfBytes[1]);
        assertEquals('D', (char) pdfBytes[2]);
        assertEquals('F', (char) pdfBytes[3]);
    }

    @Test
    void excelExportService_generatesValidXlsxZipBytes() {
        ReportResult mockReport = new ReportResult(
                "UTILIZATION",
                "Utilization Report",
                LocalDateTime.now(),
                Map.of("institutionId", 1L),
                List.of(
                        com.labresource.platform.report.model.ReportColumn.text("name", "Equipment"),
                        com.labresource.platform.report.model.ReportColumn.number("used", "Hours")
                ),
                List.of(Map.of("name", "Thermal Cycler", "used", 8.0)),
                Map.of("used", 8.0),
                List.of()
        );

        byte[] xlsxBytes = excelExportService.generateReportExcel(mockReport);
        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 100);
        // Standard Zip magic header: PK\003\004
        assertEquals('P', (char) xlsxBytes[0]);
        assertEquals('K', (char) xlsxBytes[1]);
        assertEquals(3, xlsxBytes[2]);
        assertEquals(4, xlsxBytes[3]);
    }
}
