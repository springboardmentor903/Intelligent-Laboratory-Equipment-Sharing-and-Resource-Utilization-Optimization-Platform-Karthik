package com.labresource.platform.report.model;

public enum ReportType {
    UTILIZATION("Utilization Report", "Detailed equipment and category operating utilization, usage hours, and booking adoption."),
    DEPARTMENT_USAGE("Department Usage Report", "Departmental usage breakdown, internal/external chargeback spend, and budget utilization."),
    MAINTENANCE_DOWNTIME("Maintenance and Downtime Report", "Work orders, corrective vs preventive maintenance counts, equipment downtime, and service costs."),
    INTER_INSTITUTION_SHARING("Inter-Institution Sharing Report", "Partner institution equipment sharing agreements, shared booking hours, and revenue accrued."),
    PROCUREMENT_COST("Procurement and Cost Analysis Report", "Equipment lifecycle economics, purchase vs maintenance costs, usage revenue, and ROI metrics.");

    private final String title;
    private final String description;

    ReportType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public static ReportType fromString(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Report type cannot be empty.");
        }
        String normalized = name.trim().toUpperCase().replace("-", "_");
        for (ReportType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown report type: " + name);
    }
}
