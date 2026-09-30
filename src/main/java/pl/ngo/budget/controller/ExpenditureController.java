package pl.ngo.budget.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.ngo.budget.dto.ExpenditureImportResult;
import pl.ngo.budget.entity.cost.Expenditure;
import pl.ngo.budget.service.ExpenditureImportService;
import pl.ngo.budget.service.ViewExcelExportService;
import pl.ngo.budget.util.BudgetSection;
import pl.ngo.budget.util.ExcelDownload;
import pl.ngo.budget.util.PolishMonthNames;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Controller
@RequestMapping({"/admin/expenditures", "/realizacja/wydatki"})
public class ExpenditureController {

    private final ExpenditureImportService expenditureImportService;
    private final ViewExcelExportService viewExcelExportService;

    public ExpenditureController(ExpenditureImportService expenditureImportService,
                                 ViewExcelExportService viewExcelExportService) {
        this.expenditureImportService = expenditureImportService;
        this.viewExcelExportService = viewExcelExportService;
    }

    @GetMapping
    public String list(@RequestParam(value = "year", required = false) Integer year,
                       @RequestParam(value = "month", required = false) Integer month,
                       @RequestParam(value = "edit", required = false) Boolean edit,
                       @RequestParam(value = "split", required = false) Long splitId,
                       Model model) {
        int fiscalYear = year != null ? year : LocalDate.now().getYear();
        int selectedMonth = month != null ? month : LocalDate.now().getMonthValue();
        if (selectedMonth < 1 || selectedMonth > 12) {
            selectedMonth = LocalDate.now().getMonthValue();
        }

        List<Expenditure> expenditures = expenditureImportService.listForMonth(fiscalYear, selectedMonth);
        Map<Integer, Long> countsByMonth = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            countsByMonth.put(m, expenditureImportService.countForMonth(fiscalYear, m));
        }

