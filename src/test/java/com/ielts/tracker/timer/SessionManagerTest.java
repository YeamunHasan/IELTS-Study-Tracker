package com.ielts.tracker.timer;

import com.ielts.tracker.TestSupport;
import com.ielts.tracker.TestSupport.FakeTime;
import com.ielts.tracker.model.SessionStatus;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.repository.Database;
import com.ielts.tracker.repository.SessionRepository;
import com.ielts.tracker.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {
    @TempDir Path dir;
    private FakeTime time;
    private Database db;
    private SessionRepository sessions;
    private long taskId;

    @BeforeEach void setUp() {
        time = new FakeTime(Instant.parse("2026-10-28T09:00:00Z"));
        db = TestSupport.newDb(dir);
        sessions = new SessionRepository(db);
        taskId = new TaskRepository(db).insert(TestSupport.draft("Reading Test 2", Skill.READING, 60), time.now());
    }

    private SessionManager newManager() { return new SessionManager(new FocusTimer(time), sessions, time); }

    @Test void finishSavesDurationAndNotes() {
        SessionManager m = newManager();
        m.start(taskId, TimerMode.COUNTDOWN, 1800);
        time.advance(Duration.ofMinutes(20));
        m.pause();
        time.advance(Duration.ofMinutes(10));
        m.resume();
        time.advance(Duration.ofMinutes(5));
        StudySession saved = m.finish("Passage 2 was hard");

        assertEquals(SessionStatus.COMPLETED, saved.status());
        assertEquals(Duration.ofMinutes(25).toMillis(), saved.activeMillis());
        assertEquals(Duration.ofMinutes(10).toMillis(), saved.pausedMillis());
        assertEquals("Passage 2 was hard", saved.notes());
        assertEquals(1800, saved.targetSeconds());
        assertFalse(saved.recovered());
        assertNotNull(saved.end());
        assertFalse(m.hasActiveSession());
    }

    @Test void secondSessionCannotStartWhileOneIsActive() {
        SessionManager m = newManager();
        m.start(taskId, TimerMode.STOPWATCH, 0);
        assertThrows(IllegalStateException.class, () -> m.start(taskId, TimerMode.STOPWATCH, 0));
        // even a second application instance is stopped by the database
        SessionManager other = newManager();
        assertThrows(IllegalStateException.class, () -> other.start(taskId, TimerMode.STOPWATCH, 0));
        assertEquals(1, sessions.findOpen().size());
    }

    @Test void databaseRefusesTwoOpenSessionsEvenIfCodeChecksAreBypassed() {
        sessions.insertActive(taskId, time.now(), TimerMode.STOPWATCH, 0);
        assertThrows(IllegalStateException.class, () -> sessions.insertActive(taskId, time.now(), TimerMode.STOPWATCH, 0));
    }

    @Test void heartbeatIsWrittenEveryFewSeconds() {
        SessionManager m = newManager();
        m.start(taskId, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofSeconds(3));
        m.tick();
        assertEquals(0, sessions.findOpen().get(0).activeMillis());
        time.advance(Duration.ofSeconds(3));
        m.tick();
        assertEquals(6_000, sessions.findOpen().get(0).activeMillis());
    }

    @Test void countdownTargetNotificationHappensOnce() {
        SessionManager m = newManager();
        m.start(taskId, TimerMode.COUNTDOWN, 10);
        time.advance(Duration.ofSeconds(10));
        assertTrue(m.tick());
        time.advance(Duration.ofSeconds(10));
        assertFalse(m.tick());
        assertEquals(FocusTimer.State.RUNNING, m.timer().state());
        assertEquals(20_000, m.finish("").activeMillis());
    }

    @Test void recoveryAfterCrashTrustsOnlyTheLastHeartbeat() {
        SessionManager m = newManager();
        m.start(taskId, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofSeconds(10));
        m.tick(); // heartbeat records 10 s
        time.advance(Duration.ofSeconds(3)); // 3 more seconds, then the app is killed
        // ... computer stays off for 8 hours ...
        time.advance(Duration.ofHours(8));

        SessionManager restarted = newManager();
        List<StudySession> pending = restarted.pendingRecovery();
        assertEquals(1, pending.size());
        StudySession s = pending.get(0);
        assertEquals(SessionStatus.RUNNING, s.status());
        assertEquals(10_000, s.activeMillis(), "only the recorded heartbeat time is trusted");

        restarted.saveRecoveredAsInterrupted(s);
        StudySession saved = sessions.find(s.id()).orElseThrow();
        assertEquals(SessionStatus.INTERRUPTED, saved.status());
        assertEquals(10_000, saved.activeMillis());
        assertTrue(saved.recovered());
        assertEquals(s.lastCheckpoint(), saved.end(), "ends at the last heartbeat, not at restart time");
        assertTrue(restarted.pendingRecovery().isEmpty());
    }

    @Test void recoveredSessionCanBeResumedWithoutDuplicatingRecords() {
        SessionManager m = newManager();
        m.start(taskId, TimerMode.COUNTDOWN, 600);
        time.advance(Duration.ofSeconds(30));
        m.pause();
        time.advance(Duration.ofHours(1));

        SessionManager restarted = newManager();
        StudySession pending = restarted.pendingRecovery().get(0);
        restarted.resumeRecovered(pending);
        assertEquals(FocusTimer.State.PAUSED, restarted.timer().state());
        assertEquals(30_000, restarted.timer().activeMillis());
        assertEquals(pending.id(), restarted.sessionId());

        // resume and study 2 more minutes
        restarted.resume();
        time.advance(Duration.ofMinutes(2));
        StudySession done = restarted.finish("recovered");

        assertEquals(pending.id(), done.id());
        assertEquals(150_000, done.activeMillis());
        assertTrue(done.recovered());
        assertEquals(1, sessions.list(SessionRepository.Filter.none()).size(), "no duplicate session rows");
    }

    @Test void discardedRecoveredSessionIsExcludedFromTotals() {
        SessionManager m = newManager();
        m.start(taskId, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofSeconds(8));
        m.pause();

        SessionManager restarted = newManager();
        restarted.discardRecovered(restarted.pendingRecovery().get(0));
        assertTrue(restarted.pendingRecovery().isEmpty());
        assertTrue(sessions.listCounted(0, Long.MAX_VALUE).isEmpty());
        assertEquals(SessionStatus.CANCELLED, sessions.list(SessionRepository.Filter.none()).get(0).status());
    }

    @Test void cancelledSessionIsKeptButNotCounted() {
        SessionManager m = newManager();
        m.start(taskId, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(5));
        m.cancel();
        assertTrue(sessions.listCounted(0, Long.MAX_VALUE).isEmpty());
        assertEquals(1, sessions.list(SessionRepository.Filter.none()).size());
        // a new session can start right away
        m.start(taskId, TimerMode.STOPWATCH, 0);
    }

    @Test void exitWhileRunningPausesAndPersistsForNextLaunch() {
        SessionManager m = newManager();
        m.start(taskId, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(2));
        m.suspendForExit();

        StudySession s = newManager().pendingRecovery().get(0);
        assertEquals(SessionStatus.PAUSED, s.status());
        assertEquals(Duration.ofMinutes(2).toMillis(), s.activeMillis());
    }

    @Test void totalsForTheSameTaskAggregateAcrossSessionsWithoutLosingRecords() {
        SessionManager m = newManager();
        for (int minutes : new int[]{10, 20, 30}) {
            m.start(taskId, TimerMode.STOPWATCH, 0);
            time.advance(Duration.ofMinutes(minutes));
            m.finish("");
            time.advance(Duration.ofMinutes(1));
        }
        var summary = new TaskRepository(db).listSummaries().get(0);
        assertEquals(Duration.ofMinutes(60).toMillis(), summary.focusedMillis());
        assertEquals(3, summary.sessionCount());
        assertEquals(3, sessions.list(SessionRepository.Filter.none()).size());
    }
}
