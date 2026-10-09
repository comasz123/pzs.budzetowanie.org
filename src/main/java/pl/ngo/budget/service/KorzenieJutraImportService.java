package pl.ngo.budget.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.Project;
import pl.ngo.budget.entity.coverage.Sponsor;
import pl.ngo.budget.repository.GrantRepository;
import pl.ngo.budget.repository.ProjectRepository;
import pl.ngo.budget.repository.SponsorRepository;
import pl.ngo.budget.service.KorzenieJutraWorkbookParser.Line;
import pl.ngo.budget.service.KorzenieJutraWorkbookParser.ParseResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Projekt „Korzenie Jutra” współfinansowany przez PKO BP (500 000 zł) i OSIF (wkład własny 48 247 zł):
 * zakłada projekt, przenosi do niego grant PKO BP z importu kolumn „Koszty”, dodaje grant OSIF i wpisuje
 * budżet z wniosku jako płaską listę pozycji — bez kategorii i bez pokrycia kosztów budżetu organizacji.
 */
@Service
public class KorzenieJutraImportService {

    public static final String PROJECT_CODE = "KORZENIE_JUTRA";
    public static final String PROJECT_NAME = "Korzenie Jutra";
    static final String PKO_GRANT_CODE = "FPKO_BP";
    static final String OSIF_GRANT_CODE = "KORZENIE_OSIF";
    static final LocalDate START = LocalDate.of(2026, 2, 16);
    static final LocalDate END = LocalDate.of(2027, 5, 15);

    private final SponsorRepository sponsorRepository;
    private final ProjectRepository projectRepository;
    private final GrantRepository grantRepository;
    private final EntityManager entityManager;

    public KorzenieJutraImportService(SponsorRepository sponsorRepository,
                                      ProjectRepository projectRepository,
                                      GrantRepository grantRepository,
                                      EntityManager entityManager) {
        this.sponsorRepository = sponsorRepository;
        this.projectRepository = projectRepository;
        this.grantRepository = grantRepository;
        this.entityManager = entityManager;
    }

    /** Kwota pozycji finansowana przez dany grant: wkład OSIF albo reszta (PKO BP). */
    static BigDecimal amountFor(Line line, boolean osif) {
        return osif ? line.osifAmount() : line.total().subtract(line.osifAmount());
    }

    @Transactional
    public List<String> load(ParseResult parsed, boolean apply) {
        List<String> report = new ArrayList<>(parsed.warnings().stream().map(w -> "UWAGA " + w).toList());
        BigDecimal pkoTotal = parsed.lines().stream().map(l -> amountFor(l, false)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal osifTotal = parsed.lines().stream().map(l -> amountFor(l, true)).reduce(BigDecimal.ZERO, BigDecimal::add);

        report.add("Projekt " + PROJECT_NAME + ": razem " + parsed.grandTotal() + " zł, PKO BP " + pkoTotal
                + " zł, wkład OSIF " + osifTotal + " zł, " + START + " – " + END);
        for (Line line : parsed.lines()) {
            report.add("  " + String.format("%-12s", line.total().toPlainString() + " zł") + "PKO "
                    + String.format("%-12s", amountFor(line, false).toPlainString()) + "OSIF "
                    + String.format("%-10s", line.osifAmount().toPlainString()) + line.name());
        }
        if (!apply) {
            report.add("PODGLĄD (nic nie zapisano).");
            return report;
        }

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
        setBudget(pkoGrant, parsed.lines(), false, report);
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
        setBudget(osifGrant, parsed.lines(), true, report);
        grantRepository.save(osifGrant);
        report.add("Grant OSIF: " + osifGrant.getName() + " " + osifTotal + " zł");

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
     * Płaska lista pozycji wniosku: jedna pozycja grantu na pozycję budżetu (pełna nazwa i kwota), bez
     * kategorii nadrzędnych i bez pokrycia kosztów budżetu organizacji. Ponowny import dopasowuje istniejące
     * pozycje po nazwie, a resztę (dawne kategorie, pozycje spoza wniosku) usuwa — pozycje użyte w wydatkach
     * tylko wyłącza.
     */
    private void setBudget(Grant grant, List<Line> lines, boolean osif, List<String> report) {
        Set<GrantBudgetItem> keep = new HashSet<>();
        int number = 0;
        for (Line line : lines) {
            BigDecimal amount = amountFor(line, osif);
            if (amount.signum() == 0) {
                continue;
            }
            number++;
            GrantBudgetItem item = grant.getBudgetItems().stream()
                    .filter(i -> !keep.contains(i) && line.name().equals(i.getName()))
                    .findFirst()
                    .orElseGet(() -> {
                        GrantBudgetItem created = new GrantBudgetItem();
                        created.setGrant(grant);
                        created.setName(line.name());
                        grant.getBudgetItems().add(created);
                        return created;
                    });
            item.setParent(null);
            item.setCode(String.format("KJ_%02d", number));
            item.setAccountingCode(item.getCode());
            item.setPlannedAmount(amount);
            item.setActive(true);
            item.getCoverages().clear();
            keep.add(item);
        }
        for (GrantBudgetItem item : new ArrayList<>(grant.getBudgetItems())) {
            if (keep.contains(item)) {
                continue;
            }
            item.setParent(null);
            item.getCoverages().clear();
            if (isUsedByExpenditures(item)) {
                item.setActive(false);
                item.setPlannedAmount(BigDecimal.ZERO);
                report.add("UWAGA pozycja „" + item.getName() + "” (" + grant.getCode()
                        + ") ma przypisane wydatki — wyłączona zamiast usunięta.");
            } else {
                grant.getBudgetItems().remove(item);
            }
        }
    }

    private boolean isUsedByExpenditures(GrantBudgetItem item) {
        if (item.getId() == null) {
            return false;
        }
        long direct = entityManager.createQuery(
                "select count(e) from Expenditure e where e.budgetItem.id = :id", Long.class)
                .setParameter("id", item.getId()).getSingleResult();
        long shared = entityManager.createQuery(
                "select count(s) from ExpenditureGrantShare s where s.budgetItem.id = :id", Long.class)
                .setParameter("id", item.getId()).getSingleResult();
        return direct + shared > 0;
    }
}
