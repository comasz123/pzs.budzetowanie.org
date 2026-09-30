package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetItemTemplateRepository extends JpaRepository<BudgetItemTemplate, Long> {
    Optional<BudgetItemTemplate> findByCode(String code);

    Optional<BudgetItemTemplate> findByName(String name);

    List<BudgetItemTemplate> findAllByOrderByDisplayOrderAscNameAsc();
}
