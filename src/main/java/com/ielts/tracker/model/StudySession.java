package com.ielts.tracker.model;

import java.time.Instant;

/** A persisted study session joined with its task's title and skill. {@code preset} is a {@link MockPreset} id or null. */
public record StudySession(
        long id,
        long taskId,
        String taskTitle,
        Skill skill,
        Instant start,
        Instant end,
        long activeMillis,
        long pausedMillis,
        long targetSeconds,
        TimerMode mode,
        SessionStatus status,
        String notes,
        boolean recovered,
        Instant lastCheckpoint,
        String preset) {

    /** Focused time beyond the countdown target, if any. */
    public long overrunMillis() {
        return mode == TimerMode.COUNTDOWN ? Math.max(0, activeMillis - targetSeconds * 1000) : 0;
    }

    public boolean isManual() { return mode == TimerMode.MANUAL; }
}
