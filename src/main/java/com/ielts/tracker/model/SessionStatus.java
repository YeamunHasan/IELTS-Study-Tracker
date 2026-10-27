package com.ielts.tracker.model;

public enum SessionStatus {
    RUNNING("Running"), PAUSED("Paused"),
    COMPLETED("Completed"),
    /** Saved after an unexpected closure, with only the reliably recorded time. */
    INTERRUPTED("Interrupted"),
    CANCELLED("Cancelled");

    private final String label;
    SessionStatus(String label) { this.label = label; }

    /** Open sessions that were never closed properly (candidates for recovery). */
    public boolean isActive() { return this == RUNNING || this == PAUSED; }

    /** Only these sessions count towards focused-time totals. */
    public boolean countsTowardsTotals() { return this == COMPLETED || this == INTERRUPTED; }

    @Override public String toString() { return label; }
}
