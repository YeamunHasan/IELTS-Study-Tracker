package com.ielts.tracker.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Owns the SQLite file: connection setup, schema migrations and backups.
 * Every connection has foreign keys enforced. Schema version lives in PRAGMA user_version.
 */
public final class Database {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection c) throws SQLException;
    }

    /** Index i is the script that upgrades the schema from version i to i+1. Never edit old entries; append. */
    private static final List<List<String>> MIGRATIONS = List.of(
            List.of(
                    """
                    CREATE TABLE tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL CHECK (length(trim(title)) > 0),
                        description TEXT NOT NULL DEFAULT '',
                        skill TEXT NOT NULL,
                        category TEXT NOT NULL DEFAULT '',
                        target_duration_seconds INTEGER NOT NULL CHECK (target_duration_seconds > 0),
                        priority TEXT NOT NULL DEFAULT 'MEDIUM',
                        status TEXT NOT NULL DEFAULT 'PLANNED',
                        scheduled_date TEXT,
                        scheduled_time TEXT,
                        notes TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        completed_at INTEGER
                    )""",
                    """
                    CREATE TABLE study_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE RESTRICT,
                        start_timestamp INTEGER NOT NULL,
                        end_timestamp INTEGER,
                        active_duration_ms INTEGER NOT NULL DEFAULT 0 CHECK (active_duration_ms >= 0),
                        paused_duration_ms INTEGER NOT NULL DEFAULT 0 CHECK (paused_duration_ms >= 0),
                        target_duration_seconds INTEGER NOT NULL DEFAULT 0,
                        timer_mode TEXT NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT '',
                        recovered_after_interruption INTEGER NOT NULL DEFAULT 0,
                        last_checkpoint INTEGER NOT NULL
                    )""",
                    "CREATE INDEX idx_sessions_start ON study_sessions(start_timestamp)",
                    "CREATE INDEX idx_sessions_task ON study_sessions(task_id)",
                    "CREATE INDEX idx_tasks_scheduled ON tasks(scheduled_date)",
                    // At most one RUNNING/PAUSED session can exist, enforced by the database itself.
                    "CREATE UNIQUE INDEX one_active_session ON study_sessions((1)) WHERE status IN ('RUNNING','PAUSED')",
                    "CREATE TABLE app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)"
            ),
            // v2: IELTS-specific data. Old free-form categories are folded into the fixed list.
            List.of(
                    "ALTER TABLE study_sessions ADD COLUMN preset TEXT",
                    "UPDATE tasks SET skill = 'VOCABULARY' WHERE skill IN ('GRAMMAR', 'GENERAL', 'REVIEW')",
                    """
                    CREATE TABLE session_laps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id INTEGER NOT NULL REFERENCES study_sessions(id) ON DELETE CASCADE,
                        label TEXT NOT NULL,
                        at_active_ms INTEGER NOT NULL CHECK (at_active_ms >= 0)
                    )""",
                    """
                    CREATE TABLE practice_results (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        test_date TEXT NOT NULL,
                        source TEXT NOT NULL DEFAULT '',
                        skill TEXT NOT NULL,
                        raw_score INTEGER CHECK (raw_score IS NULL OR raw_score BETWEEN 0 AND 40),
                        band REAL NOT NULL CHECK (band BETWEEN 0 AND 9),
                        seconds_taken INTEGER,
                        session_id INTEGER REFERENCES study_sessions(id) ON DELETE SET NULL,
                        note TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL
                    )""",
                    "CREATE INDEX idx_practice_skill_date ON practice_results(skill, test_date)",
                    """
                    CREATE TABLE mistakes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        logged_at INTEGER NOT NULL,
                        skill TEXT NOT NULL,
                        question_type TEXT NOT NULL,
                        reason TEXT NOT NULL DEFAULT '',
                        marks_lost INTEGER NOT NULL DEFAULT 1 CHECK (marks_lost BETWEEN 1 AND 40),
                        source TEXT NOT NULL DEFAULT '',
                        practice_result_id INTEGER REFERENCES practice_results(id) ON DELETE SET NULL,
                        review_due TEXT,
                        review_status TEXT NOT NULL DEFAULT 'NONE',
                        review_task_id INTEGER REFERENCES tasks(id) ON DELETE SET NULL
                    )""",
                    "CREATE INDEX idx_mistakes_review ON mistakes(review_status, review_due)",
                    """
                    CREATE TABLE essays (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        created_at INTEGER NOT NULL,
                        task_type TEXT NOT NULL,
                        title TEXT NOT NULL DEFAULT '',
                        body TEXT NOT NULL DEFAULT '',
                        word_count INTEGER NOT NULL DEFAULT 0,
                        seconds_taken INTEGER,
                        task_response REAL, coherence REAL, lexical REAL, grammar REAL,
                        note TEXT NOT NULL DEFAULT ''
                    )""",
                    """
                    CREATE TABLE recordings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        created_at INTEGER NOT NULL,
                        part TEXT NOT NULL,
                        location TEXT NOT NULL,
                        note TEXT NOT NULL DEFAULT ''
                    )""",
                    "CREATE TABLE weekly_reviews (week_start TEXT PRIMARY KEY, answer TEXT NOT NULL, saved_at INTEGER NOT NULL)"
            )
    );

    private final Path file;
    private final String url;

    public Database(Path file) {
        this.file = file.toAbsolutePath();
        this.url = "jdbc:sqlite:" + this.file;
    }

    /** Default location: ~/.ielts-study-tracker/tracker.db (override the folder with -Dielts.tracker.home=...). */
    public static Path defaultPath() {
        String override = System.getProperty("ielts.tracker.home");
        Path dir = override != null ? Path.of(override) : Path.of(System.getProperty("user.home"), ".ielts-study-tracker");
        return dir.resolve("tracker.db");
    }

    public Path file() { return file; }

    public Connection open() {
        try {
            Files.createDirectories(file.getParent());
            Connection c = DriverManager.getConnection(url);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA busy_timeout = 5000");
            }
            return c;
        } catch (SQLException | IOException e) {
            throw new DataAccessException("Cannot open the database at " + file + ": " + e.getMessage(), e);
        }
    }

    /** Runs the work in one transaction; rolls back on any failure. */
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    public <T> T query(SqlWork<T> work) {
        try (Connection c = open()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    /** Applies any migrations newer than the file's schema version. Safe to call on every start. */
    public void migrate() {
        try (Connection c = open()) {
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode = WAL");
            }
            int version = userVersion(c);
            if (version > MIGRATIONS.size()) {
                throw new DataAccessException("This database was created by a newer version of the application (schema "
                        + version + "). Please update the application.");
            }
            for (int v = version; v < MIGRATIONS.size(); v++) {
                c.setAutoCommit(false);
                try (Statement s = c.createStatement()) {
                    for (String sql : MIGRATIONS.get(v)) s.execute(sql);
                    s.execute("PRAGMA user_version = " + (v + 1));
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not initialise the database: " + e.getMessage(), e);
        }
    }

    public int schemaVersion() {
        return query(Database::userVersion);
    }

    private static int userVersion(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); var r = s.executeQuery("PRAGMA user_version")) {
            return r.next() ? r.getInt(1) : 0;
        }
    }

    /** Writes a consistent copy of the live database. Refuses to overwrite an existing file. */
    public void backupTo(Path target) {
        if (Files.exists(target)) {
            throw new DataAccessException("A file already exists at " + target + ". Choose a new file name; backups are never overwritten.");
        }
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("VACUUM INTO ?")) {
            p.setString(1, target.toAbsolutePath().toString());
            p.execute();
        } catch (SQLException e) {
            throw new DataAccessException("Backup failed: " + e.getMessage(), e);
        }
    }
}
