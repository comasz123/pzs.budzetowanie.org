package pl.ngo.budget.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.ngo.budget.service.DatabaseBackupService;

@Component
@ConditionalOnProperty(name = "app.backup.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseBackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupScheduler.class);

    private final DatabaseBackupService databaseBackupService;

    public DatabaseBackupScheduler(DatabaseBackupService databaseBackupService) {
        this.databaseBackupService = databaseBackupService;
    }

    @Scheduled(cron = "${app.backup.cron:0 0 3 * * *}")
    public void runScheduledBackup() {
        try {
            databaseBackupService.saveBackup();
        } catch (Exception ex) {
            log.error("Automatyczna kopia zapasowa nie powiodła się: {}", ex.getMessage());
        }
    }
}
