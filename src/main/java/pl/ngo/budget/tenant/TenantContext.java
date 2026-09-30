package pl.ngo.budget.tenant;

import pl.ngo.budget.model.Organization;

import java.util.Optional;

public final class TenantContext {

    private static final ThreadLocal<Organization> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setOrganization(Organization organization) {
        CURRENT.set(organization);
    }

    public static Optional<Organization> getOrganization() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
