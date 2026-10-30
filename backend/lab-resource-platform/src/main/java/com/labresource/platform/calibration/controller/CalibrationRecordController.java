package com.labresource.platform.calibration.controller;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.service.CalibrationRecordService;
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
@RequestMapping("/api/equipment/{equipmentId}/calibrations")
public class CalibrationRecordController {

    private final CalibrationRecordService calibrationRecordService;

    public CalibrationRecordController(
            CalibrationRecordService calibrationRecordService) {
        this.calibrationRecordService = calibrationRecordService;
    }

    @GetMapping
    @PreAuthorize("@resourceAccess.canAccessEquipment(#equipmentId, authentication)")
    public List<CalibrationRecord> getByEquipment(
            @PathVariable Long equipmentId) {
        try {
            return calibrationRecordService.getByEquipment(equipmentId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessEquipment(#equipmentId, authentication)")
    public CalibrationRecord create(
            @PathVariable Long equipmentId,
            @RequestBody CalibrationRecord record) {
        try {
            return calibrationRecordService.create(equipmentId, record);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
