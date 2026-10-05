package pl.ngo.budget.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pl.ngo.budget.service.AuditService;

@Component
public class LoginAuditListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditListener.class);

    private final AuditService auditService;

    public LoginAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onSuccess(InteractiveAuthenticationSuccessEvent event) {
        HttpServletRequest request = currentRequest();
        boolean formLogin = request != null
                && "POST".equals(request.getMethod())
                && request.getRequestURI() != null
                && request.getRequestURI().endsWith("/login");
        String name = event.getAuthentication() == null ? null : event.getAuthentication().getName();
        record(() -> auditService.loginResult(true, name, formLogin ? null : "zapamiętane logowanie", request));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String name = event.getAuthentication() == null ? null : event.getAuthentication().getName();
        String detail = event instanceof AuthenticationFailureDisabledEvent ? "konto wyłączone" : null;
        HttpServletRequest request = currentRequest();
        record(() -> auditService.loginResult(false, name, detail, request));
    }

    private static void record(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Nie zapisano wyniku logowania: {}", ex.getMessage());
        }
    }

    private static HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }
}
