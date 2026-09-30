package pl.ngo.budget.util;

import jakarta.servlet.http.HttpServletResponse;

public final class ExcelDownload {

    private ExcelDownload() {
    }

    public static void prepare(HttpServletResponse response, String filename) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
    }
}
