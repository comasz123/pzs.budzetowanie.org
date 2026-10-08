package pl.ngo.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.entity.cost.ContractType;
import pl.ngo.budget.entity.cost.CostAllocation;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.repository.BudgetItemTemplateRepository;
import pl.ngo.budget.repository.CostAllocationRepository;
import pl.ngo.budget.repository.EmployeeRepository;
import pl.ngo.budget.service.EmployeeCostWorkbookParser.EmployeeCostRow;
import pl.ngo.budget.service.EmployeeCostWorkbookParser.ParseResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ładuje pracowników oraz ich miesięczne koszty (kategoria „Wynagrodzenia”, KOSZT_PER) do planu roku.
 * <p>
 * Dla każdego pracownika powstaje linia roczna (planMonth = null, suma miesięcy) i 12 linii miesięcznych
 * z kwotami z arkusza — w tym samym kształcie, w jakim zapisuje je kreator nowego budżetu.
 * Podział na projekty/granty nie jest tu importowany.
 */
@Service
public class EmployeeCostImportService {

    private static final String PERSONNEL_CODE = "KOSZT_PER";

    private final EmployeeRepository employeeRepository;
    private final CostAllocationRepository costAllocationRepository;
    private final BudgetItemTemplateRepository budgetItemTemplateRepository;
    private final BudgetSetupService budgetSetupService;

    public EmployeeCostImportService(EmployeeRepository employeeRepository,
                                     CostAllocationRepository costAllocationRepository,
                                     BudgetItemTemplateRepository budgetItemTemplateRepository,
                                     BudgetSetupService budgetSetupService) {
        this.employeeRepository = employeeRepository;
        this.costAllocationRepository = costAllocationRepository;
        this.budgetItemTemplateRepository = budgetItemTemplateRepository;
        this.budgetSetupService = budgetSetupService;
    }

    /**
     * @param apply   false = tylko raport, nic nie jest zapisywane
     * @param replace true = usuń istniejące linie KOSZT_PER (plan) importowanych pracowników w tym roku;
     *                false = przerwij, jeśli takie linie już istnieją
     * @return raport, jedna pozycja na linię
     */
    @Transactional
    public List<String> load(ParseResult parsed, int fiscalYear, boolean apply, boolean replace) {
        List<String> report = new ArrayList<>(parsed.warnings().stream().map(w -> "UWAGA " + w).toList());

        if (apply) {
            fixLegacyNames(report);
        }
        Map<String, Employee> existing = new HashMap<>();
        for (Employee e : employeeRepository.findAll()) {
            existing.putIfAbsent(key(e.getLastName(), e.getFirstName()), e);
        }

        Set<Long> matchedIds = new HashSet<>();
        for (EmployeeCostRow row : parsed.rows()) {
            Employee e = existing.get(key(row.lastName(), row.firstName()));
            if (e != null) {
                matchedIds.add(e.getId());
            }
        }
        List<CostAllocation> clash = costAllocationRepository.findAllPlanAllocationsByFiscalYear(fiscalYear).stream()
                .filter(a -> a.getCategory() != null && PERSONNEL_CODE.equals(a.getCategory().getCode()))
                .filter(a -> a.getEmployee() != null && matchedIds.contains(a.getEmployee().getId()))
                .toList();
        if (!clash.isEmpty() && !replace) {
            throw new IllegalStateException("W roku " + fiscalYear + " istnieje już " + clash.size()
                    + " linii wynagrodzeń importowanych pracowników. Użyj replace=true, aby je zastąpić.");
        }

        BudgetItemTemplate category = budgetItemTemplateRepository.findByCode(PERSONNEL_CODE).orElse(null);
        if (category == null) {
            report.add("Brak kategorii " + PERSONNEL_CODE + " — " + (apply ? "utworzono." : "zostanie utworzona."));
            if (apply) {
                category = createPersonnelCategory();
            }
        }

        if (apply && !clash.isEmpty()) {
            costAllocationRepository.deleteAll(clash);
            costAllocationRepository.flush();
            report.add("Usunięto " + clash.size() + " istniejących linii wynagrodzeń.");
        }

        int created = 0;
        int linked = 0;
        BigDecimal grand = BigDecimal.ZERO;
        for (EmployeeCostRow row : parsed.rows()) {
            String name = row.lastName() + " " + row.firstName();
            Employee employee = existing.get(key(row.lastName(), row.firstName()));
            boolean isNew = employee == null;
            if (isNew) {
                created++;
                if (apply) {
                    employee = new Employee();
                    employee.setFirstName(row.firstName());
                    employee.setLastName(row.lastName());
                    employee.setContractType(contractOf(row));
                    employee = employeeRepository.save(employee);
                    existing.put(key(row.lastName(), row.firstName()), employee);
                }
            } else {
                linked++;
            }
            BigDecimal total = row.total();
            grand = grand.add(total);
            report.add(String.format("%-4s %-28s %-8s wiersz %-3d rocznie %12s  %s",
                    isNew ? "NOWY" : "JUŻ", name,
                    row.contract() == EmployeeCostWorkbookParser.Contract.UMOWA_O_PRACE ? "UoP" : "zlecenie",
                    row.sheetRow(), total.toPlainString(), months(row.months())));
            if (apply) {
                employee.setContractType(contractOf(row));
                employeeRepository.save(employee);
                save(employee, category, row, fiscalYear);
            }
        }
        if (apply) {
            budgetSetupService.refreshPersonnelPlannedCosts(fiscalYear);
        }
        report.add(String.format("%s: %d pracowników (%d nowych, %d istniejących), suma roczna %s, rok %d.",
                apply ? "ZAPISANO" : "PODGLĄD (nic nie zapisano)",
                parsed.rows().size(), created, linked, grand.toPlainString(), fiscalYear));
        return report;
    }

