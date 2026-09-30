package pl.ngo.budget.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.dto.BudgetDashboardDto;
import pl.ngo.budget.dto.BudgetDashboardDto.BudgetDisplayRowDto;
import pl.ngo.budget.dto.GrantBudgetViewDto;
import pl.ngo.budget.dto.GrantBudgetViewDto.CoveredOrgBudgetLineDto;
import pl.ngo.budget.dto.GrantBudgetViewDto.GrantBudgetLineDto;
import pl.ngo.budget.dto.GrantListRowDto;
import pl.ngo.budget.dto.HomeChartDto;
import pl.ngo.budget.dto.NewBudgetWizardDto;
import pl.ngo.budget.dto.NewBudgetWizardDto.NewBudgetCategoryDto;
import pl.ngo.budget.dto.NewBudgetWizardDto.NewBudgetLineDto;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.cost.Expenditure;
import pl.ngo.budget.entity.cost.ExpenditureGrantShare;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.Project;
import pl.ngo.budget.entity.coverage.Sponsor;
import pl.ngo.budget.entity.coverage.SponsorContact;
import pl.ngo.budget.repository.EmployeeRepository;
import pl.ngo.budget.repository.SponsorContactRepository;
import pl.ngo.budget.repository.SponsorRepository;
import pl.ngo.budget.util.PolishMonthNames;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class ViewExcelExportService {

    private final BudgetMatrixService budgetMatrixService;
    private final GrantBudgetService grantBudgetService;
    private final ExpenditureImportService expenditureImportService;
    private final NewBudgetService newBudgetService;
    private final SponsorRepository sponsorRepository;
    private final SponsorContactRepository sponsorContactRepository;
    private final EmployeeRepository employeeRepository;

    public ViewExcelExportService(BudgetMatrixService budgetMatrixService,
                                  GrantBudgetService grantBudgetService,
                                  ExpenditureImportService expenditureImportService,
                                  NewBudgetService newBudgetService,
                                  SponsorRepository sponsorRepository,
                                  SponsorContactRepository sponsorContactRepository,
                                  EmployeeRepository employeeRepository) {
        this.budgetMatrixService = budgetMatrixService;
        this.grantBudgetService = grantBudgetService;
        this.expenditureImportService = expenditureImportService;
        this.newBudgetService = newBudgetService;
        this.sponsorRepository = sponsorRepository;
        this.sponsorContactRepository = sponsorContactRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public void writeHome(OutputStream output, int year) throws IOException {
        HomeChartDto chart = budgetMatrixService.getHomeChart(year);
        try (ExcelTables excel = ExcelTables.create()) {
            Sheet sheet = excel.sheet("Pulpit " + year);
            excel.headers(sheet, "Miesiąc", "Wpływy", "Planowany koszt", "Saldo narastające");
            BigDecimal running = BigDecimal.ZERO;
            BigDecimal incomeTotal = BigDecimal.ZERO;
            BigDecimal costTotal = BigDecimal.ZERO;
            List<String> months = chart.getMonths() != null ? chart.getMonths() : PolishMonthNames.SHORT;
            for (int i = 0; i < 12; i++) {
                BigDecimal income = at(chart.getIncome(), i);
                BigDecimal cost = at(chart.getPlannedCost(), i);
                running = running.add(income).subtract(cost);
                incomeTotal = incomeTotal.add(income);
                costTotal = costTotal.add(cost);
                Row row = sheet.createRow(i + 1);
                excel.text(row, 0, i < months.size() ? months.get(i) : PolishMonthNames.of(i + 1));
                excel.money(row, 1, income);
                excel.money(row, 2, cost);
                excel.money(row, 3, running);
            }
            Row total = sheet.createRow(13);
            excel.bold(total, 0, "Razem");
            excel.moneyBold(total, 1, incomeTotal);
            excel.moneyBold(total, 2, costTotal);
            excel.moneyBold(total, 3, running);
            excel.layout(sheet, 13, 3, 16);
            excel.write(output);
        }
    }

    @Transactional(readOnly = true)
    public void writeGrants(OutputStream output, int fiscalYear) throws IOException {
        List<GrantListRowDto> rows = grantBudgetService.buildGrantListRows(fiscalYear);
        try (ExcelTables excel = ExcelTables.create()) {
            Sheet sheet = excel.sheet("Granty " + fiscalYear);
            excel.headers(sheet, "Kod", "Nazwa", "Sponsor", "Projekt", "Od", "Do",
                    "Całkowity budżet", "Alokowane w " + fiscalYear, "Pozostało", "Aktywny");
            int rowIndex = 1;
            for (GrantListRowDto row : rows) {
                Grant grant = row.getGrant();
                Row excelRow = sheet.createRow(rowIndex++);
                excel.text(excelRow, 0, grant.getCode());
                excel.text(excelRow, 1, grant.getName());
                excel.text(excelRow, 2, grant.getSponsor() != null ? grant.getSponsor().getName() : "");
                excel.text(excelRow, 3, grant.getProject() != null ? grant.getProject().getName() : "");
                excel.date(excelRow, 4, grant.getStartDate());
                excel.date(excelRow, 5, grant.getEndDate());
                excel.money(excelRow, 6, grant.getTotalAmount());
                excel.money(excelRow, 7, row.getAllocatedInYear());
                excel.money(excelRow, 8, row.getRemaining());
                excel.text(excelRow, 9, grant.isActive() ? "Tak" : "Nie");
            }
            excel.layout(sheet, Math.max(rowIndex - 1, 1), 9, 28);
            excel.write(output);
        }
    }

    @Transactional(readOnly = true)
    public void writeGrantBudget(OutputStream output, Long grantId) throws IOException {
        GrantBudgetViewDto budget = grantBudgetService.buildGrantBudgetView(grantId);
        try (ExcelTables excel = ExcelTables.create()) {
            writeTranches(excel, budget);
            writeGrantLines(excel, budget);
            excel.write(output);
        }
    }

    @Transactional(readOnly = true)
    public void writeSponsors(OutputStream output) throws IOException {
        List<Sponsor> sponsors = sponsorRepository.findAll().stream()
                .sorted(Comparator.comparing(Sponsor::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<SponsorContact> contacts = sponsorContactRepository.findAllWithSponsor();
        try (ExcelTables excel = ExcelTables.create()) {
            Sheet sponsorsSheet = excel.sheet("Sponsorzy");
            excel.headers(sponsorsSheet, "Nazwa", "Typ", "Miasto", "E-mail", "Telefon");
            int rowIndex = 1;
            for (Sponsor sponsor : sponsors) {
                Row row = sponsorsSheet.createRow(rowIndex++);
                excel.text(row, 0, sponsor.getName());
                excel.text(row, 1, sponsor.getType());
                excel.text(row, 2, sponsor.getCity());
                excel.text(row, 3, sponsor.getEmail());
                excel.text(row, 4, sponsor.getPhone());
            }
            excel.layout(sponsorsSheet, Math.max(rowIndex - 1, 1), 4, 32);

            Sheet contactsSheet = excel.sheet("Kontakty");
            excel.headers(contactsSheet, "Sponsor", "Imię i nazwisko", "Stanowisko", "E-mail", "Telefon");
            rowIndex = 1;
            for (SponsorContact contact : contacts) {
                Row row = contactsSheet.createRow(rowIndex++);
                excel.text(row, 0, contact.getSponsor() != null ? contact.getSponsor().getName() : "");
                excel.text(row, 1, joinName(contact.getFirstName(), contact.getLastName()));
                excel.text(row, 2, contact.getPosition());
                excel.text(row, 3, contact.getEmail());
                excel.text(row, 4, contact.getPhone());
            }
            excel.layout(contactsSheet, Math.max(rowIndex - 1, 1), 4, 32);
            excel.write(output);
        }
    }

    @Transactional(readOnly = true)
    public void writeEmployees(OutputStream output) throws IOException {
        List<Employee> employees = employeeRepository.findAll().stream()
                .sorted(Comparator.comparing(Employee::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Employee::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        try (ExcelTables excel = ExcelTables.create()) {
            Sheet sheet = excel.sheet("Pracownicy");
            excel.headers(sheet, "Imię i nazwisko", "Stanowisko", "E-mail", "Telefon", "Planowany koszt");
            int rowIndex = 1;
            for (Employee employee : employees) {
                Row row = sheet.createRow(rowIndex++);
                excel.text(row, 0, joinName(employee.getFirstName(), employee.getLastName()));
                excel.text(row, 1, employee.getPosition());
                excel.text(row, 2, employee.getEmail());
                excel.text(row, 3, employee.getPhone());
                excel.money(row, 4, employee.getPlannedCost());
            }
            excel.layout(sheet, Math.max(rowIndex - 1, 1), 4, 32);
            excel.write(output);
        }
    }

    @Transactional(readOnly = true)
    public void writeExpenditures(OutputStream output, int fiscalYear, int month) throws IOException {
        List<Expenditure> expenditures = expenditureImportService.listForMonth(fiscalYear, month);
        try (ExcelTables excel = ExcelTables.create()) {
            Sheet sheet = excel.sheet(PolishMonthNames.of(month) + " " + fiscalYear);
            excel.headers(sheet, "Nr dokumentu", "Data wystawienia", "Data płatności", "Opis",
                    "Kategoria", "Netto", "Brutto", "Grant", "Projekt");
            int rowIndex = 1;
            BigDecimal net = BigDecimal.ZERO;
            BigDecimal gross = BigDecimal.ZERO;
            for (Expenditure expenditure : expenditures) {
                Row row = sheet.createRow(rowIndex++);
                excel.text(row, 0, expenditure.getDocumentNumber());
                excel.date(row, 1, expenditure.getIssueDate());
                excel.date(row, 2, expenditure.getPaymentDate());
                excel.text(row, 3, expenditure.getDescription());
                excel.text(row, 4, expenditure.getBudgetItem() != null ? expenditure.getBudgetItem().getCode() : "");
                excel.money(row, 5, expenditure.getNetAmount());
                excel.money(row, 6, expenditure.getGrossAmount());
                excel.text(row, 7, grantLabel(expenditure));
                excel.text(row, 8, projectName(expenditure));
                if (expenditure.getNetAmount() != null) {
                    net = net.add(expenditure.getNetAmount());
                }
                if (expenditure.getGrossAmount() != null) {
                    gross = gross.add(expenditure.getGrossAmount());
                }
            }
            Row total = sheet.createRow(rowIndex);
            excel.bold(total, 0, "Razem");
            excel.moneyBold(total, 5, net);
            excel.moneyBold(total, 6, gross);
            excel.layout(sheet, rowIndex, 8, 22);
            excel.write(output);
        }
    }

    @Transactional(readOnly = true)
    public void writeNewBudgetPlan(OutputStream output, int fiscalYear) throws IOException {
        NewBudgetWizardDto wizard = newBudgetService.loadPlanStep(fiscalYear);
        try (ExcelTables excel = ExcelTables.create()) {
            Sheet sheet = excel.sheet("Plan " + fiscalYear);
            excel.headers(sheet, "Kategoria", "Pozycja", "Planowany koszt", "Rozbij na 12 miesięcy");
            int rowIndex = 1;
            for (NewBudgetCategoryDto category : wizard.getCategories()) {
                for (NewBudgetLineDto line : category.getLines()) {
                    if (line.isNewLine()) {
                        continue;
                    }
                    Row row = sheet.createRow(rowIndex++);
                    excel.text(row, 0, category.getName());
                    excel.text(row, 1, line.getLabel());
                    excel.money(row, 2, line.getAnnualAmount());
                    excel.text(row, 3, category.isEvenMonthlySplit() || line.isSplitToMonths() ? "Tak" : "Nie");
                }
            }
            excel.layout(sheet, Math.max(rowIndex - 1, 1), 3, 28);
            excel.write(output);
        }
    }

    @Transactional(readOnly = true)
    public void writeNewBudgetMonth(OutputStream output, int fiscalYear, int month) throws IOException {
        NewBudgetWizardDto wizard = newBudgetService.loadMonthStep(fiscalYear, month);
        try (ExcelTables excel = ExcelTables.create()) {
            Sheet sheet = excel.sheet(PolishMonthNames.of(month) + " " + fiscalYear);
            excel.headers(sheet, "Kategoria", "Pozycja", "Planowany koszt", "Pozostało");
            int rowIndex = 1;
            for (NewBudgetCategoryDto category : wizard.getCategories()) {
                for (NewBudgetLineDto line : category.getLines()) {
                    if (line.isNewLine()) {
                        continue;
                    }
                    Row row = sheet.createRow(rowIndex++);
                    excel.text(row, 0, category.getName());
                    excel.text(row, 1, line.getLabel());
                    excel.money(row, 2, line.getMonthAmount());
                    excel.money(row, 3, line.getRemainingAmount());
                }
            }
            excel.layout(sheet, Math.max(rowIndex - 1, 1), 3, 28);
            excel.write(output);
        }
    }

    @Transactional(readOnly = true)
    public void writeNewBudgetReview(OutputStream output, int fiscalYear) throws IOException {
        BudgetDashboardDto dashboard = newBudgetService.loadReview(fiscalYear);
        try (ExcelTables excel = ExcelTables.create()) {
            Sheet sheet = excel.sheet("Podsumowanie " + fiscalYear);
            excel.headers(sheet, "Pozycja", "Planowany koszt");
            int rowIndex = 1;
            List<BudgetDisplayRowDto> rows = dashboard.getDisplayRows() != null ? dashboard.getDisplayRows() : List.of();
            for (BudgetDisplayRowDto displayRow : rows) {
                Row row = sheet.createRow(rowIndex++);
                String indent = "  ".repeat(Math.max(displayRow.getDepth(), 0));
                if (displayRow.getDepth() == 0) {
                    excel.bold(row, 0, indent + displayRow.getItemName());
                    excel.moneyBold(row, 1, displayRow.getTotalCost());
                } else {
                    excel.text(row, 0, indent + displayRow.getItemName());
                    excel.money(row, 1, displayRow.getTotalCost());
                }
            }
            Row total = sheet.createRow(rowIndex);
            excel.bold(total, 0, "Razem");
            excel.moneyBold(total, 1, dashboard.getTotalCost());
            excel.layout(sheet, rowIndex, 1, 42);
            excel.write(output);
        }
    }

    private void writeTranches(ExcelTables excel, GrantBudgetViewDto budget) {
        Sheet sheet = excel.sheet("Transze");
        excel.headers(sheet, "Transza", "Data", "Kwota", "Przyszło");
        int rowIndex = 1;
        int number = 1;
        for (GrantBudgetViewDto.TrancheDto tranche : budget.getTranches()) {
            Row row = sheet.createRow(rowIndex++);
            excel.text(row, 0, String.valueOf(number++));
            excel.date(row, 1, tranche.getPlannedDate() != null ? tranche.getPlannedDate() : tranche.getReceivedDate());
            excel.money(row, 2, tranche.getPlannedAmount() != null ? tranche.getPlannedAmount() : tranche.getReceivedAmount());
            excel.text(row, 3, tranche.isReceived() ? "Tak" : "Nie");
        }
        excel.layout(sheet, Math.max(rowIndex - 1, 1), 3, 14);
    }

    private void writeGrantLines(ExcelTables excel, GrantBudgetViewDto budget) {
        Sheet sheet = excel.sheet("Pozycje");
        boolean split = budget.isCoverageSplitByYear();
        if (split) {
            excel.headers(sheet, "Pozycja grantu", "Plan grantu", "Pokryta pozycja budżetu organizacji",
                    "Plan pokrycia 2026", "Plan pokrycia 2027", "Zostało");
        } else {
            excel.headers(sheet, "Pozycja grantu", "Plan grantu", "Pokryta pozycja budżetu organizacji",
                    "Plan pokrycia", "Zostało");
        }
        int rowIndex = 1;
        for (GrantBudgetLineDto line : budget.getLines()) {
            List<CoveredOrgBudgetLineDto> coverages = line.getCoveredOrgLines();
            if (coverages == null || coverages.isEmpty()) {
                rowIndex = writeGrantLine(excel, sheet, rowIndex, split, line, null, true);
                continue;
            }
            boolean first = true;
            for (CoveredOrgBudgetLineDto coverage : coverages) {
                rowIndex = writeGrantLine(excel, sheet, rowIndex, split, line, coverage, first);
                first = false;
            }
        }
        Row sum = sheet.createRow(rowIndex++);
        excel.bold(sum, 0, "Suma");
        excel.moneyBold(sum, 1, budget.getPlanTotal());
        excel.moneyBold(sum, 3, budget.getCoveragePlanTotal());
        if (split) {
            excel.moneyBold(sum, 4, budget.getCoveragePlanTotal2027());
            excel.moneyBold(sum, 5, budget.getLeftTotal());
        } else {
            excel.moneyBold(sum, 4, budget.getLeftTotal());
        }
        Row left = sheet.createRow(rowIndex);
        excel.bold(left, 0, "Pozostało do rozdysponowania");
        excel.moneyBold(left, 1, budget.getUnallocated());
        excel.moneyBold(left, 3, budget.getCoverageUnallocated());
        excel.layout(sheet, rowIndex, split ? 5 : 4, 36);
    }

    private int writeGrantLine(ExcelTables excel,
                               Sheet sheet,
                               int rowIndex,
                               boolean split,
                               GrantBudgetLineDto line,
                               CoveredOrgBudgetLineDto coverage,
                               boolean first) {
        Row row = sheet.createRow(rowIndex);
        excel.text(row, 0, first ? line.getName() : "");
        if (first) {
            excel.money(row, 1, line.getPlannedAmount());
        }
        excel.text(row, 2, coverage != null ? coverage.getLabel() : "");
        excel.money(row, 3, coverage != null ? coverage.getAmount() : null);
        if (split) {
            excel.money(row, 4, coverage != null ? coverage.getAmount2027() : null);
            if (first) {
                excel.money(row, 5, line.getRemaining());
            }
        } else if (first) {
            excel.money(row, 4, line.getRemaining());
        }
        return rowIndex + 1;
    }

    private static String grantLabel(Expenditure expenditure) {
        if (expenditure.getGrantShares() != null && !expenditure.getGrantShares().isEmpty()) {
            StringBuilder label = new StringBuilder();
            for (ExpenditureGrantShare share : expenditure.getGrantShares()) {
                if (!label.isEmpty()) {
                    label.append("; ");
                }
                if (share.getGrant() != null && share.getGrant().getCode() != null) {
                    label.append(share.getGrant().getCode());
                }
                if (share.getAmount() != null) {
                    label.append(' ').append(share.getAmount().toPlainString());
                }
            }
            return label.toString();
        }
        return expenditure.getGrant() != null ? expenditure.getGrant().getCode() : "";
    }

    private static String projectName(Expenditure expenditure) {
        Project project = expenditure.getProject();
        if (project == null && expenditure.getGrant() != null) {
            project = expenditure.getGrant().getProject();
        }
        return project != null ? project.getName() : "";
    }

    private static String joinName(String first, String last) {
        String left = first != null ? first : "";
        String right = last != null ? last : "";
        return (left + " " + right).trim();
    }

    private static BigDecimal at(List<BigDecimal> values, int index) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return BigDecimal.ZERO;
        }
        return values.get(index);
    }
}
