package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.BudgetSubcategoryOrder;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetSubcategoryOrderRepository extends JpaRepository<BudgetSubcategoryOrder, Long> {

    List<BudgetSubcategoryOrder> findByParentRowKeyOrderByDisplayOrderAsc(String parentRowKey);

    Optional<BudgetSubcategoryOrder> findByParentRowKeyAndRowKey(String parentRowKey, String rowKey);

    void deleteByParentRowKey(String parentRowKey);
}
