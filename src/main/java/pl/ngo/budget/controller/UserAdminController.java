package pl.ngo.budget.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.ngo.budget.model.Organization;
import pl.ngo.budget.model.Role;
import pl.ngo.budget.model.User;
import pl.ngo.budget.repository.RoleRepository;
import pl.ngo.budget.repository.UserRepository;
import pl.ngo.budget.security.AppRoles;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class UserAdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminController(UserRepository userRepository,
                               RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String list(Model model) {
        List<User> users = userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getEmail, String.CASE_INSENSITIVE_ORDER))
                .toList();
        model.addAttribute("activeSection", "users");
        model.addAttribute("users", users);
        model.addAttribute("roleLabels", AppRoles.ASSIGNABLE.stream().map(AppRoles::label).toList());
        return "admin/users";
    }

    @PostMapping
    public String create(@RequestParam String email,
                         @RequestParam String firstName,
                         @RequestParam String lastName,
                         @RequestParam String password,
                         @RequestParam String role,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String roleName = AppRoles.fromForm(role);
        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@")
                || firstName == null || firstName.isBlank()
                || lastName == null || lastName.isBlank()
                || roleName == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Uzupełnij e-mail, imię, nazwisko i rolę.");
            return "redirect:/admin/users";
        }
        if (password == null || password.length() < 8) {
            redirectAttributes.addFlashAttribute("errorMessage", "Hasło musi mieć co najmniej 8 znaków.");
            return "redirect:/admin/users";
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Konto o tym adresie już istnieje.");
            return "redirect:/admin/users";
        }
        Role assigned = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Brak roli " + roleName));
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setRoles(new HashSet<>(java.util.Set.of(assigned)));
        user.setOrganization(currentOrganization(authentication));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMessage", "Dodano konto " + normalizedEmail + ".");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam String role,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElse(null);
        String roleName = AppRoles.fromForm(role);
        if (user == null || roleName == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nie można zmienić roli.");
            return "redirect:/admin/users";
        }
        if (isSelf(user, authentication) && !AppRoles.ADMIN.equals(roleName)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nie zmienisz własnej roli administratora.");
            return "redirect:/admin/users";
        }
        if (isAdmin(user) && !AppRoles.ADMIN.equals(roleName) && enabledAdminCountExcept(user.getId()) < 1) {
            redirectAttributes.addFlashAttribute("errorMessage", "Zostaw co najmniej jednego administratora.");
            return "redirect:/admin/users";
        }
        Role assigned = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Brak roli " + roleName));
        user.setRoles(new HashSet<>(java.util.Set.of(assigned)));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMessage", "Zmieniono rolę " + user.getEmail() + ".");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@PathVariable Long id,
                             @RequestParam boolean enabled,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono konta.");
            return "redirect:/admin/users";
        }
        if (!enabled && isSelf(user, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nie wyłączysz własnego konta.");
            return "redirect:/admin/users";
        }
        if (!enabled && isAdmin(user) && enabledAdminCountExcept(user.getId()) < 1) {
            redirectAttributes.addFlashAttribute("errorMessage", "Zostaw co najmniej jednego administratora.");
            return "redirect:/admin/users";
        }
        user.setEnabled(enabled);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMessage",
                (enabled ? "Włączono konto " : "Wyłączono konto ") + user.getEmail() + ".");
        return "redirect:/admin/users";
    }

    private Organization currentOrganization(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName())
                .map(User::getOrganization)
                .orElse(null);
    }

    private boolean isSelf(User user, Authentication authentication) {
        return authentication != null && user.getEmail().equalsIgnoreCase(authentication.getName());
    }

    private boolean isAdmin(User user) {
        return AppRoles.ADMIN.equals(AppRoles.primary(user.getRoles()));
    }

    private long enabledAdminCountExcept(Long userId) {
        return userRepository.findAll().stream()
                .filter(User::isEnabled)
                .filter(user -> !user.getId().equals(userId))
                .filter(this::isAdmin)
                .count();
    }
}
