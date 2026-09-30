package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import pl.ngo.budget.config.PolishBigDecimalFormatter;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PolishBigDecimalFormatterTest {

    private final PolishBigDecimalFormatter formatter = new PolishBigDecimalFormatter();

    @Test
    void parsesSpaceAndComma() throws ParseException {
        assertEquals(0, new BigDecimal("17500.50").compareTo(formatter.parse("17 500,50", Locale.forLanguageTag("pl"))));
        assertEquals(0, new BigDecimal("5000.00").compareTo(formatter.parse("5000.00", Locale.US)));
        assertEquals(0, new BigDecimal("2500").compareTo(formatter.parse("2 500,00", Locale.forLanguageTag("pl"))));
    }

    @Test
    void printsSpaceAndComma() {
        assertEquals("5 000,00", formatter.print(new BigDecimal("5000"), Locale.forLanguageTag("pl")));
        assertEquals("20 000,50", formatter.print(new BigDecimal("20000.5"), Locale.forLanguageTag("pl")));
    }

    @Test
    void blankIsNull() throws ParseException {
        assertNull(formatter.parse("  ", Locale.forLanguageTag("pl")));
        assertEquals("", formatter.print(null, Locale.forLanguageTag("pl")));
    }
}
