package pl.ngo.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.dto.GrantSaveCommand;
import pl.ngo.budget.entity.cost.CostAllocation;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.entity.coverage.BudgetSubcategoryOrder;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.GrantTranche;
import pl.ngo.budget.entity.coverage.PlannedEvent;
import pl.ngo.budget.entity.coverage.Publication;
import pl.ngo.budget.entity.coverage.TravelBudgetLine;
import pl.ngo.budget.repository.BudgetItemTemplateRepository;
import pl.ngo.budget.repository.BudgetSubcategoryOrderRepository;
import pl.ngo.budget.repository.CostAllocationRepository;
import pl.ngo.budget.repository.EmployeeRepository;
import pl.ngo.budget.repository.GrantRepository;
import pl.ngo.budget.repository.PlannedEventRepository;
import pl.ngo.budget.repository.PublicationRepository;
import pl.ngo.budget.repository.TravelBudgetLineRepository;
import pl.ngo.budget.util.MonthlySplit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BudgetSetupService {

    private final CostAllocationRepository costAllocationRepository;
    private final GrantRepository grantRepository;
    private final BudgetItemTemplateRepository budgetItemTemplateRepository;
    private final BudgetSubcategoryOrderRepository budgetSubcategoryOrderRepository;
    private final EmployeeRepository employeeRepository;
    private final PublicationRepository publicationRepository;
    private final TravelBudgetLineRepository travelBudgetLineRepository;
    private final PlannedEventRepository plannedEventRepository;

    public BudgetSetupService(CostAllocationRepository costAllocationRepository,
                              GrantRepository grantRepository,
                              BudgetItemTemplateRepository budgetItemTemplateRepository,
                              BudgetSubcategoryOrderRepository budgetSubcategoryOrderRepository,
                              EmployeeRepository employeeRepository,
                              PublicationRepository publicationRepository,
                              TravelBudgetLineRepository travelBudgetLineRepository,
                              PlannedEventRepository plannedEventRepository) {
        this.costAllocationRepository = costAllocationRepository;
        this.grantRepository = grantRepository;
        this.budgetItemTemplateRepository = budgetItemTemplateRepository;
        this.budgetSubcategoryOrderRepository = budgetSubcategoryOrderRepository;
        this.employeeRepository = employeeRepository;
        this.publicationRepository = publicationRepository;
        this.travelBudgetLineRepository = travelBudgetLineRepository;
        this.plannedEventRepository = plannedEventRepository;
    }

    public List<Integer> getExistingFiscalYears() {
        return costAllocationRepository.findDistinctFiscalYears();
    }

    public boolean yearHasData(int fiscalYear) {
        return costAllocationRepository.countByFiscalYear(fiscalYear) > 0;
    }

    @Transactional
    public int setupNewBudgetYear(int targetYear, Integer copyFromYear) {
        if (yearHasData(targetYear)) {
            throw new IllegalStateException("Budżet na rok " + targetYear + " już istnieje.");
        }

        ensureGrantBudgetItems();

        if (copyFromYear == null) {
            return 0;
        }

        if (!yearHasData(copyFromYear)) {
            throw new IllegalStateException("Brak danych budżetowych dla roku " + copyFromYear + ".");
        }

        List<CostAllocation> source = costAllocationRepository.findPlanAllocationsByFiscalYear(copyFromYear);
        int copied = 0;
        for (CostAllocation src : source) {
            CostAllocation copy = new CostAllocation();
            copy.setEmployee(src.getEmployee());
            copy.setProject(src.getProject());
            copy.setCategory(src.getCategory());
            copy.setAmount(src.getAmount());
            copy.setPercentage(src.getPercentage());
            copy.setAdminGroup(src.getAdminGroup());
            copy.setLabel(src.getLabel());
            copy.setFiscalYear(targetYear);
            copy.setPlanMonth(src.getPlanMonth());
            copy.setSplitToMonths(src.isSplitToMonths());
            copy.setActive(true);
            costAllocationRepository.save(copy);
            copied++;
        }
        return copied;
    }

    @Transactional
    public void ensureGrantBudgetItems() {
        List<BudgetItemTemplate> templates = budgetItemTemplateRepository.findAll();
        List<Grant> grants = grantRepository.findAll();
        for (Grant grant : grants) {
            boolean changed = false;
            for (BudgetItemTemplate template : templates) {
                boolean exists = grant.getBudgetItems().stream()
                        .anyMatch(item -> template.getName().equals(item.getName()));
                if (!exists) {
                    GrantBudgetItem item = new GrantBudgetItem();
                    item.setGrant(grant);
                    item.setName(template.getName());
                    item.setCode(template.getCode());
                    item.setActive(true);
                    grant.getBudgetItems().add(item);
                    changed = true;
                }
            }
            if (changed) {
                grantRepository.save(grant);
            }
        }
    }

    @Transactional
    public void replaceGrantBudgetItems(Grant grant, List<GrantSaveCommand.BudgetItemCommand> commands) {
        if (commands == null) {
            commands = List.of();
        }
        Map<String, GrantBudgetItem> existingByKey = grant.getBudgetItems().stream()
                .filter(item -> item.getParent() == null)
                .collect(Collectors.toMap(this::budgetItemKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<GrantBudgetItem> keep = new HashSet<>();
        Set<String> usedKeys = new HashSet<>();
        for (GrantSaveCommand.BudgetItemCommand command : commands) {
            GrantBudgetItem item = toBudgetItem(grant, command);
            if (item == null) {
                continue;
            }
            String key = budgetItemKey(item);
            if (!usedKeys.add(key)) {
                continue;
            }
            GrantBudgetItem existing = existingByKey.get(budgetItemKey(item));
            if (existing != null) {
                if (command.getPlannedAmount() != null) {
                    existing.setPlannedAmount(command.getPlannedAmount());
                }
                existing.setActive(true);
                keep.add(existing);
            } else {
                grant.getBudgetItems().add(item);
                keep.add(item);
            }
        }
        // Podpozycje i pozycje mające podpozycje zmienia się tylko na stronie budżetu grantu.
        Set<GrantBudgetItem> withChildren = grant.getBudgetItems().stream()
                .map(GrantBudgetItem::getParent)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        grant.getBudgetItems().removeIf(item -> !keep.contains(item)
                && item.getParent() == null && !withChildren.contains(item));
    }

    @Transactional
    public void replaceGrantTranches(Grant grant, List<GrantSaveCommand.TrancheCommand> commands) {
        if (commands == null) {
            commands = List.of();
        }
        Map<Long, GrantTranche> existingById = grant.getTranches().stream()
                .filter(tranche -> tranche.getId() != null)
                .collect(Collectors.toMap(GrantTranche::getId, Function.identity()));
        List<GrantTranche> next = new ArrayList<>();
        int number = 1;
        for (GrantSaveCommand.TrancheCommand command : commands) {
            if (isBlankTranche(command)) {
                continue;
            }
            GrantTranche tranche = command.getId() != null ? existingById.get(command.getId()) : null;
            if (tranche == null) {
                tranche = new GrantTranche();
                tranche.setGrant(grant);
            }
            tranche.setTrancheNumber(number++);
            tranche.setPlannedDate(command.getPlannedDate());
            tranche.setPlannedAmount(command.getPlannedAmount() != null ? command.getPlannedAmount() : BigDecimal.ZERO);
            tranche.setReceived(command.isReceived());
            tranche.setReceivedDate(command.getReceivedDate());
            tranche.setReceivedAmount(command.getReceivedAmount());
            next.add(tranche);
        }
        grant.getTranches().clear();
        grant.getTranches().addAll(next);
    }

    private static boolean isBlankTranche(GrantSaveCommand.TrancheCommand command) {
        return command.getPlannedDate() == null
                && command.getPlannedAmount() == null
                && command.getReceivedDate() == null
                && command.getReceivedAmount() == null
                && !command.isReceived();
    }

    private String budgetItemKey(GrantBudgetItem item) {
        if (item.getCode() != null && !item.getCode().isBlank()) {
            return "c:" + item.getCode().trim().toUpperCase();
        }
        return "n:" + (item.getName() != null ? item.getName().trim().toUpperCase() : "");
    }

    public List<BudgetItemTemplate> listBudgetTemplates() {
        return budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc();
    }

    @Transactional
    public void moveBudgetCategory(Long id, int direction) {
        List<BudgetItemTemplate> categories = new ArrayList<>(listBudgetTemplates());
        int index = -1;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getId().equals(id)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Kategoria budżetowa nie istnieje");
        }
        int targetIndex = index + direction;
        if (targetIndex < 0 || targetIndex >= categories.size()) {
            return;
        }
        BudgetItemTemplate current = categories.get(index);
        BudgetItemTemplate other = categories.get(targetIndex);
        int currentOrder = current.getDisplayOrder();
        current.setDisplayOrder(other.getDisplayOrder());
        other.setDisplayOrder(currentOrder);
        budgetItemTemplateRepository.save(current);
        budgetItemTemplateRepository.save(other);
    }

    @Transactional
    public void reorderBudgetCategories(List<Long> orderedCategoryIds) {
        if (orderedCategoryIds == null || orderedCategoryIds.isEmpty()) {
            return;
        }
        int order = 10;
        for (Long categoryId : orderedCategoryIds) {
            BudgetItemTemplate category = budgetItemTemplateRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Kategoria budżetowa nie istnieje: " + categoryId));
            category.setDisplayOrder(order);
            budgetItemTemplateRepository.save(category);
            order += 10;
        }
    }

    @Transactional
    public void reorderBudgetSubcategories(String parentRowKey, List<String> orderedRowKeys) {
        if (parentRowKey == null || parentRowKey.isBlank() || orderedRowKeys == null || orderedRowKeys.isEmpty()) {
            return;
        }
        Map<String, BudgetSubcategoryOrder> existing = budgetSubcategoryOrderRepository
                .findByParentRowKeyOrderByDisplayOrderAsc(parentRowKey).stream()
                .collect(Collectors.toMap(BudgetSubcategoryOrder::getRowKey, Function.identity(), (a, b) -> a));
        int order = 10;
        for (String rowKey : orderedRowKeys) {
            if (rowKey == null || rowKey.isBlank()) {
                continue;
            }
            BudgetSubcategoryOrder entry = existing.getOrDefault(rowKey, new BudgetSubcategoryOrder());
            entry.setParentRowKey(parentRowKey);
            entry.setRowKey(rowKey);
            entry.setDisplayOrder(order);
            budgetSubcategoryOrderRepository.save(entry);
            order += 10;
        }
    }

    @Transactional
    public BudgetItemTemplate addBudgetCategory(String name) {
        String normalized = normalize(name);
        if (normalized == null) {
            throw new IllegalArgumentException("Podaj nazwę pozycji");
        }
        if (budgetItemTemplateRepository.findByName(normalized).isPresent()) {
            throw new IllegalArgumentException("Pozycja o tej nazwie już istnieje");
        }
        return createBudgetTemplate(uniqueTemplateCode(normalized), normalized);
    }

    /** Kategorie, które logika budżetu rozpoznaje po nazwie — bez zmiany nazwy. */
    private static final java.util.Set<String> BUILT_IN_CATEGORY_CODES = java.util.Set.of(
            "KOSZT_PER", "KOSZT_MAT", "KOSZT_KSW", "KOSZT_LOG", "KOSZT_PROM", "KOSZT_ADM", "KOSZT_SPRZ");

    @Transactional
    public void renameBudgetCategory(Long categoryId, String name) {
        String normalized = normalize(name);
        if (normalized == null) {
            throw new IllegalArgumentException("Podaj nazwę kategorii");
        }
        BudgetItemTemplate category = budgetItemTemplateRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Pozycja budżetowa nie istnieje"));
        if (BUILT_IN_CATEGORY_CODES.contains(category.getCode())) {
            throw new IllegalArgumentException("Nazwy wbudowanej kategorii nie można zmienić — budżet rozpoznaje ją po nazwie");
        }
        budgetItemTemplateRepository.findByName(normalized)
                .filter(other -> !other.getId().equals(category.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Kategoria „" + normalized + "” już istnieje");
                });
        category.setName(normalized);
        budgetItemTemplateRepository.save(category);
    }

    @Transactional
    public void renameBudgetSubcategory(String parentRowKey, String rowKey, String name) {
        String parent = normalize(parentRowKey);
        String key = normalize(rowKey);
        String normalized = normalize(name);
        if (parent == null || key == null) {
            throw new IllegalArgumentException("Brak identyfikatora podpozycji");
        }
        if (normalized == null) {
            throw new IllegalArgumentException("Podaj nazwę podpozycji");
        }
        BudgetSubcategoryOrder entry = budgetSubcategoryOrderRepository
                .findByParentRowKeyAndRowKey(parent, key)
                .orElseGet(BudgetSubcategoryOrder::new);
        entry.setParentRowKey(parent);
        entry.setRowKey(key);
        entry.setName(normalized);
        if (entry.getId() == null) {
            int nextOrder = budgetSubcategoryOrderRepository.findByParentRowKeyOrderByDisplayOrderAsc(parent).stream()
                    .mapToInt(BudgetSubcategoryOrder::getDisplayOrder)
                    .max()
                    .orElse(0) + 10;
            entry.setDisplayOrder(nextOrder);
            entry.setHidden(false);
            entry.setHasSubcategories(true);
        }
        budgetSubcategoryOrderRepository.save(entry);
    }

    @Transactional
    public void deleteBudgetCategory(Long categoryId) {
        BudgetItemTemplate category = budgetItemTemplateRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Pozycja budżetowa nie istnieje"));
        if (costAllocationRepository.countByCategory_Id(categoryId) > 0) {
            throw new IllegalArgumentException("Nie można usunąć pozycji — są do niej alokacje");
        }
        String parentRowKey = categoryRowKeyForCode(category.getCode());
        budgetSubcategoryOrderRepository.deleteByParentRowKey(parentRowKey);
        budgetItemTemplateRepository.delete(category);
    }

    @Transactional
    public void addBudgetSubcategory(String parentRowKey, String name) {
        String parent = normalize(parentRowKey);
        String normalized = normalize(name);
        if (parent == null) {
            throw new IllegalArgumentException("Brak kategorii nadrzędnej");
        }
        if (normalized == null) {
            throw new IllegalArgumentException("Podaj nazwę podpozycji");
        }
        String rowKey = uniqueSubcategoryRowKey(parent, normalized);
        BudgetSubcategoryOrder entry = budgetSubcategoryOrderRepository
                .findByParentRowKeyAndRowKey(parent, rowKey)
                .orElseGet(BudgetSubcategoryOrder::new);
        entry.setParentRowKey(parent);
        entry.setRowKey(rowKey);
        entry.setName(normalized);
        entry.setHidden(false);
        entry.setHasSubcategories(true);
        int nextOrder = budgetSubcategoryOrderRepository.findByParentRowKeyOrderByDisplayOrderAsc(parent).stream()
                .mapToInt(BudgetSubcategoryOrder::getDisplayOrder)
                .max()
                .orElse(0) + 10;
        if (entry.getId() == null) {
            entry.setDisplayOrder(nextOrder);
        }
        budgetSubcategoryOrderRepository.save(entry);
    }

    @Transactional
    public void setSubcategoryPlannedAmount(String parentRowKey, String rowKey, BigDecimal amount) {
        setSubcategoryPlannedAmount(parentRowKey, rowKey, amount, null);
    }

    @Transactional
    public void setSubcategoryPlannedAmount(String parentRowKey, String rowKey, BigDecimal amount, Integer fiscalYear) {
        String parent = normalize(parentRowKey);
        String key = normalize(rowKey);
        if (parent == null || key == null) {
            throw new IllegalArgumentException("Brak identyfikatora pozycji");
        }
        BudgetSubcategoryOrder entry = budgetSubcategoryOrderRepository
                .findByParentRowKeyAndRowKey(parent, key)
                .orElseGet(BudgetSubcategoryOrder::new);
        entry.setParentRowKey(parent);
        entry.setRowKey(key);
        entry.setHasSubcategories(true);
        if (entry.getId() == null) {
            int nextOrder = budgetSubcategoryOrderRepository.findByParentRowKeyOrderByDisplayOrderAsc(parent).stream()
                    .mapToInt(BudgetSubcategoryOrder::getDisplayOrder)
                    .max()
                    .orElse(0) + 10;
            entry.setDisplayOrder(nextOrder);
            entry.setHidden(false);
        }
        BigDecimal stored = amount != null && amount.signum() >= 0 ? amount.setScale(2, RoundingMode.HALF_UP) : null;
        entry.setPlannedAmount(stored);
        budgetSubcategoryOrderRepository.save(entry);
        if (fiscalYear != null && stored != null
                && (MonthlySplit.isSalaryOrAdminRow(parent) || MonthlySplit.isSalaryOrAdminRow(key))) {
            redistributeSalaryOrAdminStructure(fiscalYear, parent, key, stored);
        }
    }

    @Transactional
    public void updateDisplayedMonthAmount(int year, int month, String kind, String ids, BigDecimal amount) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Miesiąc poza zakresem 1–12");
        }
        BigDecimal displayed = amount == null || amount.signum() < 0
                ? BigDecimal.ZERO
                : amount.setScale(2, RoundingMode.HALF_UP);
        boolean monthlyPlan = costAllocationRepository.existsMonthlyPlanForFiscalYear(year);
        BigDecimal stored = monthlyPlan
                ? displayed
                : displayed.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);
        switch (kind == null ? "" : kind) {
            case "allocation" -> updateAllocationAmounts(year, month, monthlyPlan, parseIds(ids), stored);
            case "publication" -> updatePublicationAmounts(parseIds(ids), MonthlySplit.annualFromShare(displayed));
            case "travel" -> updateTravelAmounts(parseIds(ids), MonthlySplit.annualFromShare(displayed));
            case "event" -> updateEventAmount(year, month, parseIds(ids), displayed);
            case "employee" -> {
                List<Long> employeeIds = parseIds(ids);
                if (monthlyPlan && hasMonthlyPersonnelPlan(employeeIds.get(0), year)) {
                    // Pensja z planu miesięcznego: zmiana dotyczy tylko tego miesiąca.
                    updateEmployeeMonthAmount(employeeIds.get(0), year, month, displayed);
                } else {
                    BigDecimal annual = MonthlySplit.annualFromShare(displayed);
                    updateEmployeeAmount(employeeIds, annual);
                    syncEmployeeSalaryAllocations(employeeIds.get(0), annual);
                }
            }
            case "subcategory" -> updateSubcategoryAmount(ids, displayed);
            default -> throw new IllegalArgumentException("Nieobsługiwana pozycja");
        }
    }

    @Transactional
    public void syncEmployeeSalaryAllocations(Long employeeId, BigDecimal annualAmount) {
        if (employeeId == null || annualAmount == null) {
            return;
        }
        BigDecimal annual = annualAmount.setScale(2, RoundingMode.HALF_UP);
        for (Integer year : costAllocationRepository.findDistinctFiscalYears()) {
            syncEmployeeSalaryForYear(employeeId, annual, year);
        }
    }

    @Transactional
    public void applyEqualMonthlyShareFromAnnual(CostAllocation annual, BigDecimal monthlyShare) {
        applyEqualMonthlyShare(annual, monthlyShare);
        if (isPersonnelAllocation(annual) && annual.getEmployee() != null && annual.getFiscalYear() != null) {
            refreshEmployeePlannedCost(annual.getEmployee().getId(), annual.getFiscalYear());
        }
    }

    @Transactional
    public void refreshPersonnelPlannedCosts(int fiscalYear) {
        Map<Long, BigDecimal> sums = new HashMap<>();
        for (CostAllocation annual : costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(fiscalYear)) {
            if (!isPersonnelAllocation(annual) || annual.getEmployee() == null) {
                continue;
            }
            BigDecimal amount = annual.getAmount() != null ? annual.getAmount() : BigDecimal.ZERO;
            sums.merge(annual.getEmployee().getId(), amount, BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> entry : sums.entrySet()) {
            Employee employee = employeeRepository.findById(entry.getKey()).orElse(null);
            if (employee == null) {
                continue;
            }
            employee.setPlannedCost(entry.getValue().setScale(2, RoundingMode.HALF_UP));
            employeeRepository.save(employee);
        }
    }

    @Transactional
    public void splitSalaryAndAdminAnnualLines(int fiscalYear, boolean writeMonths) {
        for (CostAllocation annual : costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(fiscalYear)) {
            if (!isSalaryOrAdminAllocation(annual)) {
                continue;
            }
            annual.setSplitToMonths(true);
            if (writeMonths) {
                writeEvenMonthShares(annual);
            } else {
                costAllocationRepository.save(annual);
            }
        }
    }

    @Transactional
    public void setSubcategoryHasSubcategories(String parentRowKey, String rowKey, boolean hasSubcategories) {
        String parent = normalize(parentRowKey);
        String key = normalize(rowKey);
        if (parent == null || key == null) {
            throw new IllegalArgumentException("Brak identyfikatora pozycji");
        }
        BudgetSubcategoryOrder entry = budgetSubcategoryOrderRepository
                .findByParentRowKeyAndRowKey(parent, key)
                .orElseGet(BudgetSubcategoryOrder::new);
        entry.setParentRowKey(parent);
        entry.setRowKey(key);
        entry.setHasSubcategories(hasSubcategories);
        if (entry.getId() == null) {
            int nextOrder = budgetSubcategoryOrderRepository.findByParentRowKeyOrderByDisplayOrderAsc(parent).stream()
                    .mapToInt(BudgetSubcategoryOrder::getDisplayOrder)
                    .max()
                    .orElse(0) + 10;
            entry.setDisplayOrder(nextOrder);
            entry.setHidden(false);
        }
        budgetSubcategoryOrderRepository.save(entry);
    }

    @Transactional
    public void deleteBudgetSubcategory(String parentRowKey, String rowKey) {
        String parent = normalize(parentRowKey);
        String key = normalize(rowKey);
        if (parent == null || key == null) {
            throw new IllegalArgumentException("Brak identyfikatora podpozycji");
        }
        BudgetSubcategoryOrder entry = budgetSubcategoryOrderRepository
                .findByParentRowKeyAndRowKey(parent, key)
                .orElseGet(BudgetSubcategoryOrder::new);
        entry.setParentRowKey(parent);
        entry.setRowKey(key);
        entry.setHidden(true);
        if (entry.getId() == null) {
            entry.setDisplayOrder(0);
        }
        budgetSubcategoryOrderRepository.save(entry);
    }

    private void updateAllocationAmounts(int year,
                                         int month,
                                         boolean monthlyPlan,
                                         List<Long> ids,
                                         BigDecimal storedTotal) {
        List<CostAllocation> rows = new ArrayList<>();
        for (Long id : ids) {
            CostAllocation row = costAllocationRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Pozycja budżetowa nie istnieje"));
            if (!Integer.valueOf(year).equals(row.getFiscalYear()) || row.getExpenditure() != null) {
                throw new IllegalArgumentException("Pozycja nie należy do tego budżetu");
            }
            if (monthlyPlan) {
                if (!Integer.valueOf(month).equals(row.getPlanMonth())) {
                    throw new IllegalArgumentException("Pozycja nie należy do tego miesiąca");
                }
            } else if (row.getPlanMonth() != null) {
                throw new IllegalArgumentException("Pozycja nie należy do planu rocznego");
            }
            rows.add(row);
        }
        applyDistributedAmounts(rows.stream().map(CostAllocation::getAmount).toList(), storedTotal, (index, value) ->
                rows.get(index).setAmount(value));
        costAllocationRepository.saveAll(rows);
        boolean evenSplit = rows.stream().anyMatch(this::isSalaryOrAdminAllocation);
        if (!evenSplit) {
            return;
        }
        Set<Long> employeeIds = new HashSet<>();
        for (CostAllocation row : rows) {
            if (!isSalaryOrAdminAllocation(row)) {
                continue;
            }
            if (monthlyPlan) {
                // Każdy miesiąc osobno: roczna kwota pozycji to suma miesięcy.
                recomputeAnnualFromMonths(row);
            } else {
                row.setSplitToMonths(true);
                costAllocationRepository.save(row);
            }
            if (isPersonnelAllocation(row) && row.getEmployee() != null) {
                employeeIds.add(row.getEmployee().getId());
            }
        }
        for (Long employeeId : employeeIds) {
            refreshEmployeePlannedCost(employeeId, year);
        }
    }

    private void updatePublicationAmounts(List<Long> ids, BigDecimal storedTotal) {
        List<Publication> rows = ids.stream()
                .map(id -> publicationRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Publikacja nie istnieje")))
                .toList();
        applyDistributedAmounts(rows.stream().map(Publication::getPlannedCost).toList(), storedTotal, (index, value) ->
                rows.get(index).setPlannedCost(value));
        publicationRepository.saveAll(rows);
    }

    private void updateTravelAmounts(List<Long> ids, BigDecimal storedTotal) {
        List<TravelBudgetLine> rows = ids.stream()
                .map(id -> travelBudgetLineRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Pozycja podróży nie istnieje")))
                .toList();
        applyDistributedAmounts(rows.stream().map(TravelBudgetLine::getPlannedCost).toList(), storedTotal, (index, value) ->
                rows.get(index).setPlannedCost(value));
        travelBudgetLineRepository.saveAll(rows);
    }

    private void updateEventAmount(int year, int month, List<Long> ids, BigDecimal displayed) {
        if (ids.size() != 1) {
            throw new IllegalArgumentException("Nieobsługiwana pozycja");
        }
        PlannedEvent event = plannedEventRepository.findById(ids.get(0))
                .orElseThrow(() -> new IllegalArgumentException("Wydarzenie nie istnieje"));
        if (event.getEventDate() == null
                || event.getEventDate().getYear() != year
                || event.getEventDate().getMonthValue() != month) {
            throw new IllegalArgumentException("Wydarzenie nie należy do tego miesiąca");
        }
        event.setPlannedCost(displayed);
        plannedEventRepository.save(event);
    }

    private void updateEmployeeAmount(List<Long> ids, BigDecimal storedTotal) {
        if (ids.size() != 1) {
            throw new IllegalArgumentException("Nieobsługiwana pozycja");
        }
        Employee employee = employeeRepository.findById(ids.get(0))
                .orElseThrow(() -> new IllegalArgumentException("Pracownik nie istnieje"));
        employee.setPlannedCost(storedTotal);
        employeeRepository.save(employee);
    }

    private void updateSubcategoryAmount(String ids, BigDecimal displayed) {
        if (ids == null) {
            throw new IllegalArgumentException("Brak pozycji do zapisu");
        }
        int separator = ids.indexOf('|');
        if (separator <= 0 || separator >= ids.length() - 1) {
            throw new IllegalArgumentException("Brak pozycji do zapisu");
        }
        String parent = ids.substring(0, separator);
        String rowKey = ids.substring(separator + 1);
        BigDecimal stored = displayed;
        if (MonthlySplit.isSalaryOrAdminRow(parent) || MonthlySplit.isSalaryOrAdminRow(rowKey)) {
            stored = MonthlySplit.annualFromShare(displayed);
        }
        setSubcategoryPlannedAmount(parent, rowKey, stored);
    }

    private void syncEmployeeSalaryForYear(Long employeeId, BigDecimal annualAmount, int year) {
        List<CostAllocation> annuals = costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(year).stream()
                .filter(row -> isPersonnelAllocation(row) && matchesEmployee(row, employeeId))
                .toList();
        if (annuals.isEmpty()) {
            annuals = createAnnualLinesFromMonths(employeeId, year);
        }
        if (annuals.isEmpty()) {
            return;
        }
        List<CostAllocation> rows = new ArrayList<>(annuals);
        applyDistributedAmounts(rows.stream().map(CostAllocation::getAmount).toList(), annualAmount, (index, value) ->
                rows.get(index).setAmount(value));
        boolean writeMonths = costAllocationRepository.existsMonthlyPlanForFiscalYear(year);
        for (CostAllocation annual : rows) {
            annual.setSplitToMonths(true);
            if (writeMonths) {
                writeEvenMonthShares(annual);
            } else {
                costAllocationRepository.save(annual);
            }
        }
    }

    private List<CostAllocation> createAnnualLinesFromMonths(Long employeeId, int year) {
        Map<String, CostAllocation> byLine = new LinkedHashMap<>();
        for (CostAllocation row : costAllocationRepository.findAllPlanAllocationsByFiscalYear(year)) {
            if (row.getPlanMonth() == null || !isPersonnelAllocation(row) || !matchesEmployee(row, employeeId)) {
                continue;
            }
            byLine.putIfAbsent(lineKey(row), row);
        }
        List<CostAllocation> created = new ArrayList<>();
        for (CostAllocation sample : byLine.values()) {
            BigDecimal annualAmount = sample.getAmount() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : sample.getAmount().multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);
            created.add(copyPlanLine(sample, null, annualAmount));
        }
        return created;
    }

    private void redistributeSalaryOrAdminStructure(int year, String parent, String rowKey, BigDecimal annualAmount) {
        List<CostAllocation> annuals = costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(year).stream()
                .filter(row -> matchesStructureRow(row, parent, rowKey))
                .toList();
        if (annuals.isEmpty()) {
            return;
        }
        List<CostAllocation> rows = new ArrayList<>(annuals);
        applyDistributedAmounts(rows.stream().map(CostAllocation::getAmount).toList(), annualAmount, (index, value) ->
                rows.get(index).setAmount(value));
        boolean writeMonths = costAllocationRepository.existsMonthlyPlanForFiscalYear(year);
        Set<Long> employeeIds = new HashSet<>();
        for (CostAllocation annual : rows) {
            annual.setSplitToMonths(true);
            if (writeMonths) {
                writeEvenMonthShares(annual);
            } else {
                costAllocationRepository.save(annual);
            }
            if (isPersonnelAllocation(annual) && annual.getEmployee() != null) {
                employeeIds.add(annual.getEmployee().getId());
            }
        }
        for (Long employeeId : employeeIds) {
            refreshEmployeePlannedCost(employeeId, year);
        }
    }

    private void applyEqualMonthlyShare(CostAllocation anchor, BigDecimal monthlyShare) {
        BigDecimal share = monthlyShare == null || monthlyShare.signum() < 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : monthlyShare.setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualAmount = MonthlySplit.annualFromShare(share);
        Map<Integer, CostAllocation> byMonth = new HashMap<>();
        CostAllocation annualRow = null;
        for (CostAllocation row : costAllocationRepository.findAllPlanAllocationsByFiscalYear(anchor.getFiscalYear())) {
            if (!sameLine(anchor, row)) {
                continue;
            }
            if (row.getPlanMonth() == null) {
                annualRow = row;
            } else {
                byMonth.put(row.getPlanMonth(), row);
            }
        }
        if (annualRow == null) {
            annualRow = copyPlanLine(anchor, null, annualAmount);
        } else {
            annualRow.setAmount(annualAmount);
            annualRow.setSplitToMonths(true);
            costAllocationRepository.save(annualRow);
        }
        List<CostAllocation> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            CostAllocation monthRow = byMonth.get(month);
            if (monthRow == null) {
                months.add(copyPlanLine(annualRow, month, share));
            } else {
                monthRow.setAmount(share);
                monthRow.setSplitToMonths(true);
                months.add(monthRow);
            }
        }
        costAllocationRepository.saveAll(months);
    }

    private void writeEvenMonthShares(CostAllocation annual) {
        List<BigDecimal> shares = MonthlySplit.shares(annual.getAmount());
        annual.setSplitToMonths(true);
        costAllocationRepository.save(annual);
        Map<Integer, CostAllocation> byMonth = new HashMap<>();
        for (CostAllocation row : costAllocationRepository.findAllPlanAllocationsByFiscalYear(annual.getFiscalYear())) {
            if (row.getPlanMonth() != null && sameLine(annual, row)) {
                byMonth.put(row.getPlanMonth(), row);
            }
        }
        List<CostAllocation> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            BigDecimal share = shares.get(month - 1);
            CostAllocation monthRow = byMonth.get(month);
            if (monthRow == null) {
                months.add(copyPlanLine(annual, month, share));
            } else {
                monthRow.setAmount(share);
                monthRow.setSplitToMonths(true);
                months.add(monthRow);
            }
        }
        costAllocationRepository.saveAll(months);
    }

    private void refreshEmployeePlannedCost(Long employeeId, int year) {
        BigDecimal sum = costAllocationRepository.findAnnualPlanAllocationsByFiscalYear(year).stream()
                .filter(row -> isPersonnelAllocation(row) && matchesEmployee(row, employeeId))
                .map(row -> row.getAmount() != null ? row.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Pracownik nie istnieje"));
        employee.setPlannedCost(sum);
        employeeRepository.save(employee);
    }

    private boolean hasMonthlyPersonnelPlan(Long employeeId, int year) {
        return personnelRows(employeeId, year).stream().anyMatch(row -> row.getPlanMonth() != null);
    }

    private List<CostAllocation> personnelRows(Long employeeId, int year) {
        return costAllocationRepository.findAllPlanAllocationsByFiscalYear(year).stream()
                .filter(row -> isPersonnelAllocation(row) && matchesEmployee(row, employeeId)
                        && !GrantBudgetService.isSalaryCoveragePlan(row))
                .toList();
    }

    /** Zmienia pensję pracownika tylko w jednym miesiącu; roczna kwota i planowany koszt liczą się z miesięcy. */
    private void updateEmployeeMonthAmount(Long employeeId, int year, int month, BigDecimal amount) {
        List<CostAllocation> rows = personnelRows(employeeId, year);
        CostAllocation annual = rows.stream().filter(row -> row.getPlanMonth() == null).findFirst().orElse(null);
        CostAllocation monthRow = rows.stream()
                .filter(row -> Integer.valueOf(month).equals(row.getPlanMonth()))
                .findFirst().orElse(null);
        if (monthRow == null) {
            if (annual == null) {
                throw new IllegalArgumentException("Brak pensji pracownika w planie na rok " + year);
            }
            monthRow = copyPlanLine(annual, month, amount);
        } else {
            monthRow.setAmount(amount);
            costAllocationRepository.save(monthRow);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (CostAllocation row : personnelRows(employeeId, year)) {
            if (row.getPlanMonth() != null && row.getAmount() != null) {
                sum = sum.add(row.getAmount());
            }
        }
        if (annual != null) {
            annual.setAmount(sum.setScale(2, RoundingMode.HALF_UP));
            costAllocationRepository.save(annual);
        }
        refreshEmployeePlannedCost(employeeId, year);
    }

    /** Roczna linia pozycji (planMonth = null) dostaje sumę jej linii miesięcznych. */
    private void recomputeAnnualFromMonths(CostAllocation monthRow) {
        CostAllocation annual = null;
        BigDecimal sum = BigDecimal.ZERO;
        for (CostAllocation row : costAllocationRepository.findAllPlanAllocationsByFiscalYear(monthRow.getFiscalYear())) {
            if (!sameLine(monthRow, row)) {
                continue;
            }
            if (row.getPlanMonth() == null) {
                annual = row;
            } else if (row.getAmount() != null) {
                sum = sum.add(row.getAmount());
            }
        }
        if (annual != null) {
            annual.setAmount(sum.setScale(2, RoundingMode.HALF_UP));
            costAllocationRepository.save(annual);
        }
    }

    private CostAllocation copyPlanLine(CostAllocation source, Integer planMonth, BigDecimal amount) {
        CostAllocation copy = new CostAllocation();
        copy.setEmployee(source.getEmployee());
        copy.setProject(source.getProject());
        copy.setCategory(source.getCategory());
        copy.setAmount(amount);
        copy.setPercentage(source.getPercentage() != null ? source.getPercentage() : BigDecimal.valueOf(100));
        copy.setAdminGroup(source.getAdminGroup());
        copy.setLabel(source.getLabel());
        copy.setFiscalYear(source.getFiscalYear());
        copy.setPlanMonth(planMonth);
        copy.setSplitToMonths(true);
        copy.setActive(true);
        return costAllocationRepository.save(copy);
    }

    private boolean matchesStructureRow(CostAllocation allocation, String parent, String rowKey) {
        if (!isSalaryOrAdminAllocation(allocation)) {
            return false;
        }
        if ("koszty-administracyjne".equals(parent)) {
            String group = allocation.getAdminGroup();
            return switch (rowKey) {
                case "admin-biuro" -> "BIURO".equals(group);
                case "admin-pozostale" -> group == null || "POZOSTALE".equals(group);
                case "admin-wynagrodzenia" -> "WYNAGRODZENIA".equals(group);
                default -> false;
            };
        }
        if (rowKey != null && rowKey.startsWith("employee-") && isPersonnelAllocation(allocation)) {
            try {
                return matchesEmployee(allocation, Long.valueOf(rowKey.substring("employee-".length())));
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        String label = allocation.getLabel() != null ? allocation.getLabel() : "";
        return rowKey != null && rowKey.equals(structureLineKey(parent, label));
    }

    private static String structureLineKey(String parentRowKey, String label) {
        String slug = label.trim()
                .toLowerCase()
                .replace('ą', 'a').replace('ć', 'c').replace('ę', 'e')
                .replace('ł', 'l').replace('ń', 'n').replace('ó', 'o')
                .replace('ś', 's').replace('ź', 'z').replace('ż', 'z')
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            slug = "pozycja";
        }
        return parentRowKey + "-line-" + slug;
    }

    private boolean isSalaryOrAdminAllocation(CostAllocation allocation) {
        if (allocation.getCategory() == null) {
            return false;
        }
        return MonthlySplit.isSalaryOrAdminCategory(allocation.getCategory().getCode());
    }

    private boolean isPersonnelAllocation(CostAllocation allocation) {
        return allocation.getCategory() != null && "KOSZT_PER".equals(allocation.getCategory().getCode());
    }

    private static boolean matchesEmployee(CostAllocation allocation, Long employeeId) {
        return allocation.getEmployee() != null && employeeId.equals(allocation.getEmployee().getId());
    }

    private static boolean sameLine(CostAllocation left, CostAllocation right) {
        return Objects.equals(left.getCategory().getId(), right.getCategory().getId())
                && Objects.equals(text(left.getLabel()), text(right.getLabel()))
                && Objects.equals(employeeId(left), employeeId(right))
                && Objects.equals(text(left.getAdminGroup()), text(right.getAdminGroup()))
                && Objects.equals(projectId(left), projectId(right));
    }

    private static String lineKey(CostAllocation allocation) {
        return allocation.getCategory().getId()
                + "|" + text(allocation.getLabel())
                + "|" + Objects.toString(employeeId(allocation), "")
                + "|" + text(allocation.getAdminGroup())
                + "|" + Objects.toString(projectId(allocation), "");
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static Long employeeId(CostAllocation allocation) {
        return allocation.getEmployee() != null ? allocation.getEmployee().getId() : null;
    }

    private static Long projectId(CostAllocation allocation) {
        return allocation.getProject() != null ? allocation.getProject().getId() : null;
    }

    private static List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            throw new IllegalArgumentException("Brak pozycji do zapisu");
        }
        List<Long> result = new ArrayList<>();
        for (String part : ids.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(Long.valueOf(trimmed));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Nieprawidłowa pozycja");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Brak pozycji do zapisu");
        }
        return result;
    }

    private static void applyDistributedAmounts(List<BigDecimal> current,
                                                BigDecimal target,
                                                java.util.function.BiConsumer<Integer, BigDecimal> writer) {
        List<BigDecimal> next = distributeTotal(current, target);
        for (int i = 0; i < next.size(); i++) {
            writer.accept(i, next.get(i));
        }
    }

    private static List<BigDecimal> distributeTotal(List<BigDecimal> current, BigDecimal target) {
        List<BigDecimal> result = new ArrayList<>();
        if (current.isEmpty()) {
            return result;
        }
        BigDecimal old = current.stream()
                .map(value -> value != null ? value : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (old.signum() == 0) {
            result.add(target);
            for (int i = 1; i < current.size(); i++) {
                result.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
            return result;
        }
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < current.size(); i++) {
            if (i == current.size() - 1) {
                result.add(target.subtract(assigned).setScale(2, RoundingMode.HALF_UP));
            } else {
                BigDecimal part = current.get(i) != null ? current.get(i) : BigDecimal.ZERO;
                BigDecimal share = target.multiply(part).divide(old, 2, RoundingMode.HALF_UP);
                result.add(share);
                assigned = assigned.add(share);
            }
        }
        return result;
    }

    private String uniqueTemplateCode(String name) {
        String base = generateTemplateCode(name);
        String code = base;
        int suffix = 2;
        while (budgetItemTemplateRepository.findByCode(code).isPresent()) {
            String tail = "_" + suffix;
            code = base.length() + tail.length() > 48 ? base.substring(0, 48 - tail.length()) + tail : base + tail;
            suffix += 1;
        }
        return code;
    }

    private String uniqueSubcategoryRowKey(String parentRowKey, String name) {
        String slug = name.trim()
                .toLowerCase()
                .replace('ą', 'a').replace('ć', 'c').replace('ę', 'e')
                .replace('ł', 'l').replace('ń', 'n').replace('ó', 'o')
                .replace('ś', 's').replace('ź', 'z').replace('ż', 'z')
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            slug = "pozycja";
        }
        String base = parentRowKey + "-" + slug;
        if (base.length() > 110) {
            base = base.substring(0, 110);
        }
        String rowKey = base;
        int suffix = 2;
        while (budgetSubcategoryOrderRepository.findByParentRowKeyAndRowKey(parentRowKey, rowKey).isPresent()) {
            rowKey = base + "-" + suffix;
            suffix += 1;
        }
        return rowKey;
    }

    private static String categoryRowKeyForCode(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "KOSZT_PER" -> "wynagrodzenia";
            case "KOSZT_MAT" -> "publikacje";
            case "KOSZT_KSW" -> "wydarzenia";
            case "KOSZT_LOG" -> "podroze";
            case "KOSZT_ADM" -> "koszty-administracyjne";
            case "KOSZT_PROM" -> "promocja";
            case "KOSZT_SPRZ" -> "sprzet";
            default -> code;
        };
    }

    @Transactional
    public void ensureBudgetCategoryDisplayOrder() {
        List<BudgetItemTemplate> categories = budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc();
        if (categories.isEmpty()) {
            return;
        }
        boolean needsInit = categories.stream().allMatch(category -> category.getDisplayOrder() == 0);
        if (!needsInit) {
            return;
        }
        String[] preferredCodes = {
                "KOSZT_PER", "KOSZT_MAT", "KOSZT_KSW", "KOSZT_LOG", "KOSZT_ADM", "KOSZT_PROM", "KOSZT_SPRZ"
        };
        int order = 10;
        for (String code : preferredCodes) {
            for (BudgetItemTemplate category : categories) {
                if (code.equals(category.getCode())) {
                    category.setDisplayOrder(order);
                    order += 10;
                }
            }
        }
        for (BudgetItemTemplate category : categories) {
            if (category.getDisplayOrder() == 0) {
                category.setDisplayOrder(order);
                order += 10;
            }
        }
        budgetItemTemplateRepository.saveAll(categories);
    }

    private GrantBudgetItem toBudgetItem(Grant grant, GrantSaveCommand.BudgetItemCommand command) {
        String selection = command.getSelection() == null ? "" : command.getSelection().trim();
        if (selection.startsWith("t:")) {
            try {
                command.setTemplateId(Long.valueOf(selection.substring(2)));
            } catch (NumberFormatException ex) {
                return null;
            }
            command.setNewTemplate(false);
        } else if (selection.startsWith("r:")) {
            String rowKey = selection.substring(2).trim();
            if (rowKey.isEmpty()) {
                return null;
            }
            String name = normalize(command.getName());
            GrantBudgetItem item = new GrantBudgetItem();
            item.setGrant(grant);
            item.setName(name != null ? name : rowKey);
            item.setCode(rowKey);
            item.setAccountingCode(rowKey);
            item.setPlannedAmount(command.getPlannedAmount() != null ? command.getPlannedAmount() : BigDecimal.ZERO);
            item.setActive(true);
            return item;
        } else if ("new".equals(selection)) {
            command.setTemplateId(null);
            command.setNewTemplate(true);
        } else if (selection.isEmpty()) {
            return null;
        }

        String code = normalize(command.getCode());
        String name = normalize(command.getName());

        if (command.getTemplateId() != null) {
            BudgetItemTemplate template = budgetItemTemplateRepository.findById(command.getTemplateId()).orElse(null);
            if (template != null) {
                code = template.getCode();
                name = template.getName();
            }
        } else if (command.isNewTemplate()) {
            if (name == null) {
                return null;
            }
            BudgetItemTemplate template = ensureBudgetTemplate(code, name);
            code = template.getCode();
            name = template.getName();
        }

        if (name == null) {
            return null;
        }

        GrantBudgetItem item = new GrantBudgetItem();
        item.setGrant(grant);
        item.setName(name);
        item.setCode(code);
        item.setAccountingCode(code);
        item.setPlannedAmount(command.getPlannedAmount() != null ? command.getPlannedAmount() : BigDecimal.ZERO);
        item.setActive(true);
        return item;
    }

    private BudgetItemTemplate ensureBudgetTemplate(String code, String name) {
        if (code != null) {
            return budgetItemTemplateRepository.findByCode(code)
                    .orElseGet(() -> createBudgetTemplate(code, name));
        }
        return budgetItemTemplateRepository.findByName(name)
                .orElseGet(() -> createBudgetTemplate(generateTemplateCode(name), name));
    }

    private BudgetItemTemplate createBudgetTemplate(String code, String name) {
        BudgetItemTemplate template = new BudgetItemTemplate();
        template.setCode(code);
        template.setName(name);
        template.setDefaultCode(code);
        template.setDefaultCategory(BudgetItemTemplate.CategoryType.OTHER);
        template.setDisplayOrder(nextBudgetCategoryDisplayOrder());
        return budgetItemTemplateRepository.save(template);
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

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int nextBudgetCategoryDisplayOrder() {
        return budgetItemTemplateRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .mapToInt(BudgetItemTemplate::getDisplayOrder)
                .max()
                .orElse(0) + 10;
    }
}
