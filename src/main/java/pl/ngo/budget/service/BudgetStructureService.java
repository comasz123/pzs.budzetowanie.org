package pl.ngo.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.dto.BudgetDashboardDto;
import pl.ngo.budget.dto.BudgetStructureItemDto;
import pl.ngo.budget.dto.BudgetStructureNodeDto;
import pl.ngo.budget.dto.CostAllocationDto;
import pl.ngo.budget.entity.coverage.BudgetSubcategoryOrder;
import pl.ngo.budget.repository.BudgetSubcategoryOrderRepository;
import pl.ngo.budget.util.MonthlySplit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BudgetStructureService {

    private static final String WYNAGRODZENIA_ROW_KEY = "wynagrodzenia";
    private static final String ADMIN_WYNAGRODZENIA_KEY = "admin-wynagrodzenia";
    /** Wbudowane kategorie główne — ich nazw nie można zmieniać (budżet rozpoznaje je po nazwie). */
    private static final java.util.Set<String> BUILT_IN_ROW_KEYS = java.util.Set.of(
            "wynagrodzenia", "publikacje", "wydarzenia", "podroze", "koszty-administracyjne", "promocja", "sprzet");

    private final BudgetMatrixService budgetMatrixService;
    private final BudgetSubcategoryOrderRepository budgetSubcategoryOrderRepository;

    public BudgetStructureService(BudgetMatrixService budgetMatrixService,
                                  BudgetSubcategoryOrderRepository budgetSubcategoryOrderRepository) {
        this.budgetMatrixService = budgetMatrixService;
        this.budgetSubcategoryOrderRepository = budgetSubcategoryOrderRepository;
    }

    @Transactional(readOnly = true)
    public BudgetStructureNodeDto getNode(int fiscalYear, String rowKey) {
        if (rowKey == null || rowKey.isBlank()) {
            throw new IllegalArgumentException("Brak identyfikatora kategorii");
        }

        BudgetDashboardDto dashboard = budgetMatrixService.getBudgetDashboardData(fiscalYear);
        List<BudgetDashboardDto.BudgetItemRowDto> breadcrumbs = new ArrayList<>();
        BudgetDashboardDto.BudgetItemRowDto row = findRowByKey(dashboard.getRows(), rowKey.trim(), breadcrumbs)
                .orElseThrow(() -> new IllegalArgumentException("Kategoria nie istnieje: " + rowKey));

        BudgetStructureNodeDto node = new BudgetStructureNodeDto();
        node.setRowKey(row.getRowKey());
        node.setName(row.getItemName());
        node.setEditable(!WYNAGRODZENIA_ROW_KEY.equals(row.getRowKey()));
        BudgetDashboardDto.CategoryOrderInfo ownOrder = dashboard.getCategoryOrderByRowKey() != null
                ? dashboard.getCategoryOrderByRowKey().get(row.getRowKey()) : null;
        node.setCategoryTemplateId(ownOrder != null && !BUILT_IN_ROW_KEYS.contains(row.getRowKey())
                ? ownOrder.getCategoryId() : null);
        node.setParentRowKey(breadcrumbs.isEmpty() ? null : breadcrumbs.get(breadcrumbs.size() - 1).getRowKey());
        node.setEvenMonthlySplit(isEvenMonthlySplit(row.getRowKey(), breadcrumbs));
        node.setBreadcrumbs(buildBreadcrumbs(breadcrumbs, row));
        node.setItems(buildItems(row, dashboard.getCategoryOrderByRowKey()));
        return node;
    }

    /** Pozycja ze wszystkimi podpozycjami według zagnieżdżenia (strona „Struktura budżetu”). */
    @Transactional(readOnly = true)
    public BudgetStructureNodeDto getTree(int fiscalYear, String rowKey) {
        BudgetStructureNodeDto node = getNode(fiscalYear, rowKey);
        BudgetDashboardDto dashboard = budgetMatrixService.getBudgetDashboardData(fiscalYear);
        BudgetDashboardDto.BudgetItemRowDto row = findRowByKey(dashboard.getRows(), rowKey.trim(), new ArrayList<>())
                .orElseThrow(() -> new IllegalArgumentException("Kategoria nie istnieje: " + rowKey));
        node.setItems(buildTree(row, dashboard.getCategoryOrderByRowKey(), 0));
        node.setMoveTargets(buildMoveTargets(dashboard.getRows(), null));
        return node;
    }

    /** Wszystkie kategorie i podkategorie budżetu (bez pracowników i wynagrodzeń) jako cele przeniesienia wydatku. */
    private List<BudgetStructureNodeDto.BudgetStructureBreadcrumbDto> buildMoveTargets(
            List<BudgetDashboardDto.BudgetItemRowDto> rows, String prefix) {
        List<BudgetStructureNodeDto.BudgetStructureBreadcrumbDto> targets = new ArrayList<>();
        if (rows == null) {
            return targets;
        }
        for (BudgetDashboardDto.BudgetItemRowDto row : rows) {
            String key = row.getRowKey();
            if (key == null || key.isBlank() || WYNAGRODZENIA_ROW_KEY.equals(key)
                    || key.contains("wynagrodzenia") || key.startsWith("employee-")) {
                continue;
            }
            String label = prefix == null ? row.getItemName() : prefix + " › " + row.getItemName();
            targets.add(new BudgetStructureNodeDto.BudgetStructureBreadcrumbDto(key, label));
            targets.addAll(buildMoveTargets(row.getChildren(), label));
        }
        return targets;
    }

    private List<BudgetStructureItemDto> buildTree(BudgetDashboardDto.BudgetItemRowDto parent,
                                                   Map<String, BudgetDashboardDto.CategoryOrderInfo> order,
                                                   int depth) {
        List<BudgetStructureItemDto> items = buildItems(parent, order);
        if (depth >= 12 || parent.getChildren() == null) {
            return items;
        }
        for (BudgetStructureItemDto item : items) {
            if (!item.isNavigable()) {
                continue;
            }
            parent.getChildren().stream()
                    .filter(child -> item.getRowKey().equals(child.getRowKey()))
                    .findFirst()
                    .ifPresent(child -> item.setChildren(buildTree(child, order, depth + 1)));
        }
        return items;
    }

    private static boolean isEvenMonthlySplit(String rowKey, List<BudgetDashboardDto.BudgetItemRowDto> ancestors) {
        if (MonthlySplit.isSalaryOrAdminRow(rowKey)) {
            return true;
        }
        for (BudgetDashboardDto.BudgetItemRowDto ancestor : ancestors) {
            if (MonthlySplit.isSalaryOrAdminRow(ancestor.getRowKey())) {
                return true;
            }
        }
        return false;
    }

    private List<BudgetStructureNodeDto.BudgetStructureBreadcrumbDto> buildBreadcrumbs(
            List<BudgetDashboardDto.BudgetItemRowDto> ancestors,
            BudgetDashboardDto.BudgetItemRowDto current) {
        List<BudgetStructureNodeDto.BudgetStructureBreadcrumbDto> crumbs = new ArrayList<>();
        for (BudgetDashboardDto.BudgetItemRowDto ancestor : ancestors) {
            crumbs.add(new BudgetStructureNodeDto.BudgetStructureBreadcrumbDto(
                    ancestor.getRowKey(), ancestor.getItemName()));
        }
        crumbs.add(new BudgetStructureNodeDto.BudgetStructureBreadcrumbDto(
                current.getRowKey(), current.getItemName()));
        return crumbs;
    }

    private List<BudgetStructureItemDto> buildItems(
            BudgetDashboardDto.BudgetItemRowDto parent,
            Map<String, BudgetDashboardDto.CategoryOrderInfo> categoryOrderByRowKey) {
        String parentRowKey = parent.getRowKey();
        Map<String, Integer> orderByKey = loadOrderByKey(parentRowKey);
        Map<String, BudgetSubcategoryOrder> storedByKey = loadStoredByKey(parentRowKey);

        List<BudgetStructureItemDto> items = new ArrayList<>();
        if (parent.getChildren() != null) {
            List<BudgetDashboardDto.BudgetItemRowDto> children = new ArrayList<>(parent.getChildren());
            children.sort(Comparator
                    .comparingInt((BudgetDashboardDto.BudgetItemRowDto child) ->
                            orderByKey.getOrDefault(child.getRowKey(), Integer.MAX_VALUE))
                    .thenComparing(child -> child.getItemName() != null ? child.getItemName() : ""));

            for (BudgetDashboardDto.BudgetItemRowDto child : children) {
                if (child.getRowKey() == null || child.getRowKey().isBlank()) {
                    continue;
                }
                BudgetSubcategoryOrder stored = storedByKey.get(child.getRowKey());
                if (stored != null && stored.isHidden()) {
                    continue;
                }
                items.add(toStructureItem(child, parentRowKey, stored, categoryOrderByRowKey));
            }
        }

        if (parent.getAllocations() != null) {
            for (CostAllocationDto allocation : parent.getAllocations()) {
                if (allocation.getItemName() == null || allocation.getItemName().isBlank()) {
                    continue;
                }
                String allocRowKey = allocationRowKey(parentRowKey, allocation.getItemName());
                BudgetSubcategoryOrder stored = storedByKey.get(allocRowKey);
                BudgetStructureItemDto item = new BudgetStructureItemDto();
                item.setRowKey(allocRowKey);
                item.setName(allocation.getItemName());
                item.setParentRowKey(parentRowKey);
                item.setNavigable(false);
                item.setDeletable(false);
                item.setExpense(true);
                item.setLineKey(allocation.getLineKey() != null ? allocation.getLineKey() : allocRowKey);
                item.setPlannedAmount(resolvePlannedAmount(stored, allocation.getAmount()));
                items.add(item);
            }
        }
        return items;
    }

    private BudgetStructureItemDto toStructureItem(
            BudgetDashboardDto.BudgetItemRowDto child,
            String parentRowKey,
            BudgetSubcategoryOrder stored,
            Map<String, BudgetDashboardDto.CategoryOrderInfo> categoryOrderByRowKey) {
        BudgetStructureItemDto item = new BudgetStructureItemDto();
        item.setRowKey(child.getRowKey());
        item.setName(child.getItemName());
        item.setParentRowKey(parentRowKey);
        item.setNavigable(isNavigable(child.getRowKey()));
        item.setRenamable(true);
        item.setDeletable(isDeletable(child.getRowKey(), parentRowKey, stored, categoryOrderByRowKey));
        item.setPlannedAmount(resolvePlannedAmount(stored, child.getTotalCost()));
        if (categoryOrderByRowKey != null && child.getRowKey() != null) {
            BudgetDashboardDto.CategoryOrderInfo order = categoryOrderByRowKey.get(child.getRowKey());
            if (order != null) {
                item.setCategoryTemplateId(order.getCategoryId());
            }
        }
        return item;
    }

    private static BigDecimal resolvePlannedAmount(BudgetSubcategoryOrder stored, BigDecimal fallback) {
        if (stored != null && stored.getPlannedAmount() != null) {
            return stored.getPlannedAmount();
        }
        return fallback != null && fallback.signum() > 0 ? fallback : null;
    }

    private static boolean isNavigable(String rowKey) {
        return !ADMIN_WYNAGRODZENIA_KEY.equals(rowKey);
    }

    private static String allocationRowKey(String parentRowKey, String label) {
        return pl.ngo.budget.util.BudgetLineKeys.of(parentRowKey, label);
    }

    private boolean isDeletable(String rowKey,
                                  String parentRowKey,
                                  BudgetSubcategoryOrder stored,
                                  Map<String, BudgetDashboardDto.CategoryOrderInfo> categoryOrderByRowKey) {
        if (categoryOrderByRowKey != null && categoryOrderByRowKey.containsKey(rowKey)) {
            return true;
        }
        if (stored != null && stored.getName() != null && !stored.getName().isBlank()) {
            return true;
        }
        return !isBuiltInSubcategory(rowKey, parentRowKey);
    }

    private boolean isBuiltInSubcategory(String rowKey, String parentRowKey) {
        if ("koszty-administracyjne".equals(parentRowKey)) {
            return ADMIN_WYNAGRODZENIA_KEY.equals(rowKey)
                    || "admin-biuro".equals(rowKey)
                    || "admin-pozostale".equals(rowKey);
        }
        return "podroze-krajowe".equals(rowKey) || "podroze-zagraniczne".equals(rowKey);
    }

    private Map<String, BudgetSubcategoryOrder> loadStoredByKey(String parentRowKey) {
        Map<String, BudgetSubcategoryOrder> byKey = new LinkedHashMap<>();
        budgetSubcategoryOrderRepository.findByParentRowKeyOrderByDisplayOrderAsc(parentRowKey)
                .forEach(entry -> byKey.put(entry.getRowKey(), entry));
        return byKey;
    }

    private Map<String, Integer> loadOrderByKey(String parentRowKey) {
        Map<String, Integer> orderByKey = new LinkedHashMap<>();
        budgetSubcategoryOrderRepository.findByParentRowKeyOrderByDisplayOrderAsc(parentRowKey).forEach(entry ->
                orderByKey.put(entry.getRowKey(), entry.getDisplayOrder()));
        return orderByKey;
    }

    private Optional<BudgetDashboardDto.BudgetItemRowDto> findRowByKey(
            List<BudgetDashboardDto.BudgetItemRowDto> rows,
            String rowKey,
            List<BudgetDashboardDto.BudgetItemRowDto> breadcrumbs) {
        for (BudgetDashboardDto.BudgetItemRowDto row : rows) {
            if (rowKey.equals(row.getRowKey())) {
                return Optional.of(row);
            }
            if (row.getChildren() != null && !row.getChildren().isEmpty()) {
                breadcrumbs.add(row);
                Optional<BudgetDashboardDto.BudgetItemRowDto> nested =
                        findRowByKey(row.getChildren(), rowKey, breadcrumbs);
                if (nested.isPresent()) {
                    return nested;
                }
                breadcrumbs.remove(breadcrumbs.size() - 1);
            }
        }
        return Optional.empty();
    }
}
