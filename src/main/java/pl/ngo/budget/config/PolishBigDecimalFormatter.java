package pl.ngo.budget.config;

import org.springframework.format.Formatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Locale;

public class PolishBigDecimalFormatter implements Formatter<BigDecimal> {

    @Override
    public BigDecimal parse(String text, Locale locale) throws ParseException {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim()
                .replace('\u00a0', ' ')
                .replace('\u202f', ' ')
                .replace(" ", "")
                .replace(',', '.');
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new ParseException("Nieprawidłowa kwota: " + text, 0);
        }
    }

    @Override
    public String print(BigDecimal amount, Locale locale) {
        if (amount == null) {
            return "";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("pl-PL"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount).replace('\u00a0', ' ').replace('\u202f', ' ');
    }
}
