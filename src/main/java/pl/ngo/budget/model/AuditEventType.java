package pl.ngo.budget.model;

public enum AuditEventType {
    LOGIN_PAGE("Otworzył logowanie"),
    LOGIN_SUCCESS("Logowanie udane"),
    LOGIN_FAILURE("Logowanie nieudane"),
    CHANGE("Zmiana");

    private final String label;

    AuditEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
