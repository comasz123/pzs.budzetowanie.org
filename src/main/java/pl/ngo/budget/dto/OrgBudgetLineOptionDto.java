package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OrgBudgetLineOptionDto {

    private String orgSourceType;
    private Long orgSourceId;
    private String categoryCode;
    private String label;
    private String sourceRef;
    private String orgSourceKey;
    /** Bilans tej pozycji w budżecie rocznym organizacji. */
    private BigDecimal balance;

    public static OrgBudgetLineOptionDto of(String orgSourceType, Long orgSourceId,
                                            String categoryCode, String label) {
        OrgBudgetLineOptionDto dto = new OrgBudgetLineOptionDto();
        dto.setOrgSourceType(orgSourceType);
        dto.setOrgSourceId(orgSourceId);
        dto.setCategoryCode(categoryCode);
        dto.setLabel(label);
        dto.setSourceRef(orgSourceType + "|" + orgSourceId);
        return dto;
    }

    public static OrgBudgetLineOptionDto ofKey(String orgSourceType, String sourceKey,
                                               String categoryCode, String label) {
        OrgBudgetLineOptionDto dto = new OrgBudgetLineOptionDto();
        dto.setOrgSourceType(orgSourceType);
        dto.setOrgSourceKey(sourceKey);
        dto.setCategoryCode(categoryCode);
        dto.setLabel(label);
        dto.setSourceRef(orgSourceType + "|" + sourceKey);
        return dto;
    }
}
