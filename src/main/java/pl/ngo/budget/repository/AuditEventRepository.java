package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.ngo.budget.model.AuditEvent;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop500ByOrderByCreatedAtDesc();

    @Query("""
            select e from AuditEvent e
            where e.username is not null and e.ip is not null
              and e.id = (
                  select max(later.id) from AuditEvent later
                  where lower(later.username) = lower(e.username)
                    and later.ip is not null
              )
            """)
    List<AuditEvent> findLatestIpPerUsername();

    @Modifying
    @Query("delete from AuditEvent e where e.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
