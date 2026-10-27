package com.ielts.tracker.repository;

import com.ielts.tracker.model.PracticeResult;
import com.ielts.tracker.model.Skill;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PracticeRepository {
    private final Database db;

    public PracticeRepository(Database db) { this.db = db; }

    public long insert(PracticeResult r, Instant now) {
        return db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO practice_results(test_date, source, skill, raw_score, band, seconds_taken, session_id, note, created_at)
                    VALUES(?,?,?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, r.testDate().toString());
                p.setString(2, r.source() == null ? "" : r.source().trim());
                p.setString(3, r.skill().name());
                if (r.rawScore() == null) p.setNull(4, Types.INTEGER); else p.setInt(4, r.rawScore());
                p.setDouble(5, r.band());
                if (r.secondsTaken() == null) p.setNull(6, Types.INTEGER); else p.setLong(6, r.secondsTaken());
                if (r.sessionId() == null) p.setNull(7, Types.INTEGER); else p.setLong(7, r.sessionId());
                p.setString(8, r.note() == null ? "" : r.note());
                p.setLong(9, now.toEpochMilli());
                p.executeUpdate();
                try (ResultSet k = p.getGeneratedKeys()) {
                    k.next();
                    return k.getLong(1);
                }
            }
        });
    }

    public void delete(long id) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM practice_results WHERE id=?")) {
                p.setLong(1, id);
                p.executeUpdate();
                return null;
            }
        });
    }

    /** Oldest first, so the list can be plotted directly. */
    public List<PracticeResult> list(Skill skill) {
        return query(skill == null ? "" : "WHERE skill = ?", skill == null ? null : skill.name(), "ORDER BY test_date, id");
    }

    /** Most recent result for the skill: latest test date, then latest entry. */
    public Optional<PracticeResult> latest(Skill skill) {
        List<PracticeResult> l = query("WHERE skill = ?", skill.name(), "ORDER BY test_date DESC, id DESC LIMIT 1");
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    private List<PracticeResult> query(String where, String arg, String order) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT * FROM practice_results " + where + " " + order)) {
                if (arg != null) p.setString(1, arg);
                List<PracticeResult> out = new ArrayList<>();
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) out.add(map(r));
                }
                return out;
            }
        });
    }

    private static PracticeResult map(ResultSet r) throws SQLException {
        int raw = r.getInt("raw_score");
        Integer rawScore = r.wasNull() ? null : raw;
        long secs = r.getLong("seconds_taken");
        Long seconds = r.wasNull() ? null : secs;
        long sid = r.getLong("session_id");
        Long sessionId = r.wasNull() ? null : sid;
        return new PracticeResult(r.getLong("id"), LocalDate.parse(r.getString("test_date")), r.getString("source"),
                Skill.valueOf(r.getString("skill")), rawScore, r.getDouble("band"), seconds, sessionId, r.getString("note"));
    }
}
