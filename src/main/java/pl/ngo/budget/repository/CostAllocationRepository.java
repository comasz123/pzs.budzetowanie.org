package pl.ngo.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.ngo.budget.entity.cost.CostAllocation;

import java.util.List;

@Repository
public interface CostAllocationRepository extends JpaRepository<CostAllocation, Long> {

    long countByFiscalYear(Integer fiscalYear);

    long countByCategory_Id(Long categoryId);

    @Modifying
    @Query("DELETE FROM CostAllocation a WHERE a.fiscalYear = :fiscalYear")
    void deleteByFiscalYear(Integer fiscalYear);

    @Query("""
            SELECT DISTINCT a.fiscalYear FROM CostAllocation a
            WHERE a.fiscalYear IS NOT NULL
            ORDER BY a.fiscalYear DESC
            """)
    List<Integer> findDistinctFiscalYears();

    @Query("""
            SELECT a FROM CostAllocation a
            LEFT JOIN FETCH a.employee
            LEFT JOIN FETCH a.expenditure
            LEFT JOIN FETCH a.project
            LEFT JOIN FETCH a.category
            WHERE a.active = true AND a.fiscalYear = :fiscalYear
            ORDER BY a.id
            """)
    List<CostAllocation> findAllActiveWithDetailsByFiscalYear(Integer fiscalYear);

    @Query("""
            SELECT a FROM CostAllocation a
            LEFT JOIN FETCH a.employee
            LEFT JOIN FETCH a.project
            LEFT JOIN FETCH a.category
            WHERE a.active = true AND a.fiscalYear = :fiscalYear AND a.expenditure IS NULL
              AND a.planMonth IS NULL
            ORDER BY a.id
            """)
    List<CostAllocation> findPlanAllocationsByFiscalYear(Integer fiscalYear);

    @Query("""
            SELECT a FROM CostAllocation a
            LEFT JOIN FETCH a.employee
            LEFT JOIN FETCH a.project
            LEFT JOIN FETCH a.category
            WHERE a.active = true AND a.fiscalYear = :fiscalYear AND a.expenditure IS NULL
              AND a.planMonth IS NULL
            ORDER BY a.id
            """)
    List<CostAllocation> findAnnualPlanAllocationsByFiscalYear(Integer fiscalYear);

    @Query("""
            SELECT a FROM CostAllocation a
            LEFT JOIN FETCH a.employee
            LEFT JOIN FETCH a.project
            LEFT JOIN FETCH a.category
            WHERE a.active = true AND a.fiscalYear = :fiscalYear AND a.expenditure IS NULL
              AND a.planMonth = :planMonth
            ORDER BY a.id
            """)
    List<CostAllocation> findPlanAllocationsByFiscalYearAndPlanMonth(Integer fiscalYear, Integer planMonth);

    @Query("""
            SELECT a FROM CostAllocation a
            LEFT JOIN FETCH a.employee
            LEFT JOIN FETCH a.project
            LEFT JOIN FETCH a.category
            WHERE a.active = true AND a.fiscalYear = :fiscalYear AND a.expenditure IS NULL
            ORDER BY a.id
            """)
    List<CostAllocation> findAllPlanAllocationsByFiscalYear(Integer fiscalYear);

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM CostAllocation a
            WHERE a.active = true AND a.fiscalYear = :fiscalYear AND a.expenditure IS NULL
              AND a.planMonth IS NOT NULL
            """)
    boolean existsMonthlyPlanForFiscalYear(Integer fiscalYear);

    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM CostAllocation a
            WHERE a.expenditure IS NULL AND a.label = :label
            """)
    void deleteCoveragePlanByLabel(String label);

    @Modifying
    @Query("DELETE FROM CostAllocation a WHERE a.expenditure IS NOT NULL AND a.fiscalYear = :fiscalYear")
    void deleteExpenditureAllocationsByFiscalYear(Integer fiscalYear);

    @Modifying
    @Query("DELETE FROM CostAllocation a WHERE a.expenditure IS NULL AND a.fiscalYear = :fiscalYear")
    void deletePlanAllocationsByFiscalYear(Integer fiscalYear);

    @Modifying
    @Query("DELETE FROM CostAllocation a WHERE a.project.id IN :projectIds")
    void deleteByProjectIdIn(List<Long> projectIds);

    @Modifying
    @Query("DELETE FROM CostAllocation a WHERE a.expenditure.grant.id IN :grantIds")
    void deleteExpenditureAllocationsByGrantIdIn(List<Long> grantIds);

    @Modifying
    @Query("""
            DELETE FROM CostAllocation a
            WHERE a.expenditure IS NOT NULL
              AND a.fiscalYear = :fiscalYear
              AND a.expenditure.issueDate >= :fromDate
              AND a.expenditure.issueDate <= :toDate
            """)
    void deleteExpenditureAllocationsByFiscalYearAndIssueDateBetween(
            Integer fiscalYear, java.time.LocalDate fromDate, java.time.LocalDate toDate);
}
