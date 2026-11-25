package com.labresource.platform.report.service;

import com.labresource.platform.billing.model.InvoiceDetailDTO;
import com.labresource.platform.billing.model.InvoiceItemDTO;
import com.labresource.platform.report.model.ReportColumn;
import com.labresource.platform.report.model.ReportResult;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class PdfExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    public byte[] generateReportPdf(ReportResult report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 30, 30);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // Document Header
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(15, 23, 42));
            Paragraph title = new Paragraph(report.reportTitle(), titleFont);
            title.setSpacingAfter(4);
            document.add(title);

            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(100, 116, 139));
            String generatedText = "Generated at: " + (report.generatedAt() != null ? report.generatedAt().format(DATE_TIME_FORMATTER) : "Now")
                    + " | Lab Resource Management Platform";
            Paragraph subtitle = new Paragraph(generatedText, subtitleFont);
            subtitle.setSpacingAfter(8);
            document.add(subtitle);

            // Filters Summary Box
            if (report.appliedFilters() != null && !report.appliedFilters().isEmpty()) {
                StringBuilder filterText = new StringBuilder("Filters: ");
                report.appliedFilters().forEach((k, v) -> {
                    if (v != null) filterText.append(k).append("=").append(v).append("; ");
                });
                Font filterFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, new Color(71, 85, 105));
                Paragraph filterPara = new Paragraph(filterText.toString(), filterFont);
                filterPara.setSpacingAfter(12);
                document.add(filterPara);
            }

            // Report Table
            int colCount = report.columns().size();
            PdfPTable table = new PdfPTable(colCount);
            table.setWidthPercentage(100);
            table.setSpacingBefore(8);

            // Table Headers
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Color headerBg = new Color(2, 132, 199);

            for (ReportColumn col : report.columns()) {
                PdfPCell cell = new PdfPCell(new Phrase(col.label(), headerFont));
                cell.setBackgroundColor(headerBg);
                cell.setPadding(6);
                cell.setHorizontalAlignment(isNumeric(col.type()) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                table.addCell(cell);
            }

            // Table Rows
            Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(30, 41, 59));
            Color zebraBg = new Color(248, 250, 252);
            boolean alternate = false;

            for (Map<String, Object> row : report.rows()) {
                for (ReportColumn col : report.columns()) {
                    Object val = row.get(col.key());
                    String formattedVal = formatCellValue(val, col.type());
                    PdfPCell cell = new PdfPCell(new Phrase(formattedVal, rowFont));
                    if (alternate) {
                        cell.setBackgroundColor(zebraBg);
                    }
                    cell.setPadding(5);
                    cell.setHorizontalAlignment(isNumeric(col.type()) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                    table.addCell(cell);
                }
                alternate = !alternate;
            }

            // Totals Row
            if (report.totals() != null && !report.totals().isEmpty()) {
                Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(15, 23, 42));
                Color totalBg = new Color(226, 232, 240);

                for (int i = 0; i < report.columns().size(); i++) {
                    ReportColumn col = report.columns().get(i);
                    Object val = report.totals().get(col.key());
                    if (val == null && i == 0) {
                        val = "TOTAL";
                    }
                    String text = val != null ? formatCellValue(val, col.type()) : "-";
                    PdfPCell cell = new PdfPCell(new Phrase(text, totalFont));
                    cell.setBackgroundColor(totalBg);
                    cell.setPadding(6);
                    cell.setHorizontalAlignment(isNumeric(col.type()) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                    table.addCell(cell);
                }
            }

            document.add(table);
            document.close();
        } catch (DocumentException ex) {
            throw new RuntimeException("Error rendering report PDF", ex);
        }

        return baos.toByteArray();
    }

    public byte[] generateInvoicePdf(InvoiceDetailDTO invoiceDetail) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            com.labresource.platform.billing.model.InvoiceDTO inv = invoiceDetail.getInvoice();

            // Header Banner
            Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(2, 132, 199));
            document.add(new Paragraph("LAB RESOURCE PLATFORM", brandFont));

            Font invoiceTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(15, 23, 42));
            String invoiceNumberStr = inv != null && inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : (inv != null ? "#" + inv.getId() : "#");
            Paragraph invoiceHeading = new Paragraph("INVOICE: " + invoiceNumberStr, invoiceTitleFont);
            invoiceHeading.setSpacingAfter(4);
            document.add(invoiceHeading);

            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(100, 116, 139));
            if (inv != null) {
                document.add(new Paragraph("Status: " + inv.getStatus() + " | Type: " + inv.getInvoiceType(), metaFont));
                document.add(new Paragraph("Billing Period: " + inv.getPeriodStart() + " to " + inv.getPeriodEnd(), metaFont));
                document.add(new Paragraph("Due Date: " + (inv.getDueDate() != null ? inv.getDueDate() : "Due on receipt"), metaFont));
            }

            Paragraph separator = new Paragraph(" ");
            separator.setSpacingAfter(10);
            document.add(separator);

            // Party Information
            PdfPTable partiesTable = new PdfPTable(2);
            partiesTable.setWidthPercentage(100);
            partiesTable.setSpacingAfter(16);

            Font partyHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(30, 41, 59));
            Font partyTextFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(71, 85, 105));

            PdfPCell fromCell = new PdfPCell();
            fromCell.setBorder(Rectangle.NO_BORDER);
            fromCell.addElement(new Paragraph("ISSUED BY:", partyHeaderFont));
            if (inv != null) {
                fromCell.addElement(new Paragraph("Institution ID: " + inv.getInstitutionId() + (inv.getInstitutionName() != null ? " (" + inv.getInstitutionName() + ")" : ""), partyTextFont));
            }
            partiesTable.addCell(fromCell);

            PdfPCell toCell = new PdfPCell();
            toCell.setBorder(Rectangle.NO_BORDER);
            toCell.addElement(new Paragraph("BILLED TO:", partyHeaderFont));
            if (inv != null && inv.getRecipientInstitutionId() != null) {
                toCell.addElement(new Paragraph("Partner Institution ID: " + inv.getRecipientInstitutionId() + (inv.getRecipientInstitutionName() != null ? " (" + inv.getRecipientInstitutionName() + ")" : ""), partyTextFont));
            }
            if (inv != null && inv.getRecipientDepartmentId() != null) {
                toCell.addElement(new Paragraph("Department ID: " + inv.getRecipientDepartmentId() + (inv.getRecipientDepartmentName() != null ? " (" + inv.getRecipientDepartmentName() + ")" : ""), partyTextFont));
            }
            partiesTable.addCell(toCell);
            document.add(partiesTable);

            // Line Items Table
            PdfPTable itemsTable = new PdfPTable(new float[]{1.2f, 4f, 1.5f, 1.5f, 2f});
            itemsTable.setWidthPercentage(100);
            itemsTable.setSpacingBefore(8);

            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Color headerBg = new Color(15, 23, 42);

            String[] headers = {"Item #", "Description", "Duration (Hrs)", "Rate (₹/hr)", "Amount (₹)"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, tableHeaderFont));
                cell.setBackgroundColor(headerBg);
                cell.setPadding(6);
                cell.setHorizontalAlignment(h.contains("₹") || h.contains("Duration") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                itemsTable.addCell(cell);
            }

            Font itemFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(30, 41, 59));
            int idx = 1;
            if (invoiceDetail.getItems() != null) {
                for (InvoiceItemDTO item : invoiceDetail.getItems()) {
                    itemsTable.addCell(createCell(String.valueOf(idx++), itemFont, Element.ALIGN_LEFT));
                    String desc = item.getEquipmentName() != null ? item.getEquipmentName() : "Lab usage charge";
                    itemsTable.addCell(createCell(desc, itemFont, Element.ALIGN_LEFT));
                    double hours = item.getUsageHours() != null ? item.getUsageHours().doubleValue()
                            : (item.getUsageMinutes() != null ? Math.round((item.getUsageMinutes() / 60.0) * 10.0) / 10.0 : 1.0);
                    itemsTable.addCell(createCell(String.format("%.1f", hours), itemFont, Element.ALIGN_RIGHT));
                    itemsTable.addCell(createCell(item.getHourlyRate() != null ? String.format("₹%.2f", item.getHourlyRate()) : "₹0.00", itemFont, Element.ALIGN_RIGHT));
                    itemsTable.addCell(createCell(item.getAmount() != null ? String.format("₹%.2f", item.getAmount()) : "₹0.00", itemFont, Element.ALIGN_RIGHT));
                }
            }

            document.add(itemsTable);

            // Totals Block
            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(40);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalsTable.setSpacingBefore(12);

            Font totalLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(15, 23, 42));
            Font totalValueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(2, 132, 199));

            PdfPCell subtotalLabel = new PdfPCell(new Phrase("Subtotal:", totalLabelFont));
            subtotalLabel.setBorder(Rectangle.NO_BORDER);
            subtotalLabel.setPadding(4);
            totalsTable.addCell(subtotalLabel);

            BigDecimal subtotal = (inv != null && inv.getSubtotal() != null) ? inv.getSubtotal() : (inv != null ? inv.getTotalAmount() : BigDecimal.ZERO);
            PdfPCell subtotalVal = new PdfPCell(new Phrase(String.format("₹%.2f", subtotal != null ? subtotal : BigDecimal.ZERO), totalLabelFont));
            subtotalVal.setBorder(Rectangle.NO_BORDER);
            subtotalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            subtotalVal.setPadding(4);
            totalsTable.addCell(subtotalVal);

            PdfPCell taxLabel = new PdfPCell(new Phrase("Tax (0%):", totalLabelFont));
            taxLabel.setBorder(Rectangle.NO_BORDER);
            taxLabel.setPadding(4);
            totalsTable.addCell(taxLabel);

            PdfPCell taxVal = new PdfPCell(new Phrase("₹0.00", totalLabelFont));
            taxVal.setBorder(Rectangle.NO_BORDER);
            taxVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            taxVal.setPadding(4);
            totalsTable.addCell(taxVal);

            PdfPCell totalLabel = new PdfPCell(new Phrase("Total Due:", totalValueFont));
            totalLabel.setBorder(Rectangle.TOP);
            totalLabel.setPadding(6);
            totalsTable.addCell(totalLabel);

            BigDecimal total = (inv != null && inv.getTotalAmount() != null) ? inv.getTotalAmount() : BigDecimal.ZERO;
            PdfPCell totalVal = new PdfPCell(new Phrase(String.format("₹%.2f", total), totalValueFont));
            totalVal.setBorder(Rectangle.TOP);
            totalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalVal.setPadding(6);
            totalsTable.addCell(totalVal);

            document.add(totalsTable);

            document.close();
        } catch (DocumentException ex) {
            throw new RuntimeException("Error rendering invoice PDF", ex);
        }

        return baos.toByteArray();
    }

    private PdfPCell createCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private boolean isNumeric(String type) {
        return "NUMBER".equalsIgnoreCase(type) || "CURRENCY".equalsIgnoreCase(type) || "PERCENTAGE".equalsIgnoreCase(type);
    }

    private String formatCellValue(Object val, String type) {
        if (val == null) return "-";
        if ("CURRENCY".equalsIgnoreCase(type)) {
            if (val instanceof BigDecimal bd) return String.format("₹%.2f", bd);
            if (val instanceof Number n) return String.format("₹%.2f", n.doubleValue());
        }
        if ("PERCENTAGE".equalsIgnoreCase(type)) {
            if (val instanceof Number n) return String.format("%.1f%%", n.doubleValue());
        }
        return String.valueOf(val);
    }
}
