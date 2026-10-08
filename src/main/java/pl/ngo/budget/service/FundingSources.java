package pl.ngo.budget.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Źródła finansowania (kolumny projektów w arkuszu „Koszty”) i dane projektów/grantów, które z nich
 * powstają. Okresy zaczerpnięto z nagłówków arkuszy projektowych; tam, gdzie arkusza brak, grant obejmuje
 * rok 2026. Sponsor to skrót finansującego z nagłówka kolumny — do uzupełnienia w programie.
 */
public final class FundingSources {

    /** Projekt, do którego należy grant; domyślnie osobny projekt o tym samym kodzie i nazwie co grant. */
    public record Source(String key, String name, String sponsor, String headerPrefix,
                         LocalDate start, LocalDate end, String projectCode, String projectName) {
        public Source(String key, String name, String sponsor, String headerPrefix,
                      LocalDate start, LocalDate end) {
            this(key, name, sponsor, headerPrefix, start, end, key, name);
        }
    }

    private static final LocalDate Y26_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Y26_END = LocalDate.of(2026, 12, 31);

    public static final List<Source> ALL = List.of(
            new Source("ECF_JT_GAS", "ECF JT+GAS 25-26", "ECF", "ecf jt",
                    LocalDate.of(2025, 10, 1), LocalDate.of(2026, 9, 30)),
            new Source("ECF_WNE", "ECF WNE 25", "ECF", "ecf wne", Y26_START, Y26_END),
            new Source("ECF_TRANS_WNE_MFF", "ECF TRANS+WNE+MFF 26", "ECF", "ecf trans",
                    LocalDate.of(2026, 2, 1), LocalDate.of(2027, 1, 31)),
            new Source("ECF_NRL", "ECF NRL 25-26", "ECF", "ecf nrl",
                    LocalDate.of(2024, 12, 15), LocalDate.of(2026, 6, 14)),
            new Source("BWN_GA", "BWN GA 26", "BWN", "bwn ga", Y26_START, Y26_END),
            new Source("BWN_PREMISES", "BWN PREMISES 26", "BWN", "bwn premises", Y26_START, Y26_END),
            new Source("BWN_REPOWER", "BWN REPOWER 25", "BWN", "bwn repower",
                    LocalDate.of(2023, 9, 1), LocalDate.of(2026, 3, 31)),
            new Source("LIFE_EFFECT", "LIFE EFFECT", "LIFE", "life effect",
                    LocalDate.of(2024, 7, 1), LocalDate.of(2027, 6, 30)),
            new Source("ROPT", "ROPT", "ROPT", "ropt", Y26_START, Y26_END),
            new Source("LAC", "LAC", "LAC", "lac",
                    LocalDate.of(2023, 7, 1), LocalDate.of(2026, 6, 30)),
            new Source("OSIF", "OSIF", "OSIF", "osif",
                    LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30)),
            new Source("DPT", "DPT", "DPT", "dpt", Y26_START, LocalDate.of(2028, 12, 31)),
            new Source("MPT_FENG", "MPT FENG", "MPT", "mpt feng", Y26_START, Y26_END),
            new Source("MPT_FENIKS", "MPT FEnIKS", "MPT", "mpt feniks", Y26_START, Y26_END),
            new Source("MPT_FERS", "MPT FERS", "MPT", "mpt fers", Y26_START, Y26_END),
            new Source("PAFW", "PAFW", "PAFW", "pafw", Y26_START, LocalDate.of(2027, 12, 31)),
            new Source("FPKO_BP", "PKO BP – Korzenie Jutra", "PKO BP", "fpko",
                    LocalDate.of(2026, 2, 16), LocalDate.of(2027, 5, 15),
                    KorzenieJutraImportService.PROJECT_CODE, KorzenieJutraImportService.PROJECT_NAME),
            new Source("ODPLATNA", "Odpłatna", "Odpłatna", "odpłatna", Y26_START, Y26_END));

    private FundingSources() {
    }

    /** Dopasowanie nagłówka kolumny (także „LAC - do końca czerwca”) do źródła. */
    public static Optional<Source> forHeader(String header) {
        if (header == null) {
            return Optional.empty();
        }
        String h = header.replace(' ', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return ALL.stream().filter(s -> h.startsWith(s.headerPrefix())).findFirst();
    }
}
