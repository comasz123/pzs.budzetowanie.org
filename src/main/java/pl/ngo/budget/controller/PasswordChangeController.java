package pl.ngo.budget.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.ngo.budget.model.User;
import pl.ngo.budget.repository.UserRepository;
import pl.ngo.budget.security.FailedAttemptLimiter;
import pl.ngo.budget.service.AuditService;

import java.net.URI;

@Controller
public class PasswordChangeController {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeController.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final FailedAttemptLimiter limiter;

    public PasswordChangeController(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    AuditService auditService,
                                    FailedAttemptLimiter limiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.limiter = limiter;
    }

    @PostMapping("/account/password")
    public String change(@RequestParam(required = false) String currentPassword,
                         @RequestParam(required = false) String newPassword,
                         @RequestParam(required = false) String confirmPassword,
                         Authentication authentication,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        String email = authentication == null ? null : authentication.getName();
        User user = email == null || "anonymousUser".equals(email)
                ? null
                : userRepository.findByEmail(email).orElse(null);

        String error = user == null
                ? "Zaloguj się, aby zmienić hasło."
                : validate(user, currentPassword, newPassword, confirmPassword);
        if (error != null) {
            redirectAttributes.addFlashAttribute("passwordError", error);
            audit(false, email, request);
            return back(request);
        }

        limiter.reset(user.getEmail());
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("passwordMessage", "Hasło zostało zmienione.");
        audit(true, user.getEmail(), request);
        return back(request);
    }

    /** Zwraca komunikat błędu albo null, gdy dane są poprawne. */
    private String validate(User user, String current, String next, String confirm) {
        if (limiter.isBlocked(user.getEmail())) {
            return "Zbyt wiele nieudanych prób. Spróbuj ponownie za kilkanaście minut.";
        }
        if (current == null || current.isBlank() || !passwordEncoder.matches(current, user.getPassword())) {
            limiter.recordFailure(user.getEmail());
            return "Obecne hasło jest nieprawidłowe.";
        }
        if (next == null || next.length() < MIN_PASSWORD_LENGTH) {
            return "Nowe hasło musi mieć co najmniej " + MIN_PASSWORD_LENGTH + " znaków.";
        }
        if (!next.equals(confirm)) {
            return "Nowe hasła nie są takie same.";
        }
        if (passwordEncoder.matches(next, user.getPassword())) {
            return "Nowe hasło musi różnić się od obecnego.";
        }
        return null;
    }

    private void audit(boolean success, String username, HttpServletRequest request) {
        try {
            auditService.passwordChange(success, username, request);
        } catch (RuntimeException ex) {
            log.warn("Nie zapisano zmiany hasła w dzienniku: {}", ex.getMessage());
        }
    }

    /** Wraca na stronę, z której wysłano formularz (tylko ścieżka i query, nigdy obcy host). */
    private static String back(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "redirect:/";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return "redirect:/";
            }
            String query = uri.getRawQuery();
            return "redirect:" + path + (query == null || query.isBlank() ? "" : "?" + query);
        } catch (IllegalArgumentException ex) {
            return "redirect:/";
        }
    }
}
