package pl.ngo.budget.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.ngo.budget.security.CurrentAccess;
import pl.ngo.budget.service.BudgetExcelExportService;
import pl.ngo.budget.util.ExcelDownload;
import pl.ngo.budget.service.BudgetMatrixService;
import pl.ngo.budget.service.BudgetSetupService;
import pl.ngo.budget.service.BudgetStructureService;
import pl.ngo.budget.service.DatabaseBackupService;
import pl.ngo.budget.util.BudgetSection;
import pl.ngo.budget.util.PolishMonthNames;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@Controller
public class DashboardController {

    private final BudgetMatrixService budgetMatrixService;
    private final BudgetSetupService budgetSetupService;
    private final BudgetStructureService budgetStructureService;
    private final DatabaseBackupService databaseBackupService;
    private final BudgetExcelExportService budgetExcelExportService;
    private final CurrentAccess currentAccess;

    public DashboardController(BudgetMatrixService budgetMatrixService,
                               BudgetSetupService budgetSetupService,
                               BudgetStructureService budgetStructureService,
                               DatabaseBackupService databaseBackupService,
                               BudgetExcelExportService budgetExcelExportService,
                               CurrentAccess currentAccess) {
        this.budgetMatrixService = budgetMatrixService;
        this.budgetSetupService = budgetSetupService;
        this.budgetStructureService = budgetStructureService;
        this.databaseBackupService = databaseBackupService;
        this.currentAccess = currentAccess;
        this.budgetExcelExportService = budgetExcelExportService;
    }

    @GetMapping({"/dashboard", "/realizacja"})
    public String dashboard(@RequestParam(required = false) Integer year,
                            @RequestParam(required = false) Boolean edit,
                            Model model,
                            HttpServletRequest request) {
        ensureSession(request);
        int selectedYear = year != null ? year : LocalDate.now().getYear();
        populateYearDashboard(model, selectedYear);
        model.addAttribute("editMode", currentAccess.editRequested(edit)
                && !BudgetSection.REALIZATION.equals(BudgetSection.currentBase()));
        return "dashboard";
    }

    @GetMapping({"/dashboard/month", "/realizacja/month"})
    public String dashboardMonth(@RequestParam(required = false) Integer year,
                                 @RequestParam(required = false) Integer month,
                                 @RequestParam(required = false) Boolean edit,
                                 Model model,
                                 HttpServletRequest request) {
        ensureSession(request);
        int selectedYear = year != null ? year : LocalDate.now().getYear();
        int selectedMonth = month != null ? month : LocalDate.now().getMonthValue();
        if (selectedMonth < 1 || selectedMonth > 12) {
            selectedMonth = LocalDate.now().getMonthValue();
        }
        model.addAttribute("monthView", true);
        model.addAttribute("editMode", currentAccess.editRequested(edit)
                && !BudgetSection.REALIZATION.equals(BudgetSection.currentBase()));
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("selectedMonthName", PolishMonthNames.of(selectedMonth));
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("availableYears", budgetMatrixService.getAvailableFiscalYears());
        model.addAttribute("budgetData", budgetMatrixService.getBudgetDashboardDataForMonth(selectedYear, selectedMonth));
        realizeIfNeeded(model, selectedYear, selectedMonth);
        model.addAttribute("backupInfo", databaseBackupService.getLatestBackupInfo());
        return "dashboard";
    }

    @GetMapping({"/dashboard/row", "/realizacja/row"})
    public String dashboardRow(@RequestParam int year,
                               @RequestParam(required = false) Integer month,
                               @RequestParam String rowKey,
                               Model model) {
        if (month != null && (month < 1 || month > 12)) {
            month = LocalDate.now().getMonthValue();
        }
        model.addAttribute("monthView", month != null);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedMonth", month);
        if (month != null) {
            model.addAttribute("selectedMonthName", PolishMonthNames.of(month));
        }
        model.addAttribute("availableYears", budgetMatrixService.getAvailableFiscalYears());
        model.addAttribute("rowDetail", budgetMatrixService.getBudgetRowDetail(year, month, rowKey));
        realizeIfNeeded(model, year, month);
        return "dashboard-row";
    }

    @GetMapping({"/dashboard/export", "/realizacja/export"})
    public void exportBudget(@RequestParam(required = false) Integer year,
                             @RequestParam(required = false) Integer month,
                             @RequestParam(required = false) String rowKey,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        int selectedYear = year != null ? year : LocalDate.now().getYear();
        Integer selectedMonth = month != null && month >= 1 && month <= 12 ? month : null;
        boolean realization = BudgetSection.REALIZATION.equals(BudgetSection.base(request));
        ExcelDownload.prepare(response, budgetExportFilename(realization, selectedYear, selectedMonth, rowKey));
        if (rowKey != null && !rowKey.isBlank()) {
            budgetExcelExportService.writeRow(response.getOutputStream(), selectedYear, selectedMonth, rowKey.trim(), realization);
            return;
        }
        budgetExcelExportService.writeDashboard(response.getOutputStream(), selectedYear, selectedMonth, realization);
    }

