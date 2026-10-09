package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.TravelBudgetLine;

import java.util.List;

@Repository
public interface TravelBudgetLineRepository extends JpaRepository<TravelBudgetLine, Long> {

    /** Planowany koszt budżetu z obecnej kwoty (jednorazowo, dla pozycji sprzed tej kolumny). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE TravelBudgetLine p SET p.budgetPlannedCost = p.plannedCost WHERE p.budgetPlannedCost IS NULL")
    int freezeBudgetPlannedCosts();
    List<TravelBudgetLine> findByActiveTrueOrderByScopeAscExpenseTypeAsc();

    @Modifying
    @Query("DELETE FROM TravelBudgetLine t WHERE t.grant.id IN :grantIds")
    void deleteByGrantIdIn(List<Long> grantIds);
}
