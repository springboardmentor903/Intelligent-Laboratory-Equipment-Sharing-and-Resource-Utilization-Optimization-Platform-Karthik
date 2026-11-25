package com.labresource.platform.report.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ReportResult(
        String reportType,
        String reportTitle,
        LocalDateTime generatedAt,
        Map<String, Object> appliedFilters,
        List<ReportColumn> columns,
        List<Map<String, Object>> rows,
        Map<String, Object> totals,
        List<Map<String, Object>> chartData
) {}
