package pl.ngo.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.GrantBudgetItemCoverage;
import pl.ngo.budget.entity.coverage.OrgBudgetSourceType;
import pl.ngo.budget.entity.coverage.Project;
import pl.ngo.budget.entity.coverage.Sponsor;
import pl.ngo.budget.repository.BudgetItemTemplateRepository;
import pl.ngo.budget.repository.CostAllocationRepository;
import pl.ngo.budget.repository.EmployeeRepository;
import pl.ngo.budget.repository.GrantRepository;
import pl.ngo.budget.repository.ProjectRepository;
import pl.ngo.budget.repository.SponsorRepository;
import pl.ngo.budget.service.KorzenieJutraWorkbookParser.Line;
import pl.ngo.budget.service.KorzenieJutraWorkbookParser.ParseResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Projekt „Korzenie Jutra” współfinansowany przez PKO BP (500 000 zł) i OSIF (wkład własny 48 247 zł):
 * zakłada projekt, przenosi do niego grant PKO BP z importu kolumn „Koszty”, dodaje grant OSIF, wpisuje
 * budżet z wniosku według kategorii oraz uzupełnia pokrycie wynagrodzeń i kosztów stałych na rok 2027.
 */
@Service
public class KorzenieJutraImportService {

    public static final String PROJECT_CODE = "KORZENIE_JUTRA";
    public static final String PROJECT_NAME = "Korzenie Jutra";
    static final String PKO_GRANT_CODE = "FPKO_BP";
    static final String OSIF_GRANT_CODE = "KORZENIE_OSIF";
    static final LocalDate START = LocalDate.of(2026, 2, 16);
    static final LocalDate END = LocalDate.of(2027, 5, 15);
    private static final int NEXT_YEAR = 2027;

    enum Category {
        PER("KOSZT_PER", "Wynagrodzenia", "Wynagrodzenia personelu merytorycznego i zarządczego",
                BudgetItemTemplate.CategoryType.PERSONNEL),
        ADM("KOSZT_ADM", "Koszty Administracyjne", "Obsługa księgowa, biurowa, opłaty bankowe i prawne",
                BudgetItemTemplate.CategoryType.OFFICE),
        LOG("KOSZT_LOG", "Podróże", "Podróże służbowe krajowe i zagraniczne",
                BudgetItemTemplate.CategoryType.TRAVEL),
        KSW("KOSZT_KSW", "Konferencje, spotkania, warsztaty",
                "Planowane konferencje, spotkania partnerskie i warsztaty merytoryczne",
                BudgetItemTemplate.CategoryType.SERVICES),
        PROM("KOSZT_PROM", "Promocja i Komunikacja", "Druk ulotek, reklama w mediach, strona www",
                BudgetItemTemplate.CategoryType.SERVICES),
        USL("GRANT_USLUGI", "Usługi zewnętrzne", "Opracowania, ekspertyzy i usługi zlecane na zewnątrz",
                BudgetItemTemplate.CategoryType.SERVICES);

        final String code;
        final String name;
        final String description;
        final BudgetItemTemplate.CategoryType type;

        Category(String code, String name, String description, BudgetItemTemplate.CategoryType type) {
            this.code = code;
            this.name = name;
            this.description = description;
            this.type = type;
        }
    }

    private final EmployeeRepository employeeRepository;
    private final SponsorRepository sponsorRepository;
    private final ProjectRepository projectRepository;
    private final GrantRepository grantRepository;
    private final BudgetItemTemplateRepository budgetItemTemplateRepository;
    private final CostAllocationRepository costAllocationRepository;

    public KorzenieJutraImportService(EmployeeRepository employeeRepository,
                                      SponsorRepository sponsorRepository,
                                      ProjectRepository projectRepository,
                                      GrantRepository grantRepository,
                                      BudgetItemTemplateRepository budgetItemTemplateRepository,
                                      CostAllocationRepository costAllocationRepository) {
        this.employeeRepository = employeeRepository;
        this.sponsorRepository = sponsorRepository;
        this.projectRepository = projectRepository;
        this.grantRepository = grantRepository;
        this.budgetItemTemplateRepository = budgetItemTemplateRepository;
        this.costAllocationRepository = costAllocationRepository;
    }

