package pl.ngo.budget.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.ngo.budget.dto.BudgetDashboardDto;
import pl.ngo.budget.dto.NewBudgetMonthSaveCommand;
import pl.ngo.budget.dto.NewBudgetPlanSaveCommand;
import pl.ngo.budget.dto.NewBudgetWizardDto;
import pl.ngo.budget.service.NewBudgetService;
import pl.ngo.budget.service.ViewExcelExportService;
import pl.ngo.budget.util.BudgetSection;
import pl.ngo.budget.util.ExcelDownload;

import java.io.IOException;

@Controller
@RequestMapping({"/admin/budget/new", "/realizacja/budget/new"})
public class NewBudgetController {

    private final NewBudgetService newBudgetService;
    private final ViewExcelExportService viewExcelExportService;

    public NewBudgetController(NewBudgetService newBudgetService,
                               ViewExcelExportService viewExcelExportService) {
        this.newBudgetService = newBudgetService;
        this.viewExcelExportService = viewExcelExportService;
    }

    private static String wizardRedirect(String suffix) {
        return "redirect:" + BudgetSection.pageBase() + "/budget/new" + suffix;
    }

    @PostMapping("/start")
    public String start(@RequestParam int fiscalYear,
                        @RequestParam(required = false) Integer copyFromYear,
                        RedirectAttributes redirectAttributes) {
        try {
            newBudgetService.prepareYear(fiscalYear, copyFromYear);
            return wizardRedirect("/" + fiscalYear + "/plan");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return wizardRedirect("");
        }
    }

    @GetMapping("/{year}/plan")
    public String plan(@PathVariable int year, Model model) {
        model.addAttribute("activeSection", "budget-new");
        model.addAttribute("wizard", newBudgetService.loadPlanStep(year));
        return "admin/budget-new-plan";
    }

    @GetMapping("/{year}/plan/export")
    public void exportPlan(@PathVariable int year, HttpServletResponse response) throws IOException {
        ExcelDownload.prepare(response, "plan-" + year + ".xlsx");
        viewExcelExportService.writeNewBudgetPlan(response.getOutputStream(), year);
    }

    @PostMapping("/{year}/plan")
    public String savePlan(@PathVariable int year,
                           @ModelAttribute NewBudgetPlanSaveCommand command,
                           RedirectAttributes redirectAttributes) {
        try {
            newBudgetService.savePlanStep(year, command);
            return wizardRedirect("/" + year + "/month/1");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return wizardRedirect("/" + year + "/plan");
        }
    }

    @GetMapping("/{year}/month/{month}")
    public String month(@PathVariable int year,
                        @PathVariable int month,
                        Model model) {
        model.addAttribute("activeSection", "budget-new");
        model.addAttribute("wizard", newBudgetService.loadMonthStep(year, month));
        return "admin/budget-new-month";
    }

    @GetMapping("/{year}/month/{month}/export")
    public void exportMonth(@PathVariable int year,
                            @PathVariable int month,
                            HttpServletResponse response) throws IOException {
        ExcelDownload.prepare(response, "plan-" + year + "-" + String.format("%02d", month) + ".xlsx");
        viewExcelExportService.writeNewBudgetMonth(response.getOutputStream(), year, month);
    }

    @PostMapping("/{year}/month/{month}")
    public String saveMonth(@PathVariable int year,
                            @PathVariable int month,
                            @ModelAttribute NewBudgetMonthSaveCommand command,
                            RedirectAttributes redirectAttributes) {
        try {
            newBudgetService.saveMonthStep(year, month, command);
            if (month >= 12) {
                return wizardRedirect("/" + year + "/review");
            }
            return wizardRedirect("/" + year + "/month/" + (month + 1));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return wizardRedirect("/" + year + "/month/" + month);
        }
    }

    @GetMapping("/{year}/review")
    public String review(@PathVariable int year, Model model) {
        BudgetDashboardDto dashboard = newBudgetService.loadReview(year);
        model.addAttribute("activeSection", "budget-new");
        model.addAttribute("fiscalYear", year);
        model.addAttribute("dashboard", dashboard);
        return "admin/budget-new-review";
    }

    @GetMapping("/{year}/review/export")
    public void exportReview(@PathVariable int year, HttpServletResponse response) throws IOException {
        ExcelDownload.prepare(response, "budzet-" + year + "-podsumowanie.xlsx");
        viewExcelExportService.writeNewBudgetReview(response.getOutputStream(), year);
    }
}
