package com.labresource.platform.billing.controller;

import com.labresource.platform.billing.model.GenerateInvoiceRequest;
import com.labresource.platform.billing.model.InvoiceDTO;
import com.labresource.platform.billing.model.InvoiceDetailDTO;
import com.labresource.platform.billing.model.InvoiceStatus;
import com.labresource.platform.billing.service.InvoiceService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/billing/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ResourceAccessPolicy resourceAccess;

    public InvoiceController(InvoiceService invoiceService, ResourceAccessPolicy resourceAccess) {
        this.invoiceService = invoiceService;
        this.resourceAccess = resourceAccess;
    }

    @PostMapping("/generate")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN')")
    public ResponseEntity<List<InvoiceDTO>> generateInvoices(
            @RequestParam(required = false) Long institutionId,
            @RequestBody GenerateInvoiceRequest request,
            Authentication authentication) {

        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        List<InvoiceDTO> generated = invoiceService.generateInvoices(scopedInstId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(generated);
    }

    @GetMapping("/issued")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'LAB_MANAGER')")
    public ResponseEntity<Page<InvoiceDTO>> getIssuedInvoices(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Authentication authentication) {

        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), parseSort(sort));
        return ResponseEntity.ok(invoiceService.getIssuedInvoices(scopedInstId, status, startDate, endDate, pageable));
    }

    @GetMapping("/received")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'LAB_MANAGER', 'DEPARTMENT_HEAD')")
    public ResponseEntity<Page<InvoiceDTO>> getReceivedInvoices(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Authentication authentication) {

        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        User user = resourceAccess.currentUser(authentication).orElse(null);
        Long deptId = departmentId;
        if (resourceAccess.hasAnyRole(authentication, "DEPARTMENT_HEAD") && user != null) {
            deptId = user.getDepartmentId();
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), parseSort(sort));
        return ResponseEntity.ok(invoiceService.getReceivedInvoices(scopedInstId, deptId, status, startDate, endDate, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'LAB_MANAGER', 'DEPARTMENT_HEAD')")
    public ResponseEntity<InvoiceDetailDTO> getInvoiceDetail(
            @PathVariable Long id,
            Authentication authentication) {

        User user = resourceAccess.currentUser(authentication).orElse(null);
        Long actorInstId = user != null ? user.getInstitutionId() : null;
        Long actorDeptId = user != null ? user.getDepartmentId() : null;
        return ResponseEntity.ok(invoiceService.getInvoiceDetail(id, actorInstId, actorDeptId));
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN')")
    public ResponseEntity<InvoiceDTO> issueInvoice(
            @PathVariable Long id,
            Authentication authentication) {

        User user = resourceAccess.currentUser(authentication).orElse(null);
        Long actorInstId = user != null ? user.getInstitutionId() : null;
        return ResponseEntity.ok(invoiceService.issueInvoice(id, actorInstId));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN')")
    public ResponseEntity<InvoiceDTO> markInvoiceAsPaid(
            @PathVariable Long id,
            Authentication authentication) {

        User user = resourceAccess.currentUser(authentication).orElse(null);
        Long actorInstId = user != null ? user.getInstitutionId() : null;
        return ResponseEntity.ok(invoiceService.markInvoiceAsPaid(id, actorInstId));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN')")
    public ResponseEntity<InvoiceDTO> cancelInvoice(
            @PathVariable Long id,
            Authentication authentication) {

        User user = resourceAccess.currentUser(authentication).orElse(null);
        Long actorInstId = user != null ? user.getInstitutionId() : null;
        return ResponseEntity.ok(invoiceService.cancelInvoice(id, actorInstId));
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