    private static ContractType contractOf(EmployeeCostRow row) {
        return row.contract() == EmployeeCostWorkbookParser.Contract.UMOWA_O_PRACE
                ? ContractType.UMOWA_O_PRACE : ContractType.UMOWA_ZLECENIE;
    }

    /** Pierwszy import zapisał te osoby pod złymi nazwami (NN NN, imię i nazwisko zamienione). */
    private void fixLegacyNames(List<String> report) {
        String[][] fixes = {{"NN", "NN", "Franke", "Kinga"}, {"Majka", "Wiśniewska", "Wiśniewska", "Majka"}};
        List<Employee> all = employeeRepository.findAll();
        for (String[] f : fixes) {
            boolean target = all.stream().anyMatch(e -> f[2].equals(e.getLastName()) && f[3].equals(e.getFirstName()));
            if (target) {
                continue;
            }
            for (Employee e : all) {
                if (f[0].equals(e.getLastName()) && f[1].equals(e.getFirstName())) {
                    e.setLastName(f[2]);
                    e.setFirstName(f[3]);
                    employeeRepository.save(e);
                    report.add("Poprawiono nazwę: " + f[0] + " " + f[1] + " → " + f[2] + " " + f[3]);
                }
            }
        }
    }

    private void save(Employee employee, BudgetItemTemplate category, EmployeeCostRow row, int fiscalYear) {
        String label = (employee.getFirstName() != null ? employee.getFirstName() : "")
                + " " + (employee.getLastName() != null ? employee.getLastName() : "").trim();
        costAllocationRepository.save(line(employee, category, label, fiscalYear, null, row.total()));
        for (int m = 1; m <= 12; m++) {
            costAllocationRepository.save(line(employee, category, label, fiscalYear, m, row.months().get(m - 1)));
        }
    }

    private static CostAllocation line(Employee employee, BudgetItemTemplate category, String label,
                                       int fiscalYear, Integer planMonth, BigDecimal amount) {
        CostAllocation a = new CostAllocation();
        a.setEmployee(employee);
        a.setCategory(category);
        a.setLabel(label);
        a.setAmount(amount);
        a.setPercentage(BigDecimal.valueOf(100));
        a.setFiscalYear(fiscalYear);
        a.setPlanMonth(planMonth);
        a.setSplitToMonths(true);
        a.setActive(true);
        return a;
    }

    private BudgetItemTemplate createPersonnelCategory() {
        BudgetItemTemplate template = new BudgetItemTemplate();
        template.setCode(PERSONNEL_CODE);
        template.setName("Wynagrodzenia");
        template.setDescription("Wynagrodzenia personelu merytorycznego i zarządczego");
        template.setDefaultCode(PERSONNEL_CODE);
        template.setDefaultCategory(BudgetItemTemplate.CategoryType.PERSONNEL);
        return budgetItemTemplateRepository.save(template);
    }

    private static String months(List<BigDecimal> months) {
        return months.stream().map(BigDecimal::toPlainString).collect(java.util.stream.Collectors.joining(" | "));
    }

    private static String key(String last, String first) {
        return (last == null ? "" : last.trim().toLowerCase(Locale.ROOT))
                + "|" + (first == null ? "" : first.trim().toLowerCase(Locale.ROOT));
    }
}
