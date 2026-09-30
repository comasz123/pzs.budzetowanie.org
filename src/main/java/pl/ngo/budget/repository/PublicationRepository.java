package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.Publication;

import java.util.List;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {
    List<Publication> findByActiveTrueOrderByTitleAsc();

    @Modifying
    @Query("DELETE FROM Publication p WHERE p.grant.id IN :grantIds")
    void deleteByGrantIdIn(List<Long> grantIds);
}