    static Category classify(Line line) {
        String name = line.name().toLowerCase(Locale.ROOT);
        String type = line.costType() == null ? "" : line.costType().toLowerCase(Locale.ROOT);
        String unit = line.unit() == null ? "" : line.unit().toLowerCase(Locale.ROOT);
        if (type.contains("administracyjne") || name.contains("licencj")) {
            return Category.ADM;
        }
        if (unit.equals("miesiąc")) {
            return Category.PER;
        }
        if (name.contains("delegacj") || unit.equals("podróż") || unit.equals("ryczałt")) {
            return Category.LOG;
        }
        if (name.contains("wydarzeni") || name.contains("jubileusz") || name.contains("śniadanie")
                || name.contains("szkolenia dla") || name.contains("cykl szkole")) {
            return Category.KSW;
        }
        if (name.contains("gadżet") || name.contains("brandbook") || name.contains("stron")
                || name.contains("szkolenie medialne")) {
            return Category.PROM;
        }
        return Category.USL;
    }

    /** Kwoty budżetu według kategorii; {@code osif} = część finansowana wkładem OSIF, w przeciwnym razie PKO BP. */
    static Map<Category, BigDecimal> byCategory(List<Line> lines, boolean osif) {
        Map<Category, BigDecimal> result = new EnumMap<>(Category.class);
        for (Line line : lines) {
            BigDecimal amount = osif ? line.osifAmount() : line.total().subtract(line.osifAmount());
            if (amount.signum() != 0) {
                result.merge(classify(line), amount, BigDecimal::add);
            }
        }
        return result;
    }

    /** Pracownik finansowany z pozycji płacowej wniosku (po słowach kluczowych w nazwie pozycji). */
    static String employeeKeyFor(String lineName) {
        String n = lineName.toLowerCase(Locale.ROOT);
        if (n.contains("strategii")) {
            return "furmaga|joanna";
        }
        if (n.contains("stabilności")) {
            return "daliga|katarzyna";
        }
        if (n.contains("komunikacyjn")) {
            return "skórka|karolina";
        }
        if (n.contains("koordynacja grantu")) {
            return "krawczyk-duda|anna";
        }
        if (n.contains("fundusze europejskie dla klimatu")) {
            return "mrozek|krzysztof";
        }
        if (n.contains("klimat i energia")) {
            return "piekarz|alicja";
        }
        if (n.contains("sprawiedliwa transformacja")) {
            return "kowalik|mateusz";
        }
        return null;
    }

    @Transactional
    public List<String> load(ParseResult parsed, boolean apply) {
        List<String> report = new ArrayList<>(parsed.warnings().stream().map(w -> "UWAGA " + w).toList());
        Map<Category, BigDecimal> pko = byCategory(parsed.lines(), false);
        Map<Category, BigDecimal> osif = byCategory(parsed.lines(), true);
        BigDecimal pkoTotal = pko.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal osifTotal = osif.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        report.add("Projekt " + PROJECT_NAME + ": razem " + parsed.grandTotal() + " zł, PKO BP " + pkoTotal
                + " zł, wkład OSIF " + osifTotal + " zł, " + START + " – " + END);
        pko.forEach((c, v) -> report.add("  PKO BP  " + String.format("%-30s", c.name) + v));
        osif.forEach((c, v) -> report.add("  OSIF    " + String.format("%-30s", c.name) + v));
        if (!apply) {
            report.add("PODGLĄD (nic nie zapisano).");
            return report;
        }

        migrateLegacyServicesCategory(report);

        Grant pkoGrant = grantRepository.findByCode(PKO_GRANT_CODE).orElse(null);
        if (pkoGrant == null) {
            report.add("UWAGA brak grantu " + PKO_GRANT_CODE
                    + " — najpierw załaduj import z arkusza Koszty (wynagrodzenia, projekty).");
            return report;
        }

        // Dotychczasowy projekt grantu PKO BP (FPKO_BP) staje się projektem „Korzenie Jutra” — zostaje ten sam
        // rekord, bo odwołują się do niego plany kosztów.
        Project previous = pkoGrant.getProject();
        Project project = projectRepository.findByCode(PROJECT_CODE).orElseGet(() ->
                previous != null && "FPKO_BP".equals(previous.getCode()) ? previous : new Project());
        project.setCode(PROJECT_CODE);
        project.setName(PROJECT_NAME);
        project.setDescription("Projekt współfinansowany przez PKO BP i OSIF (wkład własny).");
        project.setStartDate(START);
        project.setEndDate(END);
        project.setTotalBudget(parsed.grandTotal());
        project.setActive(true);
        project = projectRepository.save(project);

        Sponsor pkoSponsor = pkoSponsor(pkoGrant);
        pkoGrant.setName("PKO BP – " + PROJECT_NAME);
        pkoGrant.setSponsor(pkoSponsor);
        pkoGrant.setProject(project);
        pkoGrant.setStartDate(START);
        pkoGrant.setEndDate(END);
        pkoGrant.setTotalAmount(pkoTotal);
        pkoGrant.setCurrency("PLN");
        pkoGrant.setActive(true);
        setBudget(pkoGrant, pko, parsed.lines(), false);
        report.add("Grant PKO BP: " + pkoGrant.getName() + " " + pkoTotal + " zł");

        Grant osifGrant = grantRepository.findByCode(OSIF_GRANT_CODE).orElseGet(Grant::new);
        osifGrant.setCode(OSIF_GRANT_CODE);
        osifGrant.setName("OSIF – " + PROJECT_NAME + " (wkład własny)");
        osifGrant.setSponsor(sponsorNamed("OSIF"));
        osifGrant.setProject(project);
        osifGrant.setStartDate(START);
        osifGrant.setEndDate(END);
        osifGrant.setTotalAmount(osifTotal);
        osifGrant.setCurrency("PLN");
        osifGrant.setActive(true);
        setBudget(osifGrant, osif, parsed.lines(), true);
        grantRepository.save(osifGrant);
        report.add("Grant OSIF: " + osifGrant.getName() + " " + osifTotal + " zł");

        extendCoverageToNextYear(pkoGrant, parsed.lines(), report);
        grantRepository.save(pkoGrant);

        report.add("ZAPISANO.");
        return report;
    }

