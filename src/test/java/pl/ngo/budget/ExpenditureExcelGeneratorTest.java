package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import pl.ngo.budget.service.ExpenditureImportService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenditureExcelGeneratorTest {

    @Test
    void generateSampleExpenditureWorkbooks() throws IOException {
        Files.createDirectories(Path.of("src/main/resources/import"));
        for (int year : new int[] {2025, 2026}) {
            Path output = Path.of("src/main/resources/import/wydatki-" + year + ".xlsx");
            try (OutputStream stream = Files.newOutputStream(output)) {
                ExpenditureImportService.generateSampleExcelFile(stream, year);
            }
            assertTrue(Files.size(output) > 1000, "Generated workbook for " + year + " should not be empty");
        }
    }
}
