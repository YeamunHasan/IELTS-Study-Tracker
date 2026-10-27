package com.ielts.tracker.service;

import com.ielts.tracker.repository.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/** Copies the database into a folder of the user's choice (for example one that is already synced) and keeps the newest copies. */
public final class BackupService {
    public static final int KEEP = 10;
    private static final String PREFIX = "ielts-tracker-";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private BackupService() {}

    /** Writes a consistent copy, then deletes all but the newest {@code keep} backups made by this app. Returns the new file. */
    public static Path copy(Database db, Path folder, Instant now, ZoneId zone, int keep) throws IOException {
        Files.createDirectories(folder);
        Path target = folder.resolve(PREFIX + STAMP.format(now.atZone(zone)) + ".db");
        int n = 2;
        while (Files.exists(target)) { // two exits in the same second must not overwrite each other
            target = folder.resolve(PREFIX + STAMP.format(now.atZone(zone)) + "-" + n++ + ".db");
        }
        db.backupTo(target);
        prune(folder, keep);
        return target;
    }

    static void prune(Path folder, int keep) throws IOException {
        List<Path> mine;
        try (Stream<Path> s = Files.list(folder)) {
            mine = s.filter(p -> p.getFileName().toString().startsWith(PREFIX) && p.getFileName().toString().endsWith(".db"))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString())).toList();
        }
        for (int i = keep; i < mine.size(); i++) Files.deleteIfExists(mine.get(i));
    }
}
