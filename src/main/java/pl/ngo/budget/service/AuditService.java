package pl.ngo.budget.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.model.AuditEvent;
import pl.ngo.budget.model.AuditEventType;
import pl.ngo.budget.repository.AuditEventRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AuditService {

    private static final int RETENTION_DAYS = 180;
    private static final Set<String> SKIPPED_PARAMS = Set.of(
            "password", "_csrf", "remember-me", "edit", "categoryid", "ids");
    private static final List<String> PREFERRED_PARAMS = List.of(
            "name", "code", "email", "firstName", "lastName", "role", "enabled",
            "year", "month", "amount", "kind");

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> latest() {
        return auditEventRepository.findTop500ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Map<String, String> latestIpByUsername() {
        Map<String, String> ips = new HashMap<>();
        for (AuditEvent event : auditEventRepository.findLatestIpPerUsername()) {
            if (event.getUsername() == null || event.getIp() == null || event.getIp().isBlank()) {
                continue;
            }
            ips.put(event.getUsername().toLowerCase(Locale.ROOT), event.getIp());
        }
        return ips;
    }

    @Transactional
    public void loginPage(HttpServletRequest request) {
        persist(AuditEventType.LOGIN_PAGE, null, null, request);
    }

    @Transactional
    public void loginResult(boolean success, String username, String detail, HttpServletRequest request) {
        persist(success ? AuditEventType.LOGIN_SUCCESS : AuditEventType.LOGIN_FAILURE,
                cleanUsername(username), detail, request);
    }

    @Transactional
    public void change(HttpServletRequest request) {
        persist(AuditEventType.CHANGE, currentUsername(), describe(request), request);
    }

    @Transactional
    public void passwordChange(boolean success, String username, HttpServletRequest request) {
        persist(success ? AuditEventType.CHANGE : AuditEventType.PASSWORD_FAILURE,
                cleanUsername(username),
                success ? "Zmiana własnego hasła" : "Nieudana zmiana hasła",
                request);
    }

    private void persist(AuditEventType type, String username, String detail, HttpServletRequest request) {
        AuditEvent event = new AuditEvent();
        event.setCreatedAt(LocalDateTime.now());
        event.setType(type);
        event.setUsername(limit(username, 200));
        event.setDetail(limit(detail, 1000));
        if (request != null) {
            event.setIp(limit(request.getRemoteAddr(), 64));
            event.setUserAgent(limit(compact(request.getHeader("User-Agent")), 300));
        }
        auditEventRepository.save(event);
        auditEventRepository.deleteOlderThan(LocalDateTime.now().minusDays(RETENTION_DAYS));
    }

    private static String describe(HttpServletRequest request) {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        String label = labelFor(path);
        String extra = params(request);
        if (request.getContentType() != null && request.getContentType().toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            extra = extra.isBlank() ? "plik" : extra + ", plik";
        }
        if (extra.isBlank()) {
            return label;
        }
        return label + ": " + extra;
    }

    private static String labelFor(String path) {
        if (path.contains("/month/amount")) {
            return "Zmiana kwoty w miesiącu";
        }
        if (path.contains("/structure/amount")) {
            return "Zmiana kwoty pozycji";
        }
        if (path.contains("/structure/has-subcategories")) {
            return "Zmiana struktury pozycji";
        }
        if (path.contains("/reorder")) {
            return "Zmiana kolejności";
        }
        if (path.contains("/categories/") && path.endsWith("/move")) {
            return "Przesunięcie kategorii";
        }
        if (path.contains("/categories/") && path.endsWith("/delete")) {
            return "Usunięcie kategorii";
        }
        if (path.endsWith("/budget-period")) {
            return "Zmiana okresu pracy w budżecie";
        }
        if (path.endsWith("/lines/planned")) {
            return "Zmiana planowanego kosztu wydatku";
        }
        if (path.endsWith("/lines/delete")) {
            return "Usunięcie wydatku";
        }
        if (path.endsWith("/lines/split-months")) {
            return "Rozpisanie wydatku na 12 miesięcy";
        }
        if (path.endsWith("/subcategories/delete")) {
            return "Usunięcie podpozycji";
        }
        if (path.endsWith("/subcategories")) {
            return "Dodanie podpozycji";
        }
        if (path.endsWith("/categories")) {
            return "Dodanie kategorii";
        }
        if (path.contains("/grants/") && path.endsWith("/budget")) {
            return "Zapis budżetu grantu";
        }
        if (path.contains("/grants/") && path.endsWith("/delete")) {
            return "Usunięcie grantu";
        }
        if (path.endsWith("/grants")) {
            return "Zapis grantu";
        }
        if (path.contains("/sponsors/") && path.endsWith("/delete")) {
            return "Usunięcie sponsora";
        }
        if (path.endsWith("/sponsors")) {
            return "Zapis sponsora";
        }
        if (path.contains("/sponsor-contacts/") && path.endsWith("/delete")) {
            return "Usunięcie kontaktu";
        }
        if (path.endsWith("/sponsor-contacts")) {
            return "Zapis kontaktu";
        }
        if (path.contains("/employees/") && path.endsWith("/delete")) {
            return "Usunięcie pracownika";
        }
        if (path.endsWith("/employees")) {
            return "Zapis pracownika";
        }
        if (path.endsWith("/projects")) {
            return "Dodanie projektu";
        }
        if (path.endsWith("/budget/new/start")) {
            return "Start nowego budżetu";
        }
        if (path.contains("/budget/new/") && path.endsWith("/plan")) {
            return "Zapis planu nowego budżetu";
        }
        if (path.contains("/budget/new/") && path.contains("/month/")) {
            return "Zapis miesiąca nowego budżetu";
        }
        if (path.endsWith("/split/clear")) {
            return "Usunięcie podziału wydatku";
        }
        if (path.endsWith("/split")) {
            return "Podział wydatku";
        }
        if (path.endsWith("/grant")) {
            return "Przypisanie grantu do wydatku";
        }
        if (path.endsWith("/project")) {
            return "Przypisanie projektu do wydatku";
        }
        if (path.endsWith("/status")) {
            return "Zmiana stanu wydatku";
        }
        if (path.endsWith("/import")) {
            return "Import wydatków";
        }
        if (path.contains("/admin/users/") && path.endsWith("/role")) {
            return "Zmiana roli";
        }
        if (path.contains("/admin/users/") && path.endsWith("/enabled")) {
            return "Zmiana statusu konta";
        }
        return "Zapis " + path;
    }

    private static String params(HttpServletRequest request) {
        List<String> parts = new ArrayList<>();
        for (String name : PREFERRED_PARAMS) {
            appendParam(parts, request, name);
        }
        if (parts.isEmpty() && request.getParameterMap() != null) {
            for (String name : request.getParameterMap().keySet()) {
                if (parts.size() >= 4) {
                    break;
                }
                appendParam(parts, request, name);
            }
        }
        return String.join(", ", parts);
    }

    private static void appendParam(List<String> parts, HttpServletRequest request, String name) {
        if (name == null) {
            return;
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.contains("password") || SKIPPED_PARAMS.contains(lowerName)) {
            return;
        }
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            return;
        }
        String shown = value.trim().replaceAll("\\s+", " ");
        if (shown.length() > 80) {
            shown = shown.substring(0, 80) + "…";
        }
        parts.add(name + "=" + shown);
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return cleanUsername(auth.getName());
    }

    private static String cleanUsername(String username) {
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return null;
        }
        return username.trim();
    }

    private static String compact(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
