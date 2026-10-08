package pl.ngo.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.GrantBudgetItemCoverage;
import pl.ngo.budget.entity.coverage.OrgBudgetSourceType;
import pl.ngo.budget.entity.coverage.Project;
import pl.ngo.budget.entity.coverage.Sponsor;
import pl.ngo.budget.repository.EmployeeRepository;
import pl.ngo.budget.repository.GrantRepository;
import pl.ngo.budget.repository.ProjectRepository;
import pl.ngo.budget.repository.SponsorRepository;
import pl.ngo.budget.service.EmployeeCostWorkbookParser.EmployeeCostRow;
import pl.ngo.budget.service.EmployeeCostWorkbookParser.ParseResult;
import pl.ngo.budget.util.GrantCoverageYear;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tworzy z kolumn źródeł finansowania arkusza „Koszty” projekty i granty, w każdym pozycję
 * „Wynagrodzenia” (budżet = suma pokrycia z arkusza) i pokrycie wynagrodzeń pracowników z prawdziwymi
 * kwotami miesięcznymi. Wymaga wcześniej załadowanych pracowników ({@link EmployeeCostImportService}).
 */
@Service
public class FundingImportService {

    private static final String PERSONNEL_CODE = "KOSZT_PER";
    private static final String PERSONNEL_NAME = "Wynagrodzenia";

    private final EmployeeRepository employeeRepository;
    private final SponsorRepository sponsorRepository;
    private final ProjectRepository projectRepository;
    private final GrantRepository grantRepository;

    public FundingImportService(EmployeeRepository employeeRepository,
                                SponsorRepository sponsorRepository,
                                ProjectRepository projectRepository,
                                GrantRepository grantRepository) {
        this.employeeRepository = employeeRepository;
        this.sponsorRepository = sponsorRepository;
        this.projectRepository = projectRepository;
        this.grantRepository = grantRepository;
    }

    @Transactional
    public List<String> load(ParseResult parsed, int fiscalYear, boolean apply) {
        List<String> report = new ArrayList<>();

        Map<String, Employee> employees = new HashMap<>();
        for (Employee e : employeeRepository.findAll()) {
            employees.putIfAbsent(key(e.getLastName(), e.getFirstName()), e);
        }
        Map<String, Sponsor> sponsors = new HashMap<>();
        for (Sponsor s : sponsorRepository.findAll()) {
            sponsors.putIfAbsent(s.getName().trim().toLowerCase(Locale.ROOT), s);
        }

        BigDecimal unallocated = BigDecimal.ZERO;
        for (EmployeeCostRow row : parsed.rows()) {
            BigDecimal assigned = BigDecimal.ZERO;
            for (List<BigDecimal> series : row.sources().values()) {
                assigned = assigned.add(sum(series));
            }
            unallocated = unallocated.add(row.total().subtract(assigned));
            BigDecimal over = BigDecimal.ZERO;
            List<Integer> overMonths = new ArrayList<>();
            for (int m = 0; m < 12; m++) {
                BigDecimal inMonth = BigDecimal.ZERO;
                for (List<BigDecimal> series : row.sources().values()) {
                    inMonth = inMonth.add(series.get(m));
                }
                BigDecimal excess = inMonth.subtract(row.months().get(m));
                if (excess.compareTo(new BigDecimal("0.01")) > 0) {
                    over = over.add(excess);
                    overMonths.add(m + 1);
                }
            }
            if (!overMonths.isEmpty()) {
                report.add("UWAGA " + row.lastName() + " " + row.firstName() + ": źródła w pliku przekraczają pensję o "
                        + over.toPlainString() + " zł (miesiące " + overMonths + ") — zaimportowano jak w pliku.");
            }
            if (apply && !employees.containsKey(key(row.lastName(), row.firstName()))) {
                report.add("UWAGA brak pracownika w bazie: " + row.lastName() + " " + row.firstName()
                        + " — pokrycie pominięte (uruchom najpierw import wynagrodzeń).");
            }
        }

        for (FundingSources.Source source : FundingSources.ALL) {
            Map<EmployeeCostRow, List<BigDecimal>> bySource = new LinkedHashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (EmployeeCostRow row : parsed.rows()) {
                List<BigDecimal> series = row.sources().get(source.key());
                if (series != null) {
                    bySource.put(row, series);
                    total = total.add(sum(series));
                }
            }
            report.add(String.format("%-22s %s – %s  wynagrodzenia %12s zł, %d osób",
                    source.name(), source.start(), source.end(), total.toPlainString(), bySource.size()));
            if (apply) {
                save(source, bySource, total, fiscalYear, employees, sponsors);
            }
        }

        report.add("Płace bez przypisanego źródła w pliku: " + unallocated.toPlainString() + " zł (nie wchodzą do żadnego projektu).");
        report.add((apply ? "ZAPISANO" : "PODGLĄD (nic nie zapisano)") + ": " + FundingSources.ALL.size()
                + " projektów/grantów, rok " + fiscalYear + ".");
        return report;
    }

