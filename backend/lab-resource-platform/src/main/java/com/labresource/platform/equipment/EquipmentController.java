package com.labresource.platform.equipment;

import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.service.EquipmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.equipment.service.EquipmentLiveStatusService;
import com.labresource.platform.equipment.api.EquipmentLiveStatus;
import com.labresource.platform.equipment.api.PageResponse;
import java.util.Set;
import java.util.List;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final EquipmentLiveStatusService liveStatusService;
    private final ResourceAccessPolicy resourceAccess;

    public EquipmentController(EquipmentService equipmentService,
                               EquipmentLiveStatusService liveStatusService,
                               ResourceAccessPolicy resourceAccess) {
        this.equipmentService = equipmentService;
        this.liveStatusService = liveStatusService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping("/live-status")
    @PreAuthorize("hasAnyRole('RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public PageResponse<EquipmentLiveStatus> getLiveEquipmentStatus(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long institutionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        if (!resourceAccess.canSearchInstitution(institutionId, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Institution access denied");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Page must be nonnegative and page size must be between 1 and 100.");
        }
        if (q != null && q.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Search text must be 150 characters or fewer.");
        }
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (normalizedStatus != null && !Set.of("AVAILABLE", "BOOKED", "UNDER_MAINTENANCE",
                "OUT_OF_SERVICE", "RETIRED").contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid equipment status.");
        }
        boolean includeManagerDetails = resourceAccess.hasAnyRole(
                authentication, "LAB_MANAGER", "INSTITUTION_ADMIN", "SYSTEM_ADMIN");
        Long scopedInstitutionId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        if (resourceAccess.hasAnyRole(authentication, "LAB_MANAGER") && scopedInstitutionId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "A lab manager must have an institution scope to view live equipment usage.");
        }
        return liveStatusService.getPage(q, normalizedStatus,
                scopedInstitutionId,
                page, size, includeManagerDetails);
    }

    @GetMapping
    public List<Equipment> getAllEquipment(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String labLocation,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            Authentication authentication) {
        if (!resourceAccess.canSearchInstitution(institutionId, authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Institution access denied");
        }
        return equipmentService.searchEquipment(
                name,
                category,
                description,
                labLocation,
                status,
                tag,
                resourceAccess.scopedInstitutionId(institutionId, authentication),
                departmentId);
    }

    @PreAuthorize("@resourceAccess.canAccessEquipment(#id, authentication)")
    @GetMapping("/{id}")
    public Equipment getEquipmentById(@PathVariable Long id) {
        try {
            return equipmentService.getEquipmentById(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    exception.getMessage()
            );
        }
    }

    @PreAuthorize("@resourceAccess.canAccessInstitution(#institutionId, authentication)")
    @GetMapping("/institution/{institutionId}")
    public List<Equipment> getEquipmentByInstitution(
            @PathVariable Long institutionId) {
        return equipmentService.getEquipmentByInstitution(institutionId);
    }

    @GetMapping("/department/{departmentId}")
    public List<Equipment> getEquipmentByDepartment(
            @PathVariable Long departmentId,
            Authentication authentication) {
        return equipmentService.getEquipmentByDepartment(departmentId);
    }

    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canCreateEquipment(#equipment.institutionId, authentication)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Equipment createEquipment(@RequestBody Equipment equipment) {
        try {
            return equipmentService.createEquipment(equipment);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }
    }

    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessEquipment(#id, authentication) and @resourceAccess.canCreateEquipment(#equipment.institutionId, authentication)")
    @PutMapping("/{id}")
    public Equipment updateEquipment(
            @PathVariable Long id,
            @RequestBody Equipment equipment) {
        try {
            return equipmentService.updateEquipment(id, equipment);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }
    }

    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessEquipment(#id, authentication)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEquipment(@PathVariable Long id) {
        try {
            equipmentService.deleteEquipment(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    exception.getMessage()
            );
        }
    }
}
