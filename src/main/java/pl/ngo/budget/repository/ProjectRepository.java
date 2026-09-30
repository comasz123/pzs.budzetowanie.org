package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.Project;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByActiveTrue();
    List<Project> findByGrantCoordinatorIdAndActiveTrue(Long coordinatorId);

    java.util.Optional<Project> findByCode(String code);

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.grantCoordinator ORDER BY p.name")
    List<Project> findAllWithCoordinator();
}
