package com.labresource.platform.report.service;

import com.labresource.platform.report.model.ReportMetadata;
import com.labresource.platform.report.model.ReportType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ReportRegistry {

    private static final Map<ReportType, List<String>> PERMITTED_ROLES = Map.of(
            ReportType.UTILIZATION, List.of("INSTITUTION_ADMIN", "LAB_MANAGER", "DEPARTMENT_HEAD", "SYSTEM_ADMIN"),
            ReportType.DEPARTMENT_USAGE, List.of("INSTITUTION_ADMIN", "DEPARTMENT_HEAD", "SYSTEM_ADMIN"),
            ReportType.MAINTENANCE_DOWNTIME, List.of("INSTITUTION_ADMIN", "LAB_MANAGER", "SYSTEM_ADMIN"),
            ReportType.INTER_INSTITUTION_SHARING, List.of("INSTITUTION_ADMIN", "SYSTEM_ADMIN"),
            ReportType.PROCUREMENT_COST, List.of("INSTITUTION_ADMIN", "SYSTEM_ADMIN")
    );

    private static final Map<ReportType, List<String>> SUPPORTED_FILTERS = Map.of(
            ReportType.UTILIZATION, List.of("startDate", "endDate", "departmentId", "category"),
            ReportType.DEPARTMENT_USAGE, List.of("startDate", "endDate", "departmentId"),
            ReportType.MAINTENANCE_DOWNTIME, List.of("startDate", "endDate", "departmentId"),
            ReportType.INTER_INSTITUTION_SHARING, List.of("startDate", "endDate"),
            ReportType.PROCUREMENT_COST, List.of("category")
    );

    public boolean isPermitted(ReportType type, List<String> userRoles) {
        if (type == null || userRoles == null) return false;
        List<String> permitted = PERMITTED_ROLES.get(type);
        if (permitted == null) return false;
        return userRoles.stream().anyMatch(permitted::contains);
    }

    public List<ReportMetadata> getAvailableReports(List<String> userRoles) {
        if (userRoles == null) return Collections.emptyList();
        List<ReportMetadata> results = new ArrayList<>();
        for (ReportType type : ReportType.values()) {
            if (isPermitted(type, userRoles)) {
                results.add(new ReportMetadata(
                        type.name(),
                        type.getTitle(),
                        type.getDescription(),
                        SUPPORTED_FILTERS.getOrDefault(type, Collections.emptyList()),
                        PERMITTED_ROLES.getOrDefault(type, Collections.emptyList())
                ));
            }
        }
        return results;
    }

    public List<String> getPermittedRoles(ReportType type) {
        return PERMITTED_ROLES.getOrDefault(type, Collections.emptyList());
    }

    public List<String> getSupportedFilters(ReportType type) {
        return SUPPORTED_FILTERS.getOrDefault(type, Collections.emptyList());
    }
}
