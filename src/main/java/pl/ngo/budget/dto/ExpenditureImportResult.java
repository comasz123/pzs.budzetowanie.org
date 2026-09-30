package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ExpenditureImportResult {

    private int rowsRead;
    private int imported;
    private int skipped;
    private int replaced;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void addError(int rowNumber, String message) {
        errors.add("Wiersz " + rowNumber + ": " + message);
    }

    public void addWarning(int rowNumber, String message) {
        warnings.add("Wiersz " + rowNumber + ": " + message);
    }

    public boolean isSuccess() {
        return errors.isEmpty() && imported > 0;
    }
}
