package com.ielts.tracker.service;

import com.ielts.tracker.model.ExamType;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.util.Bands;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.repository.SettingsRepository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/** Typed access to the key/value settings table, with defaults and validation. */
public final class AppSettings {
    public static final int DEFAULT_DAILY_GOAL_MINUTES = 180;
    public static final int DEFAULT_STREAK_MINUTES = 30;
    public static final int DEFAULT_WEEKLY_GOAL_MINUTES = 1260;
    public static final int DEFAULT_SESSION_MINUTES = 25;

    private static final String EXAM_DATE = "exam_date";
    private static final String EXAM_TIME = "exam_time";
    private static final String EXAM_TYPE = "exam_type";
    private static final String EXAM_NOTES = "exam_notes";
    private static final String DAILY_GOAL = "daily_goal_minutes";
    private static final String WEEKLY_GOAL = "weekly_goal_minutes";
    private static final String STREAK_MIN = "streak_minutes";
    private static final String DEFAULT_MODE = "default_timer_mode";
    private static final String DEFAULT_MINUTES = "default_session_minutes";

    public static final double DEFAULT_TARGET_BAND = 7.0;
    public static final int DEFAULT_IDLE_MINUTES = 10;

    private static final String BACKUP_FOLDER = "backup_folder";
    private static final String IDLE_MINUTES = "idle_minutes";

    private final SettingsRepository repo;

    public AppSettings(SettingsRepository repo) { this.repo = repo; }

    public ExamInfo exam() {
        LocalDate date = null;
        LocalTime time = null;
        try {
            date = repo.get(EXAM_DATE).filter(s -> !s.isBlank()).map(LocalDate::parse).orElse(null);
            time = repo.get(EXAM_TIME).filter(s -> !s.isBlank()).map(LocalTime::parse).orElse(null);
        } catch (DateTimeParseException ignored) {
            // A damaged value is treated as "not set" rather than crashing the dashboard.
        }
        ExamType type = repo.get(EXAM_TYPE).map(s -> enumOr(ExamType.class, s, ExamType.ACADEMIC)).orElse(ExamType.ACADEMIC);
        return new ExamInfo(date, time, type, repo.get(EXAM_NOTES).orElse(""));
    }

    public void saveExam(ExamInfo e) {
        repo.set(EXAM_DATE, e.date() == null ? "" : e.date().toString());
        repo.set(EXAM_TIME, e.date() == null || e.time() == null ? "" : e.time().toString());
        repo.set(EXAM_TYPE, e.type().name());
        repo.set(EXAM_NOTES, e.notes() == null ? "" : e.notes());
    }

    public void clearExam() { saveExam(ExamInfo.none()); }

    public int dailyGoalMinutes() { return intValue(DAILY_GOAL, DEFAULT_DAILY_GOAL_MINUTES); }
    public int weeklyGoalMinutes() { return intValue(WEEKLY_GOAL, DEFAULT_WEEKLY_GOAL_MINUTES); }
    public int streakMinutes() { return intValue(STREAK_MIN, DEFAULT_STREAK_MINUTES); }
    public int defaultSessionMinutes() { return intValue(DEFAULT_MINUTES, DEFAULT_SESSION_MINUTES); }

    public TimerMode defaultMode() {
        return repo.get(DEFAULT_MODE).map(s -> enumOr(TimerMode.class, s, TimerMode.COUNTDOWN)).orElse(TimerMode.COUNTDOWN);
    }

    public void saveStudyPreferences(int dailyGoal, int weeklyGoal, int streakMinutes, TimerMode mode, int defaultMinutes) {
        requireRange("Daily goal", dailyGoal, 1, 1440);
        requireRange("Weekly goal", weeklyGoal, 1, 10080);
        requireRange("Streak threshold", streakMinutes, 1, 1440);
        requireRange("Default session length", defaultMinutes, 1, 1440);
        repo.set(DAILY_GOAL, Integer.toString(dailyGoal));
        repo.set(WEEKLY_GOAL, Integer.toString(weeklyGoal));
        repo.set(STREAK_MIN, Integer.toString(streakMinutes));
        repo.set(DEFAULT_MODE, mode.name());
        repo.set(DEFAULT_MINUTES, Integer.toString(defaultMinutes));
    }

    /** Target band for one of the four skills (7.0 until changed). */
    public double targetBand(Skill skill) {
        return repo.get("target_band_" + skill.name().toLowerCase()).map(s -> {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return DEFAULT_TARGET_BAND; }
        }).filter(Bands::isValidBand).orElse(DEFAULT_TARGET_BAND);
    }

    public void saveTargetBand(Skill skill, double band) {
        if (!skill.hasBand()) throw new IllegalArgumentException(skill.label() + " has no band target.");
        if (!Bands.isValidBand(band)) throw new IllegalArgumentException("A band must be between 0 and 9 in steps of 0.5.");
        repo.set("target_band_" + skill.name().toLowerCase(), Double.toString(band));
    }

    /** Minutes without keyboard or mouse input before "still studying?" is asked. */
    public int idleMinutes() { return intValue(IDLE_MINUTES, DEFAULT_IDLE_MINUTES); }

    public void saveIdleMinutes(int minutes) {
        requireRange("Idle check", minutes, 1, 240);
        repo.set(IDLE_MINUTES, Integer.toString(minutes));
    }

    public java.util.Optional<Path> backupFolder() {
        return repo.get(BACKUP_FOLDER).filter(s -> !s.isBlank()).map(Path::of);
    }

    public void saveBackupFolder(Path folder) { repo.set(BACKUP_FOLDER, folder == null ? "" : folder.toString()); }

    private int intValue(String key, int fallback) {
        return repo.get(key).map(s -> {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
        }).filter(v -> v > 0).orElse(fallback);
    }

    private static void requireRange(String name, int v, int min, int max) {
        if (v < min || v > max) throw new IllegalArgumentException(name + " must be between " + min + " and " + max + " minutes.");
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String value, E fallback) {
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException e) { return fallback; }
    }
}
