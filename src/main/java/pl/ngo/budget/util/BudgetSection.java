package pl.ngo.budget.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class BudgetSection {

    public static final String PLAN = "/dashboard";
    public static final String REALIZATION = "/realizacja";
    public static final String PLAN_PAGES = "/admin";

    private BudgetSection() {
    }

    /** Shared pages: /admin under the plan, /realizacja under realization. */
    public static String pageBase() {
        return REALIZATION.equals(currentBase()) ? REALIZATION : PLAN_PAGES;
    }

    public static String currentBase() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return base(servletAttributes.getRequest());
        }
        return PLAN;
    }

    public static String base(HttpServletRequest request) {
        if (request == null) {
            return PLAN;
        }
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (path != null && context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        if (path == null || path.isEmpty()) {
            path = request.getServletPath();
        }
        if (isRealization(path) || isRealization(request.getServletPath()) || isRealization(request.getPathInfo())) {
            return REALIZATION;
        }
        return PLAN;
    }

    private static boolean isRealization(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return REALIZATION.equals(path) || path.startsWith(REALIZATION + "/");
    }
}
