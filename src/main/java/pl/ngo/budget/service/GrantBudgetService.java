package pl.ngo.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.dto.GrantBudgetSaveCommand;
import pl.ngo.budget.dto.GrantBudgetViewDto;
import pl.ngo.budget.dto.GrantSaveCommand;
import pl.ngo.budget.dto.GrantBudgetViewDto.CoveredOrgBudgetLineDto;
import pl.ngo.budget.dto.GrantBudgetViewDto.CategoryOptionDto;
import pl.ngo.budget.dto.GrantBudgetViewDto.GrantBudgetLineDto;
import pl.ngo.budget.dto.GrantListRowDto;
import pl.ngo.budget.dto.OrgBudgetLineOptionDto;
import pl.ngo.budget.entity.cost.CostAllocation;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.cost.Expenditure;
import pl.ngo.budget.entity.coverage.*;
import pl.ngo.budget.repository.BudgetItemTemplateRepository;
import pl.ngo.budget.repository.CostAllocationRepository;
import pl.ngo.budget.repository.EmployeeRepository;
import pl.ngo.budget.repository.ExpenditureRepository;
import pl.ngo.budget.repository.GrantRepository;
import pl.ngo.budget.repository.PlannedEventRepository;
import pl.ngo.budget.repository.PublicationRepository;
import pl.ngo.budget.repository.TravelBudgetLineRepository;
import pl.ngo.budget.util.GrantCoverageYear;
import pl.ngo.budget.util.GrantPeriodCoverage;
import pl.ngo.budget.util.GrantYearSpendable;
import pl.ngo.budget.util.MonthlySplit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GrantBudgetService {

    private static final String CODE_PER = "KOSZT_PER";
    private static final String CODE_MAT = "KOSZT_MAT";
    private static final String CODE_KSW = "KOSZT_KSW";
    private static final String CODE_LOG = "KOSZT_LOG";
    private static final String CODE_ADM = "KOSZT_ADM";
    private static final String CODE_PROM = "KOSZT_PROM";
    private static final String CODE_SPRZ = "KOSZT_SPRZ";
    private static final String ROW = "DASHBOARD_ROW";
    static final String COVERAGE_LABEL_PREFIX = "grant-coverage:";

    static boolean isSalaryCoveragePlan(CostAllocation allocation) {
        return allocation != null
                && allocation.getLabel() != null
                && allocation.getLabel().startsWith(COVERAGE_LABEL_PREFIX);
    }

    private final GrantRepository grantRepository;
    private final CostAllocationRepository costAllocationRepository;
    private final BudgetItemTemplateRepository budgetItemTemplateRepository;
    private final EmployeeRepository employeeRepository;
    private final PublicationRepository publicationRepository;
    private final PlannedEventRepository plannedEventRepository;
    private final TravelBudgetLineRepository travelBudgetLineRepository;
    private final ExpenditureRepository expenditureRepository;
    private final BudgetSetupService budgetSetupService;

    public GrantBudgetService(GrantRepository grantRepository,
                              CostAllocationRepository costAllocationRepository,
                              BudgetItemTemplateRepository budgetItemTemplateRepository,
                              EmployeeRepository employeeRepository,
                              PublicationRepository publicationRepository,
                              PlannedEventRepository plannedEventRepository,
                              TravelBudgetLineRepository travelBudgetLineRepository,
                              ExpenditureRepository expenditureRepository,
                              BudgetSetupService budgetSetupService) {
        this.grantRepository = grantRepository;
        this.costAllocationRepository = costAllocationRepository;
        this.budgetItemTemplateRepository = budgetItemTemplateRepository;
        this.employeeRepository = employeeRepository;
        this.publicationRepository = publicationRepository;
        this.plannedEventRepository = plannedEventRepository;
        this.travelBudgetLineRepository = travelBudgetLineRepository;
        this.expenditureRepository = expenditureRepository;
        this.budgetSetupService = budgetSetupService;
    }

    @Transactional(readOnly = true)
    public GrantBudgetViewDto buildGrantBudgetView(Long grantId) {
        Grant grant = grantRepository.findByIdWithBudgetItems(grantId)
                .orElseThrow(() -> new IllegalArgumentException("Grant nie istnieje"));

        int fiscalYear = resolveFiscalYear(grant);
        List<CostAllocation> allocations = costAllocationRepository.findPlanAllocationsByFiscalYear(fiscalYear);
        Map<Long, BigDecimal> spentByBudgetItemId = sumExpendituresByBudgetItem(
                expenditureRepository.findByGrantIdAndFiscalYear(grantId, fiscalYear));

        GrantBudgetViewDto dto = new GrantBudgetViewDto();
        dto.setGrantId(grant.getId());
        dto.setGrantCode(grant.getCode());
        dto.setGrantName(grant.getName());
        dto.setSponsorName(grant.getSponsor() != null ? grant.getSponsor().getName() : null);
        dto.setProjectName(grant.getProject() != null ? grant.getProject().getName() : null);
        dto.setTotalAmount(grant.getTotalAmount());
        dto.setStartDate(grant.getStartDate());
        dto.setEndDate(grant.getEndDate());
        dto.setAmountPerActiveMonth(MonthlySplit.amountPerActiveMonth(
                grant.getTotalAmount(),
                GrantPeriodCoverage.activeMonths(grant.getStartDate(), grant.getEndDate()).size()));
        dto.getOrgBudgetLineOptions().addAll(buildOrgBudgetLineOptions(allocations));
        for (BudgetItemTemplate template : budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc()) {
            CategoryOptionDto category = new CategoryOptionDto();
            category.setCode(template.getCode());
            category.setName(template.getName());
            dto.getCategoryOptions().add(category);
        }

        grant.getBudgetItems().stream()
                .filter(GrantBudgetItem::isActive)
                .sorted(Comparator.comparing(GrantBudgetItem::getCode, Comparator.nullsLast(String::compareTo))
                        .thenComparing(GrantBudgetItem::getName))
                .map(item -> toLineDto(item, spentByBudgetItemId, GrantCoverageYear.splits(grant)))
                .forEach(line -> dto.getLines().add(line));

        if (grant.getTranches() != null) {
            grant.getTranches().stream()
                    .sorted(Comparator.comparingInt(GrantTranche::getTrancheNumber))
                    .map(this::toTrancheDto)
                    .forEach(tranche -> dto.getTranches().add(tranche));
        }

        BigDecimal planned = dto.getLines().stream()
                .map(line -> line.getPlannedAmount() != null ? line.getPlannedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = dto.getTotalAmount() != null ? dto.getTotalAmount() : BigDecimal.ZERO;
        boolean split = GrantCoverageYear.splits(grant);
        BigDecimal coverage = coveragePlanTotal(dto, false);
        BigDecimal coverage2027 = split ? coveragePlanTotal(dto, true) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal coverageAll = coverage.add(coverage2027);
        dto.setCoverageSplitByYear(split);
        dto.setPlanTotal(planned);
        dto.setUnallocated(total.subtract(planned));
        dto.setCoveragePlanTotal(coverage);
        dto.setCoveragePlanTotal2027(coverage2027);
        dto.setCoverageUnallocated(total.subtract(coverageAll));
        dto.setLeftTotal(planned.subtract(coverageAll));

        return dto;
    }

    @Transactional
    public void saveGrantBudget(Long grantId, GrantBudgetSaveCommand command) {
        Grant grant = grantRepository.findByIdWithBudgetItems(grantId)
                .orElseThrow(() -> new IllegalArgumentException("Grant nie istnieje"));

        if (command.getTotalAmount() != null) {
            grant.setTotalAmount(command.getTotalAmount());
        }
        if (command.getStartDate() != null) {
            grant.setStartDate(command.getStartDate());
        }
        if (command.getEndDate() != null) {
            grant.setEndDate(command.getEndDate());
        }

        if (command.getItems() == null) {
            grantRepository.save(grant);
            return;
        }

        Map<Long, GrantBudgetItem> itemsById = new HashMap<>();
        for (GrantBudgetItem item : grant.getBudgetItems()) {
            if (item.getId() != null) {
                itemsById.put(item.getId(), item);
            }
        }

        java.util.Set<Long> submittedIds = new java.util.HashSet<>();
        for (GrantBudgetSaveCommand.ItemCommand itemCommand : command.getItems()) {
            GrantBudgetItem item;
            if (itemCommand.getBudgetItemId() != null) {
                item = itemsById.get(itemCommand.getBudgetItemId());
                if (item == null) {
                    continue;
                }
                submittedIds.add(item.getId());
            } else {
                String name = itemCommand.getName() != null ? itemCommand.getName().trim() : "";
                if (name.isBlank()) {
                    continue;
                }
                item = new GrantBudgetItem();
                item.setGrant(grant);
                item.setActive(true);
                grant.getBudgetItems().add(item);
            }
            if (itemCommand.getName() != null && !itemCommand.getName().isBlank()) {
                item.setName(itemCommand.getName().trim());
            }
            if (itemCommand.getCode() != null && !itemCommand.getCode().isBlank()) {
                item.setCode(itemCommand.getCode().trim());
                item.setAccountingCode(item.getCode());
            }
            item.setPlannedAmount(itemCommand.getPlannedAmount() != null
                    ? itemCommand.getPlannedAmount()
                    : BigDecimal.ZERO);
            item.setActive(true);
            item.getCoverages().clear();
            if (itemCommand.getCoverages() != null) {
                for (GrantBudgetSaveCommand.CoverageCommand coverageCommand : itemCommand.getCoverages()) {
                    resolveCoverageSource(coverageCommand);
                    if (coverageCommand.getOrgSourceType() == null) {
                        continue;
                    }
                    boolean hasId = coverageCommand.getOrgSourceId() != null;
                    boolean hasKey = coverageCommand.getOrgSourceKey() != null
                            && !coverageCommand.getOrgSourceKey().isBlank();
                    if (!hasId && !hasKey) {
                        continue;
                    }
                    OrgBudgetSourceType sourceType = OrgBudgetSourceType.valueOf(coverageCommand.getOrgSourceType());
                    GrantBudgetItemCoverage coverage = new GrantBudgetItemCoverage();
                    coverage.setGrantBudgetItem(item);
                    coverage.setOrgSourceType(sourceType);
                    coverage.setOrgSourceId(hasId ? coverageCommand.getOrgSourceId() : 0L);
                    coverage.setOrgSourceKey(hasKey ? coverageCommand.getOrgSourceKey().trim() : null);
                    coverage.setOrgLabel(coverageCommand.getLabel() != null && !coverageCommand.getLabel().isBlank()
                            ? coverageCommand.getLabel().trim()
                            : resolveOrgLabel(sourceType, coverageCommand.getOrgSourceId(), coverage.getOrgSourceKey()));
                    coverage.setCoveredAmount(coverageCommand.getAmount() != null
                            ? coverageCommand.getAmount()
                            : BigDecimal.ZERO);
                    if (GrantCoverageYear.splits(grant)) {
                        coverage.setCoveredAmount2027(coverageCommand.getAmount2027() != null
                                ? coverageCommand.getAmount2027()
                                : BigDecimal.ZERO);
                    } else {
                        coverage.setCoveredAmount2027(null);
                    }
                    item.getCoverages().add(coverage);
                }
            }
        }
        for (GrantBudgetItem item : grant.getBudgetItems()) {
            if (item.getId() != null && !submittedIds.contains(item.getId())) {
                item.setActive(false);
            }
        }

        grant.getTranches().size();
        budgetSetupService.replaceGrantTranches(grant, toTrancheCommands(command.getTranches()));
        grantRepository.save(grant);
        syncEmployeeCoverageMonths(grant);
    }

    private GrantBudgetViewDto.TrancheDto toTrancheDto(GrantTranche tranche) {
        GrantBudgetViewDto.TrancheDto dto = new GrantBudgetViewDto.TrancheDto();
        dto.setId(tranche.getId());
        dto.setPlannedDate(tranche.getPlannedDate());
        dto.setPlannedAmount(tranche.getPlannedAmount());
        dto.setReceived(tranche.isReceived());
        dto.setReceivedDate(tranche.getReceivedDate());
        dto.setReceivedAmount(tranche.getReceivedAmount());
        return dto;
    }

    private List<GrantSaveCommand.TrancheCommand> toTrancheCommands(List<GrantBudgetSaveCommand.TrancheCommand> source) {
        List<GrantSaveCommand.TrancheCommand> commands = new ArrayList<>();
        if (source == null) {
            return commands;
        }
        for (GrantBudgetSaveCommand.TrancheCommand row : source) {
            GrantSaveCommand.TrancheCommand command = new GrantSaveCommand.TrancheCommand();
            command.setId(row.getId());
            command.setPlannedDate(row.getPlannedDate());
            command.setPlannedAmount(row.getPlannedAmount());
            command.setReceived(row.isReceived());
            command.setReceivedDate(row.getReceivedDate());
            command.setReceivedAmount(row.getReceivedAmount());
            commands.add(command);
        }
        return commands;
    }

    /** Istniejące plany pokrycia wynagrodzeń, także zapisane wcześniej, rozpisane na miesiące grantu. */
    @Transactional
    public void syncAllEmployeeCoverageMonths() {
        for (Grant grant : grantRepository.findAllWithBudgetItems()) {
            if (grant.getProject() != null) {
                grant.getProject().getId();
            }
            for (GrantBudgetItem item : grant.getBudgetItems()) {
                if (item.getCoverages() != null) {
                    item.getCoverages().size();
                }
            }
            syncEmployeeCoverageMonths(grant);
        }
    }

    /**
     * Plan pokrycia wynagrodzeń dzielony przez liczbę miesięcy, w których grant jest aktywny w danym roku.
     * W 2026 przy trzech miesiącach 9 000 daje 3 000 w każdym z nich. Rok zostaje przy pełnej kwocie.
     */
    private void syncEmployeeCoverageMonths(Grant grant) {
        String label = COVERAGE_LABEL_PREFIX + grant.getId();
        costAllocationRepository.deleteCoveragePlanByLabel(label);
        if (grant.getProject() == null || grant.getBudgetItems() == null) {
            return;
        }
        List<YearMonth> months = GrantPeriodCoverage.activeMonths(grant.getStartDate(), grant.getEndDate());
        if (months.isEmpty()) {
            return;
        }
        BudgetItemTemplate personnel = budgetItemTemplateRepository.findByCode(CODE_PER).orElse(null);
        if (personnel == null) {
            return;
        }
        Map<Integer, List<YearMonth>> monthsByYear = new LinkedHashMap<>();
        for (YearMonth month : months) {
            monthsByYear.computeIfAbsent(month.getYear(), ignored -> new ArrayList<>()).add(month);
        }
        boolean split = GrantCoverageYear.splits(grant);
        Map<Long, Map<Integer, BigDecimal>> byEmployeeYear = new LinkedHashMap<>();
        for (GrantBudgetItem item : grant.getBudgetItems()) {
            if (!item.isActive() || !CODE_PER.equals(item.getCode()) || item.getCoverages() == null) {
                continue;
            }
            for (GrantBudgetItemCoverage coverage : item.getCoverages()) {
                if (coverage.getOrgSourceType() != OrgBudgetSourceType.EMPLOYEE
                        || coverage.getOrgSourceId() == null
                        || coverage.getOrgSourceId() == 0L) {
                    continue;
                }
                if (split) {
                    addEmployeeYearAmount(byEmployeeYear, coverage.getOrgSourceId(), 2026, coverage.getCoveredAmount());
                    addEmployeeYearAmount(byEmployeeYear, coverage.getOrgSourceId(), 2027, coverage.getCoveredAmount2027());
                } else {
                    for (Integer year : monthsByYear.keySet()) {
                        addEmployeeYearAmount(byEmployeeYear, coverage.getOrgSourceId(), year, coverage.getCoveredAmount());
                    }
                }
            }
        }
        for (Map.Entry<Long, Map<Integer, BigDecimal>> entry : byEmployeeYear.entrySet()) {
            Employee employee = employeeRepository.findById(entry.getKey()).orElse(null);
            if (employee == null) {
                continue;
            }
            for (List<YearMonth> yearMonths : monthsByYear.values()) {
                BigDecimal yearAmount = entry.getValue().get(yearMonths.get(0).getYear());
                if (yearAmount == null || yearAmount.signum() <= 0) {
                    continue;
                }
                List<BigDecimal> shares = MonthlySplit.sharesAcross(yearAmount, yearMonths.size());
                for (int i = 0; i < yearMonths.size(); i++) {
                    YearMonth month = yearMonths.get(i);
                    CostAllocation allocation = new CostAllocation();
                    allocation.setEmployee(employee);
                    allocation.setProject(grant.getProject());
                    allocation.setCategory(personnel);
                    allocation.setAmount(shares.get(i));
                    allocation.setPercentage(new BigDecimal("100.00"));
                    allocation.setFiscalYear(month.getYear());
                    allocation.setPlanMonth(month.getMonthValue());
                    allocation.setLabel(label);
                    allocation.setActive(true);
                    allocation.setSplitToMonths(false);
                    costAllocationRepository.save(allocation);
                }
            }
        }
    }

    private static void addEmployeeYearAmount(Map<Long, Map<Integer, BigDecimal>> byEmployeeYear,
                                               Long employeeId,
                                               int year,
                                               BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        byEmployeeYear.computeIfAbsent(employeeId, ignored -> new LinkedHashMap<>())
                .merge(year, amount, BigDecimal::add);
    }

    private GrantBudgetLineDto toLineDto(GrantBudgetItem item,
                                         Map<Long, BigDecimal> spentByBudgetItemId,
                                         boolean coverageSplitByYear) {
        GrantBudgetLineDto line = new GrantBudgetLineDto();
        line.setBudgetItemId(item.getId());
        line.setName(item.getName());
        line.setCode(item.getCode());
        line.setPlannedAmount(item.getPlannedAmount() != null ? item.getPlannedAmount() : BigDecimal.ZERO);
        line.setActualSpent(spentByBudgetItemId.getOrDefault(item.getId(), BigDecimal.ZERO));
        line.setRemaining(BigDecimal.ZERO);

        if (item.getCoverages() != null && !item.getCoverages().isEmpty()) {
            item.getCoverages().stream()
                    .sorted(Comparator.comparing(GrantBudgetItemCoverage::getOrgLabel))
                    .map(this::toCoverageDto)
                    .forEach(c -> line.getCoveredOrgLines().add(c));
        }
        if (line.getCoveredOrgLines().isEmpty()) {
            line.getCoveredOrgLines().add(new CoveredOrgBudgetLineDto());
        }

        BigDecimal covered = line.getCoveredOrgLines().stream()
                .map(coverage -> {
                    BigDecimal amount = coverage.getAmount() != null ? coverage.getAmount() : BigDecimal.ZERO;
                    if (coverageSplitByYear && coverage.getAmount2027() != null) {
                        amount = amount.add(coverage.getAmount2027());
                    }
                    return amount;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        line.setRemaining(line.getPlannedAmount().subtract(covered));
        line.setUnspent(line.getPlannedAmount().subtract(line.getActualSpent()));
        if (line.getPlannedAmount().signum() == 0) {
            line.setUnspentPercent(null);
        } else {
            line.setUnspentPercent(line.getUnspent()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(line.getPlannedAmount(), 1, RoundingMode.HALF_UP));
        }

        return line;
    }

    private List<OrgBudgetLineOptionDto> buildOrgBudgetLineOptions(List<CostAllocation> allocations) {
        List<OrgBudgetLineOptionDto> options = new ArrayList<>();

        employeeRepository.findAll().stream()
                .filter(employee -> employee.getEmail() == null || !"system@ngo.pl".equals(employee.getEmail()))
                .sorted(Comparator.comparing(Employee::getLastName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Employee::getFirstName, Comparator.nullsLast(String::compareTo)))
                .forEach(employee -> options.add(OrgBudgetLineOptionDto.of(
                        OrgBudgetSourceType.EMPLOYEE.name(), employee.getId(), CODE_PER, personName(employee))));

        publicationRepository.findByActiveTrueOrderByTitleAsc()
                .forEach(p -> options.add(OrgBudgetLineOptionDto.of(
                        OrgBudgetSourceType.PUBLICATION.name(), p.getId(), CODE_MAT, p.getTitle())));

        plannedEventRepository.findByActiveTrueOrderByEventDateAscTitleAsc()
                .forEach(e -> {
                    String label = e.getTitle();
                    if (e.getEventDate() != null) {
                        label = label + " (" + e.getEventDate() + ")";
                    }
                    options.add(OrgBudgetLineOptionDto.of(
                            OrgBudgetSourceType.PLANNED_EVENT.name(), e.getId(), CODE_KSW, label));
                });

        addRow(options, CODE_LOG, "podroze-krajowe", "Krajowe");
        addRow(options, CODE_LOG, "podroze-zagraniczne", "Zagraniczne");
        addRow(options, CODE_ADM, "admin-biuro", "Biuro");
        addRow(options, CODE_ADM, "admin-pozostale", "Pozostałe");
        addRow(options, CODE_ADM, "admin-wynagrodzenia", "Wynagrodzenia");
        addRow(options, CODE_PROM, "promocja-social-media", "Social Media");
        addRow(options, CODE_PROM, "promocja-publikacje", "Publikacje");
        addRow(options, CODE_PROM, "promocja-ogloszenia", "Ogłoszenia");
        addRow(options, CODE_PROM, "promocja-wynagrodzenia", "Wynagrodzenia");
        addRow(options, CODE_SPRZ, "sprzet-komputerowy", "Sprzęt komputerowy");
        addRow(options, CODE_SPRZ, "sprzet-meble", "Meble");
        addRow(options, CODE_SPRZ, "sprzet-inne", "Inne");

        java.util.Set<String> existingLabels = options.stream()
                .map(opt -> (opt.getCategoryCode() + "|" + opt.getLabel()).toLowerCase())
                .collect(java.util.stream.Collectors.toSet());
        for (CostAllocation allocation : allocations) {
            String code = categoryCode(allocation);
            if (code.isBlank() || CODE_PER.equals(code) || CODE_LOG.equals(code) || CODE_MAT.equals(code)) {
                continue;
            }
            String label = CODE_ADM.equals(code) ? adminLineLabel(allocation) : allocationLabel(allocation);
            if (label == null || label.isBlank()) {
                continue;
            }
            if (!existingLabels.add((code + "|" + label).toLowerCase())) {
                continue;
            }
            options.add(OrgBudgetLineOptionDto.of(
                    OrgBudgetSourceType.COST_ALLOCATION.name(), allocation.getId(), code, label));
        }

        options.sort(Comparator
                .comparing(OrgBudgetLineOptionDto::getCategoryCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(OrgBudgetLineOptionDto::getLabel, Comparator.nullsLast(String::compareTo)));
        return options;
    }

    private static void addRow(List<OrgBudgetLineOptionDto> options, String categoryCode, String rowKey, String label) {
        options.add(OrgBudgetLineOptionDto.ofKey(ROW, rowKey, categoryCode, label));
    }

    private static void resolveCoverageSource(GrantBudgetSaveCommand.CoverageCommand command) {
        if (command.getOrgSourceType() != null && command.getOrgSourceId() != null) {
            return;
        }
        if (command.getSourceRef() == null || command.getSourceRef().isBlank()) {
            return;
        }
        String[] parts = command.getSourceRef().split("\\|", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            return;
        }
        command.setOrgSourceType(parts[0]);
        try {
            command.setOrgSourceId(Long.parseLong(parts[1]));
        } catch (NumberFormatException ex) {
            command.setOrgSourceKey(parts[1]);
            command.setOrgSourceId(0L);
        }
    }

    private String resolveOrgLabel(OrgBudgetSourceType sourceType, Long sourceId, String sourceKey) {
        return switch (sourceType) {
            case DASHBOARD_ROW -> dashboardRowLabel(sourceKey);
            case BUDGET_CATEGORY -> budgetItemTemplateRepository.findById(sourceId)
                    .map(BudgetItemTemplate::getName)
                    .orElse("Kategoria budżetowa");
            case COST_ALLOCATION -> costAllocationRepository.findById(sourceId)
                    .map(a -> categoryCode(a).equals(CODE_ADM) ? adminLineLabel(a)
                            : categoryCode(a).equals(CODE_PER) && a.getEmployee() != null
                            ? personName(a.getEmployee())
                            : allocationLabel(a))
                    .orElse("Pozycja budżetu");
            case PUBLICATION -> publicationRepository.findById(sourceId)
                    .map(Publication::getTitle)
                    .orElse("Publikacja");
            case PLANNED_EVENT -> plannedEventRepository.findById(sourceId)
                    .map(PlannedEvent::getTitle)
                    .orElse("Wydarzenie");
            case TRAVEL_LINE -> travelBudgetLineRepository.findById(sourceId)
                    .map(GrantBudgetService::travelLabel)
                    .orElse("Podróż");
            case EMPLOYEE -> employeeRepository.findById(sourceId)
                    .map(this::personName)
                    .orElse("Pracownik");
        };
    }

    private static Long derivedPayrollEmployeeId(String key) {
        if (key == null) {
            return null;
        }
        for (String prefix : List.of("admin-wynagrodzenia-pracownik-", "promocja-wynagrodzenia-pracownik-")) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String id = key.substring(prefix.length());
            if (!id.isEmpty() && id.chars().allMatch(Character::isDigit)) {
                return Long.valueOf(id);
            }
        }
        return null;
    }

    private static String dashboardRowLabel(String key) {
        if (key == null) {
            return "Pozycja budżetu";
        }
        return switch (key) {
            case "podroze-krajowe" -> "Krajowe";
            case "podroze-zagraniczne" -> "Zagraniczne";
            case "admin-biuro" -> "Biuro";
            case "admin-pozostale" -> "Pozostałe";
            case "admin-wynagrodzenia", "promocja-wynagrodzenia" -> "Wynagrodzenia";
            case "promocja-menadzer-pr" -> "Menedżer PR i Marketingu";
            case "promocja-social-media" -> "Social Media";
            case "promocja-ogloszenia" -> "Ogłoszenia";
            case "promocja-publikacje" -> "Publikacje";
            case "sprzet-komputerowy" -> "Sprzęt komputerowy";
            case "sprzet-meble" -> "Meble";
            case "sprzet-inne" -> "Inne";
            default -> {
                int slash = key.indexOf('/');
                if (slash > 0 && slash < key.length() - 1) {
                    String parent = dashboardRowLabel(key.substring(0, slash));
                    String child = key.substring(slash + 1).replace('-', ' ');
                    yield parent.equals(key.substring(0, slash)) ? child : parent + " / " + child;
                }
                yield key;
            }
        };
    }

    private CoveredOrgBudgetLineDto toCoverageDto(GrantBudgetItemCoverage coverage) {
        CoveredOrgBudgetLineDto dto = new CoveredOrgBudgetLineDto();
        dto.setCoverageId(coverage.getId());
        dto.setOrgSourceType(coverage.getOrgSourceType() != null ? coverage.getOrgSourceType().name() : null);
        dto.setOrgSourceId(coverage.getOrgSourceId());
        dto.setOrgSourceKey(coverage.getOrgSourceKey());
        dto.setLabel(coverage.getOrgLabel());
        Long derivedEmployeeId = derivedPayrollEmployeeId(coverage.getOrgSourceKey());
        if (derivedEmployeeId != null) {
            dto.setOrgSourceType(OrgBudgetSourceType.EMPLOYEE.name());
            dto.setOrgSourceId(derivedEmployeeId);
            dto.setOrgSourceKey(null);
            dto.setSourceRef(OrgBudgetSourceType.EMPLOYEE.name() + "|" + derivedEmployeeId);
            dto.setLabel(employeeRepository.findById(derivedEmployeeId).map(this::personName).orElse(dto.getLabel()));
        } else if (coverage.getOrgSourceKey() != null && !coverage.getOrgSourceKey().isBlank()) {
            dto.setSourceRef(dto.getOrgSourceType() + "|" + coverage.getOrgSourceKey());
        } else if (dto.getOrgSourceType() != null && coverage.getOrgSourceId() != null) {
            dto.setSourceRef(dto.getOrgSourceType() + "|" + coverage.getOrgSourceId());
        }
        dto.setAmount(coverage.getCoveredAmount());
        dto.setAmount2027(coverage.getCoveredAmount2027());
        return dto;
    }

    private static int resolveFiscalYear(Grant grant) {
        if (grant.getStartDate() != null) {
            return grant.getStartDate().getYear();
        }
        return LocalDate.now().getYear();
    }

    private static String categoryCode(CostAllocation allocation) {
        return allocation.getCategory() != null ? allocation.getCategory().getCode() : "";
    }

    private String personName(Employee employee) {
        if (employee == null) {
            return "Pracownik";
        }
        String first = employee.getFirstName() != null ? employee.getFirstName() : "";
        String last = employee.getLastName() != null ? employee.getLastName() : "";
        return (first + " " + last).trim();
    }

    private static String adminLineLabel(CostAllocation allocation) {
        if (allocation.getLabel() != null && !allocation.getLabel().isBlank()) {
            return allocation.getLabel();
        }
        if (allocation.getAdminGroup() != null) {
            return allocation.getAdminGroup();
        }
        return "Koszty administracyjne";
    }

    private static String allocationLabel(CostAllocation allocation) {
        if (allocation.getLabel() != null && !allocation.getLabel().isBlank()) {
            return allocation.getLabel();
        }
        return categoryCode(allocation);
    }

    private static String travelLabel(TravelBudgetLine line) {
        String scope = line.getScope() == TravelBudgetLine.TravelScope.DOMESTIC ? "Kraj" : "Zagranica";
        return "Podróże / " + scope + " / " + line.getExpenseType();
    }

    private static Map<Long, BigDecimal> sumExpendituresByBudgetItem(List<Expenditure> expenditures) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (Expenditure expenditure : expenditures) {
            if (!GrantPeriodCoverage.countsTowardGrant(expenditure)) {
                continue;
            }
            if (expenditure.getBudgetItem() == null) {
                continue;
            }
            Long itemId = expenditure.getBudgetItem().getId();
            BigDecimal amount = expenditure.getGrossAmount() != null
                    ? expenditure.getGrossAmount()
                    : expenditure.getNetAmount();
            if (amount == null) {
                continue;
            }
            totals.merge(itemId, amount, BigDecimal::add);
        }
        return totals;
    }

    @Transactional(readOnly = true)
    public List<GrantListRowDto> buildGrantListRows(int fiscalYear) {
        ExpenditureTotals totals = aggregateExpendituresByGrant(fiscalYear);
        List<GrantListRowDto> rows = new ArrayList<>();
        LocalDate yearStart = LocalDate.of(fiscalYear, 1, 1);
        for (Grant grant : grantRepository.findAllWithDetails()) {
            BigDecimal total = grant.getTotalAmount() != null ? grant.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal spentBefore = totals.spentBeforeYear().getOrDefault(grant.getId(), BigDecimal.ZERO);
            BigDecimal spent = totals.spentInYear().getOrDefault(grant.getId(), BigDecimal.ZERO);
            BigDecimal opening = computeOpeningBalance(grant, yearStart, total, spentBefore);
            if (grant.getTranches() != null) {
                grant.getTranches().size();
            }
            rows.add(new GrantListRowDto(
                    grant,
                    opening,
                    spent,
                    GrantYearSpendable.allocatedByActiveMonths(grant, fiscalYear),
                    computeGrantRemaining(grant, totals, fiscalYear)));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public GrantSpendPlan computeGrantSpendPlan(List<Grant> grants, int fiscalYear) {
        Map<String, BigDecimal> yearAmountByName = new LinkedHashMap<>();
        Map<String, BigDecimal> totalByName = new LinkedHashMap<>();
        Map<String, BigDecimal> remainingByName = new LinkedHashMap<>();
        Map<String, BigDecimal> nextYearByName = new LinkedHashMap<>();
        for (Grant grant : grants) {
            if (grant.getTranches() != null) {
                grant.getTranches().size();
            }
            String name = grant.getName();
            BigDecimal coverage = coveragePlanSum(grant, fiscalYear);
            BigDecimal yearAmount = GrantYearSpendable.inYear(grant, fiscalYear);
            BigDecimal displayedTotal = GrantYearSpendable.displayedTotal(grant, fiscalYear, coverage);
            yearAmountByName.put(name, yearAmount);
            totalByName.put(name, displayedTotal);
            remainingByName.put(name, displayedTotal.subtract(coverage).setScale(2, RoundingMode.HALF_UP));
            nextYearByName.put(name, GrantYearSpendable.afterYear(grant, fiscalYear));
        }
        return new GrantSpendPlan(yearAmountByName, totalByName, remainingByName, nextYearByName);
    }

    private static BigDecimal coveragePlanTotal(GrantBudgetViewDto dto, boolean year2027) {
        BigDecimal sum = BigDecimal.ZERO;
        for (GrantBudgetViewDto.GrantBudgetLineDto line : dto.getLines()) {
            for (GrantBudgetViewDto.CoveredOrgBudgetLineDto coverage : line.getCoveredOrgLines()) {
                BigDecimal amount = year2027 ? coverage.getAmount2027() : coverage.getAmount();
                if (amount != null) {
                    sum = sum.add(amount);
                }
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal coveragePlanSum(Grant grant, int fiscalYear) {
        BigDecimal sum = BigDecimal.ZERO;
        if (grant.getBudgetItems() == null) {
            return sum.setScale(2, RoundingMode.HALF_UP);
        }
        for (GrantBudgetItem item : grant.getBudgetItems()) {
            if (!item.isActive() || item.getCoverages() == null) {
                continue;
            }
            item.getCoverages().size();
            for (GrantBudgetItemCoverage coverage : item.getCoverages()) {
                BigDecimal amount = GrantCoverageYear.amount(grant, coverage, fiscalYear);
                if (amount != null) {
                    sum = sum.add(amount);
                }
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeGrantRemaining(Grant grant, ExpenditureTotals totals, int fiscalYear) {
        BigDecimal spentBefore = totals.spentBeforeYear().getOrDefault(grant.getId(), BigDecimal.ZERO);
        BigDecimal spent = totals.spentInYear().getOrDefault(grant.getId(), BigDecimal.ZERO);
        return GrantYearSpendable.throughYear(grant, fiscalYear).subtract(spentBefore).subtract(spent);
    }

    private ExpenditureTotals aggregateExpendituresByGrant(int fiscalYear) {
        Map<Long, BigDecimal> spentInYear = new HashMap<>();
        Map<Long, BigDecimal> spentBeforeYear = new HashMap<>();
        for (Expenditure expenditure : expenditureRepository.findAllWithGrantAndItem()) {
            if (!GrantPeriodCoverage.countsTowardGrant(expenditure)) {
                continue;
            }
            if (expenditure.getGrant() == null) {
                continue;
            }
            Integer expenditureYear = expenditure.getFiscalYear();
            if (expenditureYear == null) {
                continue;
            }
            BigDecimal amount = expenditure.getGrossAmount() != null
                    ? expenditure.getGrossAmount()
                    : expenditure.getNetAmount();
            if (amount == null) {
                continue;
            }
            Long grantId = expenditure.getGrant().getId();
            if (expenditureYear == fiscalYear) {
                spentInYear.merge(grantId, amount, BigDecimal::add);
            } else if (expenditureYear < fiscalYear) {
                spentBeforeYear.merge(grantId, amount, BigDecimal::add);
            }
        }
        return new ExpenditureTotals(spentInYear, spentBeforeYear);
    }

    private static BigDecimal computeOpeningBalance(
            Grant grant, LocalDate yearStart, BigDecimal total, BigDecimal spentBefore) {
        LocalDate grantStart = grant.getStartDate();
        if (grantStart == null || !grantStart.isBefore(yearStart)) {
            return BigDecimal.ZERO;
        }
        return total.subtract(spentBefore);
    }

    public record GrantSpendPlan(
            Map<String, BigDecimal> yearAmountByName,
            Map<String, BigDecimal> totalByName,
            Map<String, BigDecimal> remainingByName,
            Map<String, BigDecimal> nextYearByName) {
    }

    private record ExpenditureTotals(Map<Long, BigDecimal> spentInYear, Map<Long, BigDecimal> spentBeforeYear) {
    }
}
