package pl.ngo.budget.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.ngo.budget.model.User;
import pl.ngo.budget.repository.UserRepository;

import java.net.URI;

@Controller
public class PasswordChangeController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordChangeController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/account/password")
    public String change(@RequestParam(required = false) String currentPassword,
                         @RequestParam(required = false) String newPassword,
                         @RequestParam(required = false) String confirmPassword,
                         Authentication authentication,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        String back = back(request);
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            redirectAttributes.addFlashAttribute("passwordError", "Zaloguj się, aby zmienić hasło.");
            return back;
        }
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("passwordError", "Nie znaleziono konta.");
            return back;
        }
        if (currentPassword == null || currentPassword.isBlank()
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("passwordError", "Obecne hasło jest nieprawidłowe.");
            return back;
        }
        if (newPassword == null || newPassword.length() < 8) {
            redirectAttributes.addFlashAttribute("passwordError", "Nowe hasło musi mieć co najmniej 8 znaków.");
            return back;
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("passwordError", "Nowe hasła nie są takie same.");
            return back;
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("passwordError", "Nowe hasło musi różnić się od obecnego.");
            return back;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("passwordMessage", "Hasło zostało zmienione.");
        return back;
    }

    private static String back(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "redirect:/";
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getHost() != null && request.getServerName() != null
                    && !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                return "redirect:/";
            }
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
