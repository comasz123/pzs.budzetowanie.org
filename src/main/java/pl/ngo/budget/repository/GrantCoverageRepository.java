package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.GrantCoverage;

import java.util.List;

@Repository
public interface GrantCoverageRepository extends JpaRepository<GrantCoverage, Long> {
    List<GrantCoverage> findByBudgetItemId(Long budgetItemId);
    List<GrantCoverage> findByGrantId(Long grantId);

    @Modifying
    @Query("DELETE FROM GrantCoverage c WHERE c.grant.id IN :grantIds")
    void deleteByGrantIdIn(List<Long> grantIds);
}
