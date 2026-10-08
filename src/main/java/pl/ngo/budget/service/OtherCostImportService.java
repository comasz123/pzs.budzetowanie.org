package pl.ngo.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.entity.cost.CostAllocation;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.GrantBudgetItemCoverage;
import pl.ngo.budget.entity.coverage.OrgBudgetSourceType;
import pl.ngo.budget.repository.BudgetItemTemplateRepository;
import pl.ngo.budget.repository.CostAllocationRepository;
import pl.ngo.budget.repository.GrantRepository;
import pl.ngo.budget.service.OtherCostWorkbookParser.CostLine;
import pl.ngo.budget.service.OtherCostWorkbookParser.ParseResult;
import pl.ngo.budget.util.GrantCoverageYear;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ładuje pozostałe koszty (administracyjne: biuro, pozostałe, ekwiwalenty) do planu roku — linia roczna i
 * 12 linii miesięcznych na pozycję, jak w kreatorze nowego budżetu — oraz ich pokrycie w grantach
 * z prawdziwymi kwotami miesięcznymi. Granty muszą już istnieć ({@link FundingImportService}).
 */
@Service
public class OtherCostImportService {

    private static final String ADMIN_CODE = "KOSZT_ADM";
    private static final String ADMIN_NAME = "Koszty Administracyjne";

    private final CostAllocationRepository costAllocationRepository;
    private final BudgetItemTemplateRepository budgetItemTemplateRepository;
    private final GrantRepository grantRepository;

    public OtherCostImportService(CostAllocationRepository costAllocationRepository,
                                  BudgetItemTemplateRepository budgetItemTemplateRepository,
                                  GrantRepository grantRepository) {
        this.costAllocationRepository = costAllocationRepository;
        this.budgetItemTemplateRepository = budgetItemTemplateRepository;
        this.grantRepository = grantRepository;
    }

