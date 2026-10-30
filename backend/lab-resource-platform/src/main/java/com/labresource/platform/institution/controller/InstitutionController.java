package com.labresource.platform.institution.controller;

import com.labresource.platform.institution.dto.InstitutionRegistrationRequest;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.service.InstitutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/institutions")
public class InstitutionController {

    private final InstitutionService institutionService;
    private final DepartmentRepository departmentRepository;

    public InstitutionController(
            InstitutionService institutionService,
            DepartmentRepository departmentRepository) {
        this.institutionService = institutionService;
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    public List<Institution> getAllInstitutions() {
        return institutionService.getAllInstitutions();
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public List<Institution> getPendingInstitutions() {
        return institutionService.getPendingInstitutions();
    }

    @GetMapping("/{id}")
    public Institution getInstitutionById(@PathVariable Long id) {
        return institutionService.getInstitutionById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerInstitution(@Valid @RequestBody InstitutionRegistrationRequest request) {
        try {
            Institution institution = institutionService.registerInstitution(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", institution.getId(),
                    "name", institution.getName(),
                    "status", institution.getStatus(),
                    "contactEmail", institution.getContactEmail() != null ? institution.getContactEmail() : "",
                    "message", "Institution registration submitted. A platform System Administrator must review and approve your institution before administrator access is active."
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> approveInstitution(@PathVariable Long id, Authentication authentication) {
        try {
            Institution approved = institutionService.approveInstitution(id, authentication.getName());
            return ResponseEntity.ok(Map.of(
                    "id", approved.getId(),
                    "name", approved.getName(),
                    "status", approved.getStatus(),
                    "message", "Institution and primary administrator approved successfully."
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> rejectInstitution(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        try {
            String reason = body != null ? body.get("reason") : "Registration rejected by platform administrator";
            Institution rejected = institutionService.rejectInstitution(id, authentication.getName(), reason);
            return ResponseEntity.ok(Map.of(
                    "id", rejected.getId(),
                    "name", rejected.getName(),
                    "status", rejected.getStatus(),
                    "message", "Institution registration rejected."
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Institution createInstitution(
            @RequestBody Institution institution) {
        institution.setId(null);
        InstitutionRegistrationRequest req = new InstitutionRegistrationRequest();
        req.setName(institution.getName());
        req.setAddress(institution.getAddress());
        req.setLocation(institution.getLocation());
        req.setContactEmail(institution.getContactEmail());
        req.setPhoneNumber(institution.getPhoneNumber());
        return institutionService.registerInstitution(req);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String status = body.get("status");
            Institution updated = institutionService.updateStatus(id, status);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/{institutionId}/departments")
    @PreAuthorize("@resourceAccess.canAccessInstitution(#institutionId, authentication)")
    public List<Department> getDepartmentsByInstitution(@PathVariable Long institutionId) {
        return departmentRepository.findByInstitutionId(institutionId);
    }

    @GetMapping("/{institutionId}/departments/public")
    public List<Department> getDepartmentsByInstitutionPublic(@PathVariable Long institutionId) {
        return departmentRepository.findByInstitutionId(institutionId);
    }
}