    private void save(FundingSources.Source source,
                      Map<EmployeeCostRow, List<BigDecimal>> bySource,
                      BigDecimal total,
                      int fiscalYear,
                      Map<String, Employee> employees,
                      Map<String, Sponsor> sponsors) {
        Grant grant = grantRepository.findByCode(source.key()).orElseGet(() -> {
            Project project = projectRepository.findByCode(source.projectCode()).orElseGet(() -> {
                Project created = new Project();
                created.setCode(source.projectCode());
                created.setName(source.projectName());
                created.setStartDate(source.start());
                created.setEndDate(source.end());
                created.setTotalBudget(total);
                created.setActive(true);
                return projectRepository.save(created);
            });
            Sponsor sponsor = sponsors.computeIfAbsent(source.sponsor().trim().toLowerCase(Locale.ROOT), k -> {
                Sponsor createdSponsor = new Sponsor();
                createdSponsor.setName(source.sponsor());
                return sponsorRepository.save(createdSponsor);
            });
            Grant created = new Grant();
            created.setCode(source.key());
            created.setName(source.name());
            created.setSponsor(sponsor);
            created.setProject(project);
            created.setStartDate(source.start());
            created.setEndDate(source.end());
            created.setTotalAmount(total);
            created.setCurrency("PLN");
            created.setActive(true);
            return created;
        });

        GrantBudgetItem item = grant.getBudgetItems().stream()
                .filter(i -> i.getParent() == null)
                .filter(i -> PERSONNEL_CODE.equals(i.getCode()) || PERSONNEL_NAME.equals(i.getName()))
                .findFirst()
                .orElseGet(() -> {
                    GrantBudgetItem created = new GrantBudgetItem();
                    created.setGrant(grant);
                    created.setName(PERSONNEL_NAME);
                    created.setCode(PERSONNEL_CODE);
                    created.setAccountingCode(PERSONNEL_CODE);
                    created.setActive(true);
                    grant.getBudgetItems().add(created);
                    return created;
                });
        item.setActive(true);
        item.setPlannedAmount(total);
        item.getCoverages().removeIf(c -> c.getOrgSourceType() == OrgBudgetSourceType.EMPLOYEE);

        for (Map.Entry<EmployeeCostRow, List<BigDecimal>> entry : bySource.entrySet()) {
            EmployeeCostRow row = entry.getKey();
            Employee employee = employees.get(key(row.lastName(), row.firstName()));
            if (employee == null) {
                continue;
            }
            GrantBudgetItemCoverage coverage = new GrantBudgetItemCoverage();
            coverage.setGrantBudgetItem(item);
            coverage.setOrgSourceType(OrgBudgetSourceType.EMPLOYEE);
            coverage.setOrgSourceId(employee.getId());
            coverage.setOrgLabel((employee.getFirstName() + " " + employee.getLastName()).trim());
            coverage.setCoveredAmount(sum(entry.getValue()));
            coverage.setCoveredAmount2027(GrantCoverageYear.splits(grant) ? BigDecimal.ZERO : null);
            for (int m = 0; m < 12; m++) {
                BigDecimal amount = entry.getValue().get(m);
                if (amount.signum() > 0) {
                    coverage.getMonthlyAmounts().put(GrantBudgetItemCoverage.periodKey(fiscalYear, m + 1), amount);
                }
            }
            item.getCoverages().add(coverage);
        }
        grantRepository.save(grant);
    }

    private static BigDecimal sum(List<BigDecimal> series) {
        return series.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String key(String last, String first) {
        return (last == null ? "" : last.trim().toLowerCase(Locale.ROOT))
                + "|" + (first == null ? "" : first.trim().toLowerCase(Locale.ROOT));
    }
}
