package pl.ngo.budget.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

final class ExcelTables implements AutoCloseable {

    private final XSSFWorkbook workbook;
    private final XSSFCellStyle header;
    private final XSSFCellStyle text;
    private final XSSFCellStyle textBold;
    private final XSSFCellStyle money;
    private final XSSFCellStyle moneyNeg;
    private final XSSFCellStyle moneyBold;
    private final XSSFCellStyle date;

    private ExcelTables(XSSFWorkbook workbook) {
        this.workbook = workbook;
        short moneyFormat = workbook.createDataFormat().getFormat("#,##0.00");
        short dateFormat = workbook.createDataFormat().getFormat("dd.mm.yyyy");
        header = headerStyle();
        text = textStyle(false);
        textBold = textStyle(true);
        money = moneyStyle(moneyFormat, false, IndexedColors.AUTOMATIC.getIndex());
        moneyNeg = moneyStyle(moneyFormat, true, IndexedColors.RED.getIndex());
        moneyBold = moneyStyle(moneyFormat, true, IndexedColors.AUTOMATIC.getIndex());
        date = textStyle(false);
        date.setDataFormat(dateFormat);
        date.setAlignment(HorizontalAlignment.RIGHT);
    }

    static ExcelTables create() {
        return new ExcelTables(new XSSFWorkbook());
    }

    Sheet sheet(String name) {
        String cleaned = name.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        if (cleaned.length() > 31) {
            cleaned = cleaned.substring(0, 31);
        }
        return workbook.createSheet(cleaned.isEmpty() ? "Arkusz" : cleaned);
    }

    void headers(Sheet sheet, String... titles) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(22);
        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(header);
        }
    }

    void text(Row row, int column, String value) {
        writeText(row, column, value, text);
    }

    void bold(Row row, int column, String value) {
        writeText(row, column, value, textBold);
    }

    void money(Row row, int column, BigDecimal value) {
        Cell cell = row.createCell(column);
        boolean negative = value != null && value.signum() < 0;
        cell.setCellStyle(negative ? moneyNeg : money);
        cell.setCellValue(value == null ? 0d : value.setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    void moneyBold(Row row, int column, BigDecimal value) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(value != null && value.signum() < 0 ? moneyNeg : moneyBold);
        cell.setCellValue(value == null ? 0d : value.setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    void date(Row row, int column, LocalDate value) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(date);
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    void layout(Sheet sheet, int lastRow, int lastColumn, int labelWidth) {
        sheet.createFreezePane(1, 1);
        if (lastRow >= 0 && lastColumn >= 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(lastRow, 1), 0, lastColumn));
        }
        sheet.setColumnWidth(0, labelWidth * 256);
        for (int column = 1; column <= lastColumn; column++) {
            sheet.setColumnWidth(column, 18 * 256);
        }
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
    }

    void write(OutputStream output) throws IOException {
        workbook.write(output);
    }

    @Override
    public void close() throws IOException {
        workbook.close();
    }

    private void writeText(Row row, int column, String value, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private XSSFCellStyle headerStyle() {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[] {0x21, 0x25, 0x29}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        borders(style);
        return style;
    }

    private XSSFCellStyle textStyle(boolean bold) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = workbook.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        borders(style);
        return style;
    }

    private XSSFCellStyle moneyStyle(short format, boolean bold, short color) {
        XSSFCellStyle style = textStyle(bold);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(format);
        XSSFFont font = workbook.createFont();
        font.setBold(bold);
        font.setColor(color);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private static void borders(XSSFCellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }
}
