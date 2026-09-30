package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.SponsorContact;

import java.util.List;

@Repository
public interface SponsorContactRepository extends JpaRepository<SponsorContact, Long> {
    List<SponsorContact> findBySponsorId(Long sponsorId);

    @Query("SELECT c FROM SponsorContact c JOIN FETCH c.sponsor ORDER BY c.sponsor.name, c.lastName, c.firstName")
    List<SponsorContact> findAllWithSponsor();
}
