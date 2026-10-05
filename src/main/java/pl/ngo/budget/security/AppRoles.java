package pl.ngo.budget.security;

import pl.ngo.budget.model.Role;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class AppRoles {

    public static final String ADMIN = "ROLE_ADMIN";
    public static final String EDIT = "ROLE_EDIT";
    public static final String READ_ONLY = "ROLE_READ_ONLY";

    public static final List<String> ASSIGNABLE = List.of(ADMIN, EDIT, READ_ONLY);

    private static final Set<String> LEGACY = Set.of("ROLE_USER", "ROLE_FINANCE");

    private AppRoles() {
    }

    public static boolean isLegacy(String roleName) {
        return LEGACY.contains(roleName);
    }

    public static String label(String roleName) {
        if (roleName == null) {
            return "";
        }
        return switch (roleName) {
            case ADMIN -> "admin";
            case EDIT -> "edit";
            case READ_ONLY -> "read-only";
            default -> roleName;
        };
    }

    public static String fromForm(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "admin", ADMIN -> ADMIN;
            case "edit", EDIT -> EDIT;
            case "read-only", READ_ONLY -> READ_ONLY;
            default -> null;
        };
    }

    public static String primary(Collection<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        boolean admin = false;
        boolean edit = false;
        boolean readOnly = false;
        String other = null;
        for (Role role : roles) {
            String name = role.getName();
            if (ADMIN.equals(name)) {
                admin = true;
            } else if (EDIT.equals(name)) {
                edit = true;
            } else if (READ_ONLY.equals(name)) {
                readOnly = true;
            } else if (other == null) {
                other = name;
            }
        }
        if (admin) {
            return ADMIN;
        }
        if (edit) {
            return EDIT;
        }
        if (readOnly) {
            return READ_ONLY;
        }
        return other == null ? "" : other;
    }
}
