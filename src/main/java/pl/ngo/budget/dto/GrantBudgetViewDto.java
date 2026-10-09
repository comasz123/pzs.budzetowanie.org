package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GrantBudgetViewDto {

    private Long grantId;
    private String grantCode;
    private String grantName;
    private String sponsorName;
    private String projectName;
    private BigDecimal totalAmount;
    /** Suma kolumny Plan grantu. */
    private BigDecimal planTotal = BigDecimal.ZERO;
    /** Kwota grantu minus suma planów pozycji. */
    private BigDecimal unallocated = BigDecimal.ZERO;
    /** Suma kolumny Plan pokrycia albo Plan pokrycia 2026. */
    private BigDecimal coveragePlanTotal = BigDecimal.ZERO;
    /** Suma kolumny Plan pokrycia 2027. Zero, gdy grant nie kończy się w 2027. */
    private BigDecimal coveragePlanTotal2027 = BigDecimal.ZERO;
    /** Grant kończy się w 2027: osobne kolumny planu pokrycia na 2026 i 2027. */
    private boolean coverageSplitByYear;
    /** Suma kolumny Zostało. */
    private BigDecimal leftTotal = BigDecimal.ZERO;
    /** Kwota grantu minus suma planu pokrycia. */
    private BigDecimal coverageUnallocated = BigDecimal.ZERO;
    private LocalDate startDate;
    private LocalDate endDate;
    /** Kwota grantu / liczba miesięcy, w których grant jest aktywny. */
    private BigDecimal amountPerActiveMonth = BigDecimal.ZERO;
    private List<OrgBudgetLineOptionDto> orgBudgetLineOptions = new ArrayList<>();
    private List<CategoryOptionDto> categoryOptions = new ArrayList<>();
    private List<GrantBudgetLineDto> lines = new ArrayList<>();
    private List<TrancheDto> tranches = new ArrayList<>();

    @Getter
    @Setter
    public static class CategoryOptionDto {
        private String code;
        private String name;
    }

    @Getter
    @Setter
    public static class GrantBudgetLineDto {
        private Long budgetItemId;
        /** Poziom zagnieżdżenia: 0 = pozycja główna. */
        private int depth;
        private Long parentId;
        /** Pozycja ma podpozycje: kwoty są sumą podpozycji. */
        private boolean hasChildren;
        /** Kategoria grupująca pozycje (zaznaczona jako kategoria albo mająca podpozycje). */
        private boolean category;
        private String name;
        private String code;
        private BigDecimal plannedAmount;
        private List<CoveredOrgBudgetLineDto> coveredOrgLines = new ArrayList<>();
        private BigDecimal actualSpent = BigDecimal.ZERO;
        private BigDecimal remaining = BigDecimal.ZERO;
        /** Plan grantu minus faktycznie wydane. */
        private BigDecimal unspent = BigDecimal.ZERO;
        /** {@link #unspent} as a percent of plan grantu. Null when the plan is zero. */
        private BigDecimal unspentPercent;
    }

    @Getter
    @Setter
    public static class TrancheDto {
        private Long id;
        private LocalDate plannedDate;
        private BigDecimal plannedAmount;
        private boolean received;
        private LocalDate receivedDate;
        private BigDecimal receivedAmount;
    }

    @Getter
    @Setter
    public static class CoveredOrgBudgetLineDto {
        private Long coverageId;
        private String orgSourceType;
        private Long orgSourceId;
        private String orgSourceKey;
        private String label;
        private String sourceRef;
        private BigDecimal amount;
        /** Plan pokrycia 2027. Wypełniany, gdy grant kończy się w 2027. */
        private BigDecimal amount2027;
    }
}
