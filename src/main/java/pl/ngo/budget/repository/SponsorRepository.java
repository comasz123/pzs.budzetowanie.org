package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.Sponsor;

@Repository
public interface SponsorRepository extends JpaRepository<Sponsor, Long> {
}
