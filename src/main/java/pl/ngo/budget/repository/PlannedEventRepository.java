package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.coverage.PlannedEvent;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PlannedEventRepository extends JpaRepository<PlannedEvent, Long> {
    List<PlannedEvent> findByActiveTrueOrderByEventDateAscTitleAsc();

    @Modifying
    @Query("DELETE FROM PlannedEvent e WHERE e.grant.id IN :grantIds")
    void deleteByGrantIdIn(List<Long> grantIds);

    @Modifying
    @Query("DELETE FROM PlannedEvent e WHERE e.eventDate >= :fromDate AND e.eventDate <= :toDate")
    void deleteByEventDateBetween(LocalDate fromDate, LocalDate toDate);
}
