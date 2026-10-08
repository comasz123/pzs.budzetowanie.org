package pl.ngo.budget.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.ngo.budget.dto.DatabaseBackupInfoDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class DatabaseBackupService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);
    private static final Pattern JDBC_URL = Pattern.compile(
            "jdbc:mariadb://([^:/]+)(?::(\\d+))?/([^?]+)");
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Path backupDirectory;
    private final int retentionCount;
    private final boolean enabled;

    public DatabaseBackupService(@Value("${spring.datasource.url}") String jdbcUrl,
                                 @Value("${spring.datasource.username}") String username,
                                 @Value("${spring.datasource.password}") String password,
                                 @Value("${app.backup.directory:./backups}") String backupDirectory,
                                 @Value("${app.backup.retention-count:14}") int retentionCount,
                                 @Value("${app.backup.enabled:true}") boolean enabled) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.backupDirectory = Path.of(backupDirectory).toAbsolutePath().normalize();
        this.retentionCount = Math.max(1, retentionCount);
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public DatabaseBackupInfoDto getLatestBackupInfo() {
        DatabaseBackupInfoDto info = new DatabaseBackupInfoDto();
        findLatestBackupFile().ifPresent(path -> fillInfo(info, path));
        return info;
    }

    public Optional<Path> findLatestBackupFile() {
        if (!enabled || !Files.isDirectory(backupDirectory)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(backupDirectory)) {
            return files
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".sql"))
                    .max(Comparator.comparing(this::fileSortKey));
        } catch (IOException ex) {
            log.warn("Nie udało się odczytać katalogu kopii zapasowych {}: {}", backupDirectory, ex.getMessage());
            return Optional.empty();
        }
    }

    public Path saveBackup() throws IOException, InterruptedException {
        if (!enabled) {
            throw new IllegalStateException("Automatyczne kopie zapasowe są wyłączone");
        }
        Files.createDirectories(backupDirectory);
        Path target = backupDirectory.resolve(buildFilename());
        JdbcConnectionInfo info = parseJdbcUrl();

        IOException lastFailure = null;
        for (String executable : List.of("mariadb-dump", "mysqldump")) {
            try {
                runDumpToFile(executable, info, target);
                pruneOldBackups();
                log.info("Zapisano kopię zapasową bazy: {}", target);
                return target;
            } catch (IOException ex) {
                lastFailure = ex;
                Files.deleteIfExists(target);
            }
        }
        throw new IllegalStateException(
                "Nie znaleziono mariadb-dump ani mysqldump na serwerze. "
                        + (lastFailure != null ? lastFailure.getMessage() : ""));
    }

    private void runDumpToFile(String executable, JdbcConnectionInfo info, Path target)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--host=" + info.host());
        command.add("--port=" + info.port());
        command.add("--user=" + username);
        command.add("--single-transaction");
        command.add("--routines");
        command.add("--triggers");
        command.add("--default-character-set=utf8mb4");
        command.add("--skip-lock-tables");
        command.add("--result-file=" + target.toString());
        command.add(info.database());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("MARIADB_PWD", password);
        processBuilder.environment().put("MYSQL_PWD", password);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String errorOutput = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.isRegularFile(target) || Files.size(target) == 0) {
            throw new IOException(
                    (errorOutput.isBlank() ? executable + " zakończył się kodem " + exitCode : errorOutput.trim()));
        }
    }

    private void pruneOldBackups() throws IOException {
        if (!Files.isDirectory(backupDirectory)) {
            return;
        }
        List<Path> backups;
        try (Stream<Path> files = Files.list(backupDirectory)) {
            backups = files
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(this::fileSortKey).reversed())
                    .toList();
        }
        for (int i = retentionCount; i < backups.size(); i++) {
            Path old = backups.get(i);
            Files.deleteIfExists(old);
            log.info("Usunięto starą kopię zapasową: {}", old.getFileName());
        }
    }

    private String buildFilename() {
        return parseJdbcUrl().database() + "-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".sql";
    }

    private Instant fileSortKey(Path path) {
        try {
            FileTime modified = Files.getLastModifiedTime(path);
            return modified.toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    private void fillInfo(DatabaseBackupInfoDto info, Path path) {
        info.setAvailable(true);
        info.setFilename(path.getFileName().toString());
        try {
            info.setSizeBytes(Files.size(path));
            info.setCreatedAt(LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(),
                    ZoneId.systemDefault()));
        } catch (IOException ex) {
            info.setCreatedAt(null);
            info.setSizeBytes(0);
        }
    }

    private JdbcConnectionInfo parseJdbcUrl() {
        Matcher matcher = JDBC_URL.matcher(jdbcUrl);
        if (!matcher.find()) {
            throw new IllegalStateException("Nieobsługiwany URL bazy danych: " + jdbcUrl);
        }
        String host = matcher.group(1);
        String port = matcher.group(2) != null ? matcher.group(2) : "3306";
        String database = matcher.group(3);
        return new JdbcConnectionInfo(host, port, database);
    }

    private record JdbcConnectionInfo(String host, String port, String database) {}
}
