package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.cost.ExpenditureAllocation;

import java.util.List;

@Repository
public interface ExpenditureAllocationRepository extends JpaRepository<ExpenditureAllocation, Long> {
    List<ExpenditureAllocation> findByExpenditureId(Long expenditureId);
    List<ExpenditureAllocation> findByGrantId(Long grantId);
    List<ExpenditureAllocation> findByBudgetItemId(Long budgetItemId);

    @Modifying
    @Query("DELETE FROM ExpenditureAllocation a WHERE a.grant.id IN :grantIds")
    void deleteByGrantIdIn(List<Long> grantIds);
}
