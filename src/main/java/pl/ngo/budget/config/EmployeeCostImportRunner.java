package pl.ngo.budget.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.ngo.budget.service.EmployeeCostImportService;
import pl.ngo.budget.service.FundingImportService;
import pl.ngo.budget.service.GrantBudgetService;
import pl.ngo.budget.service.OtherCostImportService;
import pl.ngo.budget.service.OtherCostWorkbookParser;
import pl.ngo.budget.service.EmployeeCostWorkbookParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Jednorazowy import pracowników i ich miesięcznych kosztów z arkusza „Koszty”. Uruchamia się po starcie
 * aplikacji (po DataInitializer), tylko gdy ustawiono {@code app.import.employee-costs.file}.
 * Domyślnie zapisuje i zastępuje wcześniej zaimportowane wynagrodzenia tych pracowników (można powtarzać);
 * {@code app.import.employee-costs.apply=false} daje sam podgląd.
 */
@Component
@ConditionalOnProperty(name = "app.import.employee-costs.file")
public class EmployeeCostImportRunner {

    private static final Logger log = LoggerFactory.getLogger(EmployeeCostImportRunner.class);

    private final EmployeeCostImportService service;
    private final FundingImportService fundingService;
    private final GrantBudgetService grantBudgetService;
    private final OtherCostImportService otherCostService;

    @Value("${app.import.employee-costs.file}")
    private String file;

    @Value("${app.import.employee-costs.year:2026}")
    private int year;

    @Value("${app.import.employee-costs.apply:true}")
    private boolean apply;

    @Value("${app.import.employee-costs.replace:true}")
    private boolean replace;

    /** Czy po wynagrodzeniach utworzyć też projekty/granty z pokryciem miesięcznym. */
    @Value("${app.import.employee-costs.projects:true}")
    private boolean projects;

    /** Czy załadować też pozostałe koszty (administracyjne) wraz z pokryciem w projektach. */
    @Value("${app.import.employee-costs.other-costs:true}")
    private boolean otherCosts;

    public EmployeeCostImportRunner(EmployeeCostImportService service,
                                    FundingImportService fundingService,
                                    GrantBudgetService grantBudgetService,
                                    OtherCostImportService otherCostService) {
        this.otherCostService = otherCostService;
        this.service = service;
        this.fundingService = fundingService;
        this.grantBudgetService = grantBudgetService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void run() {
        try {
            doRun();
        } catch (Exception e) {
            // Błąd importu nie powinien przewracać aplikacji; transakcje wycofują niedokończone zapisy.
            log.error("Import nie powiódł się: {}", e.getMessage(), e);
        }
    }

    private void doRun() throws IOException {
        EmployeeCostWorkbookParser.ParseResult parsed;
        try (InputStream in = Files.newInputStream(Path.of(file))) {
            parsed = EmployeeCostWorkbookParser.parse(in, Map.of());
        }
        log.info("Import kosztów pracowników z {} (rok {}, apply={}, replace={})", file, year, apply, replace);
        service.load(parsed, year, apply, replace).forEach(line -> log.info("{}", line));
        if (projects) {
            log.info("Import projektów i pokrycia wynagrodzeń (apply={})", apply);
            fundingService.load(parsed, year, apply).forEach(line -> log.info("{}", line));
            if (apply) {
                grantBudgetService.syncAllEmployeeCoverageMonths();
            }
        }
        if (otherCosts) {
            OtherCostWorkbookParser.ParseResult other;
            try (InputStream in = Files.newInputStream(Path.of(file))) {
                other = OtherCostWorkbookParser.parse(in);
            }
            log.info("Import pozostałych kosztów (apply={}, replace={})", apply, replace);
            otherCostService.load(other, year, apply, replace).forEach(line -> log.info("{}", line));
        }
    }
}
