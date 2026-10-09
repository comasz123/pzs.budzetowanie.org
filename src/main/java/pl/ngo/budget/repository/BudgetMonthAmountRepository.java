package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.BudgetMonthAmount;

import java.util.List;

@Repository
public interface BudgetMonthAmountRepository extends JpaRepository<BudgetMonthAmount, Long> {

    List<BudgetMonthAmount> findByFiscalYear(Integer fiscalYear);

    List<BudgetMonthAmount> findByFiscalYearAndKindAndRefKeyOrderByPlanMonthAsc(Integer fiscalYear, String kind, String refKey);

    @Modifying(flushAutomatically = true)
    void deleteByFiscalYearAndKindAndRefKey(Integer fiscalYear, String kind, String refKey);
}
