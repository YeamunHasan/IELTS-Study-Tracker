package com.ielts.tracker.repository;

import com.ielts.tracker.model.SessionStatus;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.model.TimerMode;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SessionRepository {

    /** History filter. Null fields are ignored; times are epoch millis, from inclusive and to exclusive. */
    public record Filter(Long fromMillis, Long toMillis, Skill skill, Long taskId, SessionStatus status, TimerMode mode) {
        public static Filter none() { return new Filter(null, null, null, null, null, null); }
    }

    private static final String SELECT = """
            SELECT s.*, t.title AS task_title, t.skill AS task_skill
            FROM study_sessions s JOIN tasks t ON t.id = s.task_id
            """;

    private final Database db;

    public SessionRepository(Database db) { this.db = db; }

    /** Creates the open (RUNNING) row for a new session. Fails if another session is still open. */
    public long insertActive(long taskId, Instant start, TimerMode mode, long targetSeconds) {
        return insertActive(taskId, start, mode, targetSeconds, null);
    }

    public long insertActive(long taskId, Instant start, TimerMode mode, long targetSeconds, String preset) {
        return db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO study_sessions(task_id, start_timestamp, target_duration_seconds, timer_mode, status, last_checkpoint, preset)
                    VALUES(?,?,?,?, 'RUNNING', ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
                p.setLong(1, taskId);
                p.setLong(2, start.toEpochMilli());
                p.setLong(3, targetSeconds);
                p.setString(4, mode.name());
                p.setLong(5, start.toEpochMilli());
                p.setString(6, preset);
                p.executeUpdate();
                try (ResultSet k = p.getGeneratedKeys()) {
                    k.next();
                    return k.getLong(1);
                }
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                    throw new IllegalStateException("Another study session is already active.", e);
                }
                throw e;
            }
        });
    }

    /** Saves a session typed in by hand (study done away from the laptop). It is stored already COMPLETED, in MANUAL mode. */
    public long insertManual(long taskId, Instant start, long activeMillis, String notes) {
        if (activeMillis <= 0) throw new IllegalArgumentException("Enter a duration greater than zero.");
        return db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO study_sessions(task_id, start_timestamp, end_timestamp, active_duration_ms, target_duration_seconds,
                                               timer_mode, status, notes, last_checkpoint)
                    VALUES(?,?,?,?,0,'MANUAL','COMPLETED',?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                long end = start.toEpochMilli() + activeMillis;
                p.setLong(1, taskId);
                p.setLong(2, start.toEpochMilli());
                p.setLong(3, end);
                p.setLong(4, activeMillis);
                p.setString(5, notes == null ? "" : notes);
                p.setLong(6, end);
                p.executeUpdate();
                try (ResultSet k = p.getGeneratedKeys()) {
                    k.next();
                    return k.getLong(1);
                }
            }
        });
    }

    /** A lap is a labelled moment in a session, e.g. "Passage 2" at 38:10 of focused time. */
    public record Lap(String label, long atActiveMillis) {}

    public void addLap(long sessionId, String label, long atActiveMillis) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO session_laps(session_id, label, at_active_ms) VALUES(?,?,?)")) {
                p.setLong(1, sessionId);
                p.setString(2, label);
                p.setLong(3, Math.max(0, atActiveMillis));
                p.executeUpdate();
                return null;
            }
        });
    }

    public List<Lap> laps(long sessionId) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT label, at_active_ms FROM session_laps WHERE session_id=? ORDER BY at_active_ms, id")) {
                p.setLong(1, sessionId);
                List<Lap> out = new ArrayList<>();
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) out.add(new Lap(r.getString(1), r.getLong(2)));
                }
                return out;
            }
        });
    }

    /** Persists the live state of an open session. Called on every state change and periodically while running. */
    public void checkpoint(long id, SessionStatus status, long activeMillis, long pausedMillis, Instant now, boolean recovered) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    UPDATE study_sessions SET status=?, active_duration_ms=?, paused_duration_ms=?, last_checkpoint=?,
                                              recovered_after_interruption=?
                    WHERE id=? AND status IN ('RUNNING','PAUSED')""")) {
                p.setString(1, status.name());
                p.setLong(2, activeMillis);
                p.setLong(3, pausedMillis);
                p.setLong(4, now.toEpochMilli());
                p.setInt(5, recovered ? 1 : 0);
                p.setLong(6, id);
                p.executeUpdate();
                return null;
            }
        });
    }

    /** Closes a session. Only open sessions can be closed, so a session can never be finalised twice. */
    public boolean close(long id, SessionStatus finalStatus, Instant end, long activeMillis, long pausedMillis,
                         String notes, boolean recovered) {
        return db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    UPDATE study_sessions SET status=?, end_timestamp=?, active_duration_ms=?, paused_duration_ms=?, notes=?,
                                              recovered_after_interruption=?, last_checkpoint=?
                    WHERE id=? AND status IN ('RUNNING','PAUSED')""")) {
                p.setString(1, finalStatus.name());
                if (end == null) p.setNull(2, Types.INTEGER); else p.setLong(2, end.toEpochMilli());
                p.setLong(3, activeMillis);
                p.setLong(4, pausedMillis);
                p.setString(5, notes == null ? "" : notes);
                p.setInt(6, recovered ? 1 : 0);
                p.setLong(7, end == null ? 0 : end.toEpochMilli());
                p.setLong(8, id);
                return p.executeUpdate() == 1;
            }
        });
    }

    public Optional<StudySession> find(long id) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement(SELECT + "WHERE s.id = ?")) {
                p.setLong(1, id);
                try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(map(r)) : Optional.<StudySession>empty(); }
            }
        });
    }

    /** Sessions left RUNNING or PAUSED, e.g. after a crash or power loss. */
    public List<StudySession> findOpen() {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement(SELECT + "WHERE s.status IN ('RUNNING','PAUSED') ORDER BY s.id")) {
                return read(p);
            }
        });
    }

    /** Sessions that count towards totals, starting in [fromMillis, toMillis). */
    public List<StudySession> listCounted(long fromMillis, long toMillis) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement(SELECT
                    + "WHERE s.status IN ('COMPLETED','INTERRUPTED') AND s.start_timestamp >= ? AND s.start_timestamp < ? "
                    + "ORDER BY s.start_timestamp")) {
                p.setLong(1, fromMillis);
                p.setLong(2, toMillis);
                return read(p);
            }
        });
    }

    /** Closed sessions (any final status), newest first, for the history screen. */
    public List<StudySession> list(Filter f) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE s.status NOT IN ('RUNNING','PAUSED')");
        List<Object> args = new ArrayList<>();
        if (f.fromMillis() != null) { sql.append(" AND s.start_timestamp >= ?"); args.add(f.fromMillis()); }
        if (f.toMillis() != null) { sql.append(" AND s.start_timestamp < ?"); args.add(f.toMillis()); }
        if (f.skill() != null) { sql.append(" AND t.skill = ?"); args.add(f.skill().name()); }
        if (f.taskId() != null) { sql.append(" AND s.task_id = ?"); args.add(f.taskId()); }
        if (f.status() != null) { sql.append(" AND s.status = ?"); args.add(f.status().name()); }
        if (f.mode() != null) { sql.append(" AND s.timer_mode = ?"); args.add(f.mode().name()); }
        sql.append(" ORDER BY s.start_timestamp DESC, s.id DESC");
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) p.setObject(i + 1, args.get(i));
                return read(p);
            }
        });
    }

    public void updateNotes(long id, String notes) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("UPDATE study_sessions SET notes=? WHERE id=?")) {
                p.setString(1, notes == null ? "" : notes);
                p.setLong(2, id);
                p.executeUpdate();
                return null;
            }
        });
    }

    /** Deletes a closed session. Open sessions are refused. */
    public boolean delete(long id) {
        return db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "DELETE FROM study_sessions WHERE id=? AND status NOT IN ('RUNNING','PAUSED')")) {
                p.setLong(1, id);
                return p.executeUpdate() == 1;
            }
        });
    }

    private static List<StudySession> read(PreparedStatement p) throws SQLException {
        List<StudySession> out = new ArrayList<>();
        try (ResultSet r = p.executeQuery()) {
            while (r.next()) out.add(map(r));
        }
        return out;
    }

    private static StudySession map(ResultSet r) throws SQLException {
        long end = r.getLong("end_timestamp");
        Instant endAt = r.wasNull() ? null : Instant.ofEpochMilli(end);
        return new StudySession(
                r.getLong("id"), r.getLong("task_id"), r.getString("task_title"), Skill.valueOf(r.getString("task_skill")),
                Instant.ofEpochMilli(r.getLong("start_timestamp")), endAt,
                r.getLong("active_duration_ms"), r.getLong("paused_duration_ms"), r.getLong("target_duration_seconds"),
                TimerMode.valueOf(r.getString("timer_mode")), SessionStatus.valueOf(r.getString("status")),
                r.getString("notes"), r.getInt("recovered_after_interruption") == 1,
                Instant.ofEpochMilli(r.getLong("last_checkpoint")), r.getString("preset"));
    }
}
