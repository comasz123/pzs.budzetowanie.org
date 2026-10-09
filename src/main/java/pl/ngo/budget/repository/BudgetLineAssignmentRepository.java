package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.BudgetLineAssignment;

import java.util.Optional;

@Repository
public interface BudgetLineAssignmentRepository extends JpaRepository<BudgetLineAssignment, Long> {

    Optional<BudgetLineAssignment> findByLineKey(String lineKey);
}
