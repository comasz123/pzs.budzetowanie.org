package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import pl.ngo.budget.service.KorzenieJutraWorkbookParser;
import pl.ngo.budget.service.KorzenieJutraWorkbookParser.ParseResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KorzenieJutraImportTest {

    /** Uruchamiany tylko gdy podano -Dpko.xlsx=/ścieżka/do/pliku.xlsx */
    @Test
    void parsesBudgetAndClassifiesLines() throws Exception {
        String path = System.getProperty("pko.xlsx");
        assumeTrue(path != null && Files.exists(Path.of(path)));
        ParseResult parsed;
        try (var in = Files.newInputStream(Path.of(path))) {
            parsed = KorzenieJutraWorkbookParser.parse(in);
        }
        parsed.warnings().forEach(System.out::println);
        assertTrue(parsed.warnings().isEmpty(), "sumy pozycji muszą zgadzać się z arkuszem: " + parsed.warnings());
        assertEquals(31, parsed.lines().size());
        assertEquals(0, new BigDecimal("548247").compareTo(parsed.grandTotal()));
        assertEquals(0, new BigDecimal("48247").compareTo(parsed.osifTotal()));
        parsed.lines().forEach(l -> System.out.println(l.name().substring(0, Math.min(60, l.name().length()))
                + " | " + l.unit() + " | " + l.amountByYear() + " | osif " + l.osifAmount()));
    }
}
