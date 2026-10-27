package com.ielts.tracker.repository;

import com.ielts.tracker.model.Essay;
import com.ielts.tracker.model.Recording;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Essays and speaking recordings. */
public final class WritingSpeakingRepository {
    private final Database db;

    public WritingSpeakingRepository(Database db) { this.db = db; }

    // ---- essays ----

    public long saveEssay(Essay e, Instant now) {
        return db.inTransaction(c -> {
            if (e.id() == 0) {
                try (PreparedStatement p = c.prepareStatement("""
                        INSERT INTO essays(created_at, task_type, title, body, word_count, seconds_taken, task_response, coherence, lexical, grammar, note)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                    p.setLong(1, now.toEpochMilli());
                    bindEssay(p, e, 2);
                    p.executeUpdate();
                    try (ResultSet k = p.getGeneratedKeys()) {
                        k.next();
                        return k.getLong(1);
                    }
                }
            }
            try (PreparedStatement p = c.prepareStatement("""
                    UPDATE essays SET task_type=?, title=?, body=?, word_count=?, seconds_taken=?, task_response=?, coherence=?, lexical=?, grammar=?, note=?
                    WHERE id=?""")) {
                bindEssay(p, e, 1);
                p.setLong(11, e.id());
                p.executeUpdate();
                return e.id();
            }
        });
    }

    private static void bindEssay(PreparedStatement p, Essay e, int i) throws SQLException {
        p.setString(i, e.taskType());
        p.setString(i + 1, e.title() == null ? "" : e.title());
        p.setString(i + 2, e.body() == null ? "" : e.body());
        p.setInt(i + 3, e.wordCount());
        if (e.secondsTaken() == null) p.setNull(i + 4, Types.INTEGER); else p.setLong(i + 4, e.secondsTaken());
        setBand(p, i + 5, e.taskResponse());
        setBand(p, i + 6, e.coherence());
        setBand(p, i + 7, e.lexical());
        setBand(p, i + 8, e.grammar());
        p.setString(i + 9, e.note() == null ? "" : e.note());
    }

    private static void setBand(PreparedStatement p, int i, Double v) throws SQLException {
        if (v == null) p.setNull(i, Types.REAL); else p.setDouble(i, v);
    }

    public List<Essay> essays() {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT * FROM essays ORDER BY created_at DESC, id DESC");
                 ResultSet r = p.executeQuery()) {
                List<Essay> out = new ArrayList<>();
                while (r.next()) out.add(mapEssay(r));
                return out;
            }
        });
    }

    public Optional<Essay> essay(long id) {
        return essays().stream().filter(e -> e.id() == id).findFirst();
    }

    public void deleteEssay(long id) { delete("essays", id); }

    private static Essay mapEssay(ResultSet r) throws SQLException {
        long secs = r.getLong("seconds_taken");
        Long seconds = r.wasNull() ? null : secs;
        return new Essay(r.getLong("id"), Instant.ofEpochMilli(r.getLong("created_at")), r.getString("task_type"), r.getString("title"),
                r.getString("body"), r.getInt("word_count"), seconds, band(r, "task_response"), band(r, "coherence"),
                band(r, "lexical"), band(r, "grammar"), r.getString("note"));
    }

    private static Double band(ResultSet r, String col) throws SQLException {
        double v = r.getDouble(col);
        return r.wasNull() ? null : v;
    }

    // ---- recordings ----

    public long addRecording(Recording rec, Instant now) {
        return db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO recordings(created_at, part, location, note) VALUES(?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setLong(1, now.toEpochMilli());
                p.setString(2, rec.part());
                p.setString(3, rec.location().trim());
                p.setString(4, rec.note() == null ? "" : rec.note());
                p.executeUpdate();
                try (ResultSet k = p.getGeneratedKeys()) {
                    k.next();
                    return k.getLong(1);
                }
            }
        });
    }

    public List<Recording> recordings() {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT * FROM recordings ORDER BY created_at DESC, id DESC");
                 ResultSet r = p.executeQuery()) {
                List<Recording> out = new ArrayList<>();
                while (r.next()) {
                    out.add(new Recording(r.getLong("id"), Instant.ofEpochMilli(r.getLong("created_at")), r.getString("part"),
                            r.getString("location"), r.getString("note")));
                }
                return out;
            }
        });
    }

    public void deleteRecording(long id) { delete("recordings", id); }

    private void delete(String table, long id) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM " + table + " WHERE id=?")) {
                p.setLong(1, id);
                p.executeUpdate();
                return null;
            }
        });
    }
}
