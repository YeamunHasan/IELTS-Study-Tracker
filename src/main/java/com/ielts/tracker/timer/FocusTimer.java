package com.ielts.tracker.timer;

import com.ielts.tracker.model.TimerMode;

import java.time.Instant;

/**
 * Pure state machine for one study session; no UI and no database.
 *
 * Elapsed time is always recomputed from the monotonic clock ({@code nanoTime} differences), never from a
 * count of UI ticks, so a late or skipped repaint cannot cause drift. Paused time is tracked separately and
 * is never part of the active (focused) duration.
 */
public final class FocusTimer {

    public enum State { IDLE, RUNNING, PAUSED, COMPLETED, CANCELLED }

    /** Final figures of a finished or cancelled session. */
    public record Result(Instant startedAt, Instant endedAt, long activeMillis, long pausedMillis) {}

    private final TimeSource time;

    private State state = State.IDLE;
    private long taskId;
    private TimerMode mode;
    private long targetSeconds;
    private Instant startedAt;
    private Instant endedAt;
    private boolean recovered;
    private boolean targetEventFired;

    /** Nanoseconds of active / paused time accumulated in closed segments. */
    private long activeNanos;
    private long pausedNanos;
    /** nanoTime at the start of the current segment (RUNNING or PAUSED). */
    private long segmentStart;

    public FocusTimer(TimeSource time) { this.time = time; }

    // ---- transitions ----

    public void start(long taskId, TimerMode mode, long targetSeconds) {
        if (isActive()) throw new IllegalStateException("A session is already active.");
        if (mode == null) throw new IllegalArgumentException("Timer mode is required.");
        if (mode == TimerMode.COUNTDOWN && targetSeconds <= 0) {
            throw new IllegalArgumentException("A countdown needs a target duration greater than zero.");
        }
        reset();
        this.taskId = taskId;
        this.mode = mode;
        this.targetSeconds = Math.max(0, targetSeconds);
        this.startedAt = time.now();
        this.segmentStart = time.nanoTime();
        this.state = State.RUNNING;
    }

    /**
     * Rebuilds a session recorded earlier (after a restart). It comes back PAUSED with only the persisted
     * durations; the time the application was closed is never added.
     */
    public void restore(long taskId, TimerMode mode, long targetSeconds, Instant startedAt, long activeMillis, long pausedMillis) {
        if (isActive()) throw new IllegalStateException("A session is already active.");
        reset();
        this.taskId = taskId;
        this.mode = mode;
        this.targetSeconds = Math.max(0, targetSeconds);
        this.startedAt = startedAt;
        this.activeNanos = activeMillis * 1_000_000L;
        this.pausedNanos = pausedMillis * 1_000_000L;
        this.segmentStart = time.nanoTime();
        this.recovered = true;
        this.state = State.PAUSED;
        // The target may already have been reached before the interruption; don't announce it a second time.
        this.targetEventFired = targetReached();
    }

    public void pause() {
        require(State.RUNNING, "Only a running session can be paused.");
        long now = time.nanoTime();
        activeNanos += now - segmentStart;
        segmentStart = now;
        state = State.PAUSED;
    }

    /**
     * Pause as if it had happened {@code idleMillis} ago. Used when the user says they stopped studying a while back:
     * that stretch is moved from focused time to paused time. It cannot remove more than the current running stretch.
     * Returns how many milliseconds were actually removed.
     */
    public long pauseBackdated(long idleMillis) {
        require(State.RUNNING, "Only a running session can be paused.");
        long now = time.nanoTime();
        long running = now - segmentStart;
        long back = Math.min(Math.max(0, idleMillis) * 1_000_000L, running);
        activeNanos += running - back;
        segmentStart = now - back;
        state = State.PAUSED;
        return back / 1_000_000L;
    }

    public void resume() {
        require(State.PAUSED, "Only a paused session can be resumed.");
        long now = time.nanoTime();
        pausedNanos += now - segmentStart;
        segmentStart = now;
        state = State.RUNNING;
    }

    public Result finish() {
        if (!isActive()) throw new IllegalStateException("There is no active session to finish.");
        return close(State.COMPLETED);
    }

    public Result cancel() {
        if (!isActive()) throw new IllegalStateException("There is no active session to cancel.");
        return close(State.CANCELLED);
    }

    /** Undo a finish or cancel whose result could not be saved: the session returns to PAUSED so nothing is lost. */
    public void reopenAfterFailedSave() {
        if (state != State.COMPLETED && state != State.CANCELLED) throw new IllegalStateException("Nothing to reopen.");
        segmentStart = time.nanoTime();
        endedAt = null;
        state = State.PAUSED;
    }

    /** Back to IDLE once a finished or cancelled session has been handled. */
    public void reset() {
        if (isActive()) throw new IllegalStateException("A session is still active.");
        state = State.IDLE;
        activeNanos = pausedNanos = segmentStart = 0;
        startedAt = endedAt = null;
        recovered = targetEventFired = false;
        taskId = 0;
        targetSeconds = 0;
        mode = null;
    }

    private Result close(State finalState) {
        long now = time.nanoTime();
        if (state == State.RUNNING) activeNanos += now - segmentStart; else pausedNanos += now - segmentStart;
        segmentStart = now;
        endedAt = time.now();
        state = finalState;
        return new Result(startedAt, endedAt, activeNanos / 1_000_000L, pausedNanos / 1_000_000L);
    }

    // ---- queries ----

    public State state() { return state; }
    public boolean isActive() { return state == State.RUNNING || state == State.PAUSED; }
    public long taskId() { return taskId; }
    public TimerMode mode() { return mode; }
    public long targetSeconds() { return targetSeconds; }
    public Instant startedAt() { return startedAt; }
    public boolean recovered() { return recovered; }

    /** Focused time so far, in milliseconds. */
    public long activeMillis() {
        long n = activeNanos;
        if (state == State.RUNNING) n += time.nanoTime() - segmentStart;
        return n / 1_000_000L;
    }

    public long pausedMillis() {
        long n = pausedNanos;
        if (state == State.PAUSED) n += time.nanoTime() - segmentStart;
        return n / 1_000_000L;
    }

    /** Milliseconds left until the countdown target; zero once reached. Only meaningful in COUNTDOWN mode. */
    public long remainingMillis() { return Math.max(0, targetSeconds * 1000 - activeMillis()); }

    /** Focused time beyond the countdown target. */
    public long overrunMillis() {
        return mode == TimerMode.COUNTDOWN ? Math.max(0, activeMillis() - targetSeconds * 1000) : 0;
    }

    public boolean targetReached() {
        return mode == TimerMode.COUNTDOWN && targetSeconds > 0 && activeMillis() >= targetSeconds * 1000;
    }

    /** True exactly once per session, the first time the countdown target is reached while running. */
    public boolean consumeTargetReached() {
        if (state == State.RUNNING && !targetEventFired && targetReached()) {
            targetEventFired = true;
            return true;
        }
        return false;
    }

    private void require(State expected, String message) {
        if (state != expected) throw new IllegalStateException(message);
    }
}
