package pl.ngo.budget;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.dto.BudgetDashboardDto;
import pl.ngo.budget.dto.CostAllocationDto;
import pl.ngo.budget.entity.cost.CostAllocation;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.GrantBudgetItemCoverage;
import pl.ngo.budget.entity.coverage.OrgBudgetSourceType;
import pl.ngo.budget.entity.coverage.Project;
import pl.ngo.budget.entity.coverage.Sponsor;
import pl.ngo.budget.repository.BudgetItemTemplateRepository;
import pl.ngo.budget.service.BudgetMatrixService;
import pl.ngo.budget.service.BudgetStructureService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Koszt pokryty grantem: pokrycie trafia do miesięcy, na które rozpisano koszt, a bilans roku to pokrycie
 * minus planowany koszt.
 */
@SpringBootTest
@Transactional
class CoverageFollowsCostMonthsTest {

    private static final int YEAR = 2031;
    private static final String GRANT = "Test PKO pokrycie";
    private static final String LINE = "Microsoft test pokrycia";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BudgetItemTemplateRepository templateRepository;

    @Autowired
    private BudgetMatrixService budgetMatrixService;

    @Autowired
    private BudgetStructureService budgetStructureService;

    @Test
    void grantCoverageFollowsCostMonthsAndBalancesPlannedCost() {
        BudgetItemTemplate admin = templateRepository.findByCode("KOSZT_ADM").orElseThrow();

        CostAllocation annual = line(admin, null, new BigDecimal("2400.00"));
        annual.setPlannedAmount(new BigDecimal("3200.00"));
        entityManager.persist(annual);
        for (int month = 1; month <= 12; month++) {
            entityManager.persist(line(admin, month, month <= 6 ? new BigDecimal("400.00") : BigDecimal.ZERO));
        }

        Sponsor sponsor = new Sponsor();
        sponsor.setName("Sponsor testu pokrycia");
        entityManager.persist(sponsor);
        Project project = new Project();
        project.setName("Projekt testu pokrycia");
        entityManager.persist(project);
        Grant grant = new Grant();
        grant.setCode("TEST-POKRYCIE-" + YEAR);
        grant.setName(GRANT);
        grant.setSponsor(sponsor);
        grant.setProject(project);
        grant.setStartDate(LocalDate.of(YEAR, 1, 1));
        grant.setEndDate(LocalDate.of(YEAR, 12, 31));
        entityManager.persist(grant);
        GrantBudgetItem item = new GrantBudgetItem();
        item.setGrant(grant);
        item.setName("Oprogramowanie");
        item.setCode("GRANT_TEST_POKRYCIE");
        grant.getBudgetItems().add(item);
        entityManager.persist(item);
        GrantBudgetItemCoverage coverage = new GrantBudgetItemCoverage();
        coverage.setGrantBudgetItem(item);
        coverage.setOrgSourceType(OrgBudgetSourceType.COST_ALLOCATION);
        coverage.setOrgSourceId(annual.getId());
        coverage.setOrgLabel(LINE);
        coverage.setCoveredAmount(new BigDecimal("3200.00"));
        item.getCoverages().add(coverage);
        entityManager.persist(coverage);
        entityManager.flush();

        List<BudgetDashboardDto> months = budgetMatrixService.getBudgetDashboardDataForMonths(YEAR);
        BigDecimal coveredInMonths = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            CostAllocationDto monthLine = findLine(months.get(month - 1).getRows());
            assertNotNull(monthLine, "linia w miesiącu " + month);
            BigDecimal covered = monthLine.getAmountByGrant().getOrDefault(GRANT, BigDecimal.ZERO);
            if (month > 6) {
                assertEquals(0, covered.signum(), "miesiąc bez kosztu nie dostaje pokrycia: " + month);
            } else {
                // 3200 × 400 / 2400 = 533,33; ostatni miesiąc z kosztem bierze resztę z zaokrągleń.
                assertTrue(covered.subtract(new BigDecimal("533.33")).abs().compareTo(new BigDecimal("0.03")) <= 0,
                        "pokrycie w miesiącu " + month + ": " + covered);
            }
            coveredInMonths = coveredInMonths.add(covered);
        }
        assertEquals(0, new BigDecimal("3200.00").compareTo(coveredInMonths), "suma pokrycia w miesiącach");

        BudgetDashboardDto year = budgetMatrixService.getBudgetDashboardDataForYear(YEAR);
        budgetStructureService.applyPlannedCosts(year);
        BudgetDashboardDto.BudgetDisplayRowDto display = year.getDisplayRows().stream()
                .filter(row -> LINE.equals(row.getItemName()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("3200.00").compareTo(display.getPlannedCost()), "planowany koszt");
        assertEquals(0, new BigDecimal("800.00").compareTo(display.getMonthsGap()), "pokrycie w miesiącach");
        assertEquals(0, new BigDecimal("3200.00").compareTo(display.getCoverageByGrant().get(GRANT)), "pokrycie roczne");
        assertEquals(0, display.getBilans().signum(), "bilans: pokrycie minus planowany koszt");

        // Edycja miesiąca: przy kwocie miesiąca widać część planowanego kosztu jeszcze nierozpisaną.
        BudgetDashboardDto march = budgetMatrixService.getBudgetDashboardDataForMonth(YEAR, 3);
        budgetStructureService.applyUnsplitAmounts(march, YEAR);
        BudgetDashboardDto.BudgetDisplayRowDto marchLine = march.getDisplayRows().stream()
                .filter(row -> LINE.equals(row.getItemName()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("800.00").compareTo(marchLine.getUnsplitAmount()), "nierozpisane w edycji miesiąca");
    }

    private CostAllocation line(BudgetItemTemplate category, Integer month, BigDecimal amount) {
        CostAllocation allocation = new CostAllocation();
        allocation.setCategory(category);
        allocation.setAdminGroup("BIURO");
        allocation.setLabel(LINE);
        allocation.setAmount(amount);
        allocation.setPercentage(BigDecimal.valueOf(100));
        allocation.setFiscalYear(YEAR);
        allocation.setPlanMonth(month);
        allocation.setSplitToMonths(true);
        allocation.setActive(true);
        return allocation;
    }

    private static CostAllocationDto findLine(List<BudgetDashboardDto.BudgetItemRowDto> rows) {
        if (rows == null) {
            return null;
        }
        for (BudgetDashboardDto.BudgetItemRowDto row : rows) {
            if (row.getAllocations() != null) {
                for (CostAllocationDto allocation : row.getAllocations()) {
                    if (Objects.equals(LINE, allocation.getItemName())) {
                        return allocation;
                    }
                }
            }
            CostAllocationDto nested = findLine(row.getChildren());
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
