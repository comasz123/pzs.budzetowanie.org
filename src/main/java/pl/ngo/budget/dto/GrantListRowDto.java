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
    /** Plan pokrycia (kwota rozdysponowana na budżet organizacji) w oglądanym roku. */
    private final BigDecimal allocatedInYear;
    /** To samo w roku następnym. */
    private final BigDecimal allocatedNextYear;
    /** Kwota grantu minus alokacja w oglądanym i następnym roku. */
    private final BigDecimal remaining;
}
