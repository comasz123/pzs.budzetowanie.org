package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.cost.BudgetItem;

import java.util.List;

@Repository
public interface BudgetItemRepository extends JpaRepository<BudgetItem, Long> {
    // Pobiera tylko główne kategorie (korzenie drzewa budżetowego)
    List<BudgetItem> findByParentIsNull();

    // Pobiera podpozycje dla danej kategorii nadrzędnej
    List<BudgetItem> findByParentId(Long parentId);
}