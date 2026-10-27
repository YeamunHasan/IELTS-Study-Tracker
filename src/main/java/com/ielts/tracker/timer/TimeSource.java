package com.ielts.tracker.timer;

import java.time.Instant;

/**
 * The only place the application reads the clock. {@link #nanoTime()} is monotonic and measures elapsed
 * time; {@link #now()} is wall-clock and is used for timestamps and calendar dates. Tests inject a fake.
 */
public interface TimeSource {
    long nanoTime();

    Instant now();

    TimeSource SYSTEM = new TimeSource() {
        @Override public long nanoTime() { return System.nanoTime(); }
        @Override public Instant now() { return Instant.now(); }
    };
}
