package pl.ngo.budget.util;

import java.util.List;

public final class PolishMonthNames {

    public static final List<String> ALL = List.of(
            "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
            "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień"
    );

    public static final List<String> SHORT = List.of(
            "Sty", "Lut", "Mar", "Kwi", "Maj", "Cze",
            "Lip", "Sie", "Wrz", "Paź", "Lis", "Gru"
    );

    private PolishMonthNames() {
    }

    public static String of(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Miesiąc poza zakresem 1–12: " + month);
        }
        return ALL.get(month - 1);
    }
}
