package com.ielts.tracker.repository;

import com.ielts.tracker.TestSupport;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.TimerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    @TempDir Path dir;

    @Test void migrationIsIdempotentAndSetsVersion() {
        Database db = TestSupport.newDb(dir);
        assertEquals(2, db.schemaVersion());
        db.migrate();
        assertEquals(2, db.schemaVersion());
    }

    @Test void refusesDatabaseFromANewerVersion() {
        Database db = TestSupport.newDb(dir);
        db.query(c -> { try (Statement s = c.createStatement()) { s.execute("PRAGMA user_version = 99"); } return null; });
        assertThrows(DataAccessException.class, db::migrate);
    }

    @Test void foreignKeysAreEnforced() {
        Database db = TestSupport.newDb(dir);
        assertThrows(DataAccessException.class, () -> new SessionRepository(db).insertActive(999, Instant.now(), TimerMode.STOPWATCH, 0));
    }

    @Test void failedTransactionRollsBack() {
        Database db = TestSupport.newDb(dir);
        SettingsRepository settings = new SettingsRepository(db);
        assertThrows(RuntimeException.class, () -> db.inTransaction(c -> {
            try (Statement s = c.createStatement()) { s.execute("INSERT INTO app_settings(key,value) VALUES('a','1')"); }
            throw new IllegalStateException("boom");
        }));
        assertTrue(settings.get("a").isEmpty());
    }

    @Test void dataSurvivesReopeningTheFile() {
        Database db = TestSupport.newDb(dir);
        new SettingsRepository(db).set("exam_date", "2026-12-12");
        Database reopened = new Database(dir.resolve("test.db"));
        reopened.migrate();
        assertEquals("2026-12-12", new SettingsRepository(reopened).get("exam_date").orElseThrow());
    }

    @Test void backupContainsDataAndIsNeverOverwritten() throws Exception {
        Database db = TestSupport.newDb(dir);
        new TaskRepository(db).insert(TestSupport.draft("Essay", Skill.WRITING, 40), Instant.now());
        Path backup = dir.resolve("backup.db");

        db.backupTo(backup);
        assertTrue(Files.exists(backup));
        DataAccessException e = assertThrows(DataAccessException.class, () -> db.backupTo(backup));
        assertTrue(e.getMessage().contains("never overwritten"));

        // "Restore": the backup is a complete, independent database file.
        Database restored = new Database(backup);
        restored.migrate();
        assertEquals("Essay", new TaskRepository(restored).listSummaries().get(0).task().title());

        // later changes to the live database do not touch the backup
        new TaskRepository(db).insert(TestSupport.draft("Another", Skill.READING, 20), Instant.now());
        assertEquals(1, new TaskRepository(restored).listSummaries().size());
    }
}
