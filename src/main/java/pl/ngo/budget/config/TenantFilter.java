package pl.ngo.budget.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.ngo.budget.model.Organization;
import pl.ngo.budget.tenant.TenantContext;
import pl.ngo.budget.tenant.TenantHostService;

import java.io.IOException;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantFilter extends OncePerRequestFilter {

    private final TenantHostService tenantHostService;

    public TenantFilter(TenantHostService tenantHostService) {
        this.tenantHostService = tenantHostService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Optional<Organization> organization = tenantHostService.resolveOrganization(request.getServerName());
            if (organization.isEmpty()) {
                response.sendError(HttpStatus.NOT_FOUND.value(),
                        "Aplikacja dostępna tylko pod domeną organizacji.");
                return;
            }
            TenantContext.setOrganization(organization.get());
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