    private Sponsor pkoSponsor(Grant grant) {
        Sponsor existing = sponsorRepository.findAll().stream()
                .filter(s -> "PKO BP".equalsIgnoreCase(s.getName().trim()))
                .findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        Sponsor current = grant.getSponsor();
        if (current != null && "FPKO BP".equals(current.getName())) {
            current.setName("PKO BP");
            return sponsorRepository.save(current);
        }
        return sponsorNamed("PKO BP");
    }

    private Sponsor sponsorNamed(String name) {
        return sponsorRepository.findAll().stream()
                .filter(s -> name.equalsIgnoreCase(s.getName().trim()))
                .findFirst()
                .orElseGet(() -> {
                    Sponsor created = new Sponsor();
                    created.setName(name);
                    return sponsorRepository.save(created);
                });
    }

    /**
     * Pozycja główna na kategorię (kwota = suma pozycji wniosku) i pod nią podpozycje: każda pozycja wniosku
     * z pełną nazwą. Ponowny import aktualizuje podpozycje o tej samej nazwie.
     */
    private void setBudget(Grant grant, Map<Category, BigDecimal> amounts, List<Line> lines, boolean osif) {
        amounts.forEach((category, amount) -> {
            GrantBudgetItem item = grant.getBudgetItems().stream()
                    .filter(i -> i.getParent() == null)
                    .filter(i -> category.code.equals(i.getCode()) || category.name.equals(i.getName()))
                    .findFirst()
                    .orElseGet(() -> {
                        GrantBudgetItem created = new GrantBudgetItem();
                        created.setGrant(grant);
                        created.setName(category.name);
                        created.setCode(category.code);
                        created.setAccountingCode(category.code);
                        grant.getBudgetItems().add(created);
                        return created;
                    });
            item.setActive(true);
            item.setPlannedAmount(amount);

            for (Line line : lines) {
                if (classify(line) != category) {
                    continue;
                }
                BigDecimal lineAmount = osif ? line.osifAmount() : line.total().subtract(line.osifAmount());
                if (lineAmount.signum() == 0) {
                    continue;
                }
                GrantBudgetItem child = grant.getBudgetItems().stream()
                        .filter(i -> i.getParent() == item && line.name().equals(i.getName()))
                        .findFirst()
                        .orElseGet(() -> {
                            GrantBudgetItem created = new GrantBudgetItem();
                            created.setGrant(grant);
                            created.setName(line.name());
                            created.setParent(item);
                            grant.getBudgetItems().add(created);
                            return created;
                        });
                child.setCode(category.code);
                child.setAccountingCode(category.code);
                child.setPlannedAmount(lineAmount);
                child.setActive(true);
            }
        });
    }

    /**
     * Wcześniejsza wersja importu zakładała kategorię organizacji „Usługi zewnętrzne” (KOSZT_USLUGI) z kategorii
     * grantu. Kategorie grantów nie należą do budżetu organizacji, więc taka kategoria jest usuwana (o ile nie ma
     * w niej kosztów organizacji), a pozycje grantów dostają kod GRANT_USLUGI.
     */
    private void migrateLegacyServicesCategory(List<String> report) {
        for (Grant grant : grantRepository.findAllWithBudgetItems()) {
            for (GrantBudgetItem item : grant.getBudgetItems()) {
                if ("KOSZT_USLUGI".equals(item.getCode())) {
                    item.setCode(Category.USL.code);
                    item.setAccountingCode(Category.USL.code);
                }
            }
        }
        budgetItemTemplateRepository.findByCode("KOSZT_USLUGI").ifPresent(template -> {
            if (costAllocationRepository.countByCategory_Id(template.getId()) == 0) {
                budgetItemTemplateRepository.delete(template);
                report.add("Usunięto kategorię organizacji „" + template.getName()
                        + "” utworzoną przez poprzedni import (kategorie grantów nie należą do budżetu organizacji).");
            }
        });
    }

