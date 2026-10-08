package pl.ngo.budget.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureCredentialsExpiredEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureExpiredEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pl.ngo.budget.repository.UserRepository;
import pl.ngo.budget.service.AuditService;

@Component
public class LoginAuditListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditListener.class);

    private final AuditService auditService;
    private final UserRepository userRepository;

    public LoginAuditListener(AuditService auditService, UserRepository userRepository) {
        this.auditService = auditService;
        this.userRepository = userRepository;
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
        HttpServletRequest request = currentRequest();
        record(() -> auditService.loginResult(false, name, failureReason(event, name), request));
    }

    private String failureReason(AbstractAuthenticationFailureEvent event, String name) {
        if (event instanceof AuthenticationFailureDisabledEvent) {
            return "konto wyłączone";
        }
        if (event instanceof AuthenticationFailureLockedEvent) {
            return "konto zablokowane";
        }
        if (event instanceof AuthenticationFailureExpiredEvent) {
            return "konto wygasło";
        }
        if (event instanceof AuthenticationFailureCredentialsExpiredEvent) {
            return "hasło wygasło";
        }
        if (event instanceof AuthenticationFailureBadCredentialsEvent) {
            if (name == null || name.isBlank() || userRepository.findByEmail(name).isEmpty()) {
                return "nieznany adres e-mail";
            }
            return "złe hasło";
        }
        return "błąd logowania";
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
