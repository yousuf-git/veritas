package com.reconengine.settlement;

import com.reconengine.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Keeps the original uploaded bytes so any reported figure can be traced back to the file it
 * came from. Stored names are generated, never taken from the client, so a crafted filename
 * cannot escape the storage root.
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(AppProperties properties) {
        this.root = Path.of(properties.storage().root()).toAbsolutePath().normalize();
    }

    public Stored store(String originalFilename, InputStream content) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String relative = "%d/%02d/%s-%s".formatted(
                today.getYear(), today.getMonthValue(), UUID.randomUUID(), sanitize(originalFilename));
        Path target = root.resolve(relative).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalStateException("resolved storage path escaped the storage root");
        }

        try {
            Files.createDirectories(target.getParent());
            long bytes = Files.copy(content, target);
            return new Stored(relative, bytes);
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to store settlement file", ex);
        }
    }

    public InputStream read(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("storage path escaped the storage root");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to read settlement file " + relativePath, ex);
        }
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "settlement.csv";
        }
        String base = Path.of(filename).getFileName().toString();
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 100 ? cleaned.substring(cleaned.length() - 100) : cleaned;
    }

    public record Stored(String relativePath, long sizeBytes) {
    }
}
