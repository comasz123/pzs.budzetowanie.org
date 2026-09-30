package pl.ngo.budget.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.ngo.budget.service.GrantBudgetService;

@Component
@Order(Integer.MAX_VALUE)
public class SalaryCoverageMonthSync implements ApplicationRunner {

    private final GrantBudgetService grantBudgetService;

    public SalaryCoverageMonthSync(GrantBudgetService grantBudgetService) {
        this.grantBudgetService = grantBudgetService;
    }

    @Override
    public void run(ApplicationArguments args) {
        grantBudgetService.syncAllEmployeeCoverageMonths();
    }
}
