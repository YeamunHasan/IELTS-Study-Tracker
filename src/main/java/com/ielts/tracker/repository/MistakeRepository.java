package com.ielts.tracker.repository;

import com.ielts.tracker.model.Mistake;
import com.ielts.tracker.model.ReviewStatus;
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

public final class MistakeRepository {

    /** Marks lost, grouped by skill and question type. */
    public record TypeCost(Skill skill, String questionType, int count, int marksLost) {}

    private final Database db;

    public MistakeRepository(Database db) { this.db = db; }

    public long insert(Mistake m) {
        return db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO mistakes(logged_at, skill, question_type, reason, marks_lost, source, practice_result_id,
                                         review_due, review_status)
                    VALUES(?,?,?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                p.setLong(1, m.loggedAt().toEpochMilli());
                p.setString(2, m.skill().name());
                p.setString(3, m.questionType());
                p.setString(4, m.reason() == null ? "" : m.reason().trim());
                p.setInt(5, m.marksLost());
                p.setString(6, m.source() == null ? "" : m.source());
                if (m.practiceResultId() == null) p.setNull(7, Types.INTEGER); else p.setLong(7, m.practiceResultId());
                p.setString(8, m.reviewDue() == null ? null : m.reviewDue().toString());
                p.setString(9, m.reviewStatus().name());
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
            try (PreparedStatement p = c.prepareStatement("DELETE FROM mistakes WHERE id=?")) {
                p.setLong(1, id);
                p.executeUpdate();
                return null;
            }
        });
    }

    /** Newest first. */
    public List<Mistake> list(Skill skill) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT * FROM mistakes " + (skill == null ? "" : "WHERE skill = ? ")
                    + "ORDER BY logged_at DESC, id DESC")) {
                if (skill != null) p.setString(1, skill.name());
                return read(p);
            }
        });
    }

    /** Suggested reviews whose due date has arrived. */
    public List<Mistake> dueReviews(LocalDate today) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT * FROM mistakes WHERE review_status = 'SUGGESTED' AND review_due <= ? ORDER BY review_due, skill, question_type, id")) {
                p.setString(1, today.toString());
                return read(p);
            }
        });
    }

    public void resolveReview(List<Long> ids, ReviewStatus status, Long taskId) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("UPDATE mistakes SET review_status=?, review_task_id=? WHERE id=?")) {
                for (long id : ids) {
                    p.setString(1, status.name());
                    if (taskId == null) p.setNull(2, Types.INTEGER); else p.setLong(2, taskId);
                    p.setLong(3, id);
                    p.addBatch();
                }
                p.executeBatch();
                return null;
            }
        });
    }

    /** Which question types cost the most marks, between the logged dates (inclusive), most costly first. */
    public List<TypeCost> costByType(Instant from, Instant toExclusive) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    SELECT skill, question_type, COUNT(*), SUM(marks_lost) FROM mistakes
                    WHERE logged_at >= ? AND logged_at < ?
                    GROUP BY skill, question_type ORDER BY SUM(marks_lost) DESC, COUNT(*) DESC, skill, question_type""")) {
                p.setLong(1, from.toEpochMilli());
                p.setLong(2, toExclusive.toEpochMilli());
                List<TypeCost> out = new ArrayList<>();
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) out.add(new TypeCost(Skill.valueOf(r.getString(1)), r.getString(2), r.getInt(3), r.getInt(4)));
                }
                return out;
            }
        });
    }

    private static List<Mistake> read(PreparedStatement p) throws SQLException {
        List<Mistake> out = new ArrayList<>();
        try (ResultSet r = p.executeQuery()) {
            while (r.next()) {
                long pr = r.getLong("practice_result_id");
                Long practice = r.wasNull() ? null : pr;
                long tk = r.getLong("review_task_id");
                Long task = r.wasNull() ? null : tk;
                String due = r.getString("review_due");
                out.add(new Mistake(r.getLong("id"), Instant.ofEpochMilli(r.getLong("logged_at")), Skill.valueOf(r.getString("skill")),
                        r.getString("question_type"), r.getString("reason"), r.getInt("marks_lost"), r.getString("source"), practice,
                        due == null ? null : LocalDate.parse(due), ReviewStatus.valueOf(r.getString("review_status")), task));
            }
        }
        return out;
    }
}
