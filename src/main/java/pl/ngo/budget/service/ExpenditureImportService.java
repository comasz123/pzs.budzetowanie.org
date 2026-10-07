package pl.ngo.budget.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.ngo.budget.dto.ExpenditureImportResult;
import pl.ngo.budget.entity.cost.CostAllocation;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.cost.Expenditure;
import pl.ngo.budget.entity.cost.ExpenditureGrantShare;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.Project;
import pl.ngo.budget.repository.*;
import pl.ngo.budget.util.GrantPeriodCoverage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenditureImportService {

    public static String sampleExcelPath(int fiscalYear) {
        return "import/wydatki-" + fiscalYear + ".xlsx";
    }

    public static final String SAMPLE_EXCEL_PATH_2026 = sampleExcelPath(2026);
    /** @deprecated use {@link #sampleExcelPath(int)} */
    @Deprecated
    public static final String SAMPLE_EXCEL_PATH = SAMPLE_EXCEL_PATH_2026;
    public static final String SHEET_NAME = "Wydatki";

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };

    private static final String[] HEADERS = {
            "numer_dokumentu", "data_wystawienia", "data_platnosci", "kwota_netto", "kwota_vat",
            "opis", "kod_grantu", "kod_kategorii", "email_pracownika",
            "alokacja2_kategoria", "alokacja2_kwota"
    };

    private final ExpenditureRepository expenditureRepository;
    private final CostAllocationRepository costAllocationRepository;
    private final GrantRepository grantRepository;
    private final EmployeeRepository employeeRepository;
    private final BudgetItemTemplateRepository budgetItemTemplateRepository;
    private final ProjectRepository projectRepository;

    public ExpenditureImportService(ExpenditureRepository expenditureRepository,
                                    CostAllocationRepository costAllocationRepository,
                                    GrantRepository grantRepository,
                                    EmployeeRepository employeeRepository,
                                    BudgetItemTemplateRepository budgetItemTemplateRepository,
                                    ProjectRepository projectRepository) {
        this.expenditureRepository = expenditureRepository;
        this.costAllocationRepository = costAllocationRepository;
        this.grantRepository = grantRepository;
        this.employeeRepository = employeeRepository;
        this.budgetItemTemplateRepository = budgetItemTemplateRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<Grant> listGrants() {
        return grantRepository.findAllWithDetails();
    }

    @Transactional(readOnly = true)
    public List<Project> listProjects() {
        return projectRepository.findAllWithCoordinator();
    }

    @Transactional
    public void assignGrant(Long expenditureId, Long grantId) {
        Expenditure expenditure = expenditureRepository.findById(expenditureId)
                .orElseThrow(() -> new IllegalArgumentException("Wydatek nie istnieje"));
        Grant grant = grantRepository.findByIdWithBudgetItems(grantId)
                .orElseThrow(() -> new IllegalArgumentException("Grant nie istnieje"));
        String categoryCode = expenditure.getBudgetItem() != null ? expenditure.getBudgetItem().getCode() : null;
        GrantBudgetItem budgetItem = findBudgetItem(grant, categoryCode);
        if (budgetItem == null) {
            throw new IllegalArgumentException("Brak pozycji " + categoryCode + " w grantcie " + grant.getCode());
        }
        expenditure.getGrantShares().clear();
        expenditure.setGrant(grant);
        expenditure.setBudgetItem(budgetItem);
        expenditureRepository.save(expenditure);
    }

    @Transactional
    public void setStatus(Long expenditureId, boolean monthClosed, boolean reportClosed) {
        Expenditure expenditure = expenditureRepository.findById(expenditureId)
                .orElseThrow(() -> new IllegalArgumentException("Wydatek nie istnieje"));
        expenditure.setMonthClosed(monthClosed);
        expenditure.setReportClosed(reportClosed);
        expenditureRepository.save(expenditure);
    }

    @Transactional
    public void splitAcrossGrants(Long expenditureId, List<Long> grantIds, List<BigDecimal> amounts) {
        if (grantIds == null || amounts == null || grantIds.size() != amounts.size()) {
            throw new IllegalArgumentException("Podaj grant i kwotę dla każdej pozycji.");
        }
        int parts = grantIds.size();
        if (parts < 2 || parts > 3) {
            throw new IllegalArgumentException("Podział na 2 lub 3 pozycje.");
        }
        Expenditure expenditure = expenditureRepository.findById(expenditureId)
                .orElseThrow(() -> new IllegalArgumentException("Wydatek nie istnieje"));
        BigDecimal gross = expenditure.getGrossAmount() != null ? expenditure.getGrossAmount() : BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Kwota każdej pozycji musi być większa od zera.");
            }
            sum = sum.add(amount.setScale(2, RoundingMode.HALF_UP));
        }
        if (sum.compareTo(gross) != 0) {
            throw new IllegalArgumentException("Suma pozycji musi być równa netto + VAT "
                    + gross.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','));
        }
        String categoryCode = expenditure.getBudgetItem() != null ? expenditure.getBudgetItem().getCode() : null;
        Set<Long> seen = new HashSet<>();
        List<ExpenditureGrantShare> shares = expenditure.getGrantShares();
        shares.clear();
        for (int i = 0; i < parts; i++) {
            Long grantId = grantIds.get(i);
            if (grantId == null || !seen.add(grantId)) {
                throw new IllegalArgumentException("Każda pozycja musi mieć inny grant.");
            }
            Grant grant = grantRepository.findByIdWithBudgetItems(grantId)
                    .orElseThrow(() -> new IllegalArgumentException("Grant nie istnieje"));
            GrantBudgetItem budgetItem = findBudgetItem(grant, categoryCode);
            if (budgetItem == null) {
                throw new IllegalArgumentException("Brak pozycji " + categoryCode + " w grantcie " + grant.getCode());
            }
            ExpenditureGrantShare share = new ExpenditureGrantShare();
            share.setExpenditure(expenditure);
            share.setGrant(grant);
            share.setBudgetItem(budgetItem);
            share.setAmount(amounts.get(i).setScale(2, RoundingMode.HALF_UP));
            share.setSharePosition(i + 1);
            shares.add(share);
        }
        expenditure.setGrant(shares.get(0).getGrant());
        expenditure.setBudgetItem(shares.get(0).getBudgetItem());
        expenditureRepository.save(expenditure);
    }

    @Transactional
    public void clearGrantSplit(Long expenditureId) {
        Expenditure expenditure = expenditureRepository.findById(expenditureId)
                .orElseThrow(() -> new IllegalArgumentException("Wydatek nie istnieje"));
        expenditure.getGrantShares().clear();
        expenditureRepository.save(expenditure);
    }

    @Transactional
    public void assignProject(Long expenditureId, Long projectId) {
        Expenditure expenditure = expenditureRepository.findById(expenditureId)
                .orElseThrow(() -> new IllegalArgumentException("Wydatek nie istnieje"));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Projekt nie istnieje"));
        expenditure.setProject(project);
        expenditureRepository.save(expenditure);
    }

    @Transactional(readOnly = true)
    public List<Expenditure> listForMonth(int fiscalYear, int month) {
        YearMonth ym = YearMonth.of(fiscalYear, month);
        return expenditureRepository.findByFiscalYearAndIssueDateBetween(
                fiscalYear, ym.atDay(1), ym.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public long countForMonth(int fiscalYear, int month) {
        YearMonth ym = YearMonth.of(fiscalYear, month);
        return expenditureRepository.countByFiscalYearAndIssueDateBetween(
                fiscalYear, ym.atDay(1), ym.atEndOfMonth());
    }

    @Transactional
    public ExpenditureImportResult importMonth(MultipartFile file, int fiscalYear, int month, boolean replaceExisting) {
        ExpenditureImportResult result = new ExpenditureImportResult();
        if (file == null || file.isEmpty()) {
            result.addError(0, "Nie wybrano pliku");
            return result;
        }

        YearMonth targetMonth = YearMonth.of(fiscalYear, month);
        Map<String, Grant> grantsByCode = loadGrantsByCode();
        Map<String, Employee> employeesByEmail = loadEmployeesByEmail();
        Map<String, BudgetItemTemplate> categoriesByCode = loadCategoriesByCode();
        Map<Long, List<CostAllocation>> planByEmployee = costAllocationRepository
                .findPlanAllocationsByFiscalYear(fiscalYear).stream()
                .filter(a -> a.getEmployee() != null)
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId()));

        if (replaceExisting) {
            List<Expenditure> existing = listForMonth(fiscalYear, month);
            for (Expenditure expenditure : existing) {
                expenditureRepository.delete(expenditure);
            }
            result.setReplaced(existing.size());
        }

        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            }
            if (sheet == null) {
                result.addError(0, "Plik Excel nie zawiera arkusza");
                return result;
            }

            Map<String, Integer> columnIndex = resolveColumns(sheet.getRow(0));
            if (columnIndex.isEmpty()) {
                result.addError(1, "Brak nagłówków kolumn — oczekiwane: " + String.join(", ", HEADERS));
                return result;
            }

            Set<String> documentsInFile = new HashSet<>();
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isEmptyRow(row, columnIndex)) {
                    continue;
                }
                int excelRow = rowIdx + 1;
                result.setRowsRead(result.getRowsRead() + 1);

                try {
                    LocalDate issueDate = parseDate(cellString(row, columnIndex, "data_wystawienia"), excelRow, result);
                    if (issueDate == null) {
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }
                    if (issueDate.getYear() != fiscalYear || issueDate.getMonthValue() != month) {
                        continue;
                    }

                    String documentNumber = requiredString(row, columnIndex, "numer_dokumentu", excelRow, result);
                    if (documentNumber == null) {
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }
                    if (!documentsInFile.add(documentNumber)) {
                        result.addError(excelRow, "Duplikat numeru dokumentu w pliku: " + documentNumber);
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }

                    String grantCode = requiredString(row, columnIndex, "kod_grantu", excelRow, result);
                    String categoryCode = requiredString(row, columnIndex, "kod_kategorii", excelRow, result);
                    if (grantCode == null || categoryCode == null) {
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }

                    Grant grant = grantsByCode.get(grantCode.trim());
                    if (grant == null) {
                        result.addError(excelRow, "Nie znaleziono grantu: " + grantCode);
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }
                    if (!GrantPeriodCoverage.isWithinGrantPeriod(grant, issueDate)) {
                        result.addError(excelRow, "Grant " + grantCode + " nie obejmuje daty " + issueDate);
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }

                    GrantBudgetItem budgetItem = findBudgetItem(grant, categoryCode.trim());
                    if (budgetItem == null) {
                        result.addError(excelRow, "Brak pozycji " + categoryCode + " w grantcie " + grantCode);
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }

                    BigDecimal netAmount = parseAmount(row, columnIndex, "kwota_netto", excelRow, result);
                    if (netAmount == null) {
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }
                    BigDecimal vatAmount = parseAmount(row, columnIndex, "kwota_vat", excelRow, result);
                    if (vatAmount == null) {
                        vatAmount = BigDecimal.ZERO;
                    }
                    BigDecimal secondAmount = parseAmount(row, columnIndex, "alokacja2_kwota", excelRow, null);
                    BigDecimal grossAmount = netAmount.add(vatAmount);
                    if (secondAmount != null) {
                        grossAmount = grossAmount.add(secondAmount);
                    }

                    String employeeEmail = optionalString(row, columnIndex, "email_pracownika");
                    Employee employee = null;
                    if (employeeEmail != null && !employeeEmail.isBlank()) {
                        employee = employeesByEmail.get(employeeEmail.trim().toLowerCase(Locale.ROOT));
                        if (employee == null) {
                            result.addWarning(excelRow, "Nie znaleziono pracownika: " + employeeEmail);
                        }
                    }

                    LocalDate paymentDate = parseDate(cellString(row, columnIndex, "data_platnosci"), excelRow, result);
                    String description = optionalString(row, columnIndex, "opis");

                    Expenditure expenditure = new Expenditure();
                    expenditure.setDocumentNumber(documentNumber.trim());
                    expenditure.setIssueDate(issueDate);
                    expenditure.setPaymentDate(paymentDate);
                    expenditure.setNetAmount(netAmount);
                    expenditure.setVatAmount(vatAmount);
                    expenditure.setGrossAmount(grossAmount);
                    expenditure.setDescription(description);
                    expenditure.setGrant(grant);
                    expenditure.setBudgetItem(budgetItem);
                    expenditure.setEmployee(employee);
                    expenditure.setFiscalYear(fiscalYear);
                    expenditureRepository.save(expenditure);

                    attachCostAllocations(
                            expenditure, employee, grant, categoryCode.trim(),
                            optionalString(row, columnIndex, "alokacja2_kategoria"),
                            secondAmount,
                            categoriesByCode, planByEmployee, excelRow, result);

                    result.setImported(result.getImported() + 1);
                } catch (Exception ex) {
                    result.addError(excelRow, ex.getMessage());
                    result.setSkipped(result.getSkipped() + 1);
                }
            }
        } catch (IOException ex) {
            result.addError(0, "Nie można odczytać pliku: " + ex.getMessage());
        }
        return result;
    }

    private void attachCostAllocations(Expenditure expenditure,
                                       Employee employee,
                                       Grant grant,
                                       String primaryCategoryCode,
                                       String secondCategoryCode,
                                       BigDecimal secondAmount,
                                       Map<String, BudgetItemTemplate> categoriesByCode,
                                       Map<Long, List<CostAllocation>> planByEmployee,
                                       int excelRow,
                                       ExpenditureImportResult result) {
        BigDecimal gross = expenditure.getGrossAmount();
        List<CostAllocation> toSave = new ArrayList<>();

        if (secondCategoryCode != null && !secondCategoryCode.isBlank() && secondAmount != null) {
            BudgetItemTemplate primary = categoriesByCode.get(primaryCategoryCode);
            BudgetItemTemplate secondary = categoriesByCode.get(secondCategoryCode.trim());
            if (primary == null || secondary == null) {
                result.addWarning(excelRow, "Nie rozpoznano kategorii alokacji — zapisano bez podziału");
            } else {
                BigDecimal firstAmount = gross.subtract(secondAmount).setScale(2, RoundingMode.HALF_UP);
                toSave.add(buildAllocation(expenditure, employee, grant, primary, firstAmount, null, null));
                toSave.add(buildAllocation(expenditure, employee, grant, secondary, secondAmount, null, null));
            }
        } else if (employee != null && planByEmployee.containsKey(employee.getId())) {
            List<CostAllocation> plan = planByEmployee.get(employee.getId());
            BigDecimal planTotal = plan.stream()
                    .map(CostAllocation::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (planTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal allocated = BigDecimal.ZERO;
                for (int i = 0; i < plan.size(); i++) {
                    CostAllocation template = plan.get(i);
                    BigDecimal part = i == plan.size() - 1
                            ? gross.subtract(allocated)
                            : gross.multiply(template.getAmount()).divide(planTotal, 2, RoundingMode.HALF_UP);
                    allocated = allocated.add(part);
                    CostAllocation allocation = buildAllocation(
                            expenditure, employee, grant, template.getCategory(), part,
                            template.getAdminGroup(), template.getLabel());
                    allocation.setProject(template.getProject() != null ? template.getProject() : grant.getProject());
                    allocation.setPercentage(template.getPercentage());
                    toSave.add(allocation);
                }
            }
        }

        if (toSave.isEmpty()) {
            BudgetItemTemplate category = categoriesByCode.get(primaryCategoryCode);
            if (category != null) {
                toSave.add(buildAllocation(expenditure, employee, grant, category, gross, null, null));
            }
        }

        if (!toSave.isEmpty()) {
            costAllocationRepository.saveAll(toSave);
        }
    }

    private CostAllocation buildAllocation(Expenditure expenditure,
                                           Employee employee,
                                           Grant grant,
                                           BudgetItemTemplate category,
                                           BigDecimal amount,
                                           String adminGroup,
                                           String label) {
        CostAllocation allocation = new CostAllocation();
        allocation.setExpenditure(expenditure);
        allocation.setEmployee(employee);
        allocation.setProject(grant.getProject());
        allocation.setCategory(category);
        allocation.setAmount(amount);
        allocation.setPercentage(BigDecimal.valueOf(100));
        allocation.setAdminGroup(adminGroup);
        allocation.setLabel(label);
        allocation.setFiscalYear(expenditure.getFiscalYear());
        allocation.setActive(true);
        return allocation;
    }

    public void writeSampleExcel(OutputStream output, int fiscalYear) throws IOException {
        ClassPathResource resource = new ClassPathResource(sampleExcelPath(fiscalYear));
        if (resource.exists()) {
            try (InputStream input = resource.getInputStream()) {
                input.transferTo(output);
            }
            return;
        }
        generateSampleExcelFile(output, fiscalYear);
    }

    public void writeSampleExcel(OutputStream output) throws IOException {
        writeSampleExcel(output, 2026);
    }

    public void writeEmptyTemplate(OutputStream output) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            sheet.createFreezePane(0, 1);
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(output);
        }
    }

    public static void generateSampleExcelFile(OutputStream output, int fiscalYear) throws IOException {
        generateSampleExcelFile(output, fiscalYear, LocalDate.of(fiscalYear, 12, 31));
    }

    public static void generateSampleExcelFile(OutputStream output, LocalDate maxIssueDate) throws IOException {
        generateSampleExcelFile(output, maxIssueDate.getYear(), maxIssueDate);
    }

    public static void generateSampleExcelFile(OutputStream output, int fiscalYear, LocalDate maxIssueDate) throws IOException {
        SampleYearConfig config = SampleYearConfig.forYear(fiscalYear);
        Random random = new Random(fiscalYear * 10000L + 812L);
        String[] categories = {
                "KOSZT_PER", "KOSZT_ADM", "KOSZT_PROM", "KOSZT_SPRZ",
                "KOSZT_MAT", "KOSZT_KSW", "KOSZT_LOG"
        };
        String[][] payrollEmployees = {
                {"jan.kowalski@ngo.pl", "KOSZT_PER", "KOSZT_ADM", "0.60"},
                {"anna.nowak@ngo.pl", "KOSZT_PER", "KOSZT_ADM", "0.50"},
                {"piotr.wisniewski@ngo.pl", "KOSZT_PER", null, null},
                {"marta.wojcik@ngo.pl", "KOSZT_PROM", null, null},
                {"tomasz.zielinski@ngo.pl", "KOSZT_PER", "KOSZT_ADM", "0.75"}
        };
        String[] operationalPrefixes = {
                "Zakup materiałów", "Opłata za wynajem sali", "Transport uczestników",
                "Druk ulotek", "Usługa księgowa", "Zakup sprzętu IT", "Catering",
                "Promocja Social Media", "Opłata pocztowa", "Audyt projektu"
        };

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }

            int docCounter = 1;
            int[] perMonth = distributeCounts(350, 12);
            for (int month = 1; month <= 12 && docCounter <= 350; month++) {
                YearMonth ym = YearMonth.of(fiscalYear, month);
                LocalDate monthStart = ym.atDay(1);
                LocalDate monthEnd = ym.atEndOfMonth();
                if (monthEnd.isAfter(maxIssueDate)) {
                    monthEnd = maxIssueDate;
                }
                if (monthStart.isAfter(maxIssueDate)) {
                    continue;
                }
                int daysInMonth = (int) (monthEnd.toEpochDay() - monthStart.toEpochDay()) + 1;

                for (int i = 0; i < perMonth[month - 1] && docCounter <= 350; i++) {
                    LocalDate issueDate = monthStart.plusDays(random.nextInt(Math.max(daysInMonth, 1)));
                    if (issueDate.isAfter(maxIssueDate)) {
                        issueDate = maxIssueDate;
                    }

                    int grantIdx = pickGrantIndex(config.grantCodes.length, config.grantRanges, issueDate, random);
                    boolean payroll = random.nextDouble() < 0.35;
                    String category;
                    String employeeEmail = "";
                    String alloc2Category = "";
                    String alloc2Amount = "";

                    BigDecimal net;
                    if (payroll) {
                        String[] pe = payrollEmployees[random.nextInt(payrollEmployees.length)];
                        employeeEmail = pe[0];
                        category = pe[1];
                        net = BigDecimal.valueOf(4000 + random.nextInt(9000)).setScale(2, RoundingMode.HALF_UP);
                        if (pe[2] != null && pe[3] != null) {
                            alloc2Category = pe[2];
                            alloc2Amount = net.multiply(new BigDecimal(pe[3]))
                                    .setScale(2, RoundingMode.HALF_UP).toPlainString();
                            net = net.subtract(new BigDecimal(alloc2Amount));
                        }
                    } else {
                        category = categories[random.nextInt(categories.length)];
                        net = BigDecimal.valueOf(100 + random.nextInt(3400)).setScale(2, RoundingMode.HALF_UP);
                    }

                    Row row = sheet.createRow(docCounter);
                    row.createCell(0).setCellValue("FV/" + fiscalYear + "/" + String.format("%04d", docCounter));
                    row.createCell(1).setCellValue(issueDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    row.createCell(2).setCellValue(issueDate.plusDays(random.nextInt(14))
                            .format(DateTimeFormatter.ISO_LOCAL_DATE));
                    row.createCell(3).setCellValue(net.doubleValue());
                    row.createCell(4).setCellValue(0);
                    row.createCell(5).setCellValue(
                            (payroll ? "Wynagrodzenie" : operationalPrefixes[random.nextInt(operationalPrefixes.length)])
                                    + " #" + docCounter);
                    row.createCell(6).setCellValue(config.grantCodes[grantIdx]);
                    row.createCell(7).setCellValue(category);
                    row.createCell(8).setCellValue(employeeEmail);
                    row.createCell(9).setCellValue(alloc2Category);
                    if (!alloc2Amount.isEmpty()) {
                        row.createCell(10).setCellValue(Double.parseDouble(alloc2Amount));
                    }
                    docCounter++;
                }
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);
            workbook.write(output);
        }
    }

    private static int[] distributeCounts(int total, int buckets) {
        int[] counts = new int[buckets];
        int base = total / buckets;
        int remainder = total % buckets;
        for (int i = 0; i < buckets; i++) {
            counts[i] = base + (i < remainder ? 1 : 0);
        }
        return counts;
    }

    private static int pickGrantIndex(int count, LocalDate[][] ranges, LocalDate date, Random random) {
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (!date.isBefore(ranges[i][0]) && !date.isAfter(ranges[i][1])) {
                eligible.add(i);
            }
        }
        if (eligible.isEmpty()) {
            return random.nextInt(count);
        }
        return eligible.get(random.nextInt(eligible.size()));
    }

    private Map<String, Integer> resolveColumns(Row headerRow) {
        Map<String, Integer> index = new HashMap<>();
        if (headerRow == null) {
            return index;
        }
        for (Cell cell : headerRow) {
            String name = cell.getStringCellValue();
            if (name != null) {
                index.put(name.trim().toLowerCase(Locale.ROOT), cell.getColumnIndex());
            }
        }
        return index;
    }

    private Map<String, Grant> loadGrantsByCode() {
        Map<String, Grant> map = new HashMap<>();
        for (Grant grant : grantRepository.findAllWithBudgetItems()) {
            if (grant.getCode() != null) {
                map.put(grant.getCode(), grant);
            }
        }
        return map;
    }

    private Map<String, Employee> loadEmployeesByEmail() {
        Map<String, Employee> map = new HashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            if (employee.getEmail() != null) {
                map.put(employee.getEmail().trim().toLowerCase(Locale.ROOT), employee);
            }
        }
        return map;
    }

    private Map<String, BudgetItemTemplate> loadCategoriesByCode() {
        Map<String, BudgetItemTemplate> map = new HashMap<>();
        for (BudgetItemTemplate template : budgetItemTemplateRepository.findAll()) {
            if (template.getCode() != null) {
                map.put(template.getCode(), template);
            }
        }
        return map;
    }

    private static GrantBudgetItem findBudgetItem(Grant grant, String categoryCode) {
        if (grant.getBudgetItems() == null) {
            return null;
        }
        return grant.getBudgetItems().stream()
                .filter(item -> categoryCode.equals(item.getCode()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isEmptyRow(Row row, Map<String, Integer> columns) {
        return cellString(row, columns, "numer_dokumentu") == null
                && cellString(row, columns, "data_wystawienia") == null;
    }

    private static String cellString(Row row, Map<String, Integer> columns, String name) {
        Integer idx = columns.get(name);
        if (idx == null) {
            return null;
        }
        Cell cell = row.getCell(idx);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private static String optionalString(Row row, Map<String, Integer> columns, String name) {
        String value = cellString(row, columns, name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String requiredString(Row row, Map<String, Integer> columns, String name,
                                         int excelRow, ExpenditureImportResult result) {
        String value = cellString(row, columns, name);
        if (value == null || value.isBlank()) {
            result.addError(excelRow, "Brak wartości w kolumnie " + name);
            return null;
        }
        return value;
    }

    private static BigDecimal parseAmount(Row row, Map<String, Integer> columns, String name,
                                          int excelRow, ExpenditureImportResult result) {
        String raw = cellString(row, columns, name);
        if (raw == null || raw.isBlank()) {
            if ("kwota_vat".equals(name) || "alokacja2_kwota".equals(name)) {
                return null;
            }
            if (result != null) {
                result.addError(excelRow, "Brak kwoty w kolumnie " + name);
            }
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", ".").replace(" ", ""))
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            if (result != null) {
                result.addError(excelRow, "Nieprawidłowa kwota w " + name + ": " + raw);
            }
            return null;
        }
    }

    private static LocalDate parseDate(String raw, int excelRow, ExpenditureImportResult result) {
        if (raw == null || raw.isBlank()) {
            if (result != null) {
                result.addError(excelRow, "Brak daty wystawienia");
            }
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        if (result != null) {
            result.addError(excelRow, "Nieprawidłowa data: " + raw);
        }
        return null;
    }

    private record SampleYearConfig(String[] grantCodes, LocalDate[][] grantRanges) {
        static SampleYearConfig forYear(int fiscalYear) {
            String suffix = fiscalYear == 2025 ? "2025" : "2026";
            String[] grantCodes = {
                    "G-CYKL_" + suffix + "_01", "G-EKO_" + suffix + "_02", "G-SENIOR_" + suffix + "_03",
                    "G-KULT_" + suffix + "_04", "G-PSYCH_" + suffix + "_05"
            };
            LocalDate[][] grantRanges = {
                    {LocalDate.of(fiscalYear, 1, 1), LocalDate.of(fiscalYear, 12, 31)},
                    {LocalDate.of(fiscalYear, 3, 1), LocalDate.of(fiscalYear, 10, 31)},
                    {LocalDate.of(fiscalYear, 2, 1), LocalDate.of(fiscalYear, 11, 30)},
                    {LocalDate.of(fiscalYear, 5, 1), LocalDate.of(fiscalYear, 9, 30)},
                    {LocalDate.of(fiscalYear, 1, 1), LocalDate.of(fiscalYear, 12, 31)}
            };
            return new SampleYearConfig(grantCodes, grantRanges);
        }
    }
}
