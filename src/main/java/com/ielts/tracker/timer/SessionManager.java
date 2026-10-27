package com.ielts.tracker.timer;

import com.ielts.tracker.model.MockPreset;
import com.ielts.tracker.model.SessionStatus;
import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.repository.SessionRepository;

import java.time.Duration;
import java.util.List;

/**
 * Connects the {@link FocusTimer} to the database so a session survives a crash.
 *
 * Every state change and a heartbeat every few seconds write the current active duration to the session
 * row. After an unclean shutdown only that last recorded figure is trusted. The time between the last
 * heartbeat and the restart is never counted.
 */
public final class SessionManager {

    public static final Duration CHECKPOINT_INTERVAL = Duration.ofSeconds(5);

    private final FocusTimer timer;
    private final SessionRepository sessions;
    private final TimeSource time;

    private long sessionId = -1;
    private MockPreset preset;
    private long lastCheckpointNanos;

    public SessionManager(FocusTimer timer, SessionRepository sessions, TimeSource time) {
        this.timer = timer;
        this.sessions = sessions;
        this.time = time;
    }

    public FocusTimer timer() { return timer; }
    public long sessionId() { return sessionId; }
    public boolean hasActiveSession() { return timer.isActive(); }

    /** The exam preset of the running session, or null for an ordinary session. */
    public MockPreset preset() { return preset; }

    public long start(long taskId, TimerMode mode, long targetSeconds) {
        return start(taskId, mode, targetSeconds, null);
    }

    public long start(long taskId, TimerMode mode, long targetSeconds, MockPreset preset) {
        if (timer.isActive()) throw new IllegalStateException("A session is already active.");
        if (!sessions.findOpen().isEmpty()) {
            throw new IllegalStateException("An earlier session was not closed properly. Resolve it first (recover, save or discard).");
        }
        timer.start(taskId, mode, targetSeconds);
        try {
            sessionId = sessions.insertActive(taskId, timer.startedAt(), mode, targetSeconds, preset == null ? null : preset.id());
        } catch (RuntimeException e) {
            timer.cancel();
            timer.reset();
            throw e;
        }
        this.preset = preset;
        lastCheckpointNanos = time.nanoTime();
        return sessionId;
    }

    /** Marks a passage or section as finished at the current focused time (used by exam presets). */
    public void lap(String label) {
        if (!timer.isActive()) throw new IllegalStateException("There is no active session.");
        sessions.addLap(sessionId, label, timer.activeMillis());
    }

    /** The user says they stopped studying {@code idleMillis} ago: pause and take that stretch off the focused time. */
    public long pauseBackdated(long idleMillis) {
        long removed = timer.pauseBackdated(idleMillis);
        checkpoint(SessionStatus.PAUSED);
        return removed;
    }

    public void pause() {
        timer.pause();
        checkpoint(SessionStatus.PAUSED);
    }

    public void resume() {
        timer.resume();
        checkpoint(SessionStatus.RUNNING);
    }

    /**
     * Call regularly (the UI does it once a second). Writes a heartbeat when due and reports whether the
     * countdown target was reached just now (true only once per session).
     */
    public boolean tick() {
        if (timer.state() != FocusTimer.State.RUNNING) return false;
        if (time.nanoTime() - lastCheckpointNanos >= CHECKPOINT_INTERVAL.toNanos()) {
            checkpoint(SessionStatus.RUNNING);
        }
        return timer.consumeTargetReached();
    }

    /** Saves the session as COMPLETED. If saving fails the session stays open (paused) and nothing is lost. */
    public StudySession finish(String notes) {
        FocusTimer.Result r = timer.finish();
        try {
            sessions.close(sessionId, SessionStatus.COMPLETED, r.endedAt(), r.activeMillis(), r.pausedMillis(), notes, timer.recovered());
        } catch (RuntimeException e) {
            timer.reopenAfterFailedSave();
            throw e;
        }
        StudySession saved = sessions.find(sessionId).orElseThrow();
        endLocalSession();
        return saved;
    }

    /** Marks the session CANCELLED; it is kept for audit but excluded from every total. */
    public void cancel() {
        FocusTimer.Result r = timer.cancel();
        try {
            sessions.close(sessionId, SessionStatus.CANCELLED, r.endedAt(), r.activeMillis(), r.pausedMillis(), "", timer.recovered());
        } catch (RuntimeException e) {
            timer.reopenAfterFailedSave();
            throw e;
        }
        endLocalSession();
    }

    /** Called when the application closes mid-session: pauses and persists so the session can be resumed next launch. */
    public void suspendForExit() {
        if (timer.state() == FocusTimer.State.RUNNING) pause();
    }

    // ---- recovery after an unclean shutdown ----

    /** Sessions still marked RUNNING or PAUSED in the database. Call at startup. */
    public List<StudySession> pendingRecovery() {
        return timer.isActive() ? List.of() : sessions.findOpen();
    }

    /** Continue the session. It resumes PAUSED, holding only the last recorded active time. */
    public void resumeRecovered(StudySession s) {
        timer.restore(s.taskId(), s.mode(), s.targetSeconds(), s.start(), s.activeMillis(), s.pausedMillis());
        sessionId = s.id();
        preset = MockPreset.byId(s.preset());
        sessions.checkpoint(s.id(), SessionStatus.PAUSED, s.activeMillis(), s.pausedMillis(), time.now(), true);
        lastCheckpointNanos = time.nanoTime();
    }

    /** Save the last reliably recorded duration as an INTERRUPTED session ending at its last heartbeat. */
    public void saveRecoveredAsInterrupted(StudySession s) {
        sessions.close(s.id(), SessionStatus.INTERRUPTED, s.lastCheckpoint(), s.activeMillis(), s.pausedMillis(), s.notes(), true);
    }

    /** Throw the session away (kept as CANCELLED, excluded from totals). The UI must confirm first. */
    public void discardRecovered(StudySession s) {
        sessions.close(s.id(), SessionStatus.CANCELLED, s.lastCheckpoint(), s.activeMillis(), s.pausedMillis(), s.notes(), true);
    }

    private void checkpoint(SessionStatus status) {
        sessions.checkpoint(sessionId, status, timer.activeMillis(), timer.pausedMillis(), time.now(), timer.recovered());
        lastCheckpointNanos = time.nanoTime();
    }

    private void endLocalSession() {
        sessionId = -1;
        preset = null;
        timer.reset();
    }
}
