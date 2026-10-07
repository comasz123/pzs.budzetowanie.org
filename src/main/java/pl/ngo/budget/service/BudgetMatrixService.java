package pl.ngo.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.dto.BudgetDashboardDto;
import pl.ngo.budget.dto.GrantBudgetChoiceDto;
import pl.ngo.budget.dto.GrantBudgetChoiceGroupDto;
import pl.ngo.budget.dto.BudgetDashboardDto.BudgetDisplayRowDto;
import pl.ngo.budget.dto.BudgetDashboardDto.BudgetItemRowDto;
import pl.ngo.budget.dto.BudgetRowDetailDto;
import pl.ngo.budget.dto.CostAllocationDto;
import pl.ngo.budget.dto.HomeChartDto;
import pl.ngo.budget.dto.OrgBudgetLineOptionDto;
import pl.ngo.budget.entity.cost.CostAllocation;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.cost.Expenditure;
import pl.ngo.budget.entity.cost.ExpenditureGrantShare;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.entity.coverage.BudgetSubcategoryOrder;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.GrantTranche;
import pl.ngo.budget.entity.coverage.GrantBudgetItemCoverage;
import pl.ngo.budget.entity.coverage.OrgBudgetSourceType;
import pl.ngo.budget.entity.coverage.PlannedEvent;
import pl.ngo.budget.entity.coverage.Publication;
import pl.ngo.budget.entity.coverage.TravelBudgetLine;
import pl.ngo.budget.repository.BudgetItemTemplateRepository;
import pl.ngo.budget.repository.BudgetSubcategoryOrderRepository;
import pl.ngo.budget.repository.CostAllocationRepository;
import pl.ngo.budget.repository.EmployeeRepository;
import pl.ngo.budget.repository.ExpenditureRepository;
import pl.ngo.budget.repository.GrantRepository;
import pl.ngo.budget.repository.PlannedEventRepository;
import pl.ngo.budget.repository.PublicationRepository;
import pl.ngo.budget.repository.TravelBudgetLineRepository;
import pl.ngo.budget.util.GrantCoverageYear;
import pl.ngo.budget.util.MonthlySplit;
import pl.ngo.budget.util.GrantPeriodCoverage;
import pl.ngo.budget.util.PolishMonthNames;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BudgetMatrixService {

    private static final String PERSONNEL_ROW_KEY = "wynagrodzenia";
    private static final String PERSONNEL_LABEL = "Wynagrodzenia";
    private static final String PERSONNEL_LEGACY_NAME = "Wynagrodzenia i Personel";
    private static final String PERSONNEL_CODE = "KOSZT_PER";

    private static final String PUBLICATIONS_ROW_KEY = "publikacje";
    private static final String PUBLICATIONS_LABEL = "Produkcja publikacji";
    private static final String PUBLICATIONS_LEGACY_NAME = "Materiały i Pomoce";
    private static final String PUBLICATIONS_CODE = "KOSZT_MAT";

    private static final String EVENTS_ROW_KEY = "wydarzenia";
    private static final String EVENTS_LABEL = "Konferencje, spotkania, warsztaty";
    private static final String EVENTS_CODE = "KOSZT_KSW";

    private static final String TRAVEL_ROW_KEY = "podroze";
    private static final String TRAVEL_LABEL = "Podróże";
    private static final String TRAVEL_LEGACY_NAME = "Transport i Logistyka";
    private static final String TRAVEL_CODE = "KOSZT_LOG";
    private static final String TRAVEL_DOMESTIC_KEY = "podroze-krajowe";
    private static final String TRAVEL_INTERNATIONAL_KEY = "podroze-zagraniczne";

    private static final String ADMIN_ROW_KEY = "koszty-administracyjne";
    private static final String ADMIN_LABEL = "Koszty Administracyjne";
    private static final String ADMIN_CODE = "KOSZT_ADM";
    private static final String ADMIN_GROUP_BIURO = "BIURO";
    private static final String ADMIN_GROUP_POZOSTALE = "POZOSTALE";
    private static final String ADMIN_GROUP_WYNAGRODZENIA = "WYNAGRODZENIA";
    private static final String ADMIN_BIURO_KEY = "admin-biuro";
    private static final String ADMIN_POZOSTALE_KEY = "admin-pozostale";
    private static final String ADMIN_WYNAGRODZENIA_KEY = "admin-wynagrodzenia";

    private static final String PROMOTION_ROW_KEY = "promocja";
    private static final String PROMOTION_LABEL = "Promocja i Komunikacja";
    private static final String PROMOTION_CODE = "KOSZT_PROM";

    private static final String EQUIPMENT_ROW_KEY = "sprzet";
    private static final String EQUIPMENT_LABEL = "Sprzęt i Wyposażenie";
    private static final String EQUIPMENT_CODE = "KOSZT_SPRZ";

    private record LabeledSubcategory(String rowKey, String label) {}

    private static final String PROMO_PAYROLL_KEY = "promocja-wynagrodzenia";
    private static final List<LabeledSubcategory> PROMOTION_SUBCATEGORIES = List.of(
            new LabeledSubcategory("promocja-social-media", "Social Media"),
            new LabeledSubcategory("promocja-publikacje", "Publikacje"),
            new LabeledSubcategory("promocja-ogloszenia", "Ogłoszenia"),
            new LabeledSubcategory(PROMO_PAYROLL_KEY, "Wynagrodzenia")
    );

    private static final List<LabeledSubcategory> EQUIPMENT_SUBCATEGORIES = List.of(
            new LabeledSubcategory("sprzet-komputerowy", "Sprzęt komputerowy"),
            new LabeledSubcategory("sprzet-meble", "Meble"),
            new LabeledSubcategory("sprzet-inne", "Inne")
    );

    private final GrantRepository grantRepository;
    private final ExpenditureRepository expenditureRepository;
    private final EmployeeRepository employeeRepository;
    private final PublicationRepository publicationRepository;
    private final PlannedEventRepository plannedEventRepository;
    private final TravelBudgetLineRepository travelBudgetLineRepository;
    private final CostAllocationRepository costAllocationRepository;
    private final BudgetItemTemplateRepository budgetItemTemplateRepository;
    private final BudgetSubcategoryOrderRepository budgetSubcategoryOrderRepository;
    private final GrantBudgetService grantBudgetService;

    public BudgetMatrixService(GrantRepository grantRepository,
                               ExpenditureRepository expenditureRepository,
                               EmployeeRepository employeeRepository,
                               PublicationRepository publicationRepository,
                               PlannedEventRepository plannedEventRepository,
                               TravelBudgetLineRepository travelBudgetLineRepository,
                               CostAllocationRepository costAllocationRepository,
                               BudgetItemTemplateRepository budgetItemTemplateRepository,
                               BudgetSubcategoryOrderRepository budgetSubcategoryOrderRepository,
                               GrantBudgetService grantBudgetService) {
        this.grantRepository = grantRepository;
        this.expenditureRepository = expenditureRepository;
        this.employeeRepository = employeeRepository;
        this.publicationRepository = publicationRepository;
        this.plannedEventRepository = plannedEventRepository;
        this.travelBudgetLineRepository = travelBudgetLineRepository;
        this.costAllocationRepository = costAllocationRepository;
        this.budgetItemTemplateRepository = budgetItemTemplateRepository;
        this.budgetSubcategoryOrderRepository = budgetSubcategoryOrderRepository;
        this.grantBudgetService = grantBudgetService;
    }

    @Transactional(readOnly = true)
    public List<Integer> getAvailableFiscalYears() {
        Set<Integer> years = new TreeSet<>((a, b) -> b.compareTo(a));
        costAllocationRepository.findDistinctFiscalYears().stream()
                .filter(y -> y != null && y > 0)
                .forEach(years::add);
        expenditureRepository.findDistinctFiscalYears().stream()
                .filter(y -> y != null && y > 0)
                .forEach(years::add);
        if (years.isEmpty()) {
            years.add(LocalDate.now().getYear());
        }
        return new ArrayList<>(years);
    }

    @Transactional(readOnly = true)
    public BudgetDashboardDto getBudgetDashboardData(Integer year) {
        int fiscalYear = year != null ? year : LocalDate.now().getYear();
        return yearFromMonths(fiscalYear);
    }

    @Transactional(readOnly = true)
    public BudgetDashboardDto getBudgetDashboardDataForYear(int fiscalYear) {
        return yearFromMonths(fiscalYear);
    }

    /** Kategorie budżetu rozwinięte do podkategorii, do wyboru w formularzu grantu. */
    @Transactional(readOnly = true)
    public List<GrantBudgetChoiceGroupDto> grantFormBudgetChoices(int fiscalYear) {
        BudgetDashboardDto dashboard = buildDashboard(fiscalYear, null);
        List<GrantBudgetChoiceGroupDto> groups = new ArrayList<>();
        GrantBudgetChoiceGroupDto loose = new GrantBudgetChoiceGroupDto();
        loose.getOptions().add(GrantBudgetChoiceDto.of("new", "nowa pozycja"));
        groups.add(loose);

        Set<String> used = new LinkedHashSet<>();
        used.add("new");
        for (BudgetDashboardDto.BudgetItemRowDto row : dashboard.getRows()) {
            if (row.getItemName() == null || row.getItemName().isBlank()) {
                continue;
            }
            GrantBudgetChoiceGroupDto group = new GrantBudgetChoiceGroupDto();
            BudgetDashboardDto.CategoryOrderInfo order = row.getRowKey() == null
                    ? null
                    : dashboard.getCategoryOrderByRowKey().get(row.getRowKey());
            if (order != null && order.getCategoryId() != null) {
                addChoice(group.getOptions(), used, "t:" + order.getCategoryId(), row.getItemName());
            } else if (row.getRowKey() != null && !row.getRowKey().isBlank()) {
                addChoice(group.getOptions(), used, "r:" + row.getRowKey(), row.getItemName());
            }
            appendBudgetSubcategories(group.getOptions(), used,
                    sortSubcategories(row.getRowKey(), row.getChildren()), null);
            appendKnownSubcategories(group.getOptions(), used, row.getRowKey());
            if (!hasSubcategoryChoice(group.getOptions())) {
                appendAllocationChoices(group.getOptions(), used, row.getAllocations());
            }
            if (group.getOptions().isEmpty()) {
                continue;
            }
            boolean expanded = group.getOptions().size() > 1
                    || group.getOptions().stream().anyMatch(choice -> choice.getValue().startsWith("r:"));
            if (expanded) {
                group.setLabel(row.getItemName());
            }
            groups.add(group);
        }
        return groups;
    }

    private void appendBudgetSubcategories(List<GrantBudgetChoiceDto> options,
                                           Set<String> used,
                                           List<BudgetDashboardDto.BudgetItemRowDto> children,
                                           String prefix) {
        if (children == null) {
            return;
        }
        for (BudgetDashboardDto.BudgetItemRowDto child : children) {
            String name = child.getItemName();
            if (name == null || name.isBlank() || child.getRowKey() == null || child.getRowKey().isBlank()) {
                appendBudgetSubcategories(options, used,
                        sortSubcategories(child.getRowKey(), child.getChildren()), prefix);
                continue;
            }
            String label = prefix == null || prefix.isBlank() ? name : prefix + " — " + name;
            addChoice(options, used, "r:" + child.getRowKey(), label);
            List<BudgetDashboardDto.BudgetItemRowDto> nested = sortSubcategories(child.getRowKey(), child.getChildren());
            if (nested != null && !nested.isEmpty()) {
                appendBudgetSubcategories(options, used, nested, label);
            }
        }
    }

    private static void appendKnownSubcategories(List<GrantBudgetChoiceDto> options, Set<String> used, String rowKey) {
        List<LabeledSubcategory> known = switch (rowKey == null ? "" : rowKey) {
            case PROMOTION_ROW_KEY -> PROMOTION_SUBCATEGORIES;
            case EQUIPMENT_ROW_KEY -> EQUIPMENT_SUBCATEGORIES;
            default -> List.of();
        };
        for (LabeledSubcategory subcategory : known) {
            addChoice(options, used, "r:" + subcategory.rowKey(), subcategory.label());
        }
    }

    private static boolean hasSubcategoryChoice(List<GrantBudgetChoiceDto> options) {
        return options.stream().anyMatch(choice -> choice.getValue() != null && choice.getValue().startsWith("r:"));
    }

    private static void appendAllocationChoices(List<GrantBudgetChoiceDto> options,
                                                Set<String> used,
                                                List<CostAllocationDto> allocations) {
        if (allocations == null) {
            return;
        }
        for (CostAllocationDto allocation : allocations) {
            String kind = allocation.getAmountEditKind();
            String ids = allocation.getAmountEditIds();
            if (kind == null || ids == null || ids.isBlank() || ids.indexOf(',') >= 0) {
                continue;
            }
            addChoice(options, used, "r:" + kind + "-" + ids.trim(), allocation.getItemName());
        }
    }

    private static void addChoice(List<GrantBudgetChoiceDto> options, Set<String> used, String value, String label) {
        if (value == null || value.isBlank() || label == null || label.isBlank() || !used.add(value)) {
            return;
        }
        options.add(GrantBudgetChoiceDto.of(value, label));
    }

    /** Roczne kwoty są sumą dwunastu widoków miesiąca, nie osobnym wyliczeniem. */
    private BudgetDashboardDto yearFromMonths(int fiscalYear) {
        BudgetDashboardDto year = buildDashboard(fiscalYear, null);
        List<BudgetDashboardDto> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            months.add(buildDashboard(fiscalYear, month));
        }
        replaceCostsWithSumOfMonths(year, months);
        applyGrantRemainingToSpend(year);
        return year;
    }

    /**
     * Zostało do wydania: budżet grantu albo suma planu pokrycia na ten rok,
     * minus kwoty pokrycia stojące na pozycjach budżetu w kolumnie grantu.
     */
    private static void applyGrantRemainingToSpend(BudgetDashboardDto dto) {
        Map<String, BigDecimal> remaining = new LinkedHashMap<>();
        Map<String, BigDecimal> poolByGrant = dto.getGrantTotalByName() != null
                ? dto.getGrantTotalByName() : Map.of();
        Map<String, BigDecimal> allocatedByGrant = dto.getTotalCoverageByGrant() != null
                ? dto.getTotalCoverageByGrant() : Map.of();
        for (String grantName : dto.getGrantNames()) {
            BigDecimal pool = poolByGrant.getOrDefault(grantName, BigDecimal.ZERO);
            BigDecimal allocated = allocatedByGrant.getOrDefault(grantName, BigDecimal.ZERO);
            remaining.put(grantName, pool.subtract(allocated).setScale(2, RoundingMode.HALF_UP));
        }
        dto.setGrantRemainingByName(remaining);
        dto.setGrantRemainingTotal(remaining.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void replaceCostsWithSumOfMonths(BudgetDashboardDto year, List<BudgetDashboardDto> months) {
        Map<String, BudgetItemRowDto> yearRows = new LinkedHashMap<>();
        indexRows(year.getRows(), yearRows);
        Map<String, BigDecimal> costByKey = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> coverageByKey = new LinkedHashMap<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCoverage = BigDecimal.ZERO;
        Map<String, BigDecimal> totalByGrant = emptyGrantMap(year.getGrantNames());
        for (BudgetDashboardDto month : months) {
            totalCost = totalCost.add(month.getTotalCost() != null ? month.getTotalCost() : BigDecimal.ZERO);
            totalCoverage = totalCoverage.add(month.getTotalGrantCoverage() != null
                    ? month.getTotalGrantCoverage() : BigDecimal.ZERO);
            mergeGrantAmounts(totalByGrant, month.getTotalCoverageByGrant());
            Map<String, BudgetItemRowDto> monthRows = new LinkedHashMap<>();
            indexRows(month.getRows(), monthRows);
            for (Map.Entry<String, BudgetItemRowDto> entry : monthRows.entrySet()) {
                BudgetItemRowDto row = entry.getValue();
                costByKey.merge(entry.getKey(),
                        row.getTotalCost() != null ? row.getTotalCost() : BigDecimal.ZERO,
                        BigDecimal::add);
                Map<String, BigDecimal> coverage = coverageByKey.computeIfAbsent(
                        entry.getKey(), ignored -> emptyGrantMap(year.getGrantNames()));
                mergeGrantAmounts(coverage, row.getCoverageByGrant());
            }
        }
        for (Map.Entry<String, BudgetItemRowDto> entry : yearRows.entrySet()) {
            BudgetItemRowDto row = entry.getValue();
            BigDecimal cost = costByKey.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            Map<String, BigDecimal> coverage = coverageByKey.getOrDefault(
                    entry.getKey(), emptyGrantMap(year.getGrantNames()));
            row.setTotalCost(cost);
            row.setCoverageByGrant(coverage);
            row.setOverallCoverage(sumGrantMap(coverage));
            row.setBalance(cost.subtract(row.getOverallCoverage()));
        }
        rebalanceSalaryRemainders(year.getRows());
        year.setTotalCost(totalCost);
        year.setTotalGrantCoverage(totalCoverage);
        year.setTotalCoverageByGrant(totalByGrant);
        year.setBalance(totalCost.subtract(totalCoverage));
        year.setDisplayRows(flattenRowsForDisplay(year.getRows(), year.getCategoryOrderByRowKey()));
    }

    /**
     * Suma miesięcy potrafi odjąć z „Pozostałej części pensji” przeniesienie,
     * którego wiersza nie ma w szkielecie roku. Reszta ma być pensją pracownika
     * minus linie przeniesienia, które pod nim faktycznie wiszą.
     */
    static void rebalanceSalaryRemainders(List<BudgetItemRowDto> rows) {
        if (rows == null) {
            return;
        }
        for (BudgetItemRowDto row : rows) {
            rebalanceEmployeeRemainder(row);
            rebalanceSalaryRemainders(row.getChildren());
        }
    }

    private static void rebalanceEmployeeRemainder(BudgetItemRowDto employee) {
        if (employee.getRowKey() == null || !employee.getRowKey().matches("employee-\\d+")
                || employee.getChildren() == null) {
            return;
        }
        BudgetItemRowDto remainder = null;
        BigDecimal transferred = BigDecimal.ZERO;
        for (BudgetItemRowDto child : employee.getChildren()) {
            String key = child.getRowKey();
            if (key == null) {
                continue;
            }
            if (key.startsWith("pensja-pozostala-")) {
                remainder = child;
            } else if (key.startsWith("pensja-admin-") || key.startsWith("pensja-promocja-")) {
                transferred = transferred.add(child.getTotalCost() != null ? child.getTotalCost() : BigDecimal.ZERO);
            }
        }
        if (remainder == null) {
            return;
        }
        BigDecimal full = employee.getTotalCost() != null ? employee.getTotalCost() : BigDecimal.ZERO;
        BigDecimal left = full.subtract(transferred).setScale(2, RoundingMode.HALF_UP);
        remainder.setTotalCost(left);
        BigDecimal coverage = remainder.getCoverageByGrant() != null
                ? sumGrantMap(remainder.getCoverageByGrant()) : BigDecimal.ZERO;
        remainder.setOverallCoverage(coverage);
        remainder.setBalance(left.subtract(coverage));
    }

    @Transactional(readOnly = true)
    public BudgetDashboardDto getBudgetDashboardDataForMonth(int fiscalYear, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Miesiąc poza zakresem 1–12: " + month);
        }
        return buildDashboard(fiscalYear, month);
    }

    /** Pulpit: wpływy z transz i suma Planowany koszt, miesiąc po miesiącu. */
    @Transactional(readOnly = true)
    public HomeChartDto getHomeChart(int fiscalYear) {
        List<BigDecimal> plannedCost = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            plannedCost.add(money(buildDashboard(fiscalYear, month).getTotalCost()));
        }
        return new HomeChartDto(fiscalYear, PolishMonthNames.SHORT, trancheIncomeByMonth(fiscalYear), plannedCost);
    }

    private List<BigDecimal> trancheIncomeByMonth(int fiscalYear) {
        BigDecimal[] amounts = new BigDecimal[12];
        Arrays.fill(amounts, BigDecimal.ZERO);
        Map<Long, Grant> grants = new LinkedHashMap<>();
        for (Grant grant : grantRepository.findAllWithTranches()) {
            if (grant.getId() != null) {
                grants.putIfAbsent(grant.getId(), grant);
            }
        }
        for (Grant grant : grants.values()) {
            if (hasTrancheAmounts(grant)) {
                addTrancheIncome(amounts, grant, fiscalYear);
            } else {
                addGrantIncomeByActiveMonths(amounts, grant, fiscalYear);
            }
        }
        return Arrays.stream(amounts).map(BudgetMatrixService::money).toList();
    }

    private static boolean hasTrancheAmounts(Grant grant) {
        if (grant.getTranches() == null) {
            return false;
        }
        for (GrantTranche tranche : grant.getTranches()) {
            if (tranche.getPlannedAmount() != null && tranche.getPlannedAmount().signum() > 0) {
                return true;
            }
        }
        return false;
    }

    /** Transze z datą w danym roku. Data planowana, a gdy jej brak — data wpływu. */
    private static void addTrancheIncome(BigDecimal[] amounts, Grant grant, int fiscalYear) {
        for (GrantTranche tranche : grant.getTranches()) {
            LocalDate date = tranche.getPlannedDate() != null
                    ? tranche.getPlannedDate()
                    : tranche.getReceivedDate();
            if (date == null || date.getYear() != fiscalYear) {
                continue;
            }
            BigDecimal amount = tranche.getPlannedAmount();
            if (amount == null || amount.signum() == 0) {
                continue;
            }
            amounts[date.getMonthValue() - 1] = amounts[date.getMonthValue() - 1].add(amount);
        }
    }

    /** Grant bez transz: kwota dzieli się równo na miesiące aktywności. */
    private static void addGrantIncomeByActiveMonths(BigDecimal[] amounts, Grant grant, int fiscalYear) {
        List<YearMonth> months = GrantPeriodCoverage.activeMonths(grant.getStartDate(), grant.getEndDate());
        if (months.isEmpty() || grant.getTotalAmount() == null || grant.getTotalAmount().signum() <= 0) {
            return;
        }
        List<BigDecimal> shares = MonthlySplit.sharesAcross(grant.getTotalAmount(), months.size());
        for (int i = 0; i < months.size(); i++) {
            YearMonth month = months.get(i);
            if (month.getYear() != fiscalYear) {
                continue;
            }
            amounts[month.getMonthValue() - 1] = amounts[month.getMonthValue() - 1].add(shares.get(i));
        }
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BudgetRowDetailDto getBudgetRowDetail(int fiscalYear, Integer month, String rowKey) {
        if (rowKey == null || rowKey.isBlank()) {
            throw new IllegalArgumentException("Brak identyfikatora pozycji");
        }
        BudgetDashboardDto dashboard = month != null
                ? getBudgetDashboardDataForMonth(fiscalYear, month)
                : getBudgetDashboardDataForYear(fiscalYear);
        List<BudgetItemRowDto> breadcrumbs = new ArrayList<>();
        BudgetItemRowDto row = findRowByKey(dashboard.getRows(), rowKey.trim(), breadcrumbs)
                .orElseThrow(() -> new IllegalArgumentException("Pozycja budżetowa nie istnieje: " + rowKey));
        return new BudgetRowDetailDto(dashboard, row, breadcrumbs);
    }

    /**
     * Realizacja nie pokazuje planu pokrycia. Kwoty biorą się z rejestru wydatków.
     */
    @Transactional(readOnly = true)
    public void applyRealizationExpenditures(BudgetDashboardDto dto, int fiscalYear, Integer month) {
        if (dto == null || dto.getRows() == null) {
            return;
        }
        List<String> grantNames = dto.getGrantNames() != null ? dto.getGrantNames() : List.of();
        clearPlanFigures(dto.getRows(), grantNames);
        Map<String, SpendBucket> spendByRow = expenditureSpendByRow(fiscalYear, month, grantNames);
        Map<String, BudgetItemRowDto> rowsByKey = new LinkedHashMap<>();
        indexRows(dto.getRows(), rowsByKey);
        for (Map.Entry<String, SpendBucket> entry : spendByRow.entrySet()) {
            BudgetItemRowDto target = rowsByKey.get(entry.getKey());
            if (target == null && entry.getValue().fallbackKey != null) {
                target = rowsByKey.get(entry.getValue().fallbackKey);
            }
            if (target == null) {
                continue;
            }
            addCostAndCoverage(target, entry.getValue().total, entry.getValue().byGrant);
        }
        for (BudgetItemRowDto row : dto.getRows()) {
            rollupExpenditureRow(row, grantNames);
        }
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCoverage = BigDecimal.ZERO;
        Map<String, BigDecimal> totalByGrant = emptyGrantMap(grantNames);
        for (BudgetItemRowDto row : dto.getRows()) {
            totalCost = totalCost.add(row.getTotalCost() != null ? row.getTotalCost() : BigDecimal.ZERO);
            totalCoverage = totalCoverage.add(row.getOverallCoverage() != null ? row.getOverallCoverage() : BigDecimal.ZERO);
            mergeGrantAmounts(totalByGrant, row.getCoverageByGrant());
        }
        dto.setTotalCost(totalCost);
        dto.setTotalGrantCoverage(totalCoverage);
        dto.setTotalCoverageByGrant(totalByGrant);
        dto.setBalance(totalCost.subtract(totalCoverage));
        dto.setGrantRemainingByName(new LinkedHashMap<>());
        dto.setGrantYearAmountByName(new LinkedHashMap<>());
        dto.setGrantTotalByName(new LinkedHashMap<>());
        dto.setGrantNextYearByName(new LinkedHashMap<>());
        dto.setGrantRemainingTotal(BigDecimal.ZERO);
        dto.setDisplayRows(flattenRowsForDisplay(dto.getRows(), dto.getCategoryOrderByRowKey()));
    }

    private void clearPlanFigures(List<BudgetItemRowDto> rows, List<String> grantNames) {
        if (rows == null) {
            return;
        }
        for (BudgetItemRowDto row : rows) {
            row.setTotalCost(BigDecimal.ZERO);
            row.setCoverageByGrant(emptyGrantMap(grantNames));
            row.setOverallCoverage(BigDecimal.ZERO);
            row.setBalance(BigDecimal.ZERO);
            row.setAllocations(List.of());
            clearPlanFigures(row.getChildren(), grantNames);
        }
    }

    private void rollupExpenditureRow(BudgetItemRowDto row, List<String> grantNames) {
        if (row.getChildren() == null || row.getChildren().isEmpty()) {
            return;
        }
        BigDecimal ownCost = row.getTotalCost() != null ? row.getTotalCost() : BigDecimal.ZERO;
        Map<String, BigDecimal> ownCoverage = row.getCoverageByGrant() != null
                ? new LinkedHashMap<>(row.getCoverageByGrant())
                : emptyGrantMap(grantNames);
        boolean childHasAmount = false;
        for (BudgetItemRowDto child : row.getChildren()) {
            rollupExpenditureRow(child, grantNames);
            BigDecimal childCost = child.getTotalCost() != null ? child.getTotalCost() : BigDecimal.ZERO;
            if (childCost.signum() > 0) {
                childHasAmount = true;
            }
        }
        if (!childHasAmount) {
            return;
        }
        aggregateFromChildren(row, grantNames);
        if (ownCost.signum() > 0) {
            addCostAndCoverage(row, ownCost, ownCoverage);
        }
    }

    private Map<String, SpendBucket> expenditureSpendByRow(int fiscalYear, Integer month, List<String> grantNames) {
        Map<String, SpendBucket> byRow = new LinkedHashMap<>();
        for (Expenditure expenditure : expenditureRepository.findAllWithGrantAndItemByFiscalYear(fiscalYear)) {
            if (month != null) {
                if (expenditure.getIssueDate() == null
                        || expenditure.getIssueDate().getYear() != fiscalYear
                        || expenditure.getIssueDate().getMonthValue() != month) {
                    continue;
                }
            }
            String grantName = expenditure.getGrant() != null ? expenditure.getGrant().getName() : null;
            List<CostAllocation> allocations = expenditure.getCostAllocations() != null
                    ? expenditure.getCostAllocations()
                    : List.of();
            if (addGrantShareSpend(byRow, grantNames, expenditure, allocations)) {
                continue;
            }
            if (allocations.isEmpty()) {
                String categoryCode = expenditure.getBudgetItem() != null ? expenditure.getBudgetItem().getCode() : null;
                String rowKey = categoryCode == null ? null : categoryRowKey(categoryCode);
                addSpend(byRow, grantNames, rowKey, null, grantName, expenditureAmount(expenditure));
                continue;
            }
            for (CostAllocation allocation : allocations) {
                if (allocation.getAmount() == null || allocation.getAmount().signum() == 0) {
                    continue;
                }
                String categoryCode = allocation.getCategory() != null ? allocation.getCategory().getCode() : null;
                String target = expenditureRowKey(categoryCode, allocation);
                String fallback = categoryCode == null ? null : categoryRowKey(categoryCode);
                addSpend(byRow, grantNames, target != null ? target : fallback, fallback, grantName, allocation.getAmount());
            }
        }
        return byRow;
    }

    private boolean addGrantShareSpend(Map<String, SpendBucket> byRow,
                                       List<String> grantNames,
                                       Expenditure expenditure,
                                       List<CostAllocation> allocations) {
        List<ExpenditureGrantShare> shares = expenditure.getGrantShares();
        if (shares == null || shares.isEmpty()) {
            return false;
        }
        if (allocations.isEmpty()) {
            for (ExpenditureGrantShare share : shares) {
                String categoryCode = share.getBudgetItem() != null
                        ? share.getBudgetItem().getCode()
                        : (expenditure.getBudgetItem() != null ? expenditure.getBudgetItem().getCode() : null);
                String rowKey = categoryCode == null ? null : categoryRowKey(categoryCode);
                String grantName = share.getGrant() != null ? share.getGrant().getName() : null;
                addSpend(byRow, grantNames, rowKey, null, grantName, share.getAmount());
            }
            return true;
        }
        BigDecimal gross = expenditureAmount(expenditure);
        if (gross.signum() == 0) {
            return true;
        }
        for (CostAllocation allocation : allocations) {
            if (allocation.getAmount() == null || allocation.getAmount().signum() == 0) {
                continue;
            }
            BigDecimal left = allocation.getAmount();
            for (int i = 0; i < shares.size(); i++) {
                ExpenditureGrantShare share = shares.get(i);
                BigDecimal piece = i == shares.size() - 1
                        ? left
                        : allocation.getAmount().multiply(share.getAmount()).divide(gross, 2, RoundingMode.HALF_UP);
                left = left.subtract(piece);
                String categoryCode = allocation.getCategory() != null ? allocation.getCategory().getCode() : null;
                String target = expenditureRowKey(categoryCode, allocation);
                String fallback = categoryCode == null ? null : categoryRowKey(categoryCode);
                String grantName = share.getGrant() != null ? share.getGrant().getName() : null;
                addSpend(byRow, grantNames, target != null ? target : fallback, fallback, grantName, piece);
            }
        }
        return true;
    }

    private static void addSpend(Map<String, SpendBucket> byRow,
                                 List<String> grantNames,
                                 String rowKey,
                                 String fallbackKey,
                                 String grantName,
                                 BigDecimal amount) {
        if (rowKey == null || amount == null || amount.signum() == 0) {
            return;
        }
        SpendBucket bucket = byRow.computeIfAbsent(rowKey, ignored -> new SpendBucket(fallbackKey, grantNames));
        bucket.total = bucket.total.add(amount);
        if (grantName != null) {
            bucket.byGrant.merge(grantName, amount, BigDecimal::add);
        }
    }

    private String expenditureRowKey(String categoryCode, CostAllocation allocation) {
        if (PERSONNEL_CODE.equals(categoryCode) && allocation.getEmployee() != null) {
            return "employee-" + allocation.getEmployee().getId();
        }
        if (PROMOTION_CODE.equals(categoryCode)) {
            String child = promotionChildKey(allocation.getLabel());
            if (child != null) {
                return child;
            }
        }
        if (ADMIN_CODE.equals(categoryCode)) {
            String child = adminChildKey(allocation.getAdminGroup());
            if (child != null) {
                return child;
            }
        }
        return categoryCode == null ? null : categoryRowKey(categoryCode);
    }

    private static String promotionChildKey(String label) {
        if (label == null) {
            return null;
        }
        for (LabeledSubcategory subcategory : PROMOTION_SUBCATEGORIES) {
            if (subcategory.label().equalsIgnoreCase(label)) {
                return subcategory.rowKey();
            }
        }
        return null;
    }

    private static String adminChildKey(String adminGroup) {
        if (ADMIN_GROUP_BIURO.equals(adminGroup)) {
            return ADMIN_BIURO_KEY;
        }
        if (ADMIN_GROUP_WYNAGRODZENIA.equals(adminGroup)) {
            return ADMIN_WYNAGRODZENIA_KEY;
        }
        return ADMIN_POZOSTALE_KEY;
    }

    private static void indexRows(List<BudgetItemRowDto> rows, Map<String, BudgetItemRowDto> byKey) {
        if (rows == null) {
            return;
        }
        for (BudgetItemRowDto row : rows) {
            if (row.getRowKey() != null) {
                byKey.putIfAbsent(row.getRowKey(), row);
            }
            indexRows(row.getChildren(), byKey);
        }
    }

    private static final class SpendBucket {
        private final String fallbackKey;
        private final Map<String, BigDecimal> byGrant;
        private BigDecimal total = BigDecimal.ZERO;

        private SpendBucket(String fallbackKey, List<String> grantNames) {
            this.fallbackKey = fallbackKey;
            this.byGrant = emptyGrantMap(grantNames);
        }
    }

    private Optional<BudgetItemRowDto> findRowByKey(List<BudgetItemRowDto> rows,
                                                    String rowKey,
                                                    List<BudgetItemRowDto> breadcrumbs) {
        for (BudgetItemRowDto row : rows) {
            if (rowKey.equals(row.getRowKey())) {
                return Optional.of(row);
            }
            if (row.getChildren() != null && !row.getChildren().isEmpty()) {
                breadcrumbs.add(row);
                Optional<BudgetItemRowDto> nested = findRowByKey(row.getChildren(), rowKey, breadcrumbs);
                if (nested.isPresent()) {
                    return nested;
                }
                breadcrumbs.remove(breadcrumbs.size() - 1);
            }
        }
        return Optional.empty();
    }

    private BudgetDashboardDto buildDashboard(int fiscalYear, Integer month) {
        boolean useMonthlyPlan = month != null
                && costAllocationRepository.existsMonthlyPlanForFiscalYear(fiscalYear);
        BigDecimal amountScale = month != null && !useMonthlyPlan
                ? BigDecimal.ONE.divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        List<Grant> grants = grantRepository.findAllWithBudgetItems().stream()
                .sorted(Comparator.comparing(Grant::getName, Comparator.nullsLast(String::compareTo)))
                .toList();

        List<String> grantNames = grants.stream().map(Grant::getName).toList();
        Map<Long, String> grantNameByProjectId = new LinkedHashMap<>();
        for (Grant grant : grants) {
            if (grant.getProject() != null) {
                grantNameByProjectId.put(grant.getProject().getId(), grant.getName());
            }
        }

        Set<String> itemOrder = new LinkedHashSet<>();
        Map<String, BigDecimal> plannedByItem = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> plannedByItemAndGrant = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> spentByItemAndGrant = new LinkedHashMap<>();

        for (Grant grant : grants) {
            for (GrantBudgetItem item : grant.getBudgetItems()) {
                String key = itemKey(item);
                itemOrder.add(key);
                BigDecimal planned = scaleAmount(item.getPlannedAmount(), amountScale);
                plannedByItem.merge(key, planned, BigDecimal::add);
                plannedByItemAndGrant
                        .computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                        .merge(grant.getName(), planned, BigDecimal::add);
            }
        }

        List<Expenditure> allYearExpenditures = expenditureRepository.findAllWithGrantAndItemByFiscalYear(fiscalYear);

        List<Expenditure> expenditures = allYearExpenditures;
        if (month != null) {
            expenditures = expenditures.stream()
                    .filter(e -> e.getIssueDate() != null
                            && e.getIssueDate().getYear() == fiscalYear
                            && e.getIssueDate().getMonthValue() == month)
                    .toList();
        }
        List<CostAllocation> costAllocations = month != null && useMonthlyPlan
                ? costAllocationRepository.findPlanAllocationsByFiscalYearAndPlanMonth(fiscalYear, month)
                : costAllocationRepository.findPlanAllocationsByFiscalYear(fiscalYear);
        Map<Long, List<CostAllocation>> allocationsByEmployee = costAllocations.stream()
                .filter(a -> a.getEmployee() != null)
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId(), LinkedHashMap::new, Collectors.toList()));

        for (Expenditure expenditure : expenditures) {
            if (!GrantPeriodCoverage.countsTowardGrant(expenditure)) {
                continue;
            }
            GrantBudgetItem item = expenditure.getBudgetItem();
            Grant grant = expenditure.getGrant();
            if (item == null || grant == null) {
                continue;
            }
            String key = itemKey(item);
            itemOrder.add(key);
            BigDecimal amount = expenditureAmount(expenditure);
            spentByItemAndGrant
                    .computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                    .merge(grant.getName(), amount, BigDecimal::add);
        }

        List<BudgetItemRowDto> rows = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCoverage = BigDecimal.ZERO;
        Set<String> handledCategoryRowKeys = new LinkedHashSet<>();

        List<BudgetItemTemplate> orderedCategories = budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc();
        for (BudgetItemTemplate category : orderedCategories) {
            String code = category.getCode();
            String key = findKeyForCategory(category, itemOrder);
            if (key == null) {
                key = category.getName() != null ? category.getName() : category.getCode();
            }
            Optional<BudgetItemRowDto> categoryRow = buildCategoryRow(
                    code,
                    key,
                    handledCategoryRowKeys,
                    grantNames,
                    grantNameByProjectId,
                    plannedByItem,
                    plannedByItemAndGrant,
                    allocationsByEmployee,
                    costAllocations,
                    amountScale,
                    grants,
                    fiscalYear,
                    month);
            if (categoryRow.isPresent()) {
                BudgetItemRowDto row = categoryRow.get();
                rows.add(row);
                totalCost = totalCost.add(row.getTotalCost());
                totalCoverage = totalCoverage.add(row.getOverallCoverage());
            }
        }

        for (String key : itemOrder) {
            if (isKeyHandledByCategory(key, handledCategoryRowKeys)) {
                continue;
            }
            BudgetItemRowDto row = buildRow(key, grantNames, plannedByItem, plannedByItemAndGrant);
            rows.add(row);
            totalCost = totalCost.add(row.getTotalCost());
            totalCoverage = totalCoverage.add(row.getOverallCoverage());
        }

        BudgetDashboardDto dto = new BudgetDashboardDto();
        dto.setGrantNames(grantNames);
        GrantBudgetService.GrantSpendPlan grantSpendPlan = grantBudgetService.computeGrantSpendPlan(grants, fiscalYear);
        dto.setGrantRemainingByName(grantSpendPlan.remainingByName());
        dto.setGrantYearAmountByName(grantSpendPlan.yearAmountByName());
        dto.setGrantTotalByName(grantSpendPlan.totalByName());
        dto.setGrantNextYearByName(grantSpendPlan.nextYearByName());
        dto.setGrantRemainingTotal(grantSpendPlan.remainingByName().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        Map<String, BigDecimal> totalCoverageByGrant = emptyGrantMap(grantNames);
        for (BudgetItemRowDto row : rows) {
            mergeGrantAmounts(totalCoverageByGrant, row.getCoverageByGrant());
        }
        dto.setTotalCoverageByGrant(totalCoverageByGrant);
        dto.setRows(rows);
        Map<String, BudgetDashboardDto.CategoryOrderInfo> categoryOrderByRowKey = buildCategoryOrderByRowKey();
        dto.setDisplayRows(flattenRowsForDisplay(rows, categoryOrderByRowKey));
        dto.setCategoryOrderByRowKey(categoryOrderByRowKey);
        dto.setTotalCost(totalCost);
        dto.setTotalGrantCoverage(totalCoverage);
        dto.setBalance(totalCost.subtract(totalCoverage));
        return dto;
    }

    private Map<String, BudgetDashboardDto.CategoryOrderInfo> buildCategoryOrderByRowKey() {
        List<BudgetItemTemplate> categories = budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc();
        Map<String, BudgetDashboardDto.CategoryOrderInfo> orderByRowKey = new LinkedHashMap<>();
        for (int i = 0; i < categories.size(); i++) {
            BudgetItemTemplate category = categories.get(i);
            if (category.getCode() == null) {
                continue;
            }
            String rowKey = categoryRowKey(category.getCode());
            orderByRowKey.put(rowKey, new BudgetDashboardDto.CategoryOrderInfo(
                    category.getId(),
                    i > 0,
                    i < categories.size() - 1));
        }
        return orderByRowKey;
    }

    private List<BudgetDashboardDto.BudgetDisplayRowDto> flattenRowsForDisplay(
            List<BudgetItemRowDto> rows,
            Map<String, BudgetDashboardDto.CategoryOrderInfo> categoryOrderByRowKey) {
        List<BudgetDashboardDto.BudgetDisplayRowDto> flat = new ArrayList<>();
        for (BudgetItemRowDto row : rows) {
            String categoryRootRowKey = row.getRowKey();
            BudgetDashboardDto.BudgetDisplayRowDto categoryDisplay =
                    enrichDisplayRow(toDisplayRow(row, 0), categoryOrderByRowKey);
            categoryDisplay.setCategoryRootRowKey(categoryRootRowKey);
            flat.add(categoryDisplay);
            String parentRowKey = row.getRowKey();
            List<BudgetItemRowDto> children = sortSubcategories(parentRowKey, row.getChildren());
            if (children != null && !children.isEmpty()) {
                for (BudgetItemRowDto child : children) {
                    BudgetDashboardDto.BudgetDisplayRowDto childDisplay = toDisplayRow(child, 1);
                    childDisplay.setParentRowKey(parentRowKey);
                    childDisplay.setCategoryRootRowKey(categoryRootRowKey);
                    childDisplay.setDraggable(child.getRowKey() != null && !child.getRowKey().isBlank());
                    markLeafMonthAmount(childDisplay, parentRowKey, child);
                    flat.add(childDisplay);
                    String childRowKey = child.getRowKey();
                    List<BudgetItemRowDto> grandChildren = sortSubcategories(childRowKey, child.getChildren());
                    if (grandChildren != null && !grandChildren.isEmpty()) {
                        for (BudgetItemRowDto grandChild : grandChildren) {
                            BudgetDashboardDto.BudgetDisplayRowDto grandChildDisplay = toDisplayRow(grandChild, 2);
                            grandChildDisplay.setParentRowKey(childRowKey);
                            grandChildDisplay.setCategoryRootRowKey(categoryRootRowKey);
                            grandChildDisplay.setDraggable(
                                    grandChild.getRowKey() != null && !grandChild.getRowKey().isBlank());
                            markLeafMonthAmount(grandChildDisplay, childRowKey, grandChild);
                            flat.add(grandChildDisplay);
                        }
                    }
                    appendAllocationDisplayRows(flat, child.getAllocations(), 2, categoryRootRowKey);
                }
            } else {
                appendAllocationDisplayRows(flat, row.getAllocations(), 1, categoryRootRowKey);
            }
        }
        return flat;
    }

    private List<BudgetItemRowDto> sortSubcategories(String parentRowKey, List<BudgetItemRowDto> children) {
        if (children == null || children.size() < 2 || parentRowKey == null || parentRowKey.isBlank()) {
            return children;
        }
        Map<String, Integer> orderByKey = budgetSubcategoryOrderRepository
                .findByParentRowKeyOrderByDisplayOrderAsc(parentRowKey).stream()
                .collect(Collectors.toMap(
                        BudgetSubcategoryOrder::getRowKey,
                        BudgetSubcategoryOrder::getDisplayOrder,
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (orderByKey.isEmpty()) {
            return children;
        }
        List<BudgetItemRowDto> sorted = new ArrayList<>(children);
        sorted.sort(Comparator
                .comparingInt((BudgetItemRowDto child) -> orderByKey.getOrDefault(child.getRowKey(), Integer.MAX_VALUE))
                .thenComparing(child -> child.getItemName() != null ? child.getItemName() : ""));
        return sorted;
    }

    private BudgetDashboardDto.BudgetDisplayRowDto enrichDisplayRow(
            BudgetDashboardDto.BudgetDisplayRowDto display,
            Map<String, BudgetDashboardDto.CategoryOrderInfo> categoryOrderByRowKey) {
        if (display.getDepth() != 0 || display.getRowKey() == null || categoryOrderByRowKey == null) {
            return display;
        }
        BudgetDashboardDto.CategoryOrderInfo order = categoryOrderByRowKey.get(display.getRowKey());
        if (order == null) {
            return display;
        }
        display.setCategoryTemplateId(order.getCategoryId());
        display.setDraggable(true);
        return display;
    }

    private List<BudgetDashboardDto.BudgetDisplayRowDto> flattenRowsForDisplay(List<BudgetItemRowDto> rows) {
        return flattenRowsForDisplay(rows, Map.of());
    }

    private static void markLeafMonthAmount(BudgetDashboardDto.BudgetDisplayRowDto display,
                                             String parentRowKey,
                                             BudgetItemRowDto row) {
        if (display.isAmountEditable() || row == null || row.getRowKey() == null || row.getRowKey().isBlank()) {
            return;
        }
        if (row.getRowKey().matches("employee-\\d+")) {
            display.setAmountEditable(true);
            display.setAmountEditKind("employee");
            display.setAmountEditIds(row.getRowKey().substring("employee-".length()));
            return;
        }
        boolean hasChildren = row.getChildren() != null && !row.getChildren().isEmpty();
        boolean hasAllocations = row.getAllocations() != null && !row.getAllocations().isEmpty();
        if (hasChildren || hasAllocations || parentRowKey == null || parentRowKey.isBlank()) {
            return;
        }
        display.setAmountEditable(true);
        display.setAmountEditKind("subcategory");
        display.setAmountEditIds(parentRowKey + "|" + row.getRowKey());
    }

    private BudgetDashboardDto.BudgetDisplayRowDto toDisplayRow(BudgetItemRowDto row, int depth) {
        BudgetDashboardDto.BudgetDisplayRowDto display = new BudgetDashboardDto.BudgetDisplayRowDto();
        display.setDepth(depth);
        display.setItemName(row.getItemName());
        display.setRowKey(row.getRowKey());
        display.setLinkable(row.isExpandable() && row.getRowKey() != null && !row.getRowKey().isBlank());
        display.setTotalCost(row.getTotalCost() != null ? row.getTotalCost() : BigDecimal.ZERO);
        display.setBalance(row.getBalance() != null ? row.getBalance() : BigDecimal.ZERO);
        display.setCoverageByGrant(row.getCoverageByGrant() != null
                ? new LinkedHashMap<>(row.getCoverageByGrant())
                : emptyGrantMap(List.of()));
        return display;
    }

    private void appendAllocationDisplayRows(List<BudgetDashboardDto.BudgetDisplayRowDto> flat,
                                               List<CostAllocationDto> allocations,
                                               int depth,
                                               String categoryRootRowKey) {
        if (allocations == null || allocations.isEmpty()) {
            return;
        }
        for (CostAllocationDto allocation : allocations) {
            BudgetDashboardDto.BudgetDisplayRowDto display = new BudgetDashboardDto.BudgetDisplayRowDto();
            display.setDepth(depth);
            display.setItemName(allocation.getItemName());
            display.setCategoryRootRowKey(categoryRootRowKey);
            display.setLinkable(false);
            display.setTotalCost(allocation.getAmount() != null ? allocation.getAmount() : BigDecimal.ZERO);
            display.setBalance(allocation.getBalance() != null ? allocation.getBalance() : BigDecimal.ZERO);
            display.setCoverageByGrant(allocation.getAmountByGrant() != null
                    ? new LinkedHashMap<>(allocation.getAmountByGrant())
                    : emptyGrantMap(List.of()));
            if (allocation.getAmountEditKind() != null && allocation.getAmountEditIds() != null
                    && !allocation.getAmountEditIds().isBlank()) {
                display.setAmountEditable(true);
                display.setAmountEditKind(allocation.getAmountEditKind());
                display.setAmountEditIds(allocation.getAmountEditIds());
            }
            flat.add(display);
        }
    }

    private BudgetItemRowDto buildExpandableRow(String key,
                                                  String rowKey,
                                                  String label,
                                                  List<String> grantNames,
                                                  Map<String, BigDecimal> plannedByItem,
                                                  Map<String, Map<String, BigDecimal>> plannedByItemAndGrant) {
        BudgetItemRowDto row = buildRow(key, grantNames, plannedByItem, plannedByItemAndGrant);
        row.setRowKey(rowKey);
        row.setItemName(label);
        row.setCategory(label);
        row.setExpandable(true);
        return row;
    }

    private BudgetItemRowDto buildRow(String key,
                                      List<String> grantNames,
                                      Map<String, BigDecimal> plannedByItem,
                                      Map<String, Map<String, BigDecimal>> plannedByItemAndGrant) {
        BigDecimal planned = plannedByItem.getOrDefault(key, BigDecimal.ZERO);
        Map<String, BigDecimal> byGrant = emptyGrantMap(grantNames);

        BudgetItemRowDto row = new BudgetItemRowDto();
        row.setCategory(key);
        row.setItemName(key);
        row.setTotalCost(planned);
        row.setOverallCoverage(BigDecimal.ZERO);
        row.setCoverageByGrant(byGrant);
        row.setBalance(planned);
        return row;
    }

    private void aggregateFromChildren(BudgetItemRowDto parent, List<String> grantNames) {
        BigDecimal totalCost = BigDecimal.ZERO;
        Map<String, BigDecimal> byGrant = emptyGrantMap(grantNames);
        List<BudgetItemRowDto> children = parent.getChildren() != null ? parent.getChildren() : List.of();

        for (BudgetItemRowDto child : children) {
            BigDecimal childCost = child.getTotalCost() != null ? child.getTotalCost() : BigDecimal.ZERO;
            totalCost = totalCost.add(childCost);
            mergeGrantAmounts(byGrant, child.getCoverageByGrant());
        }

        BigDecimal coverage = sumGrantMap(byGrant);
        parent.setTotalCost(totalCost);
        parent.setCoverageByGrant(byGrant);
        parent.setOverallCoverage(coverage);
        parent.setBalance(totalCost.subtract(coverage));
    }

    private List<BudgetItemRowDto> buildEmployeeRows(List<Grant> grants,
                                                       int fiscalYear,
                                                       List<String> grantNames,
                                                       Map<Long, String> grantNameByProjectId,
                                                       Map<Long, List<CostAllocation>> allocationsByEmployee,
                                                       BigDecimal amountScale,
                                                       Integer month) {
        List<BudgetItemRowDto> children = new ArrayList<>();

        for (Employee employee : employeeRepository.findAll()) {
            if ("system@ngo.pl".equals(employee.getEmail())) {
                continue;
            }

            BigDecimal planned = month != null
                    ? MonthlySplit.shareForMonth(employee.getPlannedCost(), month)
                    : scaleAmount(employee.getPlannedCost(), amountScale);

            List<CostAllocation> employeeAllocations =
                    allocationsByEmployee.getOrDefault(employee.getId(), List.of());
            List<CostAllocation> visibleAllocations = new ArrayList<>();
            for (CostAllocation allocation : employeeAllocations) {
                if (!GrantBudgetService.isSalaryCoveragePlan(allocation)) {
                    visibleAllocations.add(allocation);
                }
            }
            List<CostAllocationDto> allocationDtos =
                    toCostAllocationDtos(visibleAllocations, grantNames, grantNameByProjectId, true, amountScale);

            Map<String, BigDecimal> byGrant = emptyGrantMap(grantNames);
            for (CostAllocationDto allocation : allocationDtos) {
                mergeGrantAmounts(byGrant, allocation.getAmountByGrant());
            }
            BigDecimal rowCoverage = sumGrantMap(byGrant);

            BudgetItemRowDto child = new BudgetItemRowDto();
            child.setRowKey("employee-" + employee.getId());
            child.setItemName(employeePayrollLabel(employee));
            child.setCategory(PERSONNEL_LABEL);
            child.setTotalCost(planned);
            child.setCoverageByGrant(byGrant);
            child.setOverallCoverage(rowCoverage);
            child.setBalance(planned.subtract(rowCoverage));
            child.setAllocations(allocationDtos);
            child.setExpandable(!child.getAllocations().isEmpty());
            children.add(child);
        }
        return children;
    }

    /**
     * Plan pokrycia z budżetu grantu. Plan pozycji grantu nie wchodzi do kolumny grantu.
     * Grant kończący się w 2027 bierze plan 2026 albo plan 2027, zależnie od oglądanego roku.
     * W roku pełna kwota, w miesiącu kwota podzielona przez miesiące aktywności grantu w tym roku.
     */
    private Map<String, Map<String, BigDecimal>> planCoverageBySource(List<Grant> grants,
                                                                       int fiscalYear,
                                                                       Integer month) {
        Map<String, Map<String, BigDecimal>> bySource = new LinkedHashMap<>();
        for (Grant grant : grants) {
            if (!grant.isActive() || grant.getName() == null || grant.getBudgetItems() == null) {
                continue;
            }
            for (GrantBudgetItem item : grant.getBudgetItems()) {
                if (!item.isActive() || item.getCoverages() == null) {
                    continue;
                }
                for (GrantBudgetItemCoverage coverage : item.getCoverages()) {
                    if (salaryDestinationCode(item) != null && employeeIdOf(coverage) != null) {
                        continue;
                    }
                    BigDecimal covered = GrantCoverageYear.amount(grant, coverage, fiscalYear);
                    if (covered == null || covered.signum() <= 0) {
                        continue;
                    }
                    BigDecimal amount = coverageAmountForPeriod(grant, covered, fiscalYear, month);
                    if (amount == null || amount.signum() <= 0) {
                        continue;
                    }
                    String sourceRef = coverageSourceRef(coverage);
                    if (sourceRef == null) {
                        continue;
                    }
                    bySource.computeIfAbsent(sourceRef, ignored -> new LinkedHashMap<>())
                            .merge(grant.getName(), amount, BigDecimal::add);
                }
            }
        }
        return bySource;
    }

    private static BigDecimal coverageAmountForPeriod(Grant grant,
                                                       BigDecimal covered,
                                                       int fiscalYear,
                                                       Integer month) {
        if (month == null) {
            if (!GrantPeriodCoverage.overlapsFiscalYear(grant.getStartDate(), grant.getEndDate(), fiscalYear)) {
                return null;
            }
            return covered;
        }
        List<YearMonth> monthsInYear = GrantPeriodCoverage.activeMonths(grant.getStartDate(), grant.getEndDate())
                .stream()
                .filter(yearMonth -> yearMonth.getYear() == fiscalYear)
                .toList();
        int index = monthsInYear.indexOf(YearMonth.of(fiscalYear, month));
        if (index < 0) {
            return null;
        }
        return MonthlySplit.sharesAcross(covered, monthsInYear.size()).get(index);
    }

    private static String coverageSourceRef(GrantBudgetItemCoverage coverage) {
        if (coverage.getOrgSourceType() == null) {
            return null;
        }
        String type = coverage.getOrgSourceType().name();
        if (coverage.getOrgSourceKey() != null && !coverage.getOrgSourceKey().isBlank()) {
            return type + "|" + coverage.getOrgSourceKey().trim();
        }
        if (coverage.getOrgSourceId() != null && coverage.getOrgSourceId() != 0L) {
            return type + "|" + coverage.getOrgSourceId();
        }
        return null;
    }

    private void applyPlanCoverage(BudgetItemRowDto row,
                                    Map<String, Map<String, BigDecimal>> bySource,
                                    List<String> grantNames) {
        if (row.getChildren() != null) {
            for (BudgetItemRowDto child : row.getChildren()) {
                applyPlanCoverage(child, bySource, grantNames);
            }
        }
        if (row.getCoverageByGrant() == null) {
            row.setCoverageByGrant(emptyGrantMap(grantNames));
        }
        if (row.getAllocations() != null) {
            for (CostAllocationDto allocation : row.getAllocations()) {
                Map<String, BigDecimal> extra = coverageForRefs(
                        sourceRefsForAllocation(allocation, row.getRowKey()), bySource);
                if (extra.isEmpty()) {
                    continue;
                }
                if (allocation.getAmountByGrant() == null) {
                    allocation.setAmountByGrant(emptyGrantMap(grantNames));
                }
                mergeGrantAmounts(allocation.getAmountByGrant(), extra);
                BigDecimal amount = allocation.getAmount() != null ? allocation.getAmount() : BigDecimal.ZERO;
                allocation.setBalance(amount.subtract(sumGrantMap(allocation.getAmountByGrant())));
                mergeGrantAmounts(row.getCoverageByGrant(), extra);
            }
        }
        mergeGrantAmounts(row.getCoverageByGrant(), coverageForRefs(sourceRefsForRow(row), bySource));
        BigDecimal coverage = sumGrantMap(row.getCoverageByGrant());
        BigDecimal planned = row.getTotalCost() != null ? row.getTotalCost() : BigDecimal.ZERO;
        row.setOverallCoverage(coverage);
        row.setBalance(planned.subtract(coverage));
        if (row.getChildren() != null && !row.getChildren().isEmpty()) {
            aggregateFromChildren(row, grantNames);
        }
    }

    /**
     * Część pensji przypisana do kosztów administracyjnych albo promocji schodzi z sumy Wynagrodzeń
     * i wchodzi jako pozycja w tej kategorii. Wiersz pracownika zostaje przy pełnej pensji:
     * pozostała część plus te przeniesione kwoty, bez drugiego odjęcia tego samego pokrycia.
     */
    private Map<Long, Map<String, AdminSalaryMove>> salaryRelocations(List<Grant> grants,
                                                                       int fiscalYear,
                                                                       Integer month) {
        Map<Long, Map<String, AdminSalaryMove>> moves = new LinkedHashMap<>();
        for (Grant grant : grants) {
            if (!grant.isActive() || grant.getName() == null || grant.getBudgetItems() == null) {
                continue;
            }
            for (GrantBudgetItem item : grant.getBudgetItems()) {
                String destination = salaryDestinationCode(item);
                if (!item.isActive() || destination == null || item.getCoverages() == null) {
                    continue;
                }
                for (GrantBudgetItemCoverage coverage : item.getCoverages()) {
                    Long employeeId = employeeIdOf(coverage);
                    if (employeeId == null) {
                        continue;
                    }
                    BigDecimal covered = GrantCoverageYear.amount(grant, coverage, fiscalYear);
                    if (covered == null || covered.signum() <= 0) {
                        continue;
                    }
                    BigDecimal amount = coverageAmountForPeriod(grant, covered, fiscalYear, month);
                    if (amount == null || amount.signum() <= 0) {
                        continue;
                    }
                    moves.computeIfAbsent(employeeId, ignored -> new LinkedHashMap<>())
                            .computeIfAbsent(destination, ignored -> new AdminSalaryMove())
                            .add(grant.getName(), amount);
                }
            }
        }
        return moves;
    }

    private void splitAdminSalaryFromPersonnel(BudgetItemRowDto personnel,
                                                Map<Long, Map<String, AdminSalaryMove>> moves,
                                                List<String> grantNames,
                                                Integer month) {
        if (personnel.getChildren() == null) {
            return;
        }
        BigDecimal movedOut = BigDecimal.ZERO;
        for (BudgetItemRowDto employee : personnel.getChildren()) {
            Long employeeId = employeeIdFromRowKey(employee.getRowKey());
            if (employeeId == null) {
                continue;
            }
            BigDecimal full = salaryForPeriod(employeeId, month);
            if (full == null) {
                continue;
            }
            employee.setTotalCost(full);
            Map<String, AdminSalaryMove> byDestination = moves.get(employeeId);
            if (byDestination == null || byDestination.isEmpty()) {
                BigDecimal coverage = sumGrantMap(employee.getCoverageByGrant());
                employee.setOverallCoverage(coverage);
                employee.setBalance(full.subtract(coverage));
                continue;
            }
            BigDecimal planned = full;
            BigDecimal slice = BigDecimal.ZERO;
            Map<String, BigDecimal> salaryCoverage = emptyGrantMap(grantNames);
            if (employee.getCoverageByGrant() != null) {
                mergeGrantAmounts(salaryCoverage, employee.getCoverageByGrant());
            }
            Map<String, BigDecimal> movedCoverage = emptyGrantMap(grantNames);
            List<BudgetItemRowDto> parts = new ArrayList<>();
            for (Map.Entry<String, AdminSalaryMove> entry : byDestination.entrySet()) {
                AdminSalaryMove move = entry.getValue();
                if (move == null || move.total.signum() <= 0) {
                    continue;
                }
                slice = slice.add(move.total);
                Map<String, BigDecimal> partCoverage = emptyGrantMap(grantNames);
                mergeGrantAmounts(partCoverage, move.byGrant);
                mergeGrantAmounts(movedCoverage, partCoverage);
                parts.add(planRow(
                        salaryPartRowKey(entry.getKey(), employeeId),
                        salaryPartLabel(entry.getKey()),
                        move.total,
                        partCoverage));
            }
            if (slice.signum() <= 0) {
                BigDecimal coverage = sumGrantMap(employee.getCoverageByGrant());
                employee.setOverallCoverage(coverage);
                employee.setBalance(full.subtract(coverage));
                continue;
            }
            parts.add(0, planRow(
                    "pensja-pozostala-" + employeeId,
                    "Pozostała część pensji",
                    planned.subtract(slice),
                    salaryCoverage));
            if (employee.getChildren() != null) {
                parts.addAll(employee.getChildren());
            }
            Map<String, BigDecimal> personCoverage = emptyGrantMap(grantNames);
            mergeGrantAmounts(personCoverage, salaryCoverage);
            mergeGrantAmounts(personCoverage, movedCoverage);
            employee.setChildren(parts);
            employee.setExpandable(true);
            employee.setTotalCost(planned);
            employee.setCoverageByGrant(personCoverage);
            employee.setOverallCoverage(sumGrantMap(personCoverage));
            employee.setBalance(planned.subtract(employee.getOverallCoverage()));
            movedOut = movedOut.add(slice);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BudgetItemRowDto child : personnel.getChildren()) {
            total = total.add(child.getTotalCost() != null ? child.getTotalCost() : BigDecimal.ZERO);
        }
        personnel.setTotalCost(total.subtract(movedOut));
        BigDecimal coverage = personnel.getOverallCoverage() != null
                ? personnel.getOverallCoverage()
                : sumGrantMap(personnel.getCoverageByGrant());
        personnel.setOverallCoverage(coverage);
        personnel.setBalance(personnel.getTotalCost().subtract(coverage));
    }

    /** Pensja na rok albo udział jednego miesiąca. Nie schodzi z kwoty już pomniejszonej o pokrycie. */
    private BigDecimal salaryForPeriod(Long employeeId, Integer month) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return null;
        }
        if (month == null) {
            BigDecimal annual = employee.getPlannedCost();
            if (annual == null) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return annual.setScale(2, RoundingMode.HALF_UP);
        }
        return MonthlySplit.shareForMonth(employee.getPlannedCost(), month);
    }

    private void addSalaryPositions(BudgetItemRowDto categoryRow,
                                     Map<Long, Map<String, AdminSalaryMove>> moves,
                                     String categoryCode,
                                     String payrollKey,
                                     String employeeKeyPrefix,
                                     List<String> grantNames) {
        if (moves.isEmpty()) {
            return;
        }
        if (categoryRow.getChildren() == null) {
            categoryRow.setChildren(new ArrayList<>());
        }
        BudgetItemRowDto payroll = null;
        for (BudgetItemRowDto child : categoryRow.getChildren()) {
            if (payrollKey.equals(child.getRowKey())) {
                payroll = child;
                break;
            }
        }
        if (payroll == null) {
            payroll = planRow(payrollKey, "Wynagrodzenia", BigDecimal.ZERO, emptyGrantMap(grantNames));
            List<BudgetItemRowDto> children = new ArrayList<>(categoryRow.getChildren());
            children.add(payroll);
            categoryRow.setChildren(children);
        }
        Map<Long, String> labels = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            if ("system@ngo.pl".equals(employee.getEmail())) {
                continue;
            }
            labels.put(employee.getId(), employeePayrollLabel(employee));
        }
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, Map<String, AdminSalaryMove>> entry : moves.entrySet()) {
            AdminSalaryMove move = entry.getValue().get(categoryCode);
            if (move != null && move.total.signum() > 0) {
                ids.add(entry.getKey());
            }
        }
        ids.sort(Comparator.comparing(id -> labels.getOrDefault(id, "")));
        List<BudgetItemRowDto> staff = payroll.getChildren() != null
                ? new ArrayList<>(payroll.getChildren())
                : new ArrayList<>();
        boolean addedStaff = false;
        for (Long id : ids) {
            AdminSalaryMove move = moves.get(id).get(categoryCode);
            Map<String, BigDecimal> byGrant = emptyGrantMap(grantNames);
            mergeGrantAmounts(byGrant, move.byGrant);
            staff.add(planRow(
                    employeeKeyPrefix + id,
                    labels.getOrDefault(id, "Pracownik"),
                    move.total,
                    byGrant));
            addedStaff = true;
        }
        if (!addedStaff) {
            return;
        }
        BigDecimal previousCost = payroll.getTotalCost() != null ? payroll.getTotalCost() : BigDecimal.ZERO;
        Map<String, BigDecimal> previousCoverage = payroll.getCoverageByGrant() != null
                ? new LinkedHashMap<>(payroll.getCoverageByGrant())
                : emptyGrantMap(grantNames);
        payroll.setChildren(staff);
        payroll.setExpandable(true);
        aggregateFromChildren(payroll, grantNames);
        Map<String, BigDecimal> coverageDelta = emptyGrantMap(grantNames);
        Map<String, BigDecimal> newCoverage = payroll.getCoverageByGrant() != null
                ? payroll.getCoverageByGrant()
                : Map.of();
        for (String grant : grantNames) {
            BigDecimal after = newCoverage.getOrDefault(grant, BigDecimal.ZERO);
            BigDecimal before = previousCoverage.getOrDefault(grant, BigDecimal.ZERO);
            coverageDelta.put(grant, after.subtract(before));
        }
        addCostAndCoverage(categoryRow, payroll.getTotalCost().subtract(previousCost), coverageDelta);
    }

    private static String salaryPartRowKey(String categoryCode, Long employeeId) {
        if (PROMOTION_CODE.equals(categoryCode)) {
            return "pensja-promocja-" + employeeId;
        }
        return "pensja-admin-" + employeeId;
    }

    private static String salaryPartLabel(String categoryCode) {
        if (PROMOTION_CODE.equals(categoryCode)) {
            return "Z promocji i komunikacji";
        }
        return "Z kosztów administracyjnych";
    }

    private static void addCostAndCoverage(BudgetItemRowDto row,
                                            BigDecimal added,
                                            Map<String, BigDecimal> addedByGrant) {
        BigDecimal total = row.getTotalCost() != null ? row.getTotalCost() : BigDecimal.ZERO;
        row.setTotalCost(total.add(added));
        if (row.getCoverageByGrant() == null) {
            row.setCoverageByGrant(new LinkedHashMap<>());
        }
        mergeGrantAmounts(row.getCoverageByGrant(), addedByGrant);
        BigDecimal coverage = sumGrantMap(row.getCoverageByGrant());
        row.setOverallCoverage(coverage);
        row.setBalance(row.getTotalCost().subtract(coverage));
    }

    private static BudgetItemRowDto planRow(String rowKey,
                                             String label,
                                             BigDecimal amount,
                                             Map<String, BigDecimal> coverageByGrant) {
        BudgetItemRowDto row = new BudgetItemRowDto();
        row.setRowKey(rowKey);
        row.setItemName(label);
        row.setTotalCost(amount);
        row.setCoverageByGrant(coverageByGrant);
        BigDecimal coverage = sumGrantMap(coverageByGrant);
        row.setOverallCoverage(coverage);
        row.setBalance(amount.subtract(coverage));
        row.setExpandable(false);
        row.setChildren(List.of());
        row.setAllocations(List.of());
        return row;
    }

    private static String salaryDestinationCode(GrantBudgetItem item) {
        if (isAdminGrantItem(item)) {
            return ADMIN_CODE;
        }
        if (isPromotionGrantItem(item)) {
            return PROMOTION_CODE;
        }
        return null;
    }

    private static boolean isPromotionGrantItem(GrantBudgetItem item) {
        if (item.getCode() != null && PROMOTION_CODE.equals(item.getCode().trim())) {
            return true;
        }
        if (item.getAccountingCode() != null && PROMOTION_CODE.equals(item.getAccountingCode().trim())) {
            return true;
        }
        String name = item.getName();
        return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("promocj");
    }

    private static boolean isAdminGrantItem(GrantBudgetItem item) {
        if (item.getCode() != null && ADMIN_CODE.equals(item.getCode().trim())) {
            return true;
        }
        if (item.getAccountingCode() != null && ADMIN_CODE.equals(item.getAccountingCode().trim())) {
            return true;
        }
        String name = item.getName();
        return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("administracyj");
    }

    private static Long employeeIdOf(GrantBudgetItemCoverage coverage) {
        if (coverage.getOrgSourceType() == OrgBudgetSourceType.EMPLOYEE
                && coverage.getOrgSourceId() != null
                && coverage.getOrgSourceId() != 0L) {
            return coverage.getOrgSourceId();
        }
        String key = coverage.getOrgSourceKey();
        Long fromKey = employeeIdFromDerivedKey(key);
        if (fromKey != null) {
            return fromKey;
        }
        return null;
    }

    private static boolean isDerivedSalaryRow(String rowKey) {
        return rowKey != null && (rowKey.startsWith("admin-wynagrodzenia-pracownik-")
                || rowKey.startsWith("promocja-wynagrodzenia-pracownik-")
                || rowKey.startsWith("pensja-pozostala-")
                || rowKey.startsWith("pensja-admin-")
                || rowKey.startsWith("pensja-promocja-"));
    }

    private static Long employeeIdFromDerivedKey(String key) {
        if (key == null) {
            return null;
        }
        String id = null;
        if (key.startsWith("employee-")) {
            id = key.substring("employee-".length());
        } else if (key.startsWith("admin-wynagrodzenia-pracownik-")) {
            id = key.substring("admin-wynagrodzenia-pracownik-".length());
        } else if (key.startsWith("promocja-wynagrodzenia-pracownik-")) {
            id = key.substring("promocja-wynagrodzenia-pracownik-".length());
        }
        if (id != null && !id.isEmpty() && id.chars().allMatch(Character::isDigit)) {
            return Long.valueOf(id);
        }
        return null;
    }

    private static Long employeeIdFromRowKey(String rowKey) {
        if (rowKey == null || !rowKey.matches("employee-\\d+")) {
            return null;
        }
        return Long.valueOf(rowKey.substring("employee-".length()));
    }

    private static final class AdminSalaryMove {
        private final Map<String, BigDecimal> byGrant = new LinkedHashMap<>();
        private BigDecimal total = BigDecimal.ZERO;

        private void add(String grantName, BigDecimal amount) {
            byGrant.merge(grantName, amount, BigDecimal::add);
            total = total.add(amount);
        }
    }

    private static Map<String, BigDecimal> coverageForRefs(List<String> refs,
                                                            Map<String, Map<String, BigDecimal>> bySource) {
        Map<String, BigDecimal> extra = new LinkedHashMap<>();
        for (String ref : refs) {
            Map<String, BigDecimal> amounts = bySource.get(ref);
            if (amounts != null) {
                mergeGrantAmounts(extra, amounts);
            }
        }
        return extra;
    }

    private static List<String> sourceRefsForRow(BudgetItemRowDto row) {
        List<String> refs = new ArrayList<>();
        String rowKey = row.getRowKey();
        if (rowKey != null && rowKey.startsWith("employee-")) {
            refs.add("EMPLOYEE|" + rowKey.substring("employee-".length()));
        }
        if (rowKey != null && !rowKey.isBlank()) {
            refs.add("DASHBOARD_ROW|" + rowKey);
        }
        return refs;
    }

    private static List<String> sourceRefsForAllocation(CostAllocationDto allocation, String parentRowKey) {
        List<String> refs = new ArrayList<>();
        String kind = allocation.getAmountEditKind();
        String ids = allocation.getAmountEditIds();
        if ("allocation".equals(kind) && singleId(ids)) {
            refs.add("COST_ALLOCATION|" + ids);
        }
        if ("travel".equals(kind) && ids != null) {
            for (String id : ids.split(",")) {
                if (singleId(id.trim())) {
                    refs.add("TRAVEL_LINE|" + id.trim());
                }
            }
        }
        if ("publication".equals(kind) && singleId(ids)) {
            refs.add("PUBLICATION|" + ids);
        }
        if ("event".equals(kind) && singleId(ids)) {
            refs.add("PLANNED_EVENT|" + ids);
        }
        if (parentRowKey != null && !parentRowKey.isBlank()
                && allocation.getItemName() != null && !allocation.getItemName().isBlank()) {
            refs.add("DASHBOARD_ROW|" + parentRowKey + "/" + coverageSlug(allocation.getItemName()));
        }
        return refs;
    }

    private static String coverageSlug(String name) {
        return name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9ąćęłńóśźż]+", "-");
    }

    private List<BudgetItemRowDto> buildLabeledCategorySubcategoryRows(List<CostAllocation> allAllocations,
                                                                          String categoryCode,
                                                                          String categoryLabel,
                                                                          List<LabeledSubcategory> subcategories,
                                                                          List<String> grantNames,
                                                                          Map<Long, String> grantNameByProjectId,
                                                                          BigDecimal amountScale) {
        List<CostAllocation> categoryAllocations = allAllocations.stream()
                .filter(a -> a.getCategory() != null && categoryCode.equals(a.getCategory().getCode()))
                .toList();

        List<BudgetItemRowDto> children = new ArrayList<>();
        for (LabeledSubcategory subcategory : subcategories) {
            List<CostAllocation> items = categoryAllocations.stream()
                    .filter(a -> labelMatches(a.getLabel(), subcategory.label()))
                    .toList();
            if (items.isEmpty()) {
                continue;
            }
            children.add(buildLabeledSubcategoryRow(
                    subcategory.rowKey(), subcategory.label(), categoryLabel, items,
                    grantNames, grantNameByProjectId, amountScale));
        }
        return children;
    }

    /** Roczna kwota w widoku miesiąca. Suma dwunastu miesięcy jest równa kwocie rocznej. */
    private static BigDecimal amountForPeriod(BigDecimal annual, Integer month) {
        if (annual == null || annual.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (month == null) {
            return annual;
        }
        return MonthlySplit.shareForMonth(annual, month);
    }

    private BudgetItemRowDto buildLabeledSubcategoryRow(String rowKey,
                                                          String label,
                                                          String categoryLabel,
                                                          List<CostAllocation> items,
                                                          List<String> grantNames,
                                                          Map<Long, String> grantNameByProjectId,
                                                          BigDecimal amountScale) {
        if (items.isEmpty()) {
            BudgetItemRowDto row = new BudgetItemRowDto();
            row.setRowKey(rowKey);
            row.setItemName(label);
            row.setCategory(categoryLabel);
            row.setExpandable(false);
            row.setTotalCost(BigDecimal.ZERO);
            row.setCoverageByGrant(emptyGrantMap(grantNames));
            row.setOverallCoverage(BigDecimal.ZERO);
            row.setBalance(BigDecimal.ZERO);
            return row;
        }
        return buildAdminGroupedRow(rowKey, label, items, grantNames, grantNameByProjectId,
                BudgetMatrixService::allocationDisplayName, amountScale);
    }

    private static boolean labelMatches(String allocationLabel, String expectedLabel) {
        if (allocationLabel == null || expectedLabel == null) {
            return false;
        }
        return allocationLabel.equalsIgnoreCase(expectedLabel);
    }

    private List<BudgetItemRowDto> buildAdminSubcategoryRows(List<CostAllocation> allAllocations,
                                                               List<String> grantNames,
                                                               Map<Long, String> grantNameByProjectId,
                                                               BigDecimal amountScale) {
        List<CostAllocation> adminAllocations = allAllocations.stream()
                .filter(this::isAdminAllocation)
                .toList();

        List<BudgetItemRowDto> groups = new ArrayList<>();
        groups.add(buildAdminGroupRow(
                ADMIN_BIURO_KEY, "Biuro", ADMIN_GROUP_BIURO, adminAllocations, grantNames, grantNameByProjectId, amountScale));
        groups.add(buildAdminGroupRow(
                ADMIN_POZOSTALE_KEY, "Pozostałe", ADMIN_GROUP_POZOSTALE, adminAllocations, grantNames, grantNameByProjectId, amountScale));
        groups.add(buildAdminPayrollGroupRow(adminAllocations, grantNames, grantNameByProjectId, amountScale));
        return groups;
    }

    private BudgetItemRowDto buildAdminGroupRow(String rowKey,
                                                  String label,
                                                  String adminGroup,
                                                  List<CostAllocation> adminAllocations,
                                                  List<String> grantNames,
                                                  Map<Long, String> grantNameByProjectId,
                                                  BigDecimal amountScale) {
        List<CostAllocation> groupItems = adminAllocations.stream()
                .filter(a -> adminGroup.equals(a.getAdminGroup())
                        || (ADMIN_GROUP_POZOSTALE.equals(adminGroup) && a.getAdminGroup() == null))
                .toList();
        return buildAdminGroupedRow(rowKey, label, groupItems, grantNames, grantNameByProjectId,
                allocation -> allocation.getLabel() != null ? allocation.getLabel() : label, amountScale);
    }

    private BudgetItemRowDto buildAdminPayrollGroupRow(List<CostAllocation> adminAllocations,
                                                         List<String> grantNames,
                                                         Map<Long, String> grantNameByProjectId,
                                                         BigDecimal amountScale) {
        List<CostAllocation> payrollItems = adminAllocations.stream()
                .filter(a -> ADMIN_GROUP_WYNAGRODZENIA.equals(a.getAdminGroup()))
                .toList();
        return buildAdminGroupedRow(ADMIN_WYNAGRODZENIA_KEY, "Wynagrodzenia", payrollItems, grantNames, grantNameByProjectId,
                allocation -> {
                    if (allocation.getEmployee() != null
                            && !"system@ngo.pl".equals(allocation.getEmployee().getEmail())) {
                        return employeePayrollLabel(allocation.getEmployee());
                    }
                    return allocationDisplayName(allocation);
                }, amountScale);
    }

    private BudgetItemRowDto buildAdminGroupedRow(String rowKey,
                                                    String label,
                                                    List<CostAllocation> groupItems,
                                                    List<String> grantNames,
                                                    Map<Long, String> grantNameByProjectId,
                                                    Function<CostAllocation, String> lineKeyExtractor,
                                                    BigDecimal amountScale) {
        Map<String, List<CostAllocation>> byLine = groupItems.stream()
                .collect(Collectors.groupingBy(lineKeyExtractor, LinkedHashMap::new, Collectors.toList()));

        List<CostAllocationDto> allocations = new ArrayList<>();

        for (Map.Entry<String, List<CostAllocation>> entry : byLine.entrySet()) {
            String lineLabel = entry.getKey();
            List<CostAllocation> parts = entry.getValue();
            BigDecimal lineTotal = BigDecimal.ZERO;
            Map<String, BigDecimal> lineByGrant = emptyGrantMap(grantNames);

            for (CostAllocation part : parts) {
                BigDecimal amount = scaleAmount(part.getAmount(), amountScale);
                lineTotal = lineTotal.add(amount);
                mergeGrantAmounts(lineByGrant, scaledAmountByGrantForAllocation(part, grantNames, grantNameByProjectId, amountScale));
            }

            CostAllocationDto dto = new CostAllocationDto();
            dto.setItemName(lineLabel);
            dto.setAmount(lineTotal);
            dto.setAmountByGrant(lineByGrant);
            dto.setAmountEditKind("allocation");
            dto.setAmountEditIds(parts.stream()
                    .map(CostAllocation::getId)
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
            allocations.add(dto);
        }

        BudgetItemRowDto row = finalizeAllocationRow(rowKey, label, ADMIN_LABEL, allocations, grantNames);
        row.setExpandable(true);
        return row;
    }

    private void applyAllocations(BudgetItemRowDto row,
                                   List<CostAllocationDto> allocations,
                                   List<String> grantNames) {
        BudgetItemRowDto finalized = finalizeAllocationRow(
                row.getRowKey(), row.getItemName(), row.getCategory(), allocations, grantNames);
        row.setExpandable(finalized.isExpandable());
        row.setTotalCost(finalized.getTotalCost());
        row.setCoverageByGrant(finalized.getCoverageByGrant());
        row.setOverallCoverage(finalized.getOverallCoverage());
        row.setBalance(finalized.getBalance());
        row.setAllocations(finalized.getAllocations());
        row.setChildren(List.of());
    }

    private BudgetItemRowDto finalizeAllocationRow(String rowKey,
                                                       String label,
                                                       String categoryLabel,
                                                       List<CostAllocationDto> allocations,
                                                       List<String> grantNames) {
        BigDecimal totalPlanned = BigDecimal.ZERO;
        Map<String, BigDecimal> byGrant = emptyGrantMap(grantNames);

        for (CostAllocationDto dto : allocations) {
            BigDecimal amount = dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO;
            totalPlanned = totalPlanned.add(amount);
            mergeGrantAmounts(byGrant, dto.getAmountByGrant());
        }

        for (CostAllocationDto dto : allocations) {
            dto.setCategory(categoryLabel);
            dto.setBalance(dto.getAmount().subtract(sumGrantMap(dto.getAmountByGrant())));
            if (totalPlanned.compareTo(BigDecimal.ZERO) > 0) {
                dto.setPercent(dto.getAmount().multiply(BigDecimal.valueOf(100))
                        .divide(totalPlanned, 1, RoundingMode.HALF_UP));
            }
        }

        BudgetItemRowDto row = new BudgetItemRowDto();
        row.setRowKey(rowKey);
        row.setItemName(label);
        row.setCategory(categoryLabel);
        row.setExpandable(!allocations.isEmpty());
        row.setTotalCost(totalPlanned);
        row.setCoverageByGrant(byGrant);
        row.setOverallCoverage(sumGrantMap(byGrant));
        row.setBalance(totalPlanned.subtract(sumGrantMap(byGrant)));
        row.setAllocations(allocations);
        return row;
    }

    private List<CostAllocationDto> toCostAllocationDtos(List<CostAllocation> allocations,
                                                          List<String> grantNames,
                                                          Map<Long, String> grantNameByProjectId,
                                                          boolean employeeView,
                                                          BigDecimal amountScale) {
        List<CostAllocationDto> result = new ArrayList<>();
        for (CostAllocation allocation : allocations) {
            CostAllocationDto dto = new CostAllocationDto();
            if (employeeView) {
                dto.setItemName(categoryName(allocation.getCategory()));
            } else {
                dto.setItemName(allocationDisplayName(allocation));
            }
            dto.setCategory(categoryName(allocation.getCategory()));
            BigDecimal amount = scaleAmount(allocation.getAmount(), amountScale);
            dto.setAmount(amount);
            dto.setPercent(allocation.getPercentage() != null
                    ? allocation.getPercentage()
                    : BigDecimal.ZERO);
            dto.setAmountByGrant(scaledAmountByGrantForAllocation(allocation, grantNames, grantNameByProjectId, amountScale));
            dto.setBalance(amount.subtract(sumGrantMap(dto.getAmountByGrant())));
            dto.setAmountEditKind("allocation");
            dto.setAmountEditIds(String.valueOf(allocation.getId()));
            result.add(dto);
        }
        return result;
    }

    private static Map<String, BigDecimal> scaledAmountByGrantForAllocation(CostAllocation allocation,
                                                                             List<String> grantNames,
                                                                             Map<Long, String> grantNameByProjectId,
                                                                             BigDecimal amountScale) {
        Map<String, BigDecimal> byGrant = amountByGrantForAllocation(allocation, grantNames, grantNameByProjectId);
        if (amountScale.compareTo(BigDecimal.ONE) == 0) {
            return byGrant;
        }
        Map<String, BigDecimal> scaled = emptyGrantMap(grantNames);
        for (Map.Entry<String, BigDecimal> entry : byGrant.entrySet()) {
            scaled.put(entry.getKey(), scaleAmount(entry.getValue(), amountScale));
        }
        return scaled;
    }

    private static Map<String, BigDecimal> amountByGrantForAllocation(CostAllocation allocation,
                                                                       List<String> grantNames,
                                                                       Map<Long, String> grantNameByProjectId) {
        return emptyGrantMap(grantNames);
    }

    private static String allocationDisplayName(CostAllocation allocation) {
        if (allocation.getLabel() != null && !allocation.getLabel().isBlank()) {
            return allocation.getLabel();
        }
        if (allocation.getProject() != null) {
            return allocation.getProject().getName();
        }
        if (allocation.getEmployee() != null && !"system@ngo.pl".equals(allocation.getEmployee().getEmail())) {
            return employeePayrollLabel(allocation.getEmployee());
        }
        return "Organizacja (koszt centralny)";
    }

    private static String categoryName(BudgetItemTemplate category) {
        if (category == null || category.getName() == null) {
            return "";
        }
        return category.getName();
    }

    private static void mergeGrantAmounts(Map<String, BigDecimal> target, Map<String, BigDecimal> source) {
        for (Map.Entry<String, BigDecimal> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
        }
    }

    private static BigDecimal sumGrantMap(Map<String, BigDecimal> byGrant) {
        return byGrant.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String employeePayrollLabel(Employee employee) {
        String name = employee.getFirstName() + " " + employee.getLastName();
        if (employee.getPosition() != null && !employee.getPosition().isBlank()) {
            return name + " — " + employee.getPosition();
        }
        return name;
    }

    private List<CostAllocationDto> buildPublicationAllocations(List<String> grantNames, Integer month) {
        List<CostAllocationDto> allocations = new ArrayList<>();
        for (Publication publication : publicationRepository.findByActiveTrueOrderByTitleAsc()) {
            BigDecimal planned = amountForPeriod(publication.getPlannedCost(), month);
            Map<String, BigDecimal> byGrant = emptyGrantMap(grantNames);

            CostAllocationDto dto = new CostAllocationDto();
            dto.setItemName(publication.getTitle());
            dto.setAmount(planned);
            dto.setAmountByGrant(byGrant);
            dto.setAmountEditKind("publication");
            dto.setAmountEditIds(String.valueOf(publication.getId()));
            allocations.add(dto);
        }
        return allocations;
    }

    private List<BudgetItemRowDto> buildTravelSubcategoryRows(List<String> grantNames, Integer month) {
        List<BudgetItemRowDto> subcategories = new ArrayList<>();
        subcategories.add(buildTravelScopeRow(
                TravelBudgetLine.TravelScope.DOMESTIC, TRAVEL_DOMESTIC_KEY, "Krajowe", grantNames, month));
        subcategories.add(buildTravelScopeRow(
                TravelBudgetLine.TravelScope.INTERNATIONAL, TRAVEL_INTERNATIONAL_KEY, "Zagraniczne", grantNames, month));
        return subcategories;
    }

    private BudgetItemRowDto buildTravelScopeRow(TravelBudgetLine.TravelScope scope,
                                                   String rowKey,
                                                   String label,
                                                   List<String> grantNames,
                                                   Integer month) {
        List<TravelBudgetLine> lines = travelBudgetLineRepository.findByActiveTrueOrderByScopeAscExpenseTypeAsc()
                .stream()
                .filter(line -> line.getScope() == scope)
                .toList();

        Map<String, List<TravelBudgetLine>> byExpense = lines.stream()
                .collect(Collectors.groupingBy(
                        line -> travelExpenseTypeLabel(line.getExpenseType()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<CostAllocationDto> allocations = new ArrayList<>();
        for (Map.Entry<String, List<TravelBudgetLine>> entry : byExpense.entrySet()) {
            BigDecimal lineTotal = BigDecimal.ZERO;
            Map<String, BigDecimal> lineByGrant = emptyGrantMap(grantNames);
            for (TravelBudgetLine line : entry.getValue()) {
                BigDecimal planned = amountForPeriod(line.getPlannedCost(), month);
                lineTotal = lineTotal.add(planned);
            }
            CostAllocationDto dto = new CostAllocationDto();
            dto.setItemName(entry.getKey());
            dto.setAmount(lineTotal);
            dto.setAmountByGrant(lineByGrant);
            dto.setAmountEditKind("travel");
            dto.setAmountEditIds(entry.getValue().stream()
                    .map(TravelBudgetLine::getId)
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
            allocations.add(dto);
        }

        return finalizeAllocationRow(rowKey, label, TRAVEL_LABEL, allocations, grantNames);
    }

    private static String travelExpenseTypeLabel(TravelBudgetLine.ExpenseType type) {
        return switch (type) {
            case TRANSPORT -> "Transport";
            case HOTEL -> "Hotel";
            case MEALS -> "Wyżywienie/Dieta";
        };
    }

    private List<CostAllocationDto> buildPlannedEventAllocations(List<String> grantNames, int fiscalYear, Integer month) {
        List<CostAllocationDto> allocations = new ArrayList<>();
        for (PlannedEvent event : plannedEventRepository.findByActiveTrueOrderByEventDateAscTitleAsc()) {
            if (month != null) {
                if (event.getEventDate() == null
                        || event.getEventDate().getYear() != fiscalYear
                        || event.getEventDate().getMonthValue() != month) {
                    continue;
                }
            }
            BigDecimal planned = event.getPlannedCost() != null
                    ? event.getPlannedCost()
                    : BigDecimal.ZERO;
            Map<String, BigDecimal> byGrant = emptyGrantMap(grantNames);

            String itemName = event.getTitle();
            if (event.getEventDate() != null) {
                itemName = itemName + " (" + event.getEventDate() + ")";
            }

            CostAllocationDto dto = new CostAllocationDto();
            dto.setItemName(itemName);
            dto.setAmount(planned);
            dto.setAmountByGrant(byGrant);
            dto.setAmountEditKind("event");
            dto.setAmountEditIds(String.valueOf(event.getId()));
            allocations.add(dto);
        }
        return allocations;
    }

    private List<BudgetItemRowDto> buildEventSubcategoryRows(List<CostAllocation> allAllocations,
                                                               List<String> grantNames,
                                                               Map<Long, String> grantNameByProjectId,
                                                               BigDecimal amountScale,
                                                               int fiscalYear,
                                                               Integer month) {
        List<BudgetItemRowDto> children = new ArrayList<>();
        LinkedHashSet<String> usedLabels = new LinkedHashSet<>();
        Map<String, List<CostAllocation>> byLabel = allAllocations.stream()
                .filter(a -> a.getCategory() != null && EVENTS_CODE.equals(a.getCategory().getCode()))
                .collect(Collectors.groupingBy(
                        BudgetMatrixService::allocationDisplayName,
                        LinkedHashMap::new,
                        Collectors.toList()));
        int index = 0;
        for (Map.Entry<String, List<CostAllocation>> entry : byLabel.entrySet()) {
            usedLabels.add(entry.getKey().toLowerCase());
            children.add(buildAdminGroupedRow(
                    "wydarzenie-plan-" + index,
                    entry.getKey(),
                    entry.getValue(),
                    grantNames,
                    grantNameByProjectId,
                    BudgetMatrixService::allocationDisplayName,
                    amountScale));
            index += 1;
        }
        for (CostAllocationDto allocation : buildPlannedEventAllocations(grantNames, fiscalYear, month)) {
            String name = allocation.getItemName();
            if (name != null && usedLabels.contains(name.toLowerCase())) {
                continue;
            }
            BudgetItemRowDto child = finalizeAllocationRow(
                    "planned-event-" + allocation.getAmountEditIds(),
                    name,
                    EVENTS_LABEL,
                    List.of(allocation),
                    grantNames);
            child.setAllocations(List.of());
            child.setExpandable(false);
            children.add(child);
            index += 1;
        }
        return children;
    }

    private static Map<String, BigDecimal> emptyGrantMap(List<String> grantNames) {
        Map<String, BigDecimal> byGrant = new LinkedHashMap<>();
        for (String grantName : grantNames) {
            byGrant.put(grantName, BigDecimal.ZERO);
        }
        return byGrant;
    }

    private static BigDecimal expenditureAmount(Expenditure expenditure) {
        if (expenditure.getGrossAmount() != null) {
            return expenditure.getGrossAmount();
        }
        return expenditure.getNetAmount() != null ? expenditure.getNetAmount() : BigDecimal.ZERO;
    }

    private boolean isAdminAllocation(CostAllocation allocation) {
        if (allocation.getCategory() == null) {
            return allocation.getAdminGroup() != null;
        }
        BudgetItemTemplate category = allocation.getCategory();
        if (ADMIN_CODE.equals(category.getCode())) {
            return true;
        }
        if (ADMIN_LABEL.equals(category.getName())) {
            return true;
        }
        return category.getName() != null
                && category.getName().toLowerCase().contains("administracyj");
    }

    private Optional<BudgetItemRowDto> buildCategoryRow(String categoryCode,
                                                        String key,
                                                        Set<String> handledCategoryRowKeys,
                                                        List<String> grantNames,
                                                        Map<Long, String> grantNameByProjectId,
                                                        Map<String, BigDecimal> plannedByItem,
                                                        Map<String, Map<String, BigDecimal>> plannedByItemAndGrant,
                                                        Map<Long, List<CostAllocation>> allocationsByEmployee,
                                                        List<CostAllocation> costAllocations,
                                                        BigDecimal amountScale,
                                                        List<Grant> grants,
                                                        int fiscalYear,
                                                        Integer month) {
        String rowKey = categoryRowKey(categoryCode);
        if (handledCategoryRowKeys.contains(rowKey)) {
            return Optional.empty();
        }

        BudgetItemRowDto row = switch (categoryCode) {
            case PERSONNEL_CODE -> {
                BudgetItemRowDto personnelRow = buildExpandableRow(
                        key, PERSONNEL_ROW_KEY, PERSONNEL_LABEL, grantNames, plannedByItem, plannedByItemAndGrant);
                personnelRow.setChildren(buildEmployeeRows(
                        grants, fiscalYear, grantNames, grantNameByProjectId, allocationsByEmployee, amountScale, month));
                aggregateFromChildren(personnelRow, grantNames);
                yield personnelRow;
            }
            case PUBLICATIONS_CODE -> {
                BudgetItemRowDto publicationsRow = buildExpandableRow(
                        key, PUBLICATIONS_ROW_KEY, PUBLICATIONS_LABEL, grantNames, plannedByItem, plannedByItemAndGrant);
                applyAllocations(publicationsRow, buildPublicationAllocations(grantNames, month), grantNames);
                yield publicationsRow;
            }
            case EVENTS_CODE -> {
                BudgetItemRowDto eventsRow = buildExpandableRow(
                        key, EVENTS_ROW_KEY, EVENTS_LABEL, grantNames, plannedByItem, plannedByItemAndGrant);
                eventsRow.setChildren(buildEventSubcategoryRows(
                        costAllocations, grantNames, grantNameByProjectId, amountScale, fiscalYear, month));
                aggregateFromChildren(eventsRow, grantNames);
                eventsRow.setExpandable(true);
                yield eventsRow;
            }
            case TRAVEL_CODE -> {
                BudgetItemRowDto travelRow = buildExpandableRow(
                        key, TRAVEL_ROW_KEY, TRAVEL_LABEL, grantNames, plannedByItem, plannedByItemAndGrant);
                travelRow.setChildren(buildTravelSubcategoryRows(grantNames, month));
                aggregateFromChildren(travelRow, grantNames);
                yield travelRow;
            }
            case ADMIN_CODE -> {
                BudgetItemRowDto adminRow = buildExpandableRow(
                        key, ADMIN_ROW_KEY, ADMIN_LABEL, grantNames, plannedByItem, plannedByItemAndGrant);
                adminRow.setChildren(buildAdminSubcategoryRows(costAllocations, grantNames, grantNameByProjectId, amountScale));
                aggregateFromChildren(adminRow, grantNames);
                adminRow.setExpandable(true);
                yield adminRow;
            }
            case PROMOTION_CODE -> {
                BudgetItemRowDto promotionRow = buildExpandableRow(
                        key, PROMOTION_ROW_KEY, PROMOTION_LABEL, grantNames, plannedByItem, plannedByItemAndGrant);
                promotionRow.setChildren(buildLabeledCategorySubcategoryRows(
                        costAllocations, PROMOTION_CODE, PROMOTION_LABEL, PROMOTION_SUBCATEGORIES,
                        grantNames, grantNameByProjectId, amountScale));
                aggregateFromChildren(promotionRow, grantNames);
                promotionRow.setExpandable(promotionRow.getChildren() != null && !promotionRow.getChildren().isEmpty());
                yield promotionRow;
            }
            case EQUIPMENT_CODE -> {
                BudgetItemRowDto equipmentRow = buildExpandableRow(
                        key, EQUIPMENT_ROW_KEY, EQUIPMENT_LABEL, grantNames, plannedByItem, plannedByItemAndGrant);
                equipmentRow.setChildren(buildLabeledCategorySubcategoryRows(
                        costAllocations, EQUIPMENT_CODE, EQUIPMENT_LABEL, EQUIPMENT_SUBCATEGORIES,
                        grantNames, grantNameByProjectId, amountScale));
                aggregateFromChildren(equipmentRow, grantNames);
                equipmentRow.setExpandable(equipmentRow.getChildren() != null && !equipmentRow.getChildren().isEmpty());
                yield equipmentRow;
            }
            default -> {
                BudgetItemRowDto customRow = buildExpandableRow(
                        key, rowKey, key, grantNames, plannedByItem, plannedByItemAndGrant);
                customRow.setChildren(List.of());
                customRow.setExpandable(true);
                aggregateFromChildren(customRow, grantNames);
                yield customRow;
            }
        };

        Integer evenMonth = month != null
                && (PERSONNEL_CODE.equals(categoryCode)
                || ADMIN_CODE.equals(categoryCode)
                || PROMOTION_CODE.equals(categoryCode))
                ? month : null;
        applyStoredSubcategoriesTree(row, grantNames, evenMonth);
        Map<Long, Map<String, AdminSalaryMove>> salaryMoves = salaryRelocations(grants, fiscalYear, month);
        applyPlanCoverage(row, planCoverageBySource(grants, fiscalYear, month), grantNames);
        if (PERSONNEL_CODE.equals(categoryCode)) {
            splitAdminSalaryFromPersonnel(row, salaryMoves, grantNames, month);
        }
        if (ADMIN_CODE.equals(categoryCode)) {
            addSalaryPositions(row, salaryMoves, ADMIN_CODE, ADMIN_WYNAGRODZENIA_KEY,
                    "admin-wynagrodzenia-pracownik-", grantNames);
        }
        if (PROMOTION_CODE.equals(categoryCode)) {
            addSalaryPositions(row, salaryMoves, PROMOTION_CODE, PROMO_PAYROLL_KEY,
                    "promocja-wynagrodzenia-pracownik-", grantNames);
        }
        handledCategoryRowKeys.add(rowKey);
        if (key != null) {
            handledCategoryRowKeys.add(key);
        }
        return Optional.of(row);
    }

    private void applyStoredSubcategoriesTree(BudgetItemRowDto row, List<String> grantNames, Integer evenMonth) {
        if (row == null) {
            return;
        }
        applyStoredSubcategories(row, grantNames, evenMonth);
        List<BudgetItemRowDto> children = row.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }
        for (BudgetItemRowDto child : children) {
            applyStoredSubcategoriesTree(child, grantNames, evenMonth);
        }
    }

    private void applyStoredSubcategories(BudgetItemRowDto row, List<String> grantNames, Integer evenMonth) {
        if (row == null || row.getRowKey() == null || row.getRowKey().isBlank()) {
            return;
        }
        List<BudgetSubcategoryOrder> stored = budgetSubcategoryOrderRepository
                .findByParentRowKeyOrderByDisplayOrderAsc(row.getRowKey());
        Set<String> hidden = stored.stream()
                .filter(BudgetSubcategoryOrder::isHidden)
                .map(BudgetSubcategoryOrder::getRowKey)
                .collect(Collectors.toSet());
        List<BudgetItemRowDto> children = row.getChildren() != null
                ? new ArrayList<>(row.getChildren())
                : new ArrayList<>();
        children.removeIf(child -> child.getRowKey() != null && hidden.contains(child.getRowKey()));
        Set<String> existing = children.stream()
                .map(BudgetItemRowDto::getRowKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String categoryLabel = row.getItemName();
        for (BudgetSubcategoryOrder entry : stored) {
            if (entry.isHidden() || entry.getName() == null || entry.getName().isBlank()) {
                continue;
            }
            if (existing.contains(entry.getRowKey())) {
                children.stream()
                        .filter(child -> entry.getRowKey().equals(child.getRowKey()))
                        .findFirst()
                        .ifPresent(child -> applySubcategoryOverlay(child, entry, evenMonth));
                continue;
            }
            BudgetItemRowDto customChild = emptySubcategoryRow(
                    entry.getRowKey(), entry.getName(), categoryLabel, grantNames,
                    monthlyPlanned(entry.getPlannedAmount(), evenMonth));
            customChild.setExpandable(true);
            children.add(customChild);
            existing.add(entry.getRowKey());
        }
        for (BudgetSubcategoryOrder entry : stored) {
            if (entry.isHidden() || entry.getName() != null) {
                continue;
            }
            children.stream()
                    .filter(child -> entry.getRowKey().equals(child.getRowKey()))
                    .findFirst()
                    .ifPresent(child -> applySubcategoryOverlay(child, entry, evenMonth));
        }
        if (children.isEmpty() && hidden.isEmpty()) {
            return;
        }
        row.setChildren(sortSubcategories(row.getRowKey(), children));
        row.setExpandable(true);
        aggregateFromChildren(row, grantNames);
    }

    private void applySubcategoryOverlay(BudgetItemRowDto child, BudgetSubcategoryOrder entry, Integer evenMonth) {
        child.setExpandable(!entry.isHidden());
        BigDecimal planned = monthlyPlanned(entry.getPlannedAmount(), evenMonth);
        if (planned != null) {
            child.setTotalCost(planned);
            BigDecimal coverage = sumGrantMap(child.getCoverageByGrant());
            child.setBalance(planned.subtract(coverage));
        }
    }

    private static BigDecimal monthlyPlanned(BigDecimal plannedAmount, Integer evenMonth) {
        if (plannedAmount == null || evenMonth == null) {
            return plannedAmount;
        }
        return MonthlySplit.shareForMonth(plannedAmount, evenMonth);
    }

    private BudgetItemRowDto emptySubcategoryRow(String rowKey,
                                                   String label,
                                                   String categoryLabel,
                                                   List<String> grantNames,
                                                   BigDecimal plannedAmount) {
        BudgetItemRowDto child = finalizeAllocationRow(rowKey, label, categoryLabel, List.of(), grantNames);
        if (plannedAmount != null) {
            child.setTotalCost(plannedAmount);
            child.setBalance(plannedAmount.subtract(sumGrantMap(child.getCoverageByGrant())));
        }
        child.setExpandable(true);
        return child;
    }

    private static String findKeyForCategory(BudgetItemTemplate category, Set<String> itemOrder) {
        for (String key : itemOrder) {
            if (keyMatchesCategory(key, category)) {
                return key;
            }
        }
        return null;
    }

    private static boolean keyMatchesCategory(String key, BudgetItemTemplate category) {
        String categoryCode = category.getCode();
        if (categoryCode == null) {
            return false;
        }
        if (keyMatchesCategoryCode(key, categoryCode)) {
            return true;
        }
        return category.getName() != null && category.getName().equals(key);
    }

    private static boolean keyMatchesCategoryCode(String key, String categoryCode) {
        return switch (categoryCode) {
            case PERSONNEL_CODE -> isPersonnelKey(key);
            case PUBLICATIONS_CODE -> isPublicationsKey(key);
            case EVENTS_CODE -> isEventsKey(key);
            case TRAVEL_CODE -> isTravelKey(key);
            case ADMIN_CODE -> isAdminKey(key);
            case PROMOTION_CODE -> isPromotionKey(key);
            case EQUIPMENT_CODE -> isEquipmentKey(key);
            default -> categoryCode.equals(key);
        };
    }

    private static String categoryRowKey(String categoryCode) {
        return switch (categoryCode) {
            case PERSONNEL_CODE -> PERSONNEL_ROW_KEY;
            case PUBLICATIONS_CODE -> PUBLICATIONS_ROW_KEY;
            case EVENTS_CODE -> EVENTS_ROW_KEY;
            case TRAVEL_CODE -> TRAVEL_ROW_KEY;
            case ADMIN_CODE -> ADMIN_ROW_KEY;
            case PROMOTION_CODE -> PROMOTION_ROW_KEY;
            case EQUIPMENT_CODE -> EQUIPMENT_ROW_KEY;
            default -> categoryCode;
        };
    }

    private static boolean isKeyHandledByCategory(String key, Set<String> handledCategoryRowKeys) {
        if (isPersonnelKey(key)) {
            return handledCategoryRowKeys.contains(PERSONNEL_ROW_KEY);
        }
        if (isPublicationsKey(key)) {
            return handledCategoryRowKeys.contains(PUBLICATIONS_ROW_KEY);
        }
        if (isEventsKey(key)) {
            return handledCategoryRowKeys.contains(EVENTS_ROW_KEY);
        }
        if (isTravelKey(key)) {
            return handledCategoryRowKeys.contains(TRAVEL_ROW_KEY);
        }
        if (isAdminKey(key)) {
            return handledCategoryRowKeys.contains(ADMIN_ROW_KEY);
        }
        if (isPromotionKey(key)) {
            return handledCategoryRowKeys.contains(PROMOTION_ROW_KEY);
        }
        if (isEquipmentKey(key)) {
            return handledCategoryRowKeys.contains(EQUIPMENT_ROW_KEY);
        }
        return handledCategoryRowKeys.contains(key);
    }

    private static boolean isPersonnelKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase();
        return PERSONNEL_LEGACY_NAME.equals(key)
                || PERSONNEL_LABEL.equals(key)
                || PERSONNEL_CODE.equals(key)
                || normalized.contains("wynagrodzen");
    }

    private static boolean isPublicationsKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase();
        return PUBLICATIONS_LEGACY_NAME.equals(key)
                || PUBLICATIONS_LABEL.equals(key)
                || PUBLICATIONS_CODE.equals(key)
                || normalized.contains("publikac")
                || normalized.contains("materiały");
    }

    private static boolean isEventsKey(String key) {
        return EVENTS_LABEL.equals(key)
                || EVENTS_CODE.equals(key);
    }

    private static boolean isTravelKey(String key) {
        return TRAVEL_LEGACY_NAME.equals(key)
                || TRAVEL_LABEL.equals(key)
                || TRAVEL_CODE.equals(key);
    }

    private static boolean isAdminKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase();
        return ADMIN_LABEL.equals(key)
                || ADMIN_CODE.equals(key)
                || normalized.contains("administracyj");
    }

    private static boolean isPromotionKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase();
        return PROMOTION_LABEL.equals(key)
                || PROMOTION_CODE.equals(key)
                || normalized.contains("promocja");
    }

    private static boolean isEquipmentKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase();
        return EQUIPMENT_LABEL.equals(key)
                || EQUIPMENT_CODE.equals(key)
                || normalized.contains("sprzęt")
                || normalized.contains("sprzet");
    }

    private static boolean isPersonnelItem(GrantBudgetItem item) {
        if (item == null) {
            return false;
        }
        return PERSONNEL_CODE.equals(item.getCode())
                || PERSONNEL_LEGACY_NAME.equals(item.getName())
                || PERSONNEL_LABEL.equals(item.getName())
                || (item.getName() != null && item.getName().contains("Wynagrodzenia"));
    }

    private static String itemKey(GrantBudgetItem item) {
        if (item.getName() != null && !item.getName().isBlank()) {
            return item.getName();
        }
        if (item.getCode() != null && !item.getCode().isBlank()) {
            return item.getCode();
        }
        return "Pozycja #" + item.getId();
    }

    @Transactional(readOnly = true)
    public List<OrgBudgetLineOptionDto> orgCoverageOptions(int fiscalYear) {
        BudgetDashboardDto dashboard = getBudgetDashboardDataForYear(fiscalYear);
        Map<Long, String> codeByTemplateId = budgetItemTemplateRepository.findAll().stream()
                .collect(Collectors.toMap(BudgetItemTemplate::getId, BudgetItemTemplate::getCode, (left, right) -> left));
        List<BudgetDisplayRowDto> rows = dashboard.getDisplayRows();
        boolean[] hasChildren = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            int depth = rows.get(i).getDepth();
            for (int j = i + 1; j < rows.size(); j++) {
                if (rows.get(j).getDepth() <= depth) {
                    break;
                }
                hasChildren[i] = true;
                break;
            }
        }
        java.util.Set<String> parentSourceRefs = coverageParentSourceRefs(dashboard, fiscalYear);
        List<OrgBudgetLineOptionDto> options = new ArrayList<>();
        java.util.Set<String> seen = new LinkedHashSet<>();
        String categoryCode = null;
        String positionName = null;
        String positionKey = null;
        for (int i = 0; i < rows.size(); i++) {
            BudgetDisplayRowDto row = rows.get(i);
            if (row.getDepth() == 0) {
                categoryCode = categoryCodeForCoverage(row, codeByTemplateId);
                positionName = null;
                positionKey = null;
                continue;
            }
            if (categoryCode == null || categoryCode.isBlank()) {
                continue;
            }
            String name = row.getItemName() != null ? row.getItemName().trim() : "";
            if (name.isBlank() || isDerivedSalaryRow(row.getRowKey())) {
                continue;
            }
            if (row.getDepth() == 1) {
                positionName = name;
                positionKey = row.getRowKey();
            }
            String sourceRef = coverageSourceRef(row, positionKey, name);
            boolean employeeRow = row.getRowKey() != null && row.getRowKey().matches("employee-\\d+");
            if (!employeeRow && (hasChildren[i] || (sourceRef != null && parentSourceRefs.contains(sourceRef)))) {
                continue;
            }
            String label = row.getDepth() > 1 && positionName != null && !positionName.equalsIgnoreCase(name)
                    ? positionName + " / " + name
                    : name;
            if (sourceRef == null || !seen.add(sourceRef)) {
                continue;
            }
            int split = sourceRef.indexOf('|');
            String type = sourceRef.substring(0, split);
            String key = sourceRef.substring(split + 1);
            OrgBudgetLineOptionDto option;
            if ("DASHBOARD_ROW".equals(type)) {
                option = OrgBudgetLineOptionDto.ofKey(type, key, categoryCode, label);
            } else {
                try {
                    option = OrgBudgetLineOptionDto.of(type, Long.valueOf(key), categoryCode, label);
                } catch (NumberFormatException ex) {
                    option = OrgBudgetLineOptionDto.ofKey(type, key, categoryCode, label);
                }
            }
            option.setBalance(row.getBalance() != null ? row.getBalance() : BigDecimal.ZERO);
            options.add(option);
        }
        List<OrgBudgetLineOptionDto> adminStaff = new ArrayList<>();
        for (OrgBudgetLineOptionDto option : options) {
            if (!"EMPLOYEE".equals(option.getOrgSourceType()) || !PERSONNEL_CODE.equals(option.getCategoryCode())) {
                continue;
            }
            BigDecimal balance = option.getBalance() != null ? option.getBalance() : BigDecimal.ZERO;
            for (String destinationCode : List.of(ADMIN_CODE, PROMOTION_CODE)) {
                OrgBudgetLineOptionDto staffOption = OrgBudgetLineOptionDto.of(
                        option.getOrgSourceType(), option.getOrgSourceId(), destinationCode,
                        "Wynagrodzenia / " + option.getLabel());
                staffOption.setBalance(balance);
                adminStaff.add(staffOption);
            }
        }
        options.addAll(adminStaff);
        return options;
    }

    @Transactional(readOnly = true)
    public java.util.Set<String> coverageParentSourceRefs(int fiscalYear) {
        return coverageParentSourceRefs(getBudgetDashboardDataForYear(fiscalYear), fiscalYear);
    }

    private java.util.Set<String> coverageParentSourceRefs(BudgetDashboardDto dashboard, int fiscalYear) {
        java.util.Set<String> parents = new LinkedHashSet<>();
        collectCoverageParents(dashboard.getRows(), parents);
        for (BudgetSubcategoryOrder order : budgetSubcategoryOrderRepository.findAll()) {
            if (order.isHidden() || order.getName() == null || order.getName().isBlank()
                    || order.getParentRowKey() == null || order.getParentRowKey().isBlank()) {
                continue;
            }
            parents.add("DASHBOARD_ROW|" + order.getParentRowKey());
        }
        for (CostAllocation allocation : costAllocationRepository.findAllPlanAllocationsByFiscalYear(fiscalYear)) {
            String groupKey = adminGroupRowKey(allocation.getAdminGroup());
            if (groupKey == null) {
                continue;
            }
            String label = allocation.getLabel();
            if (label == null || label.isBlank() || label.equalsIgnoreCase(adminGroupLabel(allocation.getAdminGroup()))) {
                continue;
            }
            parents.add("DASHBOARD_ROW|" + groupKey);
        }
        return parents;
    }

    private static void collectCoverageParents(List<BudgetItemRowDto> rows, java.util.Set<String> parents) {
        if (rows == null) {
            return;
        }
        for (BudgetItemRowDto row : rows) {
            boolean hasChildRows = row.getChildren() != null && !row.getChildren().isEmpty();
            boolean hasLines = row.getAllocations() != null && !row.getAllocations().isEmpty();
            if ((hasChildRows || hasLines) && row.getRowKey() != null && !row.getRowKey().isBlank()) {
                parents.add("DASHBOARD_ROW|" + row.getRowKey());
            }
            collectCoverageParents(row.getChildren(), parents);
        }
    }

    private static String adminGroupRowKey(String adminGroup) {
        if (ADMIN_GROUP_BIURO.equals(adminGroup)) {
            return ADMIN_BIURO_KEY;
        }
        if (ADMIN_GROUP_POZOSTALE.equals(adminGroup)) {
            return ADMIN_POZOSTALE_KEY;
        }
        if (ADMIN_GROUP_WYNAGRODZENIA.equals(adminGroup)) {
            return ADMIN_WYNAGRODZENIA_KEY;
        }
        return null;
    }

    private static String adminGroupLabel(String adminGroup) {
        if (ADMIN_GROUP_BIURO.equals(adminGroup)) {
            return "Biuro";
        }
        if (ADMIN_GROUP_POZOSTALE.equals(adminGroup)) {
            return "Pozostałe";
        }
        if (ADMIN_GROUP_WYNAGRODZENIA.equals(adminGroup)) {
            return "Wynagrodzenia";
        }
        return "";
    }

    private static String categoryCodeForCoverage(BudgetDisplayRowDto row, Map<Long, String> codeByTemplateId) {
        if (row.getCategoryTemplateId() != null) {
            String code = codeByTemplateId.get(row.getCategoryTemplateId());
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        if (row.getRowKey() == null) {
            return null;
        }
        return switch (row.getRowKey()) {
            case PERSONNEL_ROW_KEY -> PERSONNEL_CODE;
            case PUBLICATIONS_ROW_KEY -> PUBLICATIONS_CODE;
            case EVENTS_ROW_KEY -> EVENTS_CODE;
            case TRAVEL_ROW_KEY -> TRAVEL_CODE;
            case ADMIN_ROW_KEY -> ADMIN_CODE;
            case PROMOTION_ROW_KEY -> PROMOTION_CODE;
            case EQUIPMENT_ROW_KEY -> EQUIPMENT_CODE;
            default -> row.getRowKey();
        };
    }

    private static String coverageSourceRef(BudgetDisplayRowDto row, String positionKey, String name) {
        String rowKey = row.getRowKey();
        if (rowKey != null && rowKey.startsWith("employee-")) {
            return "EMPLOYEE|" + rowKey.substring("employee-".length());
        }
        if (rowKey != null && rowKey.startsWith("planned-event-")) {
            return "PLANNED_EVENT|" + rowKey.substring("planned-event-".length());
        }
        String kind = row.getAmountEditKind();
        String ids = row.getAmountEditIds();
        if ("publication".equals(kind) && singleId(ids)) {
            return "PUBLICATION|" + ids;
        }
        if ("event".equals(kind) && singleId(ids)) {
            return "PLANNED_EVENT|" + ids;
        }
        if ("allocation".equals(kind) && singleId(ids)) {
            return "COST_ALLOCATION|" + ids;
        }
        if (rowKey != null && !rowKey.isBlank()) {
            return "DASHBOARD_ROW|" + rowKey;
        }
        String parent = positionKey != null && !positionKey.isBlank() ? positionKey : "pozycja";
        String child = name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9ąćęłńóśźż]+", "-");
        if ("travel".equals(kind)) {
            return "DASHBOARD_ROW|" + parent + "/" + child;
        }
        return "DASHBOARD_ROW|" + parent + "/" + child;
    }

    private static boolean singleId(String ids) {
        return ids != null && !ids.isBlank() && !ids.contains(",") && ids.chars().allMatch(Character::isDigit);
    }

    private static BigDecimal scaleAmount(BigDecimal amount, BigDecimal scale) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (scale.compareTo(BigDecimal.ONE) == 0) {
            return amount;
        }
        return amount.multiply(scale).setScale(2, RoundingMode.HALF_UP);
    }
}
