package pl.ngo.budget.util;

/** Klucze linii wydatków budżetu (pozycje alokacji pod wierszem). */
public final class BudgetLineKeys {

    private BudgetLineKeys() {
    }

    /** Klucz linii: wiersz, pod którym powstaje, i nazwa linii. */
    public static String of(String parentRowKey, String label) {
        String slug = label == null ? "" : label.trim()
                .toLowerCase()
                .replace('ą', 'a').replace('ć', 'c').replace('ę', 'e')
                .replace('ł', 'l').replace('ń', 'n').replace('ó', 'o')
                .replace('ś', 's').replace('ź', 'z').replace('ż', 'z')
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            slug = "pozycja";
        }
        return parentRowKey + "-line-" + slug;
    }
}
