package com.labresource.platform.report.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.report.model.ReportMetadata;
import com.labresource.platform.report.model.ReportResult;
import com.labresource.platform.report.service.ReportService;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final ResourceAccessPolicy resourceAccess;
    private final UserService userService;

    public ReportController(
            ReportService reportService,
            ResourceAccessPolicy resourceAccess,
            UserService userService) {
        this.reportService = reportService;
        this.resourceAccess = resourceAccess;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ReportMetadata>> getAvailableReports(Authentication authentication) {
        User user = resourceAccess.currentUser(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session not found"));
        List<String> roles = userService.getRoleNames(user.getId());
        return ResponseEntity.ok(reportService.getAvailableReports(roles));
    }

    @GetMapping("/{type}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReportResult> getReportPreview(
            @PathVariable String type,
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            Authentication authentication) {

        User user = resourceAccess.currentUser(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session not found"));
        List<String> roles = userService.getRoleNames(user.getId());

        Long scopedInstId = resolveInstitution(institutionId, user, authentication);
        Long scopedDeptId = resolveDepartment(departmentId, user, roles);

        ReportResult report = reportService.generateReportPreview(
                type, roles, scopedInstId, scopedDeptId, startDate, endDate, category
        );
        return ResponseEntity.ok(report);
    }

    @GetMapping("/{type}/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportReport(
            @PathVariable String type,
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            Authentication authentication) {

        User user = resourceAccess.currentUser(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session not found"));
        List<String> roles = userService.getRoleNames(user.getId());

        Long scopedInstId = resolveInstitution(institutionId, user, authentication);
        Long scopedDeptId = resolveDepartment(departmentId, user, roles);

        String normalizedFormat = format.trim().toLowerCase();
        LocalDate now = LocalDate.now();

        if ("pdf".equals(normalizedFormat)) {
            byte[] pdfBytes = reportService.exportReportPdf(
                    type, roles, scopedInstId, scopedDeptId, startDate, endDate, category
            );
            String filename = String.format("report_%s_%s.pdf", type.toLowerCase(), now);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(pdfBytes);
        } else if ("xlsx".equals(normalizedFormat) || "excel".equals(normalizedFormat)) {
            byte[] excelBytes = reportService.exportReportExcel(
                    type, roles, scopedInstId, scopedDeptId, startDate, endDate, category
            );
            String filename = String.format("report_%s_%s.xlsx", type.toLowerCase(), now);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(excelBytes);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported export format: " + format + ". Supported formats: pdf, xlsx.");
        }
    }

    private Long resolveInstitution(Long requestedId, User user, Authentication authentication) {
        if (resourceAccess.isSystemAdmin(authentication)) {
            if (requestedId != null) return requestedId;
            if (user.getInstitutionId() != null) return user.getInstitutionId();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "institutionId parameter is required for System Administrator.");
        }

        Long userInstId = user.getInstitutionId();
        if (userInstId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not assigned to an institution.");
        }
        if (requestedId != null && !requestedId.equals(userInstId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to foreign institution reports.");
        }
        return userInstId;
    }

    private Long resolveDepartment(Long requestedDeptId, User user, List<String> roles) {
        // DEPARTMENT_HEAD is strictly restricted to own department
        if (roles.contains("DEPARTMENT_HEAD") && !roles.contains("INSTITUTION_ADMIN") && !roles.contains("SYSTEM_ADMIN")) {
            return user.getDepartmentId();
        }
        return requestedDeptId;
    }
}