    @GetMapping({"/dashboard/backup/download", "/realizacja/backup/download"})
    public Object downloadLatestBackup(@RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month,
                                       RedirectAttributes redirectAttributes) {
        try {
            Path backup = databaseBackupService.findLatestBackupFile()
                    .orElseThrow(() -> new IllegalStateException("Brak zapisanej kopii zapasowej"));
            Resource resource = new FileSystemResource(backup);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + backup.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType("application/sql"))
                    .body(resource);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:" + dashboardPath(year, month, false);
        }
    }

    @PostMapping({"/dashboard/categories/reorder", "/realizacja/categories/reorder"})
    public String reorderBudgetCategoriesOnDashboard(@RequestParam("categoryId") List<Long> categoryIds,
                                                     @RequestParam(required = false) Integer year,
                                                     @RequestParam(required = false) Integer month,
                                                     @RequestParam(required = false) Boolean edit,
                                                     RedirectAttributes redirectAttributes) {
        try {
            budgetSetupService.reorderBudgetCategories(categoryIds);
            redirectAttributes.addFlashAttribute("successMessage", "Zmieniono kolejność kategorii.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToDashboard(year, month, edit);
    }

    @PostMapping({"/dashboard/subcategories/reorder", "/realizacja/subcategories/reorder"})
    public String reorderBudgetSubcategoriesOnDashboard(@RequestParam String parentRowKey,
                                                        @RequestParam("rowKey") List<String> rowKeys,
                                                        @RequestParam(required = false) Integer year,
                                                        @RequestParam(required = false) Integer month,
                                                        @RequestParam(required = false) Boolean edit,
                                                        RedirectAttributes redirectAttributes) {
        try {
            budgetSetupService.reorderBudgetSubcategories(parentRowKey, rowKeys);
            redirectAttributes.addFlashAttribute("successMessage", "Zmieniono kolejność podpozycji.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToDashboard(year, month, edit);
    }

    @PostMapping({"/dashboard/categories/{id}/move", "/realizacja/categories/{id}/move"})
    public String moveBudgetCategoryOnDashboard(@PathVariable Long id,
                                                @RequestParam int direction,
                                                @RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month,
                                                @RequestParam(required = false) Boolean edit,
                                                RedirectAttributes redirectAttributes) {
        try {
            budgetSetupService.moveBudgetCategory(id, direction);
            redirectAttributes.addFlashAttribute("successMessage", "Zmieniono kolejność kategorii.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToDashboard(year, month, edit);
    }

    @PostMapping({"/dashboard/categories", "/realizacja/categories"})
    public String addBudgetCategory(@RequestParam String name,
                                    @RequestParam(required = false) Integer year,
                                    @RequestParam(required = false) Integer month,
                                    RedirectAttributes redirectAttributes) {
        try {
            budgetSetupService.addBudgetCategory(name);
            redirectAttributes.addFlashAttribute("successMessage", "Dodano pozycję.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToDashboard(year, month, true);
    }

    @PostMapping({"/dashboard/categories/{id}/delete", "/realizacja/categories/{id}/delete"})
    public String deleteBudgetCategory(@PathVariable Long id,
                                       @RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month,
                                       RedirectAttributes redirectAttributes) {
        try {
            budgetSetupService.deleteBudgetCategory(id);
            redirectAttributes.addFlashAttribute("successMessage", "Usunięto pozycję.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToDashboard(year, month, true);
    }

    @PostMapping({"/dashboard/subcategories", "/realizacja/subcategories"})
    public String addBudgetSubcategory(@RequestParam String parentRowKey,
                                       @RequestParam String name,
                                       @RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month,
                                       RedirectAttributes redirectAttributes) {
        try {
            budgetSetupService.addBudgetSubcategory(parentRowKey, name);
            redirectAttributes.addFlashAttribute("successMessage", "Dodano podpozycję.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToDashboard(year, month, true);
    }

    @PostMapping({"/dashboard/subcategories/delete", "/realizacja/subcategories/delete"})
    public String deleteBudgetSubcategory(@RequestParam String parentRowKey,
                                          @RequestParam String rowKey,
                                          @RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) Integer month,
                                          RedirectAttributes redirectAttributes) {
        try {
            budgetSetupService.deleteBudgetSubcategory(parentRowKey, rowKey);
            redirectAttributes.addFlashAttribute("successMessage", "Usunięto podpozycję.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToDashboard(year, month, true);
    }

    @GetMapping({"/dashboard/structure/node", "/realizacja/structure/node"})
    public String structureNode(@RequestParam String rowKey,
                                @RequestParam(required = false) Integer year,
                                Model model) {
        int selectedYear = year != null ? year : LocalDate.now().getYear();
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("structureNode", budgetStructureService.getNode(selectedYear, rowKey));
        return "fragments/budget-structure-node :: content";
    }

    @PostMapping({"/dashboard/month/amount", "/realizacja/month/amount"})
    @ResponseBody
    public ResponseEntity<String> updateMonthAmount(@RequestParam int year,
                                                    @RequestParam int month,
                                                    @RequestParam String kind,
                                                    @RequestParam String ids,
                                                    @RequestParam(required = false) BigDecimal amount) {
        try {
            budgetSetupService.updateDisplayedMonthAmount(year, month, kind, ids, amount);
            return ResponseEntity.ok("ok");
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage() != null ? ex.getMessage() : "Nie udało się zapisać kwoty");
        }
    }

    @PostMapping({"/dashboard/structure/amount", "/realizacja/structure/amount"})
    public String setStructurePlannedAmount(@RequestParam String parentRowKey,
                                            @RequestParam String rowKey,
                                            @RequestParam(required = false) BigDecimal amount,
                                            @RequestParam(required = false) Integer year,
                                            @RequestParam(required = false) Integer month,
                                            RedirectAttributes redirectAttributes) {
        try {
            budgetSetupService.setSubcategoryPlannedAmount(parentRowKey, rowKey, amount, year);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToDashboard(year, month, true);
    }

    @PostMapping({"/dashboard/structure/has-subcategories", "/realizacja/structure/has-subcategories"})
    public String setStructureHasSubcategories(@RequestParam String parentRowKey,
                                               @RequestParam String rowKey,
                                               @RequestParam boolean hasSubcategories,
                                               @RequestParam(required = false) Integer year,
                                               @RequestParam(required = false) Integer month,
                                               RedirectAttributes redirectAttributes) {
        try {
            budgetSetupService.setSubcategoryHasSubcategories(parentRowKey, rowKey, hasSubcategories);
            redirectAttributes.addFlashAttribute("successMessage", "Zaktualizowano ustawienia pozycji.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToDashboard(year, month, true);
    }

    private void realizeIfNeeded(Model model, int year, Integer month) {
        if (!BudgetSection.REALIZATION.equals(BudgetSection.currentBase())) {
            return;
        }
        Object data = model.getAttribute("budgetData");
        if (data instanceof pl.ngo.budget.dto.BudgetDashboardDto dashboard) {
            budgetMatrixService.applyRealizationExpenditures(dashboard, year, month);
            return;
        }
        Object detail = model.getAttribute("rowDetail");
        if (detail instanceof pl.ngo.budget.dto.BudgetRowDetailDto rowDetail && rowDetail.getDashboard() != null) {
            budgetMatrixService.applyRealizationExpenditures(rowDetail.getDashboard(), year, month);
        }
    }

    private String redirectToDashboard(Integer year, Integer month, Boolean edit) {
        return "redirect:" + dashboardPath(year, month, edit);
    }

    private String dashboardPath(Integer year, Integer month, Boolean edit) {
        int selectedYear = year != null ? year : LocalDate.now().getYear();
        String editQuery = Boolean.TRUE.equals(edit) ? "&edit=true" : "";
        String base = BudgetSection.currentBase();
        if (month != null && month >= 1 && month <= 12) {
            return base + "/month?year=" + selectedYear + "&month=" + month + editQuery;
        }
        return base + "?year=" + selectedYear + (Boolean.TRUE.equals(edit) ? "&edit=true" : "");
    }

    private String redirectToDashboard(Integer year, Integer month) {
        return redirectToDashboard(year, month, false);
    }

    private static String budgetExportFilename(boolean realization, int year, Integer month, String rowKey) {
        String prefix = realization ? "realizacja-" : "budzet-";
        if (rowKey != null && !rowKey.isBlank()) {
            String key = rowKey.trim().replaceAll("[^A-Za-z0-9._-]", "-");
            if (key.length() > 40) {
                key = key.substring(0, 40);
            }
            return prefix + "wiersz-" + key + ".xlsx";
        }
        if (month != null) {
            return prefix + year + "-" + String.format("%02d", month) + ".xlsx";
        }
        return prefix + year + ".xlsx";
    }

    private void populateYearDashboard(Model model, int selectedYear) {
        model.addAttribute("monthView", false);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("availableYears", budgetMatrixService.getAvailableFiscalYears());
        model.addAttribute("budgetData", budgetMatrixService.getBudgetDashboardData(selectedYear));
        realizeIfNeeded(model, selectedYear, null);
        model.addAttribute("backupInfo", databaseBackupService.getLatestBackupInfo());
    }

    /** POST forms with th:action need a session before Thymeleaf starts streaming the response. */
    private static void ensureSession(HttpServletRequest request) {
        request.getSession(true);
    }
}
