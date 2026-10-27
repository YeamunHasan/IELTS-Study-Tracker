package com.ielts.tracker.timer;

import com.ielts.tracker.TestSupport.FakeTime;
import com.ielts.tracker.model.TimerMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FocusTimerTest {
    private FakeTime time;
    private FocusTimer timer;

    @BeforeEach void setUp() {
        time = new FakeTime(Instant.parse("2026-10-28T09:00:00Z"));
        timer = new FocusTimer(time);
    }

    @Test void startsCountdown() {
        timer.start(1, TimerMode.COUNTDOWN, 1500);
        assertEquals(FocusTimer.State.RUNNING, timer.state());
        assertEquals(1_500_000, timer.remainingMillis());
        time.advance(Duration.ofSeconds(90));
        assertEquals(90_000, timer.activeMillis());
        assertEquals(1_410_000, timer.remainingMillis());
    }

    @Test void startsStopwatchCountingUp() {
        timer.start(1, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(7));
        assertEquals(Duration.ofMinutes(7).toMillis(), timer.activeMillis());
        assertFalse(timer.targetReached());
    }

    @Test void countdownRequiresPositiveTarget() {
        assertThrows(IllegalArgumentException.class, () -> timer.start(1, TimerMode.COUNTDOWN, 0));
    }

    @Test void pausedTimeIsNeverFocusedTime() {
        timer.start(1, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(10));
        timer.pause();
        time.advance(Duration.ofHours(2));
        assertEquals(Duration.ofMinutes(10).toMillis(), timer.activeMillis());
        assertEquals(Duration.ofHours(2).toMillis(), timer.pausedMillis());
        timer.resume();
        time.advance(Duration.ofMinutes(5));
        assertEquals(Duration.ofMinutes(15).toMillis(), timer.activeMillis());
        assertEquals(Duration.ofHours(2).toMillis(), timer.pausedMillis());
    }

    @Test void finishFreezesTotals() {
        timer.start(1, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(3));
        FocusTimer.Result r = timer.finish();
        time.advance(Duration.ofMinutes(30));
        assertEquals(FocusTimer.State.COMPLETED, timer.state());
        assertEquals(Duration.ofMinutes(3).toMillis(), r.activeMillis());
        assertEquals(Duration.ofMinutes(3).toMillis(), timer.activeMillis());
    }

    @Test void finishWhilePausedIncludesPauseInPausedTimeOnly() {
        timer.start(1, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(4));
        timer.pause();
        time.advance(Duration.ofMinutes(6));
        FocusTimer.Result r = timer.finish();
        assertEquals(Duration.ofMinutes(4).toMillis(), r.activeMillis());
        assertEquals(Duration.ofMinutes(6).toMillis(), r.pausedMillis());
    }

    @Test void targetReachedFiresExactlyOnceAndSessionContinuesIntoOverrun() {
        timer.start(1, TimerMode.COUNTDOWN, 60);
        time.advance(Duration.ofSeconds(59));
        assertFalse(timer.consumeTargetReached());
        time.advance(Duration.ofSeconds(1));
        assertTrue(timer.consumeTargetReached());
        assertFalse(timer.consumeTargetReached());
        assertEquals(0, timer.remainingMillis());

        time.advance(Duration.ofSeconds(45));
        assertEquals(FocusTimer.State.RUNNING, timer.state());
        assertEquals(45_000, timer.overrunMillis());
        assertEquals(105_000, timer.activeMillis());
        assertFalse(timer.consumeTargetReached());
    }

    @Test void wallClockChangesDoNotAffectElapsedTime() {
        timer.start(1, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(1));
        time.jumpWallClock(Duration.ofHours(-5)); // user changes the system clock
        assertEquals(Duration.ofMinutes(1).toMillis(), timer.activeMillis());
    }

    @Test void rejectsInvalidTransitions() {
        assertThrows(IllegalStateException.class, () -> timer.pause());
        assertThrows(IllegalStateException.class, () -> timer.resume());
        assertThrows(IllegalStateException.class, () -> timer.finish());
        timer.start(1, TimerMode.STOPWATCH, 0);
        assertThrows(IllegalStateException.class, () -> timer.resume());
        assertThrows(IllegalStateException.class, () -> timer.start(2, TimerMode.STOPWATCH, 0));
        timer.pause();
        assertThrows(IllegalStateException.class, () -> timer.pause());
        assertThrows(IllegalStateException.class, () -> timer.start(2, TimerMode.STOPWATCH, 0));
    }

    @Test void canStartAgainAfterFinish() {
        timer.start(1, TimerMode.STOPWATCH, 0);
        timer.finish();
        timer.start(2, TimerMode.COUNTDOWN, 60);
        assertEquals(FocusTimer.State.RUNNING, timer.state());
        assertEquals(0, timer.activeMillis());
        assertEquals(2, timer.taskId());
    }

    @Test void restoredSessionIsPausedWithOnlyPersistedTime() {
        timer.restore(5, TimerMode.COUNTDOWN, 600, Instant.parse("2026-10-28T08:00:00Z"), 120_000, 5_000);
        time.advance(Duration.ofHours(3));
        assertEquals(FocusTimer.State.PAUSED, timer.state());
        assertTrue(timer.recovered());
        assertEquals(120_000, timer.activeMillis());
        timer.resume();
        time.advance(Duration.ofSeconds(30));
        assertEquals(150_000, timer.activeMillis());
    }

    @Test void restoredSessionPastTargetDoesNotAnnounceAgain() {
        timer.restore(5, TimerMode.COUNTDOWN, 60, Instant.parse("2026-10-28T08:00:00Z"), 90_000, 0);
        timer.resume();
        assertFalse(timer.consumeTargetReached());
    }
}
