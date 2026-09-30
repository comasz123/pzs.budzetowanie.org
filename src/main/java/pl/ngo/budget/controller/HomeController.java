package pl.ngo.budget.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.ngo.budget.service.BudgetMatrixService;
import pl.ngo.budget.service.ViewExcelExportService;
import pl.ngo.budget.util.ExcelDownload;

import java.io.IOException;
import java.time.LocalDate;

@Controller
public class HomeController {

    private final BudgetMatrixService budgetMatrixService;
    private final ViewExcelExportService viewExcelExportService;

    public HomeController(BudgetMatrixService budgetMatrixService,
                          ViewExcelExportService viewExcelExportService) {
        this.budgetMatrixService = budgetMatrixService;
        this.viewExcelExportService = viewExcelExportService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("chart", budgetMatrixService.getHomeChart(LocalDate.now().getYear()));
        return "home";
    }

    @GetMapping("/export")
    public void exportHome(@RequestParam(required = false) Integer year,
                           HttpServletResponse response) throws IOException {
        int selectedYear = year != null ? year : LocalDate.now().getYear();
        ExcelDownload.prepare(response, "pulpit-" + selectedYear + ".xlsx");
        viewExcelExportService.writeHome(response.getOutputStream(), selectedYear);
    }
}
