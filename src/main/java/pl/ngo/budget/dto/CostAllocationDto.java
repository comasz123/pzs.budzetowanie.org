package pl.ngo.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CostAllocationDto {

    private String itemName;
    private String category;
    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal percent = BigDecimal.ZERO;
    private BigDecimal balance = BigDecimal.ZERO;

    /** Bilans do wyświetlania: pokrycie minus wydatek (odwrotność {@code balance} = wydatek minus pokrycie). */
    public BigDecimal getBilans() {
        return balance == null ? null : balance.negate();
    }
    private Map<String, BigDecimal> amountByGrant = new LinkedHashMap<>();
    private String amountEditKind;
    private String amountEditIds;
    /** Klucz naturalny linii wydatku (do przenoszenia między kategoriami). */
    private String lineKey;
}
