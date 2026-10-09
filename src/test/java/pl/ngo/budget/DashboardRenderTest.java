package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.entity.cost.Expenditure;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.repository.ExpenditureRepository;
import pl.ngo.budget.repository.GrantRepository;
import pl.ngo.budget.service.BudgetMatrixService;
import pl.ngo.budget.service.ExpenditureImportService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetMatrixService budgetMatrixService;

    @Autowired
    private ExpenditureImportService expenditureImportService;

    @Autowired
    private ExpenditureRepository expenditureRepository;

    @Autowired
    private GrantRepository grantRepository;

    @Test
    void monthTotalsSumToYearCost() {
        BigDecimal year = budgetMatrixService.getBudgetDashboardDataForYear(2026).getTotalCost();
        BigDecimal months = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            months = months.add(budgetMatrixService.getBudgetDashboardDataForMonth(2026, month).getTotalCost());
        }
        assertEquals(0, year.compareTo(months), "year " + year + " vs months " + months);
        var yearRows = new java.util.LinkedHashMap<String, BigDecimal>();
        collectRowCosts(budgetMatrixService.getBudgetDashboardDataForYear(2026).getRows(), yearRows);
        var monthRows = new java.util.LinkedHashMap<String, BigDecimal>();
        for (int month = 1; month <= 12; month++) {
            collectRowCosts(budgetMatrixService.getBudgetDashboardDataForMonth(2026, month).getRows(), monthRows);
        }
        for (var entry : yearRows.entrySet()) {
            BigDecimal summed = monthRows.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            assertEquals(0, entry.getValue().compareTo(summed), entry.getKey() + " year " + entry.getValue() + " months " + summed);
        }
    }

    private static void collectRowCosts(java.util.List<pl.ngo.budget.dto.BudgetDashboardDto.BudgetItemRowDto> rows,
                                         java.util.Map<String, BigDecimal> costs) {
        if (rows == null) {
            return;
        }
        for (var row : rows) {
            if (row.getRowKey() != null) {
                costs.merge(row.getRowKey(), row.getTotalCost() != null ? row.getTotalCost() : BigDecimal.ZERO, BigDecimal::add);
            }
            collectRowCosts(row.getChildren(), costs);
        }
    }

    @Test
    @Transactional
    void splitDividesGrossAcrossTwoGrants() {
        List<Expenditure> expenditures = expenditureRepository.findAllWithGrantAndItemByFiscalYear(2026);
        if (expenditures.isEmpty()) {
            return;
        }
        Expenditure expenditure = expenditures.get(0);
        String categoryCode = expenditure.getBudgetItem().getCode();
        List<Grant> matching = new ArrayList<>();
        for (Grant grant : grantRepository.findAllWithBudgetItems()) {
            boolean hasCategory = grant.getBudgetItems().stream()
                    .anyMatch(item -> categoryCode.equals(item.getCode()));
            if (hasCategory) {
                matching.add(grant);
            }
            if (matching.size() == 2) {
                break;
            }
        }
        if (matching.size() < 2) {
            return;
        }
        BigDecimal gross = expenditure.getGrossAmount();
        BigDecimal first = gross.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        BigDecimal second = gross.subtract(first);
        expenditureImportService.splitAcrossGrants(
                expenditure.getId(),
                List.of(matching.get(0).getId(), matching.get(1).getId()),
                List.of(first, second));
        Expenditure stored = expenditureRepository.findById(expenditure.getId()).orElseThrow();
        assertEquals(2, stored.getGrantShares().size());
        assertEquals(0, first.add(second).compareTo(
                stored.getGrantShares().get(0).getAmount().add(stored.getGrantShares().get(1).getAmount())));
    }

    @Test
    void startPageListsModules() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Budżet Organizacji i Pokrycie Grantowe")))
                .andExpect(content().string(containsString("href=\"/dashboard\"")))
                .andExpect(content().string(containsString("Realizacja budżetu")))
                .andExpect(content().string(containsString("href=\"/realizacja\"")))
                .andExpect(content().string(containsString("id=\"home-cashflow-chart\"")))
                .andExpect(content().string(containsString("Wpływy")))
                .andExpect(content().string(containsString("Planowany koszt")))
                .andExpect(content().string(containsString("Saldo narastająco")))
                .andExpect(content().string(containsString("01 Jan")))
                .andExpect(content().string(containsString("31 Dec")));
    }

    @Test
    void homeChartMatchesBudgetMonthsAndTranches() {
        int year = 2026;
        var chart = budgetMatrixService.getHomeChart(year);
        assertEquals(12, chart.getPlannedCost().size());
        assertEquals(12, chart.getIncome().size());
        for (int month = 1; month <= 12; month++) {
            BigDecimal expected = budgetMatrixService.getBudgetDashboardDataForMonth(year, month).getTotalCost();
            assertEquals(0, expected.compareTo(chart.getPlannedCost().get(month - 1)),
                    "month " + month);
        }
        BigDecimal[] income = new BigDecimal[12];
        java.util.Arrays.fill(income, BigDecimal.ZERO);
        var seen = new java.util.LinkedHashSet<Long>();
        for (Grant grant : grantRepository.findAllWithTranches()) {
            if (grant.getId() == null || !seen.add(grant.getId())) {
                continue;
            }
            boolean hasTranches = grant.getTranches() != null && grant.getTranches().stream()
                    .anyMatch(tranche -> tranche.getPlannedAmount() != null && tranche.getPlannedAmount().signum() > 0);
            if (hasTranches) {
                for (var tranche : grant.getTranches()) {
                    var date = tranche.getPlannedDate() != null ? tranche.getPlannedDate() : tranche.getReceivedDate();
                    if (date == null || date.getYear() != year || tranche.getPlannedAmount() == null) {
                        continue;
                    }
                    income[date.getMonthValue() - 1] = income[date.getMonthValue() - 1].add(tranche.getPlannedAmount());
                }
                continue;
            }
            var months = pl.ngo.budget.util.GrantPeriodCoverage.activeMonths(grant.getStartDate(), grant.getEndDate());
            if (months.isEmpty() || grant.getTotalAmount() == null || grant.getTotalAmount().signum() <= 0) {
                continue;
            }
            var shares = pl.ngo.budget.util.MonthlySplit.sharesAcross(grant.getTotalAmount(), months.size());
            for (int i = 0; i < months.size(); i++) {
                if (months.get(i).getYear() == year) {
                    income[months.get(i).getMonthValue() - 1] = income[months.get(i).getMonthValue() - 1].add(shares.get(i));
                }
            }
        }
        for (int month = 0; month < 12; month++) {
            assertEquals(0, income[month].compareTo(chart.getIncome().get(month)), "income month " + (month + 1));
        }
    }

    @Test
    void realizationPageRenders() throws Exception {
        mockMvc.perform(get("/realizacja"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Realizacja budżetu")))
                .andExpect(content().string(containsString("Wynagrodzenia")))
                .andExpect(content().string(containsString("href=\"/realizacja/month")))
                .andExpect(content().string(containsString("href=\"/realizacja/wydatki")))
                .andExpect(content().string(containsString("href=\"/realizacja/grants")))
                .andExpect(content().string(containsString("href=\"/realizacja/sponsors")))
                .andExpect(content().string(containsString("href=\"/realizacja/employees")))
                .andExpect(content().string(containsString("href=\"/realizacja/projects")))
                .andExpect(content().string(containsString("href=\"/realizacja/budget/new")))
                .andExpect(content().string(not(containsString("href=\"/admin/grants"))))
                .andExpect(content().string(not(containsString("Planowany Koszt"))));
    }

    @Test
    void sharedPagesStayInsideTheCurrentSection() throws Exception {
        mockMvc.perform(get("/realizacja/grants"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/realizacja/grants")))
                .andExpect(content().string(containsString("href=\"/realizacja?")))
                .andExpect(content().string(not(containsString("href=\"/admin/grants"))))
                .andExpect(content().string(not(containsString("href=\"/dashboard"))));
        mockMvc.perform(get("/admin/grants"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/admin/grants")))
                .andExpect(content().string(containsString("href=\"/dashboard?")))
                .andExpect(content().string(not(containsString("href=\"/realizacja/grants"))));
        String plain = mockMvc.perform(get("/realizacja/wydatki").param("year", "2026").param("month", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(plain.contains(">Edycja</a>"));
        org.junit.jupiter.api.Assertions.assertFalse(plain.contains("name=\"grantId\""));
        org.junit.jupiter.api.Assertions.assertFalse(plain.contains(">Split</a>"));
        String editing = mockMvc.perform(get("/realizacja/wydatki").param("year", "2026").param("month", "1").param("edit", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(editing.contains(">Zakończ</a>"));
        if (!editing.contains("Brak wydatków")) {
            org.junit.jupiter.api.Assertions.assertTrue(editing.contains(">Split</a>"));
            org.junit.jupiter.api.Assertions.assertTrue(editing.contains("name=\"grantId\""));
            org.junit.jupiter.api.Assertions.assertTrue(editing.contains("name=\"projectId\""));
            java.util.regex.Matcher splitLink = java.util.regex.Pattern.compile("split=(\\d+)").matcher(editing);
            org.junit.jupiter.api.Assertions.assertTrue(splitLink.find());
            mockMvc.perform(get("/realizacja/wydatki")
                            .param("year", "2026")
                            .param("month", "1")
                            .param("edit", "true")
                            .param("split", splitLink.group(1)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Zapisz podział")))
                    .andExpect(content().string(containsString("name=\"parts\"")))
                    .andExpect(content().string(containsString(">2</button>")))
                    .andExpect(content().string(containsString(">3</button>")));
        }
    }

    @Test
    void dashboardRendersWithoutError() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wynagrodzenia")))
                .andExpect(content().string(containsString("Planowany Koszt")))
                .andExpect(content().string(containsString("href=\"/admin/grants")))
                .andExpect(content().string(not(containsString("href=\"/realizacja/grants"))))
                .andExpect(content().string(not(containsString("href=\"/realizacja/wydatki\""))))
                .andExpect(content().string(not(containsString("href=\"/admin/expenditures\""))))
                .andExpect(content().string(containsString("</html>")));
    }

    @Test
    void monthDashboardRendersWithoutError() throws Exception {
        mockMvc.perform(get("/dashboard/month").param("year", "2026").param("month", "3"))
                .andExpect(status().isOk());
    }

    @Test
    void structurePagesRenderPlannedCostControls() throws Exception {
        mockMvc.perform(get("/dashboard/structure").param("year", "2026").param("rowKey", "wynagrodzenia"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("structure-period-from")))
                .andExpect(content().string(containsString("structure-split12")))
                .andExpect(content().string(containsString("</html>")));
        mockMvc.perform(get("/dashboard/structure").param("year", "2026").param("rowKey", "koszty-administracyjne"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("structure-split12")))
                .andExpect(content().string(containsString("structure-line-delete")))
                .andExpect(content().string(containsString("</html>")));
    }

    @Test
    void dashboardRowRenders() throws Exception {
        mockMvc.perform(get("/dashboard/row").param("year", "2026").param("rowKey", "wynagrodzenia"))
                .andExpect(status().isOk());
    }

    @Test
    void promotionRowRenders() throws Exception {
        mockMvc.perform(get("/dashboard/row").param("year", "2026").param("rowKey", "promocja"))
                .andExpect(status().isOk());
    }
}
