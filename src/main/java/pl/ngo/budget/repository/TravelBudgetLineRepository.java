package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.TravelBudgetLine;

import java.util.List;

@Repository
public interface TravelBudgetLineRepository extends JpaRepository<TravelBudgetLine, Long> {
    List<TravelBudgetLine> findByActiveTrueOrderByScopeAscExpenseTypeAsc();

    @Modifying
    @Query("DELETE FROM TravelBudgetLine t WHERE t.grant.id IN :grantIds")
    void deleteByGrantIdIn(List<Long> grantIds);
}
