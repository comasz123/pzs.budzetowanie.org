package pl.ngo.budget.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import pl.ngo.budget.model.User;
import pl.ngo.budget.model.Organization;
import pl.ngo.budget.repository.UserRepository;
import pl.ngo.budget.security.CurrentAccess;
import pl.ngo.budget.tenant.TenantContext;
import pl.ngo.budget.util.BudgetSection;
import pl.ngo.budget.util.PolishMonthNames;

import java.time.LocalDate;
import java.util.List;

@ControllerAdvice
public class NavModelAdvice {

    private final UserRepository userRepository;
    private final CurrentAccess currentAccess;

    public NavModelAdvice(UserRepository userRepository, CurrentAccess currentAccess) {
        this.userRepository = userRepository;
        this.currentAccess = currentAccess;
    }

    @ModelAttribute("navCurrentYear")
    public int navCurrentYear() {
        return LocalDate.now().getYear();
    }

    @ModelAttribute("navCurrentMonth")
    public int navCurrentMonth() {
        return LocalDate.now().getMonthValue();
    }

    @ModelAttribute("navYear")
    public int navYear(@RequestParam(value = "year", required = false) Integer year) {
        return year != null ? year : LocalDate.now().getYear();
    }

    @ModelAttribute("navMonth")
    public Integer navMonth(@RequestParam(value = "month", required = false) Integer month) {
        return month;
    }

    @ModelAttribute("monthNames")
    public List<String> monthNames() {
        return PolishMonthNames.ALL;
    }

    @ModelAttribute("budgetBase")
    public String budgetBase() {
        return BudgetSection.currentBase();
    }

    @ModelAttribute("adminBase")
    public String adminBase() {
        return BudgetSection.pageBase();
    }

    @ModelAttribute("expenditureBase")
    public String expenditureBase() {
        return BudgetSection.REALIZATION.equals(BudgetSection.currentBase())
                ? "/realizacja/wydatki"
                : "/admin/expenditures";
    }

    @ModelAttribute("currentOrganization")
    public Organization currentOrganization() {
        return TenantContext.getOrganization().orElse(null);
    }

    @ModelAttribute("canEdit")
    public boolean canEdit() {
        return currentAccess.canEdit();
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        return currentAccess.isAdmin();
    }

    @ModelAttribute("userAuthenticated")
    public boolean userAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()));
    }

    @ModelAttribute("currentUserDisplay")
    public String currentUserDisplay() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            return null;
        }
        return userRepository.findByEmail(auth.getName())
                .map(this::formatUserName)
                .orElse(auth.getName());
    }

    private String formatUserName(User user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()
                && user.getLastName() != null && !user.getLastName().isBlank()) {
            return user.getFirstName() + " " + user.getLastName();
        }
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName();
        }
        return user.getEmail();
    }
}
