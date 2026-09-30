package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.Grant;

import java.util.List;

@Repository
public interface GrantRepository extends JpaRepository<Grant, Long> {
    List<Grant> findByActiveTrue();
    List<Grant> findByGrantCoordinatorIdAndActiveTrue(Long coordinatorId);
    List<Grant> findByProjectId(Long projectId);

    @Query("SELECT DISTINCT g FROM Grant g LEFT JOIN FETCH g.budgetItems WHERE g.active = true")
    List<Grant> findActiveWithBudgetItems();

    @Query("SELECT DISTINCT g FROM Grant g LEFT JOIN FETCH g.sponsor LEFT JOIN FETCH g.project ORDER BY g.name")
    List<Grant> findAllWithDetails();

    @Query("SELECT g FROM Grant g LEFT JOIN FETCH g.sponsor LEFT JOIN FETCH g.project WHERE g.id = :id")
    java.util.Optional<Grant> findByIdWithDetails(Long id);

    @Query("""
            SELECT DISTINCT g FROM Grant g
            LEFT JOIN FETCH g.sponsor
            LEFT JOIN FETCH g.project
            LEFT JOIN FETCH g.budgetItems
            WHERE g.id = :id
            """)
    java.util.Optional<Grant> findByIdWithBudgetItems(Long id);

    @Query("SELECT DISTINCT g FROM Grant g LEFT JOIN FETCH g.budgetItems")
    List<Grant> findAllWithBudgetItems();

    @Query("SELECT DISTINCT g FROM Grant g LEFT JOIN FETCH g.tranches")
    List<Grant> findAllWithTranches();

    java.util.Optional<Grant> findByCode(String code);

    @Query(value = """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 'personnel_cost_allocations'
            """, nativeQuery = true)
    long countLegacyPersonnelAllocationsTable();

    @Modifying
    @Query(value = "DELETE FROM personnel_cost_allocations WHERE grant_id IN :grantIds", nativeQuery = true)
    void deleteLegacyPersonnelAllocationsByGrantIdIn(List<Long> grantIds);
}
