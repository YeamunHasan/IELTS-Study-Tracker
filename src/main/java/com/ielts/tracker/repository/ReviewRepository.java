package com.ielts.tracker.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** The one-sentence answer written at each weekly review, keyed by the Monday of that week. */
public final class ReviewRepository {
    private final Database db;

    public ReviewRepository(Database db) { this.db = db; }

    public Optional<String> answer(LocalDate weekStart) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT answer FROM weekly_reviews WHERE week_start = ?")) {
                p.setString(1, weekStart.toString());
                try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(r.getString(1)) : Optional.<String>empty(); }
            }
        });
    }

    public void save(LocalDate weekStart, String answer, Instant now) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO weekly_reviews(week_start, answer, saved_at) VALUES(?,?,?)
                    ON CONFLICT(week_start) DO UPDATE SET answer = excluded.answer, saved_at = excluded.saved_at""")) {
                p.setString(1, weekStart.toString());
                p.setString(2, answer);
                p.setLong(3, now.toEpochMilli());
                p.executeUpdate();
                return null;
            }
        });
    }
}
