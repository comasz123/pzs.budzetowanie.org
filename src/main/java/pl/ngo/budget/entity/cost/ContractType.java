package pl.ngo.budget.entity.cost;

public enum ContractType {
    UMOWA_O_PRACE("Umowy o pracę"),
    UMOWA_ZLECENIE("Umowy zlecenia"),
    B2B("B2B");

    private final String label;

    ContractType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
