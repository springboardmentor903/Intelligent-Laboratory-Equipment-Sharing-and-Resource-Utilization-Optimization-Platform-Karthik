package com.labresource.platform.certification.controller;

import com.labresource.platform.certification.entity.CertificationRecord;
import com.labresource.platform.certification.service.CertificationRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/equipment/{equipmentId}/certifications")
public class CertificationRecordController {

    private final CertificationRecordService certificationRecordService;

    public CertificationRecordController(
            CertificationRecordService certificationRecordService) {
        this.certificationRecordService = certificationRecordService;
    }

    @GetMapping
    @PreAuthorize("@resourceAccess.canAccessEquipment(#equipmentId, authentication)")
    public List<CertificationRecord> getByEquipment(
            @PathVariable Long equipmentId) {
        try {
            return certificationRecordService.getByEquipment(equipmentId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessEquipment(#equipmentId, authentication)")
    public CertificationRecord create(
            @PathVariable Long equipmentId,
            @RequestBody CertificationRecord record) {
        try {
            return certificationRecordService.create(equipmentId, record);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
