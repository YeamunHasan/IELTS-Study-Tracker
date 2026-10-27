package com.ielts.tracker.service;

import com.ielts.tracker.TestSupport;
import com.ielts.tracker.TestSupport.FakeTime;
import com.ielts.tracker.model.Priority;
import com.ielts.tracker.model.SessionStatus;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.repository.Database;
import com.ielts.tracker.repository.SessionRepository;
import com.ielts.tracker.repository.SettingsRepository;
import com.ielts.tracker.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StatsServiceTest {
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka"); // UTC+6, no DST
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @TempDir Path dir;
    private FakeTime time;
    private Database db;
    private TaskRepository tasks;
    private SessionRepository sessions;
    private AppSettings settings;
    private long reading, listening, writing;

    @BeforeEach void setUp() {
        db = TestSupport.newDb(dir);
        tasks = new TaskRepository(db);
        sessions = new SessionRepository(db);
        settings = new AppSettings(new SettingsRepository(db));
        time = new FakeTime(ZonedDateTime.of(2026, 10, 28, 20, 0, 0, 0, DHAKA).toInstant());
        reading = tasks.insert(TestSupport.draft("Reading Test", Skill.READING, 60), time.now());
        listening = tasks.insert(TestSupport.draft("Listening Test", Skill.LISTENING, 40), time.now());
        writing = tasks.insert(TestSupport.draft("Essay", Skill.WRITING, 40), time.now());
    }

    private StatsService stats(ZoneId zone) { return new StatsService(sessions, tasks, settings, time, zone); }

    private long record(long taskId, ZonedDateTime start, long minutes, SessionStatus status) {
        long id = sessions.insertActive(taskId, start.toInstant(), TimerMode.STOPWATCH, 0);
        sessions.close(id, status, start.toInstant().plus(Duration.ofMinutes(minutes)), minutes * 60_000, 0, "", false);
        return id;
    }

    private static ZonedDateTime at(int day, int hour, int minute, ZoneId zone) {
        return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone);
    }

    @Test void dailyTotalsSumPerLocalDateAndFillEmptyDays() {
        record(reading, at(26, 9, 0, DHAKA), 30, SessionStatus.COMPLETED);
        record(reading, at(26, 15, 0, DHAKA), 15, SessionStatus.COMPLETED);
        record(listening, at(28, 8, 0, DHAKA), 45, SessionStatus.COMPLETED);

        Map<LocalDate, Long> totals = stats(DHAKA).dailyTotals(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28));
        assertEquals(3, totals.size());
        assertEquals(45 * 60_000L, totals.get(LocalDate.of(2026, 10, 26)));
        assertEquals(0L, totals.get(LocalDate.of(2026, 10, 27)));
        assertEquals(45 * 60_000L, totals.get(LocalDate.of(2026, 10, 28)));
    }

    @Test void cancelledAndOpenSessionsAreExcluded() {
        record(reading, at(28, 9, 0, DHAKA), 30, SessionStatus.COMPLETED);
        record(reading, at(28, 10, 0, DHAKA), 50, SessionStatus.CANCELLED);
        sessions.insertActive(reading, at(28, 11, 0, DHAKA).toInstant(), TimerMode.STOPWATCH, 0);
        LocalDate d = LocalDate.of(2026, 10, 28);
        assertEquals(30 * 60_000L, stats(DHAKA).dailyTotals(d, d).get(d));
    }

    @Test void interruptedSessionsCountOnlyTheirRecordedTime() {
        record(reading, at(28, 9, 0, DHAKA), 12, SessionStatus.INTERRUPTED);
        var summary = stats(DHAKA).summary(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28));
        assertEquals(12 * 60_000L, summary.totalMillis());
        assertEquals(1, summary.interruptedSessions());
    }

    @Test void sessionStartingBeforeMidnightBelongsToItsStartDate() {
        record(reading, at(27, 23, 50, DHAKA), 40, SessionStatus.COMPLETED); // runs past midnight
        record(reading, at(28, 0, 0, DHAKA), 10, SessionStatus.COMPLETED);   // first instant of the new day
        StatsService s = stats(DHAKA);
        assertEquals(40 * 60_000L, s.dailyTotals(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 27)).get(LocalDate.of(2026, 10, 27)));
        assertEquals(10 * 60_000L, s.dailyTotals(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28)).get(LocalDate.of(2026, 10, 28)));
    }

    @Test void changingTimezoneRegroupsTheSameInstantsWithoutChangingTotals() {
        // 22:30 UTC on the 27th is 04:30 on the 28th in Dhaka, but still the 27th in London.
        Instant start = Instant.parse("2026-10-27T22:30:00Z");
        long id = sessions.insertActive(reading, start, TimerMode.STOPWATCH, 0);
        sessions.close(id, SessionStatus.COMPLETED, start.plusSeconds(1800), 1_800_000, 0, "", false);

        LocalDate d27 = LocalDate.of(2026, 10, 27), d28 = LocalDate.of(2026, 10, 28);
        assertEquals(1_800_000L, stats(LONDON).dailyTotals(d27, d28).get(d27));
        assertEquals(0L, stats(LONDON).dailyTotals(d27, d28).get(d28));
        assertEquals(0L, stats(DHAKA).dailyTotals(d27, d28).get(d27));
        assertEquals(1_800_000L, stats(DHAKA).dailyTotals(d27, d28).get(d28));
        assertEquals(1_800_000L, stats(LONDON).summary(d27, d28).totalMillis());
        assertEquals(1_800_000L, stats(DHAKA).summary(d27, d28).totalMillis());
    }

    @Test void dailyWeeklyMonthlySkillAndTaskTotalsReconcile() {
        record(reading, at(5, 9, 0, DHAKA), 60, SessionStatus.COMPLETED);
        record(listening, at(12, 9, 0, DHAKA), 40, SessionStatus.COMPLETED);
        record(writing, at(26, 9, 0, DHAKA), 35, SessionStatus.COMPLETED);
        record(reading, at(26, 14, 0, DHAKA), 25, SessionStatus.INTERRUPTED);
        record(reading, at(27, 9, 0, DHAKA), 20, SessionStatus.COMPLETED);

        StatsService s = stats(DHAKA);
        LocalDate monthStart = LocalDate.of(2026, 10, 1), monthEnd = LocalDate.of(2026, 10, 31);
        LocalDate weekStart = LocalDate.of(2026, 10, 26), weekEnd = LocalDate.of(2026, 11, 1);

        long month = s.summary(monthStart, monthEnd).totalMillis();
        long week = s.summary(weekStart, weekEnd).totalMillis();
        assertEquals((60 + 40 + 35 + 25 + 20) * 60_000L, month);
        assertEquals((35 + 25 + 20) * 60_000L, week);

        assertEquals(month, s.dailyTotals(monthStart, monthEnd).values().stream().mapToLong(Long::longValue).sum());
        assertEquals(month, s.skillTotals(monthStart, monthEnd).values().stream().mapToLong(Long::longValue).sum());
        assertEquals(month, s.taskTotals(monthStart, monthEnd).stream().mapToLong(StatsService.TaskTotal::millis).sum());
        assertEquals(105 * 60_000L, s.skillTotals(monthStart, monthEnd).get(Skill.READING));

        var summary = s.summary(monthStart, monthEnd);
        assertEquals(5, summary.sessions());
        assertEquals(4, summary.activeDays());
        assertEquals(60 * 60_000L, summary.longestSessionMillis());
        assertEquals(20 * 60_000L, summary.shortestSessionMillis());
        // and the task's own accumulated figure matches the report
        assertEquals(105 * 60_000L, tasks.listSummaries().stream().filter(t -> t.task().id() == reading).findFirst().orElseThrow().focusedMillis());
    }

    @Test void todaySummaryHandlesGoalPercentRemainingAndLiveTime() {
        settings.saveStudyPreferences(120, 600, 30, TimerMode.COUNTDOWN, 25);
        record(reading, at(28, 9, 0, DHAKA), 45, SessionStatus.COMPLETED);
        record(listening, at(28, 11, 0, DHAKA), 15, SessionStatus.COMPLETED);

        var day = stats(DHAKA).today(0);
        assertEquals(60 * 60_000L, day.focusedMillis());
        assertEquals(60 * 60_000L, day.remainingMillis());
        assertEquals(0.5, day.goalFraction(), 1e-9);
        assertEquals(2, day.sessions());

        var withLive = stats(DHAKA).today(30 * 60_000L);
        assertEquals(90 * 60_000L, withLive.focusedMillis());
        assertEquals(0.75, withLive.goalFraction(), 1e-9);
    }

    @Test void todayCountsScheduledAndCompletedTasks() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        long a = tasks.insert(new StudyTask(0, "A", "", Skill.READING, "", 600, Priority.LOW, TaskStatus.PLANNED, today, null, "", null, null, null), time.now());
        tasks.insert(new StudyTask(0, "B", "", Skill.READING, "", 600, Priority.LOW, TaskStatus.PLANNED, today, null, "", null, null, null), time.now());
        tasks.insert(new StudyTask(0, "C", "", Skill.READING, "", 600, Priority.LOW, TaskStatus.PLANNED, today.plusDays(1), null, "", null, null, null), time.now());
        tasks.setStatus(a, TaskStatus.COMPLETED, time.now());
        var day = stats(DHAKA).today(0);
        assertEquals(2, day.scheduledTasks());
        assertEquals(1, day.completedTasks());
    }

    @Test void plannedVersusActualUsesTaskTargetsAndSavedSessions() {
        LocalDate d = LocalDate.of(2026, 10, 28);
        tasks.insert(new StudyTask(0, "Plan", "", Skill.READING, "", 3600, Priority.LOW, TaskStatus.PLANNED, d, null, "", null, null, null), time.now());
        long skipped = tasks.insert(new StudyTask(0, "Skip", "", Skill.READING, "", 1200, Priority.LOW, TaskStatus.PLANNED, d, null, "", null, null, null), time.now());
        tasks.setStatus(skipped, TaskStatus.SKIPPED, time.now());
        record(reading, at(28, 9, 0, DHAKA), 50, SessionStatus.COMPLETED);

        var row = stats(DHAKA).plannedVsActual(d, d).get(0);
        assertEquals(3600_000L, row.plannedMillis());
        assertEquals(50 * 60_000L, row.actualMillis());
    }

    @Test void streaksComeFromSavedSessionsAndTheConfiguredThreshold() {
        settings.saveStudyPreferences(120, 600, 30, TimerMode.COUNTDOWN, 25);
        record(reading, at(24, 9, 0, DHAKA), 30, SessionStatus.COMPLETED);
        record(reading, at(25, 9, 0, DHAKA), 10, SessionStatus.COMPLETED);
        record(reading, at(25, 15, 0, DHAKA), 25, SessionStatus.COMPLETED); // 35 min total that day
        record(reading, at(26, 9, 0, DHAKA), 29, SessionStatus.COMPLETED);  // below threshold
        record(reading, at(27, 9, 0, DHAKA), 60, SessionStatus.COMPLETED);
        record(reading, at(28, 9, 0, DHAKA), 40, SessionStatus.CANCELLED);  // does not count

        var r = stats(DHAKA).streaks();
        assertEquals(1, r.current(), "only the 27th qualifies; today (28th) is cancelled only, but is still in progress");
        assertEquals(2, r.longest());
    }

    @Test void missingExamDateGivesNotSetCountdown() {
        assertEquals(Countdown.State.NOT_SET, Countdown.of(settings.exam(), time.now(), DHAKA).state());
    }
}
