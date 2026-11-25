package com.labresource.platform.report.service;

import com.labresource.platform.report.model.ReportColumn;
import com.labresource.platform.report.model.ReportResult;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExcelExportService {

    public byte[] generateReportExcel(ReportResult report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            // 1. [Content_Types].xml
            writeEntry(zos, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Default Extension="xml" ContentType="application/xml"/>
                        <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                        <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                        <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>
                    """);

            // 2. _rels/.rels
            writeEntry(zos, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);

            // 3. xl/_rels/workbook.xml.rels
            writeEntry(zos, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                        <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>
                    """);

            // 4. xl/workbook.xml
            writeEntry(zos, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                        <sheets>
                            <sheet name="Report" sheetId="1" r:id="rId1"/>
                        </sheets>
                    </workbook>
                    """);

            // 5. xl/styles.xml
            writeEntry(zos, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                        <fonts count="2">
                            <font><name val="Calibri"/><sz val="11"/></font>
                            <font><b/><name val="Calibri"/><sz val="11"/></font>
                        </fonts>
                        <fills count="2">
                            <fill><patternFill patternType="none"/></fill>
                            <fill><patternFill patternType="gray125"/></fill>
                        </fills>
                        <borders count="1">
                            <border><left/><right/><top/><bottom/></border>
                        </borders>
                        <cellXfs count="2">
                            <xf fontId="0" fillId="0" borderId="0"/>
                            <xf fontId="1" fillId="0" borderId="0"/>
                        </cellXfs>
                    </styleSheet>
                    """);

            // 6. xl/worksheets/sheet1.xml
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(zos, StandardCharsets.UTF_8));
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            pw.println("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            pw.println("  <sheetData>");

            int rowIdx = 1;

            // Title row
            pw.println("    <row r=\"" + rowIdx + "\">");
            pw.println("      <c r=\"A" + rowIdx + "\" s=\"1\" t=\"inlineStr\"><is><t>" + escapeXml(report.reportTitle()) + "</t></is></c>");
            pw.println("    </row>");
            rowIdx++;

            // Generated at row
            pw.println("    <row r=\"" + rowIdx + "\">");
            pw.println("      <c r=\"A" + rowIdx + "\" t=\"inlineStr\"><is><t>Generated at: " + escapeXml(String.valueOf(report.generatedAt())) + "</t></is></c>");
            pw.println("    </row>");
            rowIdx++;

            // Blank separator row
            rowIdx++;

            // Header row
            pw.println("    <row r=\"" + rowIdx + "\">");
            for (int colIdx = 0; colIdx < report.columns().size(); colIdx++) {
                ReportColumn col = report.columns().get(colIdx);
                String cellRef = getColumnRef(colIdx) + rowIdx;
                pw.println("      <c r=\"" + cellRef + "\" s=\"1\" t=\"inlineStr\"><is><t>" + escapeXml(col.label()) + "</t></is></c>");
            }
            pw.println("    </row>");
            rowIdx++;

            // Data rows
            for (Map<String, Object> dataRow : report.rows()) {
                pw.println("    <row r=\"" + rowIdx + "\">");
                for (int colIdx = 0; colIdx < report.columns().size(); colIdx++) {
                    ReportColumn col = report.columns().get(colIdx);
                    String cellRef = getColumnRef(colIdx) + rowIdx;
                    Object val = dataRow.get(col.key());
                    writeCellXml(pw, cellRef, val, col.type(), false);
                }
                pw.println("    </row>");
                rowIdx++;
            }

            // Totals row
            if (report.totals() != null && !report.totals().isEmpty()) {
                pw.println("    <row r=\"" + rowIdx + "\">");
                for (int colIdx = 0; colIdx < report.columns().size(); colIdx++) {
                    ReportColumn col = report.columns().get(colIdx);
                    String cellRef = getColumnRef(colIdx) + rowIdx;
                    Object val = report.totals().get(col.key());
                    if (val == null && colIdx == 0) {
                        val = "TOTAL";
                    }
                    writeCellXml(pw, cellRef, val, col.type(), true);
                }
                pw.println("    </row>");
            }

            pw.println("  </sheetData>");
            pw.println("</worksheet>");
            pw.flush();
            zos.closeEntry();
        } catch (IOException ex) {
            throw new RuntimeException("Error rendering report Excel spreadsheet", ex);
        }

        return baos.toByteArray();
    }

    private void writeEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        byte[] bytes = content.trim().getBytes(StandardCharsets.UTF_8);
        zos.write(bytes);
        zos.closeEntry();
    }

    private void writeCellXml(PrintWriter pw, String cellRef, Object val, String type, boolean bold) {
        String sAttr = bold ? " s=\"1\"" : "";
        if (val == null) {
            pw.println("      <c r=\"" + cellRef + "\"" + sAttr + " t=\"inlineStr\"><is><t>-</t></is></c>");
            return;
        }

        if (isNumeric(type) && val instanceof Number num) {
            pw.println("      <c r=\"" + cellRef + "\"" + sAttr + "><v>" + num + "</v></c>");
        } else {
            String strVal = escapeXml(String.valueOf(val));
            pw.println("      <c r=\"" + cellRef + "\"" + sAttr + " t=\"inlineStr\"><is><t>" + strVal + "</t></is></c>");
        }
    }

    private boolean isNumeric(String type) {
        return "NUMBER".equalsIgnoreCase(type) || "CURRENCY".equalsIgnoreCase(type) || "PERCENTAGE".equalsIgnoreCase(type);
    }

    private String getColumnRef(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = (n / 26) - 1;
        }
        return sb.toString();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
