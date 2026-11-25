package com.labresource.platform.report.model;

import java.util.List;

public record ReportMetadata(
        String type,
        String title,
        String description,
        List<String> supportedFilters,
        List<String> permittedRoles
) {}
