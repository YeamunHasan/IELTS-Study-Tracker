package com.ielts.tracker.repository;

import com.ielts.tracker.model.Priority;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.model.TaskSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TaskRepository {

    private static final String SUMMARY_SQL = """
            SELECT t.*, COALESCE(a.ms, 0) AS focused_ms, COALESCE(a.cnt, 0) AS session_count, a.last_start
            FROM tasks t
            LEFT JOIN (SELECT task_id, SUM(active_duration_ms) AS ms, COUNT(*) AS cnt, MAX(start_timestamp) AS last_start
                       FROM study_sessions WHERE status IN ('COMPLETED','INTERRUPTED') GROUP BY task_id) a
              ON a.task_id = t.id
            """;

    private final Database db;

    public TaskRepository(Database db) { this.db = db; }

    /** Inserts a task; the id and timestamps of the argument are ignored. Returns the new id. */
    public long insert(StudyTask t, Instant now) {
        return db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO tasks(title, description, skill, category, target_duration_seconds, priority, status,
                                      scheduled_date, scheduled_time, notes, created_at, updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                bind(p, t);
                p.setLong(11, now.toEpochMilli());
                p.setLong(12, now.toEpochMilli());
                p.executeUpdate();
                try (ResultSet keys = p.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        });
    }

    public void update(StudyTask t, Instant now) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    UPDATE tasks SET title=?, description=?, skill=?, category=?, target_duration_seconds=?, priority=?,
                                     status=?, scheduled_date=?, scheduled_time=?, notes=?, updated_at=?, completed_at=?
                    WHERE id=?""")) {
                bind(p, t);
                p.setLong(11, now.toEpochMilli());
                if (t.status() == TaskStatus.COMPLETED) {
                    p.setLong(12, (t.completedAt() != null ? t.completedAt() : now).toEpochMilli());
                } else {
                    p.setNull(12, Types.INTEGER);
                }
                p.setLong(13, t.id());
                p.executeUpdate();
                return null;
            }
        });
    }

    public void setStatus(long id, TaskStatus status, Instant now) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "UPDATE tasks SET status=?, updated_at=?, completed_at=? WHERE id=?")) {
                p.setString(1, status.name());
                p.setLong(2, now.toEpochMilli());
                if (status == TaskStatus.COMPLETED) p.setLong(3, now.toEpochMilli()); else p.setNull(3, Types.INTEGER);
                p.setLong(4, id);
                p.executeUpdate();
                return null;
            }
        });
    }

    public void reschedule(long id, LocalDate date, Instant now) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("UPDATE tasks SET scheduled_date=?, updated_at=? WHERE id=?")) {
                p.setString(1, date == null ? null : date.toString());
                p.setLong(2, now.toEpochMilli());
                p.setLong(3, id);
                p.executeUpdate();
                return null;
            }
        });
    }

    /**
     * Deletes a task that has no sessions. If it has sessions it is archived instead, so history and reports
     * keep their data. Returns true if the task was archived rather than deleted.
     */
    public boolean deleteOrArchive(long id, Instant now) {
        return db.inTransaction(c -> {
            boolean hasSessions;
            try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM study_sessions WHERE task_id=? LIMIT 1")) {
                p.setLong(1, id);
                try (ResultSet r = p.executeQuery()) { hasSessions = r.next(); }
            }
            if (hasSessions) {
                try (PreparedStatement p = c.prepareStatement("UPDATE tasks SET status='ARCHIVED', updated_at=? WHERE id=?")) {
                    p.setLong(1, now.toEpochMilli());
                    p.setLong(2, id);
                    p.executeUpdate();
                }
                return true;
            }
            try (PreparedStatement p = c.prepareStatement("DELETE FROM tasks WHERE id=?")) {
                p.setLong(1, id);
                p.executeUpdate();
            }
            return false;
        });
    }

    public Optional<StudyTask> find(long id) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT * FROM tasks WHERE id=?")) {
                p.setLong(1, id);
                try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(map(r)) : Optional.<StudyTask>empty(); }
            }
        });
    }

    /** All non-archived tasks, planned first, then by schedule and newest first. */
    public List<TaskSummary> listSummaries() {
        return querySummaries("WHERE t.status <> 'ARCHIVED' ORDER BY CASE t.status WHEN 'PLANNED' THEN 0 ELSE 1 END, "
                + "t.scheduled_date IS NULL, t.scheduled_date, t.scheduled_time, t.id DESC");
    }

    /** Non-archived tasks scheduled in [from, to] (inclusive), ordered chronologically. */
    public List<TaskSummary> listScheduled(LocalDate from, LocalDate to) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement(SUMMARY_SQL
                    + "WHERE t.status <> 'ARCHIVED' AND t.scheduled_date BETWEEN ? AND ? "
                    + "ORDER BY t.scheduled_date, t.scheduled_time IS NULL, t.scheduled_time, t.id")) {
                p.setString(1, from.toString());
                p.setString(2, to.toString());
                return readSummaries(p);
            }
        });
    }

    /** Planned tasks scheduled before the given date. */
    public List<TaskSummary> listOverdue(LocalDate before) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement(SUMMARY_SQL
                    + "WHERE t.status = 'PLANNED' AND t.scheduled_date < ? ORDER BY t.scheduled_date, t.scheduled_time")) {
                p.setString(1, before.toString());
                return readSummaries(p);
            }
        });
    }

    private List<TaskSummary> querySummaries(String tail) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement(SUMMARY_SQL + tail)) {
                return readSummaries(p);
            }
        });
    }

    private static List<TaskSummary> readSummaries(PreparedStatement p) throws SQLException {
        List<TaskSummary> out = new ArrayList<>();
        try (ResultSet r = p.executeQuery()) {
            while (r.next()) {
                long last = r.getLong("last_start");
                Instant lastStudied = r.wasNull() ? null : Instant.ofEpochMilli(last);
                out.add(new TaskSummary(map(r), r.getLong("focused_ms"), r.getInt("session_count"), lastStudied));
            }
        }
        return out;
    }

    private static void bind(PreparedStatement p, StudyTask t) throws SQLException {
        p.setString(1, t.title().trim());
        p.setString(2, nullToEmpty(t.description()));
        p.setString(3, t.skill().name());
        p.setString(4, nullToEmpty(t.category()));
        p.setLong(5, t.targetSeconds());
        p.setString(6, t.priority().name());
        p.setString(7, t.status().name());
        p.setString(8, t.scheduledDate() == null ? null : t.scheduledDate().toString());
        p.setString(9, t.scheduledTime() == null ? null : t.scheduledTime().toString());
        p.setString(10, nullToEmpty(t.notes()));
    }

    private static StudyTask map(ResultSet r) throws SQLException {
        String date = r.getString("scheduled_date");
        String time = r.getString("scheduled_time");
        long completed = r.getLong("completed_at");
        Instant completedAt = r.wasNull() ? null : Instant.ofEpochMilli(completed);
        return new StudyTask(
                r.getLong("id"), r.getString("title"), r.getString("description"),
                Skill.valueOf(r.getString("skill")), r.getString("category"),
                r.getLong("target_duration_seconds"),
                Priority.valueOf(r.getString("priority")), TaskStatus.valueOf(r.getString("status")),
                date == null ? null : LocalDate.parse(date), time == null ? null : LocalTime.parse(time),
                r.getString("notes"),
                Instant.ofEpochMilli(r.getLong("created_at")), Instant.ofEpochMilli(r.getLong("updated_at")), completedAt);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
