package pl.ngo.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.dto.BudgetDashboardDto;
import pl.ngo.budget.dto.NewBudgetMonthSaveCommand;
import pl.ngo.budget.dto.NewBudgetPlanSaveCommand;
import pl.ngo.budget.dto.NewBudgetWizardDto;
import pl.ngo.budget.dto.NewBudgetWizardDto.NewBudgetCategoryDto;
import pl.ngo.budget.dto.NewBudgetWizardDto.NewBudgetLineDto;
import pl.ngo.budget.entity.cost.CostAllocation;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.repository.BudgetItemTemplateRepository;
import pl.ngo.budget.repository.CostAllocationRepository;
import pl.ngo.budget.repository.EmployeeRepository;
import pl.ngo.budget.util.MonthlySplit;
import pl.ngo.budget.util.PolishMonthNames;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NewBudgetService {

    private static final String PERSONNEL_CODE = "KOSZT_PER";
    private static final String PROMOTION_CODE = "KOSZT_PROM";
    private static final String EQUIPMENT_CODE = "KOSZT_SPRZ";

    private static final List<String> PROMOTION_LABELS = List.of(
            "Social Media", "Publikacje", "Ogłoszenia", "Wynagrodzenia");
    private static final List<String> EQUIPMENT_LABELS = List.of(
            "Sprzęt komputerowy", "Meble", "Inne");

    private final CostAllocationRepository costAllocationRepository;
    private final BudgetItemTemplateRepository budgetItemTemplateRepository;
    private final EmployeeRepository employeeRepository;
    private final BudgetSetupService budgetSetupService;
    private final BudgetMatrixService budgetMatrixService;

    public NewBudgetService(CostAllocationRepository costAllocationRepository,
                            BudgetItemTemplateRepository budgetItemTemplateRepository,
                            EmployeeRepository employeeRepository,
                            BudgetSetupService budgetSetupService,
                            BudgetMatrixService budgetMatrixService) {
        this.costAllocationRepository = costAllocationRepository;
        this.budgetItemTemplateRepository = budgetItemTemplateRepository;
        this.employeeRepository = employeeRepository;
        this.budgetSetupService = budgetSetupService;
        this.budgetMatrixService = budgetMatrixService;
    }

    @Transactional
    public void prepareYear(int fiscalYear, Integer copyFromYear) {
        if (!costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(fiscalYear).isEmpty()
                || costAllocationRepository.existsMonthlyPlanForFiscalYear(fiscalYear)) {
            costAllocationRepository.deletePlanAllocationsByFiscalYear(fiscalYear);
        }
        budgetSetupService.ensureGrantBudgetItems();

        if (copyFromYear != null) {
            if (!budgetSetupService.yearHasData(copyFromYear)) {
                throw new IllegalStateException("Brak danych budżetowych dla roku " + copyFromYear + ".");
            }
            List<CostAllocation> source = costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(copyFromYear);
            for (CostAllocation src : source) {
                costAllocationRepository.save(copyAnnualLine(src, fiscalYear));
            }
            return;
        }

        List<CostAllocation> existing = costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(fiscalYear);
        if (!existing.isEmpty()) {
            return;
        }

        seedDefaultAnnualPlan(fiscalYear);
    }

    @Transactional(readOnly = true)
    public NewBudgetWizardDto loadPlanStep(int fiscalYear) {
        NewBudgetWizardDto dto = baseWizard(fiscalYear, "plan", null);
        dto.setCategories(buildPlanCategories(fiscalYear));
        return dto;
    }

    @Transactional
    public void savePlanStep(int fiscalYear, NewBudgetPlanSaveCommand command) {
        boolean hadMonthlyPlan = costAllocationRepository.existsMonthlyPlanForFiscalYear(fiscalYear);
        costAllocationRepository.deletePlanAllocationsByFiscalYear(fiscalYear);
        if (command == null || command.getCategories() == null) {
            return;
        }

        Map<Long, BudgetItemTemplate> templatesById = budgetItemTemplateRepository.findAll().stream()
                .collect(Collectors.toMap(BudgetItemTemplate::getId, Function.identity()));

        for (NewBudgetPlanSaveCommand.CategoryCommand categoryCommand : command.getCategories()) {
            BudgetItemTemplate category = resolveCategory(categoryCommand, templatesById);
            if (category == null) {
                continue;
            }
            templatesById.put(category.getId(), category);
            if (categoryCommand.getLines() == null) {
                continue;
            }
            for (NewBudgetPlanSaveCommand.LineCommand lineCommand : categoryCommand.getLines()) {
                if (lineCommand.isNewLine() && isBlank(lineCommand.getLabel())) {
                    continue;
                }
                String label = normalize(lineCommand.getLabel());
                if (label == null && lineCommand.getEmployeeId() == null) {
                    continue;
                }
                BigDecimal amount = lineCommand.getAnnualAmount() != null
                        ? lineCommand.getAnnualAmount()
                        : BigDecimal.ZERO;
                CostAllocation allocation = new CostAllocation();
                allocation.setCategory(category);
                allocation.setLabel(label);
                allocation.setEmployee(resolveEmployee(lineCommand.getEmployeeId()));
                allocation.setAdminGroup(normalize(lineCommand.getAdminGroup()));
                allocation.setAmount(amount);
                allocation.setPercentage(BigDecimal.valueOf(100));
                allocation.setFiscalYear(fiscalYear);
                allocation.setPlanMonth(null);
                boolean evenSplit = MonthlySplit.isSalaryOrAdminCategory(category.getCode());
                allocation.setSplitToMonths(evenSplit || lineCommand.isSplitToMonths());
                allocation.setActive(true);
                costAllocationRepository.save(allocation);
            }
        }
        if (hadMonthlyPlan) {
            budgetSetupService.splitSalaryAndAdminAnnualLines(fiscalYear, true);
        }
        budgetSetupService.refreshPersonnelPlannedCosts(fiscalYear);
    }

    @Transactional(readOnly = true)
    public NewBudgetWizardDto loadMonthStep(int fiscalYear, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Miesiąc poza zakresem 1–12: " + month);
        }
        NewBudgetWizardDto dto = baseWizard(fiscalYear, "month", month);
        dto.setCurrentMonthName(PolishMonthNames.of(month));
        dto.setCategories(buildMonthCategories(fiscalYear, month));
        return dto;
    }

    @Transactional
    public void saveMonthStep(int fiscalYear, int month, NewBudgetMonthSaveCommand command) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Miesiąc poza zakresem 1–12: " + month);
        }
        List<CostAllocation> annualLines = costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(fiscalYear);
        Map<Long, CostAllocation> annualById = annualLines.stream()
                .collect(Collectors.toMap(CostAllocation::getId, Function.identity()));

        Map<Long, BigDecimal> amountsByAnnualId = new HashMap<>();
        if (command != null && command.getCategories() != null) {
            for (NewBudgetMonthSaveCommand.CategoryCommand category : command.getCategories()) {
                if (category.getLines() == null) {
                    continue;
                }
                for (NewBudgetMonthSaveCommand.LineCommand line : category.getLines()) {
                    if (line.getAnnualAllocationId() == null) {
                        continue;
                    }
                    amountsByAnnualId.put(
                            line.getAnnualAllocationId(),
                            line.getMonthAmount() != null ? line.getMonthAmount() : BigDecimal.ZERO);
                }
            }
        }

        List<CostAllocation> existingMonth = costAllocationRepository
                .findPlanAllocationsByFiscalYearAndPlanMonth(fiscalYear, month);
        Map<String, CostAllocation> existingByKey = existingMonth.stream()
                .collect(Collectors.toMap(this::lineKey, Function.identity(), (a, b) -> a));

        for (CostAllocation annual : annualLines) {
            BigDecimal submitted = amountsByAnnualId.get(annual.getId());
            if (isEvenSplitCategory(annual) && submitted != null) {
                BigDecimal typed = submitted.setScale(2, RoundingMode.HALF_UP);
                CostAllocation stored = existingByKey.get(lineKey(annual));
                BigDecimal baseline = stored != null && stored.getAmount() != null
                        ? stored.getAmount().setScale(2, RoundingMode.HALF_UP)
                        : MonthlySplit.shareForMonth(annual.getAmount(), month);
                if (typed.compareTo(baseline) != 0) {
                    budgetSetupService.applyEqualMonthlyShareFromAnnual(annual, typed);
                    continue;
                }
            }
            BigDecimal monthAmount = resolveMonthAmount(annual, month, submitted);
            String key = lineKey(annual);
            CostAllocation monthly = existingByKey.get(key);
            if (monthly == null) {
                monthly = copyAnnualLine(annual, fiscalYear);
                monthly.setPlanMonth(month);
            }
            monthly.setAmount(monthAmount);
            monthly.setSplitToMonths(annual.isSplitToMonths());
            costAllocationRepository.save(monthly);
        }
    }

    @Transactional(readOnly = true)
    public BudgetDashboardDto loadReview(int fiscalYear) {
        return budgetMatrixService.getBudgetDashboardDataForYear(fiscalYear);
    }

    public boolean isWizardComplete(int fiscalYear) {
        return costAllocationRepository.existsMonthlyPlanForFiscalYear(fiscalYear)
                && !costAllocationRepository.findPlanAllocationsByFiscalYearAndPlanMonth(fiscalYear, 12).isEmpty();
    }

    private NewBudgetWizardDto baseWizard(int fiscalYear, String step, Integer month) {
        NewBudgetWizardDto dto = new NewBudgetWizardDto();
        dto.setFiscalYear(fiscalYear);
        dto.setStep(step);
        dto.setCurrentMonth(month);
        return dto;
    }

    private List<NewBudgetCategoryDto> buildPlanCategories(int fiscalYear) {
        List<CostAllocation> annualLines = costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(fiscalYear);
        Map<Long, List<CostAllocation>> byCategory = annualLines.stream()
                .collect(Collectors.groupingBy(a -> a.getCategory().getId(), LinkedHashMap::new, Collectors.toList()));

        List<NewBudgetCategoryDto> categories = new ArrayList<>();
        for (BudgetItemTemplate template : budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc()) {
            NewBudgetCategoryDto categoryDto = new NewBudgetCategoryDto();
            categoryDto.setTemplateId(template.getId());
            categoryDto.setName(template.getName());
            categoryDto.setEvenMonthlySplit(MonthlySplit.isSalaryOrAdminCategory(template.getCode()));

            List<CostAllocation> lines = byCategory.getOrDefault(template.getId(), List.of());
            if (lines.isEmpty()) {
                categoryDto.setLines(defaultLinesForCategory(template));
            } else {
                for (CostAllocation allocation : lines) {
                    categoryDto.getLines().add(toPlanLine(allocation));
                }
            }
            categoryDto.getLines().add(newLinePlaceholder());
            categories.add(categoryDto);
        }
        return categories;
    }

    private List<NewBudgetCategoryDto> buildMonthCategories(int fiscalYear, int month) {
        List<CostAllocation> annualLines = costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(fiscalYear);
        Map<String, BigDecimal> monthAmounts = costAllocationRepository
                .findPlanAllocationsByFiscalYearAndPlanMonth(fiscalYear, month).stream()
                .collect(Collectors.toMap(this::lineKeyFromMonthly, CostAllocation::getAmount, (a, b) -> a));
        Map<String, BigDecimal> monthlyTotals = sumMonthlyTotals(fiscalYear);

        Map<Long, List<CostAllocation>> byCategory = annualLines.stream()
                .collect(Collectors.groupingBy(a -> a.getCategory().getId(), LinkedHashMap::new, Collectors.toList()));

        List<NewBudgetCategoryDto> categories = new ArrayList<>();
        for (BudgetItemTemplate template : budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc()) {
            List<CostAllocation> lines = byCategory.get(template.getId());
            if (lines == null || lines.isEmpty()) {
                continue;
            }
            NewBudgetCategoryDto categoryDto = new NewBudgetCategoryDto();
            categoryDto.setTemplateId(template.getId());
            categoryDto.setName(template.getName());
            for (CostAllocation annual : lines) {
                NewBudgetLineDto lineDto = toPlanLine(annual);
                String key = lineKey(annual);
                BigDecimal monthAmount = monthAmounts.get(key);
                if (monthAmount == null) {
                    monthAmount = defaultMonthAmount(annual, month);
                }
                lineDto.setMonthAmount(monthAmount);
                BigDecimal allocated = monthlyTotals.getOrDefault(key, BigDecimal.ZERO);
                if (!monthAmounts.containsKey(key)) {
                    allocated = allocated.add(monthAmount);
                }
                lineDto.setRemainingAmount(annual.getAmount().subtract(allocated));
                categoryDto.getLines().add(lineDto);
            }
            categories.add(categoryDto);
        }
        return categories;
    }

    private Map<String, BigDecimal> sumMonthlyTotals(int fiscalYear) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (int m = 1; m <= 12; m++) {
            for (CostAllocation allocation : costAllocationRepository
                    .findPlanAllocationsByFiscalYearAndPlanMonth(fiscalYear, m)) {
                totals.merge(lineKeyFromMonthly(allocation), allocation.getAmount(), BigDecimal::add);
            }
        }
        return totals;
    }

    private BigDecimal resolveMonthAmount(CostAllocation annual, int month, BigDecimal submitted) {
        if (submitted != null) {
            return submitted;
        }
        List<CostAllocation> existing = costAllocationRepository
                .findPlanAllocationsByFiscalYearAndPlanMonth(annual.getFiscalYear(), month);
        for (CostAllocation row : existing) {
            if (lineKey(annual).equals(lineKeyFromMonthly(row))) {
                return row.getAmount();
            }
        }
        return defaultMonthAmount(annual, month);
    }

    private BigDecimal defaultMonthAmount(CostAllocation annual, int month) {
        if (!annual.isSplitToMonths() || annual.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        if (isEvenSplitCategory(annual)) {
            return MonthlySplit.shareForMonth(annual.getAmount(), month);
        }
        return annual.getAmount()
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    private static boolean isEvenSplitCategory(CostAllocation allocation) {
        return allocation.getCategory() != null
                && MonthlySplit.isSalaryOrAdminCategory(allocation.getCategory().getCode());
    }

    private List<NewBudgetLineDto> defaultLinesForCategory(BudgetItemTemplate template) {
        List<NewBudgetLineDto> lines = new ArrayList<>();
        String code = template.getCode();
        if (PERSONNEL_CODE.equals(code)) {
            employeeRepository.findAll().stream()
                    .filter(e -> e.getEmail() == null || !"system@ngo.pl".equals(e.getEmail()))
                    .sorted(Comparator.comparing(Employee::getLastName, Comparator.nullsLast(String::compareTo))
                            .thenComparing(Employee::getFirstName, Comparator.nullsLast(String::compareTo)))
                    .forEach(employee -> {
                        NewBudgetLineDto line = new NewBudgetLineDto();
                        line.setEmployeeId(employee.getId());
                        line.setLabel(employeeLabel(employee));
                        line.setAnnualAmount(employee.getPlannedCost() != null
                                ? employee.getPlannedCost()
                                : BigDecimal.ZERO);
                        line.setSplitToMonths(true);
                        lines.add(line);
                    });
        } else if (PROMOTION_CODE.equals(code)) {
            for (String label : PROMOTION_LABELS) {
                NewBudgetLineDto line = new NewBudgetLineDto();
                line.setLabel(label);
                lines.add(line);
            }
        } else if (EQUIPMENT_CODE.equals(code)) {
            for (String label : EQUIPMENT_LABELS) {
                NewBudgetLineDto line = new NewBudgetLineDto();
                line.setLabel(label);
                lines.add(line);
            }
        }
        return lines;
    }

    private NewBudgetLineDto newLinePlaceholder() {
        NewBudgetLineDto line = new NewBudgetLineDto();
        line.setNewLine(true);
        line.setLabel("");
        return line;
    }

    private NewBudgetLineDto toPlanLine(CostAllocation allocation) {
        NewBudgetLineDto line = new NewBudgetLineDto();
        line.setAllocationId(allocation.getId());
        if (allocation.getEmployee() != null) {
            line.setEmployeeId(allocation.getEmployee().getId());
            line.setLabel(employeeLabel(allocation.getEmployee()));
        } else {
            line.setLabel(allocation.getLabel());
        }
        line.setAdminGroup(allocation.getAdminGroup());
        line.setAnnualAmount(allocation.getAmount() != null ? allocation.getAmount() : BigDecimal.ZERO);
        line.setSplitToMonths(isEvenSplitCategory(allocation) || allocation.isSplitToMonths());
        return line;
    }

    private void seedDefaultAnnualPlan(int fiscalYear) {
        for (BudgetItemTemplate template : budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc()) {
            for (NewBudgetLineDto line : defaultLinesForCategory(template)) {
                CostAllocation allocation = new CostAllocation();
                allocation.setCategory(template);
                allocation.setLabel(normalize(line.getLabel()));
                allocation.setEmployee(resolveEmployee(line.getEmployeeId()));
                allocation.setAmount(line.getAnnualAmount());
                allocation.setPercentage(BigDecimal.valueOf(100));
                allocation.setFiscalYear(fiscalYear);
                allocation.setActive(true);
                costAllocationRepository.save(allocation);
            }
        }
    }

    private CostAllocation copyAnnualLine(CostAllocation src, int fiscalYear) {
        CostAllocation copy = new CostAllocation();
        copy.setEmployee(src.getEmployee());
        copy.setProject(src.getProject());
        copy.setCategory(src.getCategory());
        copy.setAmount(src.getAmount());
        copy.setPercentage(src.getPercentage());
        copy.setAdminGroup(src.getAdminGroup());
        copy.setLabel(src.getLabel());
        copy.setFiscalYear(fiscalYear);
        copy.setPlanMonth(src.getPlanMonth());
        copy.setSplitToMonths(src.isSplitToMonths());
        copy.setActive(true);
        return copy;
    }

    private BudgetItemTemplate resolveCategory(NewBudgetPlanSaveCommand.CategoryCommand command,
                                               Map<Long, BudgetItemTemplate> templatesById) {
        if (command.isNewCategory()) {
            String name = normalize(command.getName());
            if (name == null) {
                return null;
            }
            return budgetItemTemplateRepository.findByName(name)
                    .orElseGet(() -> {
                        BudgetItemTemplate template = new BudgetItemTemplate();
                        template.setName(name);
                        template.setCode(generateTemplateCode(name));
                        template.setDefaultCode(template.getCode());
                        template.setDefaultCategory(BudgetItemTemplate.CategoryType.OTHER);
                        template.setDisplayOrder(nextDisplayOrder());
                        return budgetItemTemplateRepository.save(template);
                    });
        }
        if (command.getTemplateId() == null) {
            return null;
        }
        return templatesById.get(command.getTemplateId());
    }

    private int nextDisplayOrder() {
        return budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .mapToInt(BudgetItemTemplate::getDisplayOrder)
                .max()
                .orElse(0) + 10;
    }

    private static String generateTemplateCode(String name) {
        String slug = name.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (slug.isBlank()) {
            slug = "KATEGORIA";
        }
        if (slug.length() > 40) {
            slug = slug.substring(0, 40);
        }
        return "KOSZT_" + slug;
    }

    private Employee resolveEmployee(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return employeeRepository.findById(employeeId).orElse(null);
    }

    private String lineKey(CostAllocation annual) {
        return annual.getCategory().getId()
                + "|" + Objects.toString(annual.getLabel(), "")
                + "|" + (annual.getEmployee() != null ? annual.getEmployee().getId() : "")
                + "|" + Objects.toString(annual.getAdminGroup(), "");
    }

    private String lineKeyFromMonthly(CostAllocation monthly) {
        return monthly.getCategory().getId()
                + "|" + Objects.toString(monthly.getLabel(), "")
                + "|" + (monthly.getEmployee() != null ? monthly.getEmployee().getId() : "")
                + "|" + Objects.toString(monthly.getAdminGroup(), "");
    }

    private static String employeeLabel(Employee employee) {
        return (employee.getFirstName() != null ? employee.getFirstName() : "")
                + " " + (employee.getLastName() != null ? employee.getLastName() : "").trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
