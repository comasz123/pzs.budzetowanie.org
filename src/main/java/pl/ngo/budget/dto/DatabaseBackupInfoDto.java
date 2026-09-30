package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DatabaseBackupInfoDto {

    private boolean available;
    private String filename;
    private LocalDateTime createdAt;
    private long sizeBytes;
}