    /**
     * Wniosek obejmuje też 2027 (np. 4 miesiące po 2 600 zł): dopisuje te kwoty do pokrycia wynagrodzeń
     * i kosztów stałych PKO BP, miesiąc po miesiącu od stycznia.
     */
    private void extendCoverageToNextYear(Grant grant, List<Line> lines, List<String> report) {
        Map<String, Employee> employees = new HashMap<>();
        for (Employee e : employeeRepository.findAll()) {
            employees.putIfAbsent((e.getLastName() + "|" + e.getFirstName()).toLowerCase(Locale.ROOT), e);
        }
        for (Line line : lines) {
            BigDecimal amount = line.amountByYear().getOrDefault(NEXT_YEAR, BigDecimal.ZERO);
            int months = line.unitsByYear().getOrDefault(NEXT_YEAR, BigDecimal.ZERO).intValue();
            if (amount.signum() <= 0 || months <= 0 || !"miesiąc".equalsIgnoreCase(line.unit())) {
                continue;
            }
            Category category = classify(line);
            GrantBudgetItemCoverage coverage;
            if (category == Category.PER) {
                String key = employeeKeyFor(line.name());
                Employee employee = key == null ? null : employees.get(key);
                if (employee == null) {
                    report.add("UWAGA nie dopasowano pracownika do pozycji „" + line.name() + "” — 2027 pominięty.");
                    continue;
                }
                coverage = coverage(grant, category, c -> c.getOrgSourceType() == OrgBudgetSourceType.EMPLOYEE
                        && employee.getId().equals(c.getOrgSourceId()), () -> {
                    GrantBudgetItemCoverage created = new GrantBudgetItemCoverage();
                    created.setOrgSourceType(OrgBudgetSourceType.EMPLOYEE);
                    created.setOrgSourceId(employee.getId());
                    created.setOrgLabel((employee.getFirstName() + " " + employee.getLastName()).trim());
                    return created;
                });
            } else if (category == Category.ADM) {
                String rowKey = adminRowKey(line.name());
                if (rowKey == null) {
                    continue;
                }
                coverage = coverage(grant, category, c -> c.getOrgSourceType() == OrgBudgetSourceType.DASHBOARD_ROW
                        && rowKey.equals(c.getOrgSourceKey()), () -> {
                    GrantBudgetItemCoverage created = new GrantBudgetItemCoverage();
                    created.setOrgSourceType(OrgBudgetSourceType.DASHBOARD_ROW);
                    created.setOrgSourceId(0L);
                    created.setOrgSourceKey(rowKey);
                    created.setOrgLabel(line.name());
                    return created;
                });
            } else {
                continue;
            }
            BigDecimal perMonth = amount.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
            BigDecimal assigned = BigDecimal.ZERO;
            for (int m = 1; m <= months && m <= 12; m++) {
                BigDecimal value = m == months ? amount.subtract(assigned) : perMonth;
                assigned = assigned.add(value);
                coverage.getMonthlyAmounts().put(GrantBudgetItemCoverage.periodKey(NEXT_YEAR, m), value);
            }
            coverage.setCoveredAmount2027(amount);
            report.add("  2027: " + String.format("%-48s", coverage.getOrgLabel()) + amount + " zł (" + months + " mies.)");
        }
    }

    private static String adminRowKey(String lineName) {
        String n = lineName.toLowerCase(Locale.ROOT);
        if (n.equals("księgowość")) {
            return "admin-pozostale/usługa-księgowa";
        }
        if (n.equals("czynsz i media")) {
            return "admin-biuro/czynsz";
        }
        return null;
    }

    private GrantBudgetItemCoverage coverage(Grant grant, Category category,
                                             java.util.function.Predicate<GrantBudgetItemCoverage> match,
                                             java.util.function.Supplier<GrantBudgetItemCoverage> create) {
        GrantBudgetItem item = grant.getBudgetItems().stream()
                .filter(i -> i.getParent() == null)
                .filter(i -> category.code.equals(i.getCode()) || category.name.equals(i.getName()))
                .findFirst()
                .orElseThrow();
        for (GrantBudgetItemCoverage c : item.getCoverages()) {
            if (match.test(c)) {
                return c;
            }
        }
        GrantBudgetItemCoverage created = create.get();
        created.setGrantBudgetItem(item);
        created.setCoveredAmount(BigDecimal.ZERO);
        item.getCoverages().add(created);
        return created;
    }
}