    @Transactional
    public List<String> load(ParseResult parsed, int fiscalYear, boolean apply, boolean replace) {
        List<String> report = new ArrayList<>(parsed.warnings().stream().map(w -> "UWAGA " + w).toList());

        Set<String> labels = new HashSet<>();
        for (CostLine line : parsed.lines()) {
            labels.add(line.label().toLowerCase(Locale.ROOT));
        }
        List<CostAllocation> clash = costAllocationRepository.findAllPlanAllocationsByFiscalYear(fiscalYear).stream()
                .filter(a -> a.getCategory() != null && ADMIN_CODE.equals(a.getCategory().getCode()))
                .filter(a -> a.getEmployee() == null && a.getLabel() != null
                        && labels.contains(a.getLabel().trim().toLowerCase(Locale.ROOT)))
                .toList();
        if (!clash.isEmpty() && !replace) {
            throw new IllegalStateException("W roku " + fiscalYear + " istnieje już " + clash.size()
                    + " linii kosztów administracyjnych o tych samych nazwach. Użyj replace=true, aby je zastąpić.");
        }

        BudgetItemTemplate category = budgetItemTemplateRepository.findByCode(ADMIN_CODE).orElse(null);
        if (category == null) {
            report.add("Brak kategorii " + ADMIN_CODE + " — " + (apply ? "utworzono." : "zostanie utworzona."));
            if (apply) {
                category = createAdminCategory();
            }
        }
        if (apply && !clash.isEmpty()) {
            costAllocationRepository.deleteAll(clash);
            costAllocationRepository.flush();
            report.add("Usunięto " + clash.size() + " istniejących linii.");
        }

        BigDecimal grand = BigDecimal.ZERO;
        BigDecimal covered = BigDecimal.ZERO;
        for (CostLine line : parsed.lines()) {
            BigDecimal total = line.total();
            BigDecimal sources = BigDecimal.ZERO;
            for (List<BigDecimal> series : line.sources().values()) {
                sources = sources.add(series.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            grand = grand.add(total);
            covered = covered.add(sources);
            report.add(String.format("%-9s %-42s wiersz %-3d rocznie %10s, źródła %10s",
                    line.group(), line.label(), line.sheetRow(), total.toPlainString(), sources.toPlainString()));
            if (apply) {
                save(category, line, fiscalYear);
            }
        }
        if (apply) {
            saveCoverage(parsed, fiscalYear, report);
        }
        report.add(String.format("%s: %d pozycji, koszty %s zł, przypisane do projektów %s zł, rok %d.",
                apply ? "ZAPISANO" : "PODGLĄD (nic nie zapisano)",
                parsed.lines().size(), grand.toPlainString(), covered.toPlainString(), fiscalYear));
        return report;
    }

    private void save(BudgetItemTemplate category, CostLine line, int fiscalYear) {
        costAllocationRepository.save(allocation(category, line, fiscalYear, null, line.total()));
        for (int m = 1; m <= 12; m++) {
            costAllocationRepository.save(allocation(category, line, fiscalYear, m, line.months().get(m - 1)));
        }
    }

    private static CostAllocation allocation(BudgetItemTemplate category, CostLine line, int fiscalYear,
                                             Integer planMonth, BigDecimal amount) {
        CostAllocation a = new CostAllocation();
        a.setCategory(category);
        a.setLabel(line.label());
        a.setAdminGroup(line.group());
        a.setAmount(amount);
        a.setPercentage(BigDecimal.valueOf(100));
        a.setFiscalYear(fiscalYear);
        a.setPlanMonth(planMonth);
        a.setSplitToMonths(true);
        a.setActive(true);
        return a;
    }

    private void saveCoverage(ParseResult parsed, int fiscalYear, List<String> report) {
        for (FundingSources.Source source : FundingSources.ALL) {
            List<CostLine> withSource = parsed.lines().stream()
                    .filter(l -> l.sources().containsKey(source.key()))
                    .toList();
            if (withSource.isEmpty()) {
                continue;
            }
            Grant grant = grantRepository.findByCode(source.key()).orElse(null);
            if (grant == null) {
                report.add("UWAGA brak grantu " + source.key() + " — pokrycie kosztów pominięte (uruchom import projektów).");
                continue;
            }
            GrantBudgetItem item = grant.getBudgetItems().stream()
                    .filter(i -> i.getParent() == null)
                    .filter(i -> ADMIN_CODE.equals(i.getCode()) || ADMIN_NAME.equals(i.getName()))
                    .findFirst()
                    .orElseGet(() -> {
                        GrantBudgetItem created = new GrantBudgetItem();
                        created.setGrant(grant);
                        created.setName(ADMIN_NAME);
                        created.setCode(ADMIN_CODE);
                        created.setAccountingCode(ADMIN_CODE);
                        created.setActive(true);
                        grant.getBudgetItems().add(created);
                        return created;
                    });
            item.setActive(true);
            item.getCoverages().removeIf(c -> c.getOrgSourceType() == OrgBudgetSourceType.DASHBOARD_ROW
                    && c.getOrgSourceKey() != null && c.getOrgSourceKey().startsWith("admin-"));

            BigDecimal total = BigDecimal.ZERO;
            for (CostLine line : withSource) {
                List<BigDecimal> series = line.sources().get(source.key());
                BigDecimal yearAmount = series.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                total = total.add(yearAmount);

                GrantBudgetItemCoverage coverage = new GrantBudgetItemCoverage();
                coverage.setGrantBudgetItem(item);
                coverage.setOrgSourceType(OrgBudgetSourceType.DASHBOARD_ROW);
                coverage.setOrgSourceId(0L);
                coverage.setOrgSourceKey(rowKey(line));
                coverage.setOrgLabel(line.label());
                coverage.setCoveredAmount(yearAmount);
                coverage.setCoveredAmount2027(GrantCoverageYear.splits(grant) ? BigDecimal.ZERO : null);
                for (int m = 0; m < 12; m++) {
                    if (series.get(m).signum() > 0) {
                        coverage.getMonthlyAmounts().put(GrantBudgetItemCoverage.periodKey(fiscalYear, m + 1), series.get(m));
                    }
                }
                item.getCoverages().add(coverage);
            }
            item.setPlannedAmount(total);
            if (!"FPKO_BP".equals(source.key())) {
                // Kwota grantu z importu to suma jego pozycji (wynagrodzenia + koszty administracyjne).
                grant.setTotalAmount(grant.getBudgetItems().stream()
                        .filter(GrantBudgetItem::isActive)
                        .map(i -> i.getPlannedAmount() != null ? i.getPlannedAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            grantRepository.save(grant);
        }
    }

    /** Klucz, pod którym dashboard szuka pokrycia pozycji: wiersz grupy + nazwa pozycji. */
    static String rowKey(CostLine line) {
        String group = switch (line.group()) {
            case OtherCostWorkbookParser.GROUP_BIURO -> "admin-biuro";
            case OtherCostWorkbookParser.GROUP_WYNAGRODZENIA -> "admin-wynagrodzenia";
            default -> "admin-pozostale";
        };
        return group + "/" + line.label().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ąćęłńóśźż]+", "-");
    }

    private BudgetItemTemplate createAdminCategory() {
        int nextOrder = budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .mapToInt(BudgetItemTemplate::getDisplayOrder).max().orElse(0) + 10;
        BudgetItemTemplate template = new BudgetItemTemplate();
        template.setCode(ADMIN_CODE);
        template.setName(ADMIN_NAME);
        template.setDescription("Obsługa księgowa, biurowa, opłaty bankowe i prawne");
        template.setDefaultCode(ADMIN_CODE);
        template.setDefaultCategory(BudgetItemTemplate.CategoryType.OFFICE);
        template.setDisplayOrder(nextOrder);
        return budgetItemTemplateRepository.save(template);
    }
}
