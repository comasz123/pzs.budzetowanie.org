package pl.ngo.budget.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccess {

    private final boolean requireAuth;

    public CurrentAccess(@Value("${app.security.require-auth:true}") boolean requireAuth) {
        this.requireAuth = requireAuth;
    }

    public boolean isAdmin() {
        if (!requireAuth) {
            return true;
        }
        return hasRole(AppRoles.ADMIN);
    }

    public boolean canEdit() {
        if (!requireAuth) {
            return true;
        }
        return hasRole(AppRoles.ADMIN) || hasRole(AppRoles.EDIT);
    }

    public boolean editRequested(Boolean edit) {
        return canEdit() && Boolean.TRUE.equals(edit);
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (role.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
