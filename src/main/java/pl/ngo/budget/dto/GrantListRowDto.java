package pl.ngo.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pl.ngo.budget.entity.coverage.Grant;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class GrantListRowDto {

    private final Grant grant;
    private final BigDecimal openingBalance;
    private final BigDecimal spentInYear;
    /** Część kwoty grantu przypadająca na oglądany rok według miesięcy aktywności. */
    private final BigDecimal allocatedInYear;
    private final BigDecimal remaining;
}
