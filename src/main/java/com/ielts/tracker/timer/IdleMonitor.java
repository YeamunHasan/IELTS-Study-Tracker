package com.ielts.tracker.timer;

import java.time.Duration;
import java.util.OptionalLong;

/**
 * Decides when to ask "still studying?". It is fed keyboard and mouse activity from the global input hook and
 * never guesses: it only reports that no input has been seen for the threshold, once per quiet spell.
 */
public final class IdleMonitor {
    private final TimeSource time;
    private final long thresholdNanos;
    private volatile long lastInputNanos;
    private volatile boolean asked;

    public IdleMonitor(TimeSource time, Duration threshold) {
        this.time = time;
        this.thresholdNanos = threshold.toNanos();
        this.lastInputNanos = time.nanoTime();
    }

    /** Called for every key press or mouse event (from any thread), and when a session starts or resumes. */
    public void onInput() {
        lastInputNanos = time.nanoTime();
        asked = false;
    }

    /** If the user has been idle long enough and has not been asked yet, returns how long they have been idle. */
    public OptionalLong poll(boolean sessionRunning) {
        if (!sessionRunning || asked) return OptionalLong.empty();
        long idle = time.nanoTime() - lastInputNanos;
        if (idle < thresholdNanos) return OptionalLong.empty();
        asked = true;
        return OptionalLong.of(idle / 1_000_000L);
    }
}
