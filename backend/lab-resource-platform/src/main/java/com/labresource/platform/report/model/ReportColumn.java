package com.labresource.platform.report.model;

public record ReportColumn(
        String key,
        String label,
        String type
) {
    public static ReportColumn of(String key, String label, String type) {
        return new ReportColumn(key, label, type);
    }

    public static ReportColumn text(String key, String label) {
        return new ReportColumn(key, label, "STRING");
    }

    public static ReportColumn number(String key, String label) {
        return new ReportColumn(key, label, "NUMBER");
    }

    public static ReportColumn currency(String key, String label) {
        return new ReportColumn(key, label, "CURRENCY");
    }

    public static ReportColumn percentage(String key, String label) {
        return new ReportColumn(key, label, "PERCENTAGE");
    }

    public static ReportColumn date(String key, String label) {
        return new ReportColumn(key, label, "DATE");
    }
}