        model.addAttribute("availableYears", List.of(2025, 2026));
        model.addAttribute("activeSection", "expenditures");
        model.addAttribute("fiscalYear", fiscalYear);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("monthNames", PolishMonthNames.ALL);
        model.addAttribute("expenditures", expenditures);
        model.addAttribute("countsByMonth", countsByMonth);
        model.addAttribute("totalInMonth", expenditures.size());
        model.addAttribute("totalNet", sum(expenditures, Expenditure::getNetAmount));
        model.addAttribute("totalGross", sum(expenditures, Expenditure::getGrossAmount));
        model.addAttribute("grants", expenditureImportService.listGrants());
        model.addAttribute("projects", expenditureImportService.listProjects());
        model.addAttribute("editMode", Boolean.TRUE.equals(edit));
        model.addAttribute("splitId", Boolean.TRUE.equals(edit) ? splitId : null);
        return "admin/expenditures";
    }

    @GetMapping("/export")
    public void exportMonth(@RequestParam(value = "year", required = false) Integer year,
                            @RequestParam(value = "month", required = false) Integer month,
                            HttpServletResponse response) throws IOException {
        int fiscalYear = year != null ? year : LocalDate.now().getYear();
        int selectedMonth = month != null ? month : LocalDate.now().getMonthValue();
        if (selectedMonth < 1 || selectedMonth > 12) {
            selectedMonth = LocalDate.now().getMonthValue();
        }
        ExcelDownload.prepare(response, "wydatki-" + fiscalYear + "-" + String.format("%02d", selectedMonth) + ".xlsx");
        viewExcelExportService.writeExpenditures(response.getOutputStream(), fiscalYear, selectedMonth);
    }

    @PostMapping("/{id}/grant")
    public String assignGrant(@PathVariable Long id,
                              @RequestParam Long grantId,
                              @RequestParam int year,
                              @RequestParam int month,
                              @RequestParam(value = "edit", required = false) Boolean edit,
                              RedirectAttributes redirectAttributes) {
        try {
            expenditureImportService.assignGrant(id, grantId);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToList(year, month, edit, null);
    }

    @PostMapping("/{id}/project")
    public String assignProject(@PathVariable Long id,
                                @RequestParam Long projectId,
                                @RequestParam int year,
                                @RequestParam int month,
                                @RequestParam(value = "edit", required = false) Boolean edit,
                                RedirectAttributes redirectAttributes) {
        try {
            expenditureImportService.assignProject(id, projectId);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToList(year, month, edit, null);
    }

    @PostMapping("/{id}/split")
    public String split(@PathVariable Long id,
                        @RequestParam int parts,
                        @RequestParam List<String> grantId,
                        @RequestParam List<String> amount,
                        @RequestParam int year,
                        @RequestParam int month,
                        RedirectAttributes redirectAttributes) {
        try {
            int count = Math.min(Math.max(parts, 0), Math.min(grantId.size(), amount.size()));
            List<Long> grantIds = new java.util.ArrayList<>();
            List<BigDecimal> amounts = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                grantIds.add(parseGrantId(grantId.get(i)));
                amounts.add(parseAmount(amount.get(i)));
            }
            expenditureImportService.splitAcrossGrants(id, grantIds, amounts);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return redirectToList(year, month, true, id);
        }
        return redirectToList(year, month, true, null);
    }

    @PostMapping("/{id}/split/clear")
    public String clearSplit(@PathVariable Long id,
                             @RequestParam int year,
                             @RequestParam int month,
                             RedirectAttributes redirectAttributes) {
        try {
            expenditureImportService.clearGrantSplit(id);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToList(year, month, true, null);
    }

    private static Long parseGrantId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Wybierz grant dla każdej pozycji.");
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Wybierz grant dla każdej pozycji.");
        }
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Podaj kwotę każdej pozycji.");
        }
        String normalized = raw.trim().replace(" ", "").replace("\u00a0", "").replace(',', '.');
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Niepoprawna kwota: " + raw);
        }
    }

    private static BigDecimal sum(List<Expenditure> expenditures, Function<Expenditure, BigDecimal> amount) {
        return expenditures.stream()
                .map(amount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String redirectToList(int year, int month, Boolean edit, Long splitId) {
        String expenditureBase = BudgetSection.REALIZATION.equals(BudgetSection.currentBase())
                ? "/realizacja/wydatki"
                : "/admin/expenditures";
        String target = "redirect:" + expenditureBase + "?year=" + year + "&month=" + month;
        if (Boolean.TRUE.equals(edit)) {
            target += "&edit=true";
        }
        if (splitId != null) {
            target += "&split=" + splitId;
        }
        return target;
    }

    @GetMapping("/import")
    public String importForm(@RequestParam(value = "year", required = false) Integer year,
                             @RequestParam(value = "month", required = false) Integer month,
                             Model model) {
        int fiscalYear = year != null ? year : LocalDate.now().getYear();
        int selectedMonth = month != null ? month : LocalDate.now().getMonthValue();

        model.addAttribute("availableYears", List.of(2025, 2026));
        model.addAttribute("activeSection", "expenditures");
        model.addAttribute("fiscalYear", fiscalYear);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("monthNames", PolishMonthNames.ALL);
        model.addAttribute("existingCount",
                expenditureImportService.countForMonth(fiscalYear, selectedMonth));
        return "admin/expenditure-import";
    }

    @PostMapping("/import")
    public String importMonth(@RequestParam("file") MultipartFile file,
                              @RequestParam("year") int year,
                              @RequestParam("month") int month,
                              @RequestParam(value = "replaceExisting", defaultValue = "false") boolean replaceExisting,
                              RedirectAttributes redirectAttributes) {
        ExpenditureImportResult result = expenditureImportService.importMonth(file, year, month, replaceExisting);

        redirectAttributes.addFlashAttribute("importResult", result);
        if (result.getImported() > 0) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Zaimportowano " + result.getImported() + " wydatków dla "
                            + PolishMonthNames.ALL.get(month - 1) + " " + year + ".");
        }
        if (!result.getErrors().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Import zakończony z błędami (" + result.getErrors().size() + ").");
        } else if (result.getImported() == 0 && result.getRowsRead() == 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Brak wierszy dla wybranego miesiąca w pliku.");
        }
        String expenditureBase = BudgetSection.REALIZATION.equals(BudgetSection.currentBase())
                ? "/realizacja/wydatki"
                : "/admin/expenditures";
        return "redirect:" + expenditureBase + "/import?year=" + year + "&month=" + month;
    }

    @GetMapping("/sample")
    public void downloadSample(@RequestParam(value = "year", defaultValue = "2026") int year,
                               HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"wydatki-" + year + ".xlsx\"");
        expenditureImportService.writeSampleExcel(response.getOutputStream(), year);
    }
}
