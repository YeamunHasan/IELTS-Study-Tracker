package com.ielts.tracker.repository;

import java.sql.PreparedStatement;
import java.util.Optional;

public final class SettingsRepository {
    private final Database db;

    public SettingsRepository(Database db) { this.db = db; }

    public Optional<String> get(String key) {
        return db.query(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT value FROM app_settings WHERE key = ?")) {
                p.setString(1, key);
                try (var r = p.executeQuery()) {
                    return r.next() ? Optional.of(r.getString(1)) : Optional.<String>empty();
                }
            }
        });
    }

    public void set(String key, String value) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO app_settings(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                p.setString(1, key);
                p.setString(2, value);
                return p.executeUpdate();
            }
        });
    }

    public void remove(String key) {
        db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM app_settings WHERE key = ?")) {
                p.setString(1, key);
                return p.executeUpdate();
            }
        });
    }
}
