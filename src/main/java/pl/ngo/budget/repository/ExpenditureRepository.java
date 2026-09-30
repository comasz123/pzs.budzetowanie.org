package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.cost.Expenditure;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenditureRepository extends JpaRepository<Expenditure, Long> {

    long countByFiscalYear(Integer fiscalYear);

    @Query("SELECT DISTINCT e.fiscalYear FROM Expenditure e WHERE e.fiscalYear IS NOT NULL ORDER BY e.fiscalYear DESC")
    List<Integer> findDistinctFiscalYears();

    @Query("""
            SELECT DISTINCT e FROM Expenditure e
            JOIN FETCH e.grant
            JOIN FETCH e.budgetItem
            LEFT JOIN FETCH e.grantShares shares
            LEFT JOIN FETCH shares.grant
            WHERE e.fiscalYear = :fiscalYear
            """)
    List<Expenditure> findAllWithGrantAndItemByFiscalYear(Integer fiscalYear);

    @Query("SELECT e FROM Expenditure e JOIN FETCH e.grant JOIN FETCH e.budgetItem")
    List<Expenditure> findAllWithGrantAndItem();

    @Query("""
            SELECT e FROM Expenditure e
            JOIN FETCH e.grant
            JOIN FETCH e.budgetItem
            WHERE e.grant.id = :grantId AND e.fiscalYear = :fiscalYear
            """)
    List<Expenditure> findByGrantIdAndFiscalYear(Long grantId, Integer fiscalYear);

    @Query("""
            SELECT COUNT(e) FROM Expenditure e
            WHERE e.fiscalYear = :fiscalYear AND e.issueDate > :maxDate
            """)
    long countByFiscalYearAndIssueDateAfter(Integer fiscalYear, LocalDate maxDate);

    @Modifying
    @Query("DELETE FROM Expenditure e WHERE e.fiscalYear = :fiscalYear")
    void deleteByFiscalYear(Integer fiscalYear);

    @Modifying
    @Query("DELETE FROM Expenditure e WHERE e.grant.id IN :grantIds")
    void deleteByGrantIdIn(List<Long> grantIds);

    @Modifying
    @Query("""
            DELETE FROM Expenditure e
            WHERE e.fiscalYear = :fiscalYear
              AND e.issueDate >= :fromDate
              AND e.issueDate <= :toDate
            """)
    void deleteByFiscalYearAndIssueDateBetween(Integer fiscalYear, LocalDate fromDate, LocalDate toDate);

    @Query("""
            SELECT COUNT(e) FROM Expenditure e
            WHERE e.fiscalYear = :fiscalYear
              AND e.issueDate >= :fromDate
              AND e.issueDate <= :toDate
            """)
    long countByFiscalYearAndIssueDateBetween(Integer fiscalYear, LocalDate fromDate, LocalDate toDate);

    @Query("""
            SELECT DISTINCT e FROM Expenditure e
            JOIN FETCH e.grant g
            LEFT JOIN FETCH g.project
            JOIN FETCH e.budgetItem
            LEFT JOIN FETCH e.employee
            LEFT JOIN FETCH e.project
            LEFT JOIN FETCH e.grantShares shares
            LEFT JOIN FETCH shares.grant
            WHERE e.fiscalYear = :fiscalYear
              AND e.issueDate >= :fromDate
              AND e.issueDate <= :toDate
            ORDER BY e.issueDate, e.documentNumber
            """)
    List<Expenditure> findByFiscalYearAndIssueDateBetween(Integer fiscalYear, LocalDate fromDate, LocalDate toDate);
}
