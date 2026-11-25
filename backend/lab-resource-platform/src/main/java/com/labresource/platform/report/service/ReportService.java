package com.labresource.platform.report.service;

import com.labresource.platform.common.cache.ExpiringCache;
import com.labresource.platform.report.model.ReportMetadata;
import com.labresource.platform.report.model.ReportResult;
import com.labresource.platform.report.model.ReportType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReportService {

    private final ReportRegistry reportRegistry;
    private final ReportBuilder reportBuilder;
    private final PdfExportService pdfExportService;
    private final ExcelExportService excelExportService;

    // 2-minute preview cache
    private final ExpiringCache<String, ReportResult> previewCache = new ExpiringCache<>(120_000);

    public ReportService(
            ReportRegistry reportRegistry,
            ReportBuilder reportBuilder,
            PdfExportService pdfExportService,
            ExcelExportService excelExportService) {
        this.reportRegistry = reportRegistry;
        this.reportBuilder = reportBuilder;
        this.pdfExportService = pdfExportService;
        this.excelExportService = excelExportService;
    }

    public List<ReportMetadata> getAvailableReports(List<String> userRoles) {
        return reportRegistry.getAvailableReports(userRoles);
    }

    public ReportResult generateReportPreview(
            String reportTypeStr,
            List<String> userRoles,
            Long institutionId,
            Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            String category) {

        ReportType type = ReportType.fromString(reportTypeStr);
        validatePermissions(type, userRoles, departmentId);

        String cacheKey = String.format("preview:%s:%s:%s:%s:%s:%s",
                type.name(), institutionId, departmentId, startDate, endDate, category);

        ReportResult cached = previewCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ReportResult report = reportBuilder.build(type, institutionId, departmentId, startDate, endDate, category);
        previewCache.put(cacheKey, report);
        return report;
    }

    public byte[] exportReportPdf(
            String reportTypeStr,
            List<String> userRoles,
            Long institutionId,
            Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            String category) {

        ReportType type = ReportType.fromString(reportTypeStr);
        validatePermissions(type, userRoles, departmentId);

        // Exports are NOT cached per specification
        ReportResult report = reportBuilder.build(type, institutionId, departmentId, startDate, endDate, category);
        return pdfExportService.generateReportPdf(report);
    }

    public byte[] exportReportExcel(
            String reportTypeStr,
            List<String> userRoles,
            Long institutionId,
            Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            String category) {

        ReportType type = ReportType.fromString(reportTypeStr);
        validatePermissions(type, userRoles, departmentId);

        // Exports are NOT cached per specification
        ReportResult report = reportBuilder.build(type, institutionId, departmentId, startDate, endDate, category);
        return excelExportService.generateReportExcel(report);
    }

    private void validatePermissions(ReportType type, List<String> userRoles, Long requestedDepartmentId) {
        if (!reportRegistry.isPermitted(type, userRoles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to report: " + type.getTitle());
        }
    }
}
