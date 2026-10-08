package pl.ngo.budget.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pl.ngo.budget.service.AuditService;

@Component
public class ChangeAuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ChangeAuditInterceptor.class);

    private final AuditService auditService;

    public ChangeAuditInterceptor(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        if (ex != null || response.getStatus() >= 400) {
            return;
        }
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method)
                && !"PATCH".equals(method) && !"DELETE".equals(method)) {
            return;
        }
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        // logowanie i zmianę hasła zapisują własne wpisy (wynik zależy od poprawności danych)
        if (path.endsWith("/login") || path.endsWith("/logout") || path.endsWith("/account/password")) {
            return;
        }
        try {
            auditService.change(request);
        } catch (RuntimeException auditError) {
            log.warn("Nie zapisano zmiany: {}", auditError.getMessage());
        }
    }
}
