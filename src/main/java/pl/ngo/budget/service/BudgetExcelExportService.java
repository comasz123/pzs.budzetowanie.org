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
import org.springframework.stereotype.Service;
import pl.ngo.budget.dto.BudgetDashboardDto;
import pl.ngo.budget.dto.BudgetDashboardDto.BudgetDisplayRowDto;
import pl.ngo.budget.dto.BudgetDashboardDto.BudgetItemRowDto;
import pl.ngo.budget.dto.BudgetRowDetailDto;
import pl.ngo.budget.dto.CostAllocationDto;
import pl.ngo.budget.util.PolishMonthNames;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BudgetExcelExportService {

    private static final String TOTAL_LABEL = "Razem (plan)";
    private static final String COST_HEADER = "Planowany Koszt";
    private static final String COVERAGE_HEADER = "Pokrycie w miesiącach";
    private static final String POSITION_HEADER = "Pozycja / Koszt";

    private final BudgetMatrixService budgetMatrixService;
    private final BudgetStructureService budgetStructureService;

    public BudgetExcelExportService(BudgetMatrixService budgetMatrixService,
                                    BudgetStructureService budgetStructureService) {
        this.budgetMatrixService = budgetMatrixService;
        this.budgetStructureService = budgetStructureService;
    }

    public void writeYear(OutputStream output, int fiscalYear) throws IOException {
        writeYears(output, List.of(fiscalYear));
    }

    public void writeYears(OutputStream output, List<Integer> fiscalYears) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            for (int fiscalYear : fiscalYears) {
                BudgetDashboardDto annual = budgetMatrixService.getBudgetDashboardDataForYear(fiscalYear);
                budgetStructureService.applyPlannedCosts(annual);
                List<BudgetDashboardDto> months = budgetMatrixService.getBudgetDashboardDataForMonths(fiscalYear);
                writeAnnualSheet(workbook, styles, fiscalYear, annual,
                        "Budżet " + fiscalYear, COST_HEADER, TOTAL_LABEL, true);
                writeMonthlySheet(workbook, styles, fiscalYear, annual, months);
            }
            workbook.write(output);
        }
    }

    /** One sheet: the matrix shown on the current year or month screen. */
    public void writeDashboard(OutputStream output, int fiscalYear, Integer month, boolean realization) throws IOException {
        BudgetDashboardDto data = loadDashboard(fiscalYear, month, realization);
        String costHeader = realization ? "Wydatki" : COST_HEADER;
        String totalLabel = realization ? "Razem" : TOTAL_LABEL;
        String title = realization ? "Realizacja " : "Budżet ";
        if (month != null) {
            title = title + PolishMonthNames.of(month) + " " + fiscalYear;
        } else {
            title = title + fiscalYear;
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            writeAnnualSheet(workbook, styles, fiscalYear, data, title, costHeader, totalLabel, !realization);
            workbook.write(output);
        }
    }

    public void writeRow(OutputStream output, int fiscalYear, Integer month, String rowKey, boolean realization) throws IOException {
        BudgetRowDetailDto detail = budgetMatrixService.getBudgetRowDetail(fiscalYear, month, rowKey);
        if (realization && detail.getDashboard() != null) {
            budgetMatrixService.applyRealizationExpenditures(detail.getDashboard(), fiscalYear, month);
        }
        BudgetDashboardDto dashboard = detail.getDashboard();
        BudgetItemRowDto row = detail.getRow();
        String costHeader = realization ? "Wydatki" : COST_HEADER;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            writeRowSheet(workbook, styles, fiscalYear, dashboard, row, costHeader, !realization);
            if (row.getAllocations() != null && !row.getAllocations().isEmpty()) {
                writeAllocationSheet(workbook, styles, dashboard, row);
            }
            workbook.write(output);
        }
    }

    private BudgetDashboardDto loadDashboard(int fiscalYear, Integer month, boolean realization) {
        BudgetDashboardDto data = month != null
                ? budgetMatrixService.getBudgetDashboardDataForMonth(fiscalYear, month)
                : budgetMatrixService.getBudgetDashboardDataForYear(fiscalYear);
        if (month == null && !realization) {
            budgetStructureService.applyPlannedCosts(data);
        }
        if (realization) {
            budgetMatrixService.applyRealizationExpenditures(data, fiscalYear, month);
        }
        return data;
    }

    private void writeAnnualSheet(XSSFWorkbook workbook,
                                  Styles styles,
                                  int fiscalYear,
                                  BudgetDashboardDto annual,
                                  String sheetTitle,
                                  String costHeader,
                                  String totalLabel,
                                  boolean includeGrantInfo) {
        Sheet sheet = workbook.createSheet(sheetName(sheetTitle));
        List<String> grants = annual.getGrantNames() != null ? annual.getGrantNames() : List.of();
        // Widok planowania: dodatkowa kolumna "Planowany Koszt" przed kolumną pokrycia w miesiącach.
        int grantStart = includeGrantInfo ? 3 : 2;
        int balanceColumn = grantStart + grants.size();

        int rowIndex = 0;
        if (includeGrantInfo) {
            rowIndex = writeGrantInfoRow(sheet, styles, rowIndex, "Zostało do wydania (rok)",
                    grants, annual.getGrantRemainingByName(), grantStart, balanceColumn);
            rowIndex = writeGrantInfoRow(sheet, styles, rowIndex, "Całkowita suma grantu",
                    grants, annual.getGrantFullTotalByName(), grantStart, balanceColumn);
            rowIndex = writeGrantInfoRow(sheet, styles, rowIndex, "Suma na rok " + fiscalYear,
                    grants, annual.getGrantYearAmountByName(), grantStart, balanceColumn);
            rowIndex = writeGrantInfoRow(sheet, styles, rowIndex, "Alokowano na rok " + (fiscalYear + 1),
                    grants, annual.getGrantNextYearByName(), grantStart, balanceColumn);
            rowIndex++;
        }

        int headerRow = rowIndex;
        Row header = sheet.createRow(headerRow);
        header.setHeightInPoints(36);
        writeText(header, 0, POSITION_HEADER, styles.headerLeft);
        // Widok planowania: kolumna "Planowany Koszt" (ze struktury budżetu), a po niej pokrycie w miesiącach.
        int monthsColumn = includeGrantInfo ? 2 : 1;
        if (includeGrantInfo) {
            writeText(header, 1, COST_HEADER, styles.header);
        }
        String monthsHeader = annual.getTotalMonthsGap() != null ? COVERAGE_HEADER
                : (includeGrantInfo ? "Koszt w miesiącu" : costHeader);
        writeText(header, monthsColumn, monthsHeader, styles.header);
        for (int i = 0; i < grants.size(); i++) {
            writeText(header, grantStart + i, grants.get(i), styles.header);
        }
        writeText(header, balanceColumn, "Bilans", styles.header);
        rowIndex++;

        List<BudgetDisplayRowDto> rows = annual.getDisplayRows() != null ? annual.getDisplayRows() : List.of();
        int firstDataRow = rowIndex;
        for (BudgetDisplayRowDto displayRow : rows) {
            int depth = depthOf(displayRow);
            Row row = sheet.createRow(rowIndex++);
            writeText(row, 0, displayRow.getItemName(), styles.text(depth));
            if (includeGrantInfo) {
                writeMoney(row, 1, displayRow.getPlannedCost(), styles.money(depth, displayRow.getPlannedCost()));
            }
            BigDecimal monthsValue = displayRow.getMonthsGap() != null ? displayRow.getMonthsGap() : displayRow.getTotalCost();
            writeMoney(row, monthsColumn, monthsValue, styles.money(depth, monthsValue));
            for (int i = 0; i < grants.size(); i++) {
                BigDecimal coverage = displayRow.getCoverageByGrant() != null
                        ? displayRow.getCoverageByGrant().get(grants.get(i))
                        : null;
                writeMoney(row, grantStart + i, coverage, styles.money(depth, coverage));
            }
            writeMoney(row, balanceColumn, displayRow.getBilans(), styles.money(depth, displayRow.getBilans()));
        }
        outlineRows(sheet, firstDataRow, rows);

        Row total = sheet.createRow(rowIndex);
        writeText(total, 0, totalLabel, styles.totalText);
        if (includeGrantInfo) {
            writeMoney(total, 1, annual.getTotalPlannedCost(), styles.totalMoney(annual.getTotalPlannedCost()));
        }
        BigDecimal monthsTotal = annual.getTotalMonthsGap() != null ? annual.getTotalMonthsGap() : annual.getTotalCost();
        writeMoney(total, monthsColumn, monthsTotal, styles.totalMoney(monthsTotal));
        for (int i = 0; i < grants.size(); i++) {
            BigDecimal coverage = annual.getTotalCoverageByGrant() != null
                    ? annual.getTotalCoverageByGrant().get(grants.get(i))
                    : null;
            writeMoney(total, grantStart + i, coverage, styles.totalMoney(coverage));
        }
        writeMoney(total, balanceColumn, annual.getBilans(), styles.totalMoney(annual.getBilans()));

        Row note = sheet.createRow(rowIndex + 2);
        writeText(note, 0,
                "Razem liczy kategorie główne. Wiersze poniżej kategorii są ich rozwinięciem i nie dodają się drugi raz.",
                styles.note);
        sheet.addMergedRegion(new CellRangeAddress(note.getRowNum(), note.getRowNum(), 0, balanceColumn));

        sheet.createFreezePane(1, headerRow + 1);
        sheet.setAutoFilter(new CellRangeAddress(headerRow, rowIndex, 0, balanceColumn));
        applySheetLayout(sheet, balanceColumn, 42, 18);
    }

    private void writeRowSheet(XSSFWorkbook workbook,
                               Styles styles,
                               int fiscalYear,
                               BudgetDashboardDto dashboard,
                               BudgetItemRowDto row,
                               String costHeader,
                               boolean includeGrantInfo) {
        Sheet sheet = workbook.createSheet(sheetName(row.getItemName() != null ? row.getItemName() : "Pozycja"));
        List<String> grants = dashboard.getGrantNames() != null ? dashboard.getGrantNames() : List.of();
        int balanceColumn = 2 + grants.size();
        int rowIndex = 0;
        if (includeGrantInfo) {
            rowIndex = writeGrantInfoRow(sheet, styles, rowIndex, "Zostało do wydania (rok)",
                    grants, dashboard.getGrantRemainingByName(), 2, balanceColumn);
            rowIndex = writeGrantInfoRow(sheet, styles, rowIndex, "Całkowita suma grantu",
                    grants, dashboard.getGrantFullTotalByName(), 2, balanceColumn);
            rowIndex = writeGrantInfoRow(sheet, styles, rowIndex, "Suma na rok " + fiscalYear,
                    grants, dashboard.getGrantYearAmountByName(), 2, balanceColumn);
            rowIndex = writeGrantInfoRow(sheet, styles, rowIndex, "Alokowano na rok " + (fiscalYear + 1),
                    grants, dashboard.getGrantNextYearByName(), 2, balanceColumn);
            rowIndex++;
        }
        int headerRow = rowIndex;
        Row header = sheet.createRow(headerRow);
        header.setHeightInPoints(36);
        writeText(header, 0, POSITION_HEADER, styles.headerLeft);
        writeText(header, 1, costHeader, styles.header);
        for (int i = 0; i < grants.size(); i++) {
            writeText(header, 2 + i, grants.get(i), styles.header);
        }
        writeText(header, balanceColumn, "Bilans", styles.header);
        rowIndex++;
        writeItemRow(sheet, styles, rowIndex++, row, grants, balanceColumn, 0);
        if (row.getChildren() != null) {
            for (BudgetItemRowDto child : row.getChildren()) {
                writeItemRow(sheet, styles, rowIndex++, child, grants, balanceColumn, 1);
            }
        }
        sheet.createFreezePane(1, headerRow + 1);
        applySheetLayout(sheet, balanceColumn, 42, 18);
    }

    private void writeItemRow(Sheet sheet,
                              Styles styles,
                              int rowIndex,
                              BudgetItemRowDto item,
                              List<String> grants,
                              int balanceColumn,
                              int depth) {
        Row row = sheet.createRow(rowIndex);
        writeText(row, 0, item.getItemName(), styles.text(depth));
        writeMoney(row, 1, item.getTotalCost(), styles.money(depth, item.getTotalCost()));
        for (int i = 0; i < grants.size(); i++) {
            BigDecimal coverage = item.getCoverageByGrant() != null
                    ? item.getCoverageByGrant().get(grants.get(i))
                    : null;
            writeMoney(row, 2 + i, coverage, styles.money(depth, coverage));
        }
        writeMoney(row, balanceColumn, item.getBilans(), styles.money(depth, item.getBilans()));
    }

    private void writeAllocationSheet(XSSFWorkbook workbook,
                                      Styles styles,
                                      BudgetDashboardDto dashboard,
                                      BudgetItemRowDto row) {
        Sheet sheet = workbook.createSheet("Alokacje");
        List<String> grants = dashboard.getGrantNames() != null ? dashboard.getGrantNames() : List.of();
        int amountColumn = 1 + grants.size();
        int balanceColumn = amountColumn + 1;
        int percentColumn = balanceColumn + 1;
        Row header = sheet.createRow(0);
        header.setHeightInPoints(36);
        writeText(header, 0, "Pozycja / Kategoria", styles.headerLeft);
        for (int i = 0; i < grants.size(); i++) {
            writeText(header, 1 + i, grants.get(i), styles.header);
        }
        writeText(header, amountColumn, "Kwota", styles.header);
        writeText(header, balanceColumn, "Bilans", styles.header);
        writeText(header, percentColumn, "Udział %", styles.header);
        int rowIndex = 1;
        for (CostAllocationDto allocation : row.getAllocations()) {
            Row excelRow = sheet.createRow(rowIndex++);
            writeText(excelRow, 0, allocation.getItemName(), styles.text(1));
            for (int i = 0; i < grants.size(); i++) {
                BigDecimal amount = allocation.getAmountByGrant() != null
                        ? allocation.getAmountByGrant().get(grants.get(i))
                        : null;
                writeMoney(excelRow, 1 + i, amount, styles.money(1, amount));
            }
            writeMoney(excelRow, amountColumn, allocation.getAmount(), styles.money(1, allocation.getAmount()));
            writeMoney(excelRow, balanceColumn, allocation.getBilans(), styles.money(1, allocation.getBilans()));
            writeMoney(excelRow, percentColumn, allocation.getPercent(), styles.money(1, allocation.getPercent()));
        }
        sheet.createFreezePane(1, 1);
        applySheetLayout(sheet, percentColumn, 42, 18);
    }

    private void writeMonthlySheet(XSSFWorkbook workbook,
                                   Styles styles,
                                   int fiscalYear,
                                   BudgetDashboardDto annual,
                                   List<BudgetDashboardDto> months) {
        Sheet sheet = workbook.createSheet(sheetName("Miesiące " + fiscalYear));
        int yearColumn = 13;

        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        writeText(header, 0, POSITION_HEADER, styles.headerLeft);
        for (int month = 1; month <= 12; month++) {
            writeText(header, month, PolishMonthNames.of(month), styles.header);
        }
        writeText(header, yearColumn, "Rok " + fiscalYear, styles.header);

        List<BudgetDisplayRowDto> rows = annual.getDisplayRows() != null ? annual.getDisplayRows() : List.of();
        List<Map<String, BigDecimal>> costByIdentity = months.stream()
                .map(month -> costsByIdentity(month.getDisplayRows()))
                .toList();
        List<String> identities = identities(rows);

        int rowIndex = 1;
        for (int i = 0; i < rows.size(); i++) {
            BudgetDisplayRowDto displayRow = rows.get(i);
            int depth = depthOf(displayRow);
            Row row = sheet.createRow(rowIndex++);
            writeText(row, 0, displayRow.getItemName(), styles.text(depth));
            String identity = identities.get(i);
            for (int month = 0; month < 12; month++) {
                BigDecimal cost = costByIdentity.get(month).get(identity);
                writeMoney(row, month + 1, cost, styles.money(depth, cost));
            }
            writeMoney(row, yearColumn, displayRow.getTotalCost(), styles.money(depth, displayRow.getTotalCost()));
        }
        outlineRows(sheet, 1, rows);

        Row total = sheet.createRow(rowIndex);
        writeText(total, 0, TOTAL_LABEL, styles.totalText);
        for (int month = 0; month < 12; month++) {
            BigDecimal cost = months.get(month).getTotalCost();
            writeMoney(total, month + 1, cost, styles.totalMoney(cost));
        }
        writeMoney(total, yearColumn, annual.getTotalCost(), styles.totalMoney(annual.getTotalCost()));

        sheet.createFreezePane(1, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(rowIndex, 1), 0, yearColumn));
        applySheetLayout(sheet, yearColumn, 42, 14);
    }

    private int writeGrantInfoRow(Sheet sheet,
                                  Styles styles,
                                  int rowIndex,
                                  String label,
                                  List<String> grants,
                                  Map<String, BigDecimal> amounts,
                                  int grantStart,
                                  int balanceColumn) {
        Row row = sheet.createRow(rowIndex);
        writeText(row, 0, label, styles.infoText);
        writeText(row, 1, "—", styles.infoText);
        if (grantStart > 2) {
            writeText(row, 2, "—", styles.infoText);
        }
        for (int i = 0; i < grants.size(); i++) {
            BigDecimal amount = amounts != null ? amounts.get(grants.get(i)) : null;
            writeMoney(row, grantStart + i, amount, styles.infoMoney(amount));
        }
        writeText(row, balanceColumn, "—", styles.infoText);
        return rowIndex + 1;
    }

    private static void outlineRows(Sheet sheet, int firstDataRow, List<BudgetDisplayRowDto> rows) {
        sheet.setRowSumsBelow(false);
        for (int i = 0; i < rows.size(); i++) {
            int depth = depthOf(rows.get(i));
            int end = i;
            while (end + 1 < rows.size() && depthOf(rows.get(end + 1)) > depth) {
                end++;
            }
            if (end > i) {
                sheet.groupRow(firstDataRow + i + 1, firstDataRow + end);
            }
        }
    }

    private static Map<String, BigDecimal> costsByIdentity(List<BudgetDisplayRowDto> rows) {
        Map<String, BigDecimal> costs = new HashMap<>();
        if (rows == null) {
            return costs;
        }
        List<String> keys = identities(rows);
        for (int i = 0; i < rows.size(); i++) {
            BigDecimal cost = rows.get(i).getTotalCost() != null ? rows.get(i).getTotalCost() : BigDecimal.ZERO;
            costs.put(keys.get(i), cost);
        }
        return costs;
    }

    private static List<String> identities(List<BudgetDisplayRowDto> rows) {
        Map<String, Integer> seen = new HashMap<>();
        List<String> keys = new ArrayList<>();
        if (rows == null) {
            return keys;
        }
        for (BudgetDisplayRowDto row : rows) {
            String base = depthOf(row)
                    + "|" + empty(row.getCategoryRootRowKey())
                    + "|" + empty(row.getParentRowKey())
                    + "|" + empty(row.getRowKey())
                    + "|" + empty(row.getItemName());
            int occurrence = seen.merge(base, 1, Integer::sum);
            keys.add(base + "|" + occurrence);
        }
        return keys;
    }

    private static void applySheetLayout(Sheet sheet, int lastColumn, int labelWidth, int amountWidth) {
        sheet.setColumnWidth(0, labelWidth * 256);
        for (int column = 1; column <= lastColumn; column++) {
            sheet.setColumnWidth(column, amountWidth * 256);
        }
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setDisplayGuts(true);
    }

    private static void writeText(Row row, int column, String value, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private static void writeMoney(Row row, int column, BigDecimal value, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        if (value == null) {
            cell.setCellValue(0d);
            return;
        }
        cell.setCellValue(value.setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    private static int depthOf(BudgetDisplayRowDto row) {
        int depth = row.getDepth();
        if (depth < 0) {
            return 0;
        }
        return Math.min(depth, 2);
    }

    private static String empty(String value) {
        return value != null ? value : "";
    }

    private static String sheetName(String name) {
        String cleaned = name.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        if (cleaned.length() > 31) {
            return cleaned.substring(0, 31);
        }
        return cleaned;
    }

    private static final class Styles {
        private final XSSFCellStyle header;
        private final XSSFCellStyle headerLeft;
        private final XSSFCellStyle infoText;
        private final MoneyStyle infoMoney;
        private final XSSFCellStyle[] text = new XSSFCellStyle[3];
        private final MoneyStyle[] money = new MoneyStyle[3];
        private final XSSFCellStyle totalText;
        private final MoneyStyle totalMoney;
        private final XSSFCellStyle note;
        private final short moneyFormat;

        private Styles(XSSFWorkbook workbook) {
            moneyFormat = workbook.createDataFormat().getFormat("#,##0.00");
            header = headerStyle(workbook, HorizontalAlignment.RIGHT);
            headerLeft = headerStyle(workbook, HorizontalAlignment.LEFT);
            infoText = infoText(workbook);
            infoMoney = moneyStyle(workbook, fill(0xF8, 0xF9, 0xFA), false, IndexedColors.AUTOMATIC.getIndex());
            text[0] = textStyle(workbook, fill(0xE9, 0xEC, 0xEF), true, IndexedColors.AUTOMATIC.getIndex(), 0);
            text[1] = textStyle(workbook, null, false, IndexedColors.AUTOMATIC.getIndex(), 2);
            text[2] = textStyle(workbook, null, false, IndexedColors.GREY_50_PERCENT.getIndex(), 4);
            money[0] = moneyStyle(workbook, fill(0xE9, 0xEC, 0xEF), true, IndexedColors.AUTOMATIC.getIndex());
            money[1] = moneyStyle(workbook, null, false, IndexedColors.AUTOMATIC.getIndex());
            money[2] = moneyStyle(workbook, null, false, IndexedColors.GREY_50_PERCENT.getIndex());
            totalText = textStyle(workbook, fill(0xDE, 0xE2, 0xE6), true, IndexedColors.AUTOMATIC.getIndex(), 0);
            totalMoney = moneyStyle(workbook, fill(0xDE, 0xE2, 0xE6), true, IndexedColors.AUTOMATIC.getIndex());
            note = noteStyle(workbook);
        }

        private XSSFCellStyle text(int depth) {
            return text[depth];
        }

        private XSSFCellStyle money(int depth, BigDecimal value) {
            return money[depth].forValue(value);
        }

        private XSSFCellStyle infoMoney(BigDecimal value) {
            return infoMoney.forValue(value);
        }

        private XSSFCellStyle totalMoney(BigDecimal value) {
            return totalMoney.forValue(value);
        }

        private XSSFCellStyle headerStyle(XSSFWorkbook workbook, HorizontalAlignment alignment) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(fill(0x21, 0x25, 0x29));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(alignment);
            style.setVerticalAlignment(VerticalAlignment.BOTTOM);
            style.setWrapText(true);
            XSSFFont font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            font.setFontHeightInPoints((short) 9);
            style.setFont(font);
            borders(style);
            return style;
        }

        private XSSFCellStyle infoText(XSSFWorkbook workbook) {
            XSSFCellStyle style = textStyle(workbook, fill(0xF8, 0xF9, 0xFA), false, IndexedColors.AUTOMATIC.getIndex(), 0);
            XSSFFont font = workbook.createFont();
            font.setItalic(true);
            font.setFontHeightInPoints((short) 9);
            style.setFont(font);
            return style;
        }

        private XSSFCellStyle textStyle(XSSFWorkbook workbook,
                                        XSSFColor background,
                                        boolean bold,
                                        short fontColor,
                                        int indent) {
            XSSFCellStyle style = workbook.createCellStyle();
            if (background != null) {
                style.setFillForegroundColor(background);
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setIndention((short) indent);
            XSSFFont font = workbook.createFont();
            font.setBold(bold);
            font.setColor(fontColor);
            font.setFontHeightInPoints((short) 10);
            style.setFont(font);
            borders(style);
            return style;
        }

        private MoneyStyle moneyStyle(XSSFWorkbook workbook,
                                      XSSFColor background,
                                      boolean bold,
                                      short fontColor) {
            XSSFCellStyle positive = amountStyle(workbook, background, bold, fontColor);
            XSSFCellStyle negative = amountStyle(workbook, background, true, IndexedColors.RED.getIndex());
            return new MoneyStyle(positive, negative);
        }

        private XSSFCellStyle amountStyle(XSSFWorkbook workbook,
                                          XSSFColor background,
                                          boolean bold,
                                          short fontColor) {
            XSSFCellStyle style = workbook.createCellStyle();
            if (background != null) {
                style.setFillForegroundColor(background);
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            style.setAlignment(HorizontalAlignment.RIGHT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setDataFormat(moneyFormat);
            XSSFFont font = workbook.createFont();
            font.setBold(bold);
            font.setColor(fontColor);
            font.setFontHeightInPoints((short) 10);
            style.setFont(font);
            borders(style);
            return style;
        }

        private XSSFCellStyle noteStyle(XSSFWorkbook workbook) {
            XSSFCellStyle style = workbook.createCellStyle();
            XSSFFont font = workbook.createFont();
            font.setItalic(true);
            font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            font.setFontHeightInPoints((short) 9);
            style.setFont(font);
            return style;
        }

        private static XSSFColor fill(int red, int green, int blue) {
            return new XSSFColor(new byte[] {(byte) red, (byte) green, (byte) blue}, null);
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

        private static final class MoneyStyle {
            private final XSSFCellStyle positive;
            private final XSSFCellStyle negative;

            private MoneyStyle(XSSFCellStyle positive, XSSFCellStyle negative) {
                this.positive = positive;
                this.negative = negative;
            }

            private XSSFCellStyle forValue(BigDecimal value) {
                if (value != null && value.signum() < 0) {
                    return negative;
                }
                return positive;
            }
        }
    }
}
