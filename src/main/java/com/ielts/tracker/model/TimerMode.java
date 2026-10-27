package com.ielts.tracker.model;

public enum TimerMode {
    COUNTDOWN("Countdown"), STOPWATCH("Stopwatch"),
    /** Typed in by hand for study done away from the laptop. Never produced by the timer. */
    MANUAL("Manual");

    private final String label;
    TimerMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
