package pl.ngo.budget.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.ngo.budget.service.KorzenieJutraImportService;
import pl.ngo.budget.service.KorzenieJutraWorkbookParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Jednorazowy import budżetu projektu „Korzenie Jutra” (PKO BP + wkład OSIF). Uruchamia się po starcie,
 * gdy ustawiono {@code app.import.korzenie-jutra.file}; po imporcie wynagrodzeń i projektów z arkusza Koszty
 * (jeśli podano oba pliki). Domyślnie zapisuje; {@code app.import.korzenie-jutra.apply=false} to podgląd.
 */
@Component
@ConditionalOnProperty(name = "app.import.korzenie-jutra.file")
public class KorzenieJutraImportRunner {

    private static final Logger log = LoggerFactory.getLogger(KorzenieJutraImportRunner.class);

    private final KorzenieJutraImportService service;

    @Value("${app.import.korzenie-jutra.file}")
    private String file;

    @Value("${app.import.korzenie-jutra.apply:true}")
    private boolean apply;

    public KorzenieJutraImportRunner(KorzenieJutraImportService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void run() {
        try {
            doRun();
        } catch (Exception e) {
            // Błąd importu nie powinien przewracać aplikacji; transakcje wycofują niedokończone zapisy.
            log.error("Import nie powiódł się: {}", e.getMessage(), e);
        }
    }

    private void doRun() throws IOException {
        KorzenieJutraWorkbookParser.ParseResult parsed;
        try (InputStream in = Files.newInputStream(Path.of(file))) {
            parsed = KorzenieJutraWorkbookParser.parse(in);
        }
        log.info("Import projektu Korzenie Jutra z {} (apply={})", file, apply);
        service.load(parsed, apply).forEach(line -> log.info("{}", line));
    }
}
