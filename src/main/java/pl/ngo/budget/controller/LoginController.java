package pl.ngo.budget.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import pl.ngo.budget.service.AuditService;

@Controller
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final AuditService auditService;

    public LoginController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request) {
        if (request.getParameter("error") == null && request.getParameter("logout") == null) {
            try {
                auditService.loginPage(request);
            } catch (RuntimeException ex) {
                log.warn("Nie zapisano otwarcia logowania: {}", ex.getMessage());
            }
        }
        return "login";
    }
}