package com.ielts.tracker.service;

import com.ielts.tracker.TestSupport;
import com.ielts.tracker.TestSupport.FakeTime;
import com.ielts.tracker.model.*;
import com.ielts.tracker.repository.*;
import com.ielts.tracker.timer.FocusTimer;
import com.ielts.tracker.timer.IdleMonitor;
import com.ielts.tracker.timer.SessionManager;
import com.ielts.tracker.util.Bands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class IeltsFeaturesTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");
    @TempDir Path dir;
    private FakeTime time;
    private Database db;
    private TaskRepository taskRepo;
    private SessionRepository sessions;
    private AppSettings settings;
    private StatsService stats;
    private PracticeRepository practiceRepo;
    private PracticeService practice;
    private GoalService goals;
    private TaskService tasks;
    private MistakeService mistakes;
    private long reading, writing;

    @BeforeEach void setUp() {
        db = TestSupport.newDb(dir);
        time = new FakeTime(ZonedDateTime.of(2026, 10, 28, 20, 0, 0, 0, ZONE).toInstant()); // Wednesday
        taskRepo = new TaskRepository(db);
        sessions = new SessionRepository(db);
        settings = new AppSettings(new SettingsRepository(db));
        stats = new StatsService(sessions, taskRepo, settings, time, ZONE);
        practiceRepo = new PracticeRepository(db);
        practice = new PracticeService(practiceRepo, time, ZONE);
        goals = new GoalService(practiceRepo, settings, stats);
        tasks = new TaskService(taskRepo, time);
        mistakes = new MistakeService(new MistakeRepository(db), tasks, time, ZONE);
        reading = taskRepo.insert(TestSupport.draft("Reading", Skill.READING, 60), time.now());
        writing = taskRepo.insert(TestSupport.draft("Writing", Skill.WRITING, 60), time.now());
    }

    private void result(Skill s, int day, double band) {
        practice.record(new PracticeResult(0, LocalDate.of(2026, 10, day), "Cambridge 18", s, null, band, null, null, ""));
    }

    private void studied(long task, int day, int hour, int minutes) {
        Instant start = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, ZONE).toInstant();
        sessions.insertManual(task, start, minutes * 60_000L, "");
    }

    // ---- bands and gaps ----

    @Test void overallBandUsesTheOfficialRounding() {
        assertEquals(6.5, Bands.overall(6.5, 6.5, 6.5, 6.5));
        assertEquals(7.0, Bands.overall(7.0, 6.5, 7.0, 6.5), "mean 6.75 rounds up to 7.0");
        assertEquals(6.5, Bands.overall(6.5, 6.5, 6.5, 6.0), "mean 6.375 rounds to 6.5");
        assertEquals(6.0, Bands.overall(6.0, 6.0, 6.0, 5.5), "mean 5.875 rounds to 6.0");
        assertEquals(6.5, Bands.overall(6.0, 6.5, 6.5, 6.5), "mean 6.375 -> 6.5");
        assertEquals(6.0, Bands.overall(6.0, 6.0, 6.0, 6.5), "mean 6.125 rounds down to 6.0");
        assertEquals(6.5, Bands.overall(6.0, 6.0, 7.0, 6.5), "mean 6.375 -> 6.5");
        assertEquals(7.0, Bands.overall(7.0, 7.0, 7.0, 6.0), "mean 6.75 -> 7.0");
        assertEquals(6.5, Bands.overall(6.5, 6.5, 6.0, 6.0), "mean 6.25 -> 6.5");
    }

    @Test void bandValidation() {
        assertTrue(Bands.isValidBand(6.5));
        assertFalse(Bands.isValidBand(6.3));
        assertFalse(Bands.isValidBand(9.5));
        assertFalse(Bands.isValidBand(-1));
    }

    @Test void writingAt6WithTargetOf7ShowsAGapOfOneAndRanksFirst() {
        settings.saveTargetBand(Skill.WRITING, 7.0);
        settings.saveTargetBand(Skill.READING, 7.0);
        result(Skill.WRITING, 20, 6.0);
        result(Skill.READING, 20, 6.5);
        studied(writing, 26, 9, 35);

        var gaps = goals.gaps();
        assertEquals(Skill.WRITING, gaps.get(0).skill());
        assertEquals(1.0, gaps.get(0).gap(), 1e-9);
        assertEquals(6.0, gaps.get(0).latestBand());
        assertEquals(35, gaps.get(0).minutesThisWeek());
        assertEquals(Skill.WRITING, goals.furthest().orElseThrow().skill());
    }

    @Test void gapUsesTheLatestResultNotTheBestOne() {
        result(Skill.WRITING, 10, 7.0);
        result(Skill.WRITING, 25, 6.0);
        assertEquals(6.0, goals.gaps().stream().filter(g -> g.skill() == Skill.WRITING).findFirst().orElseThrow().latestBand());
    }

    @Test void skillsWithoutResultsNeverOutrankMeasuredOnesAndDontClaimAGap() {
        result(Skill.READING, 20, 6.5);
        var gaps = goals.gaps();
        assertEquals(Skill.READING, gaps.get(0).skill());
        assertNull(gaps.get(3).gap());
        assertTrue(goals.furthest().isPresent());
    }

    @Test void noFurthestSkillWhenEverythingIsAtTarget() {
        for (Skill s : Skill.BAND_SKILLS) result(s, 20, 7.5);
        assertTrue(goals.furthest().isEmpty());
        assertEquals(7.5, practice.overallEstimate().orElseThrow());
    }

    @Test void overallEstimateNeedsAllFourSkills() {
        result(Skill.READING, 20, 7.0);
        assertTrue(practice.overallEstimate().isEmpty());
    }

    @Test void practiceResultValidation() {
        assertThrows(IllegalArgumentException.class, () -> practice.record(new PracticeResult(0, LocalDate.of(2026, 10, 20), "", Skill.READING, 41, 7, null, null, "")));
        assertThrows(IllegalArgumentException.class, () -> practice.record(new PracticeResult(0, LocalDate.of(2026, 10, 20), "", Skill.WRITING, 30, 7, null, null, "")));
        assertThrows(IllegalArgumentException.class, () -> practice.record(new PracticeResult(0, LocalDate.of(2026, 10, 20), "", Skill.READING, 30, 6.3, null, null, "")));
        assertThrows(IllegalArgumentException.class, () -> practice.record(new PracticeResult(0, LocalDate.of(2026, 11, 20), "", Skill.READING, 30, 6.5, null, null, "")));
        assertThrows(IllegalArgumentException.class, () -> practice.record(new PracticeResult(0, LocalDate.of(2026, 10, 20), "", Skill.MOCK_TEST, null, 6.5, null, null, "")));
    }

    @Test void targetBandDefaultsAndPersists() {
        assertEquals(7.0, settings.targetBand(Skill.SPEAKING));
        settings.saveTargetBand(Skill.SPEAKING, 6.5);
        assertEquals(6.5, settings.targetBand(Skill.SPEAKING));
        assertThrows(IllegalArgumentException.class, () -> settings.saveTargetBand(Skill.SPEAKING, 6.7));
        assertThrows(IllegalArgumentException.class, () -> settings.saveTargetBand(Skill.VOCABULARY, 6.5));
    }

    // ---- manual entry ----

    @Test void manualSessionsAreCountedButAlwaysLabelledManual() {
        SessionService svc = new SessionService(sessions, taskRepo, time, ZONE);
        long id = svc.addManual(reading, LocalDate.of(2026, 10, 28), LocalTime.of(8, 0), 45, "On the bus");
        StudySession s = sessions.find(id).orElseThrow();
        assertTrue(s.isManual());
        assertEquals(TimerMode.MANUAL, s.mode());
        assertEquals(SessionStatus.COMPLETED, s.status());
        assertEquals(45 * 60_000L, s.activeMillis());
        LocalDate d = LocalDate.of(2026, 10, 28);
        assertEquals(45 * 60_000L, stats.dailyTotals(d, d).get(d));
    }

    @Test void manualEntryRejectsFutureAndNonsense() {
        SessionService svc = new SessionService(sessions, taskRepo, time, ZONE);
        assertThrows(IllegalArgumentException.class, () -> svc.addManual(reading, LocalDate.of(2026, 10, 28), LocalTime.of(19, 30), 60, ""));
        assertThrows(IllegalArgumentException.class, () -> svc.addManual(reading, LocalDate.of(2026, 10, 29), LocalTime.of(8, 0), 30, ""));
        assertThrows(IllegalArgumentException.class, () -> svc.addManual(reading, LocalDate.of(2026, 10, 27), LocalTime.of(8, 0), 0, ""));
        assertThrows(IllegalArgumentException.class, () -> svc.addManual(reading, LocalDate.of(2026, 10, 27), LocalTime.of(8, 0), 13 * 60, ""));
        assertThrows(IllegalArgumentException.class, () -> svc.addManual(999, LocalDate.of(2026, 10, 27), LocalTime.of(8, 0), 30, ""));
    }

    @Test void resumeLastTaskIgnoresManualCancelledAndCompletedTasks() {
        SessionService svc = new SessionService(sessions, taskRepo, time, ZONE);
        SessionManager m = new SessionManager(new FocusTimer(time), sessions, time);
        m.start(reading, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(10));
        m.finish("");
        svc.addManual(writing, LocalDate.of(2026, 10, 28), LocalTime.of(8, 0), 20, "");   // newer, but manual
        assertEquals(reading, svc.lastTimedSession().orElseThrow().taskId());
        taskRepo.setStatus(reading, TaskStatus.COMPLETED, time.now());
        assertTrue(svc.lastTimedSession().isEmpty());
    }

    // ---- idle check and exam presets ----

    @Test void backdatedPauseRemovesIdleTimeFromFocusedTime() {
        FocusTimer t = new FocusTimer(time);
        t.start(1, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(30));
        long removed = t.pauseBackdated(Duration.ofMinutes(12).toMillis());
        assertEquals(Duration.ofMinutes(12).toMillis(), removed);
        assertEquals(FocusTimer.State.PAUSED, t.state());
        assertEquals(Duration.ofMinutes(18).toMillis(), t.activeMillis());
        assertEquals(Duration.ofMinutes(12).toMillis(), t.pausedMillis());
        time.advance(Duration.ofMinutes(5));
        assertEquals(Duration.ofMinutes(18).toMillis(), t.activeMillis(), "stays frozen while paused");
    }

    @Test void backdatedPauseCannotRemoveMoreThanTheRunningStretch() {
        FocusTimer t = new FocusTimer(time);
        t.start(1, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(10));
        t.pause();
        time.advance(Duration.ofMinutes(20));
        t.resume();
        time.advance(Duration.ofMinutes(3));
        assertEquals(Duration.ofMinutes(3).toMillis(), t.pauseBackdated(Duration.ofHours(2).toMillis()));
        assertEquals(Duration.ofMinutes(10).toMillis(), t.activeMillis());
    }

    @Test void managerPersistsABackdatedPause() {
        SessionManager m = new SessionManager(new FocusTimer(time), sessions, time);
        m.start(reading, TimerMode.STOPWATCH, 0);
        time.advance(Duration.ofMinutes(25));
        m.pauseBackdated(Duration.ofMinutes(15).toMillis());
        StudySession open = sessions.findOpen().get(0);
        assertEquals(SessionStatus.PAUSED, open.status());
        assertEquals(Duration.ofMinutes(10).toMillis(), open.activeMillis());
    }

    @Test void idleMonitorAsksOncePerQuietSpellAndNeverWhilePausedOrActive() {
        IdleMonitor idle = new IdleMonitor(time, Duration.ofMinutes(10));
        time.advance(Duration.ofMinutes(9));
        assertEquals(OptionalLong.empty(), idle.poll(true));
        time.advance(Duration.ofMinutes(2));
        assertEquals(OptionalLong.empty(), idle.poll(false), "not asked while no session is running");
        OptionalLong asked = idle.poll(true);
        assertTrue(asked.isPresent());
        assertEquals(Duration.ofMinutes(11).toMillis(), asked.getAsLong());
        time.advance(Duration.ofMinutes(30));
        assertEquals(OptionalLong.empty(), idle.poll(true), "only one prompt per quiet spell");
        idle.onInput();
        time.advance(Duration.ofMinutes(10));
        assertTrue(idle.poll(true).isPresent(), "asks again after activity and another quiet spell");
    }

    @Test void examPresetsHaveTheRealTimings() {
        assertEquals(40 * 60, MockPreset.LISTENING.totalSeconds());
        assertEquals(60 * 60, MockPreset.READING.totalSeconds());
        assertEquals(60 * 60, MockPreset.WRITING.totalSeconds());
        assertEquals(13 * 60, MockPreset.SPEAKING_FULL.totalSeconds());
        MockPreset l = MockPreset.LISTENING;
        assertEquals(0, l.segmentIndexAt(29 * 60 + 59));
        assertEquals(1, l.segmentIndexAt(30 * 60), "transfer time starts exactly at 30:00");
        assertEquals(10 * 60, l.segmentRemainingSeconds(30 * 60));
        assertEquals(0, l.segmentRemainingSeconds(40 * 60));
        assertEquals(MockPreset.READING, MockPreset.byId("reading"));
        assertNull(MockPreset.byId("nope"));
    }

    @Test void presetAndLapsSurviveARestartAndFeedTimePerPassage() {
        SessionManager m = new SessionManager(new FocusTimer(time), sessions, time);
        long sid = m.start(reading, TimerMode.COUNTDOWN, MockPreset.READING.totalSeconds(), MockPreset.READING);
        time.advance(Duration.ofMinutes(21));
        m.lap("Passage 1");
        time.advance(Duration.ofMinutes(19));
        m.lap("Passage 2");
        m.pause();

        SessionManager restarted = new SessionManager(new FocusTimer(time), sessions, time);
        StudySession pending = restarted.pendingRecovery().get(0);
        assertEquals("reading", pending.preset());
        restarted.resumeRecovered(pending);
        assertEquals(MockPreset.READING, restarted.preset());
        restarted.resume();
        time.advance(Duration.ofMinutes(18));
        restarted.lap("Passage 3");
        StudySession done = restarted.finish("");

        assertEquals("reading", done.preset());
        List<SessionRepository.Lap> laps = sessions.laps(sid);
        assertEquals(3, laps.size());
        assertEquals(21 * 60_000L, laps.get(0).atActiveMillis());
        assertEquals(40 * 60_000L, laps.get(1).atActiveMillis());
        assertEquals(58 * 60_000L, laps.get(2).atActiveMillis());
    }

    @Test void mockScoreIsSavedAsOneRecordWithTimeAndBand() {
        SessionManager m = new SessionManager(new FocusTimer(time), sessions, time);
        m.start(reading, TimerMode.COUNTDOWN, 3600, MockPreset.READING);
        time.advance(Duration.ofMinutes(58));
        StudySession s = m.finish("");
        practice.record(new PracticeResult(0, LocalDate.of(2026, 10, 28), "Cambridge 18 Test 2", Skill.READING, 31, 7.0,
                s.activeMillis() / 1000, s.id(), ""));
        PracticeResult r = practiceRepo.latest(Skill.READING).orElseThrow();
        assertEquals(31, r.rawScore());
        assertEquals(7.0, r.band());
        assertEquals(58 * 60L, r.secondsTaken());
        assertEquals(s.id(), r.sessionId());
    }

    // ---- error log and review tasks ----

    @Test void mistakeWithReviewIsOnlyASuggestionUntilAccepted() {
        mistakes.log(Skill.READING, "True/False/Not Given", "Chose False, the text was silent", 1, "Cambridge 18", null, 3);
        assertTrue(mistakes.dueReviews().isEmpty(), "not due yet");
        assertTrue(taskRepo.listSummaries().stream().noneMatch(t -> "Review".equals(t.task().category())), "nothing created yet");

        time.advance(Duration.ofDays(3));
        var due = mistakes.dueReviews();
        assertEquals(1, due.size());
        long taskId = mistakes.accept(due.get(0));
        StudyTask t = taskRepo.find(taskId).orElseThrow();
        assertEquals("Review: True/False/Not Given (Reading)", t.title());
        assertEquals(Skill.READING, t.skill());
        assertEquals(LocalDate.of(2026, 10, 31), t.scheduledDate());
        assertEquals(TaskStatus.PLANNED, t.status());
        assertTrue(t.description().contains("text was silent"));
        assertTrue(mistakes.dueReviews().isEmpty(), "accepted reviews are not offered again");
    }

    @Test void ignoringAReviewCreatesNoTask() {
        mistakes.log(Skill.LISTENING, "Spelling", "defence vs defense", 1, "", null, 2);
        time.advance(Duration.ofDays(2));
        int before = taskRepo.listSummaries().size();
        mistakes.ignore(mistakes.dueReviews().get(0));
        assertTrue(mistakes.dueReviews().isEmpty());
        assertEquals(before, taskRepo.listSummaries().size());
    }

    @Test void sameTypeMistakesShareOneReviewTask() {
        mistakes.log(Skill.READING, "Matching headings", "one", 1, "", null, 3);
        mistakes.log(Skill.READING, "Matching headings", "two", 2, "", null, 3);
        mistakes.log(Skill.READING, "Multiple choice", "three", 1, "", null, 3);
        time.advance(Duration.ofDays(4));
        var groups = mistakes.dueReviews();
        assertEquals(2, groups.size());
        var headings = groups.stream().filter(g -> g.questionType().equals("Matching headings")).findFirst().orElseThrow();
        assertEquals(2, headings.mistakes().size());
        long before = taskRepo.listSummaries().size();
        mistakes.accept(headings);
        assertEquals(before + 1, taskRepo.listSummaries().size());
        assertEquals(1, mistakes.dueReviews().size());
        assertEquals(LocalDate.of(2026, 10, 28).plusDays(4), taskRepo.listScheduled(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)).get(0).task().scheduledDate(),
                "an overdue suggestion is scheduled for today, not the past");
    }

    @Test void noReviewWhenDaysIsZero() {
        mistakes.log(Skill.WRITING, "Coherence and cohesion", "No paragraphing", 1, "", null, 0);
        time.advance(Duration.ofDays(30));
        assertTrue(mistakes.dueReviews().isEmpty());
    }

    @Test void mistakeValidation() {
        assertThrows(IllegalArgumentException.class, () -> mistakes.log(Skill.VOCABULARY, "Other", "", 1, "", null, 3));
        assertThrows(IllegalArgumentException.class, () -> mistakes.log(Skill.READING, "Spelling", "", 1, "", null, 3), "Spelling is a Listening type");
        assertThrows(IllegalArgumentException.class, () -> mistakes.log(Skill.READING, "Matching headings", "", 0, "", null, 3));
    }

    @Test void questionTypesAreRankedByMarksLost() {
        mistakes.log(Skill.READING, "True/False/Not Given", "", 1, "", null, 0);
        mistakes.log(Skill.READING, "True/False/Not Given", "", 1, "", null, 0);
        mistakes.log(Skill.READING, "True/False/Not Given", "", 1, "", null, 0);
        mistakes.log(Skill.LISTENING, "Spelling", "", 1, "", null, 0);
        mistakes.log(Skill.LISTENING, "Spelling", "", 4, "", null, 0);
        mistakes.log(Skill.WRITING, "Coherence and cohesion", "", 1, "", null, 0);
        var cost = mistakes.cost(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals("Spelling", cost.get(0).questionType());
        assertEquals(5, cost.get(0).marksLost());
        assertEquals(2, cost.get(0).count());
        assertEquals("True/False/Not Given", cost.get(1).questionType());
        assertEquals(3, cost.get(1).marksLost());
        assertTrue(mistakes.cost(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).isEmpty());
    }

    // ---- weekly plan and review ----

    @Test void suggestedSplitFavoursSkillsFurtherFromTarget() {
        result(Skill.WRITING, 20, 6.0);   // gap 1.0
        result(Skill.READING, 20, 7.0);   // gap 0
        result(Skill.LISTENING, 20, 7.0);
        result(Skill.SPEAKING, 20, 7.0);
        Map<Skill, Integer> split = PlanService.suggestedSplit(1200, goals.gaps());
        assertEquals(Skill.WRITING, split.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey());
        assertTrue(split.get(Skill.WRITING) > split.get(Skill.READING));
        assertEquals(1200, split.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test void weeklyReviewSummarisesHoursDaysAndWeakestTypes() {
        studied(reading, 26, 9, 60);      // Monday
        studied(writing, 27, 9, 30);      // Tuesday
        // Wednesday (today) nothing yet
        mistakes.log(Skill.READING, "Matching headings", "", 2, "", null, 0);
        ReviewRepository reviewRepo = new ReviewRepository(db);
        WeeklyReviewService svc = new WeeklyReviewService(stats, goals, settings, mistakes, reviewRepo, time);

        var week = svc.build(LocalDate.of(2026, 10, 26));
        assertEquals(90 * 60_000L, week.totalMillis());
        assertEquals(LocalDate.of(2026, 10, 26), week.bestDay().getKey());
        assertEquals(60 * 60_000L, week.bestDay().getValue());
        assertEquals(LocalDate.of(2026, 10, 28), week.worstDay().getKey(), "today has no study; future days are ignored");
        assertEquals("Matching headings", week.weakestTypes().get(0).questionType());
        assertEquals(60 * 60_000L, week.skills().stream().filter(l -> l.skill() == Skill.READING).findFirst().orElseThrow().actualMillis());
        assertTrue(week.answer().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> svc.saveAnswer(LocalDate.of(2026, 10, 26), "  "));
        svc.saveAnswer(LocalDate.of(2026, 10, 26), "Do Task 1 timed twice");
        svc.saveAnswer(LocalDate.of(2026, 10, 26), "Do Task 1 timed three times");
        assertEquals("Do Task 1 timed three times", svc.build(LocalDate.of(2026, 10, 26)).answer().orElseThrow());
    }

    @Test void weeklyReviewIsDueOnSundayUntilAnswered() {
        WeeklyReviewService svc = new WeeklyReviewService(stats, goals, settings, mistakes, new ReviewRepository(db), time);
        assertFalse(svc.dueToday(), "Wednesday");
        time.advance(Duration.ofDays(4)); // Sunday 1 Nov
        assertTrue(svc.dueToday());
        svc.saveAnswer(LocalDate.of(2026, 10, 26), "Sleep more");
        assertFalse(svc.dueToday());
    }

    @Test void weeklyMinutesPerSkillForTheLastWeeks() {
        studied(writing, 26, 9, 40);
        studied(writing, 19, 9, 20);
        studied(reading, 26, 9, 99);
        Map<LocalDate, Long> w = stats.skillWeeklyMillis(Skill.WRITING, 3);
        assertEquals(3, w.size());
        assertEquals(40 * 60_000L, w.get(LocalDate.of(2026, 10, 26)));
        assertEquals(20 * 60_000L, w.get(LocalDate.of(2026, 10, 19)));
        assertEquals(0L, w.get(LocalDate.of(2026, 10, 12)));
    }

    // ---- writing, speaking, backup, migration ----

    @Test void essayKeepsTextRatingsAndAverage() {
        WritingSpeakingRepository repo = new WritingSpeakingRepository(db);
        long id = repo.saveEssay(new Essay(0, null, "Task 2", "Technology", "Some text here", 3, 2400L, 6.0, 6.5, 6.0, null, "too slow"), time.now());
        Essay e = repo.essay(id).orElseThrow();
        assertEquals("Some text here", e.body());
        assertEquals(3, e.wordCount());
        assertEquals(2400L, e.secondsTaken());
        assertNull(e.grammar());
        assertEquals((6.0 + 6.5 + 6.0) / 3, e.selfAverage(), 1e-9);
        repo.saveEssay(new Essay(id, e.createdAt(), "Task 2", "Technology", "Edited", 1, 2400L, 6.0, 6.5, 6.0, 6.0, ""), time.now());
        assertEquals("Edited", repo.essay(id).orElseThrow().body());
        assertEquals(1, repo.essays().size());
        repo.deleteEssay(id);
        assertTrue(repo.essays().isEmpty());
    }

    @Test void recordingsStoreALocationAndNote() {
        WritingSpeakingRepository repo = new WritingSpeakingRepository(db);
        repo.addRecording(new Recording(0, null, "Part 2", "C:\\Recordings\\part2.m4a", "Stopped after 1 minute"), time.now());
        Recording r = repo.recordings().get(0);
        assertEquals("Part 2", r.part());
        assertEquals("C:\\Recordings\\part2.m4a", r.location());
    }

    @Test void backupCopiesKeepOnlyTheNewestTen() throws Exception {
        Path folder = dir.resolve("sync");
        for (int i = 0; i < 13; i++) {
            BackupService.copy(db, folder, time.now(), ZONE, BackupService.KEEP);
            time.advance(Duration.ofMinutes(5));
        }
        BackupService.copy(db, folder, time.now(), ZONE, BackupService.KEEP);
        BackupService.copy(db, folder, time.now(), ZONE, BackupService.KEEP); // same second twice: must not overwrite
        long count = Files.list(folder).count();
        assertEquals(10, count);
        Path newest = Files.list(folder).max(java.util.Comparator.comparing(p -> p.getFileName().toString())).orElseThrow();
        Database copy = new Database(newest);
        assertEquals("Reading", new TaskRepository(copy).listSummaries().stream().filter(t -> t.task().id() == reading).findFirst().orElseThrow().task().title());
    }

    @Test void backupPrunePreservesUnrelatedFiles() throws Exception {
        Path folder = dir.resolve("sync2");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("holiday-photos.db"), "x");
        for (int i = 0; i < 12; i++) { BackupService.copy(db, folder, time.now(), ZONE, 10); time.advance(Duration.ofMinutes(1)); }
        assertTrue(Files.exists(folder.resolve("holiday-photos.db")));
        assertEquals(11, Files.list(folder).count());
    }

    @Test void upgradingAVersionOneDatabaseKeepsDataAndFoldsOldCategories() throws Exception {
        Path old = dir.resolve("old.db");
        try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + old); var s = c.createStatement()) {
            s.execute("""
                CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL CHECK (length(trim(title)) > 0),
                description TEXT NOT NULL DEFAULT '', skill TEXT NOT NULL, category TEXT NOT NULL DEFAULT '',
                target_duration_seconds INTEGER NOT NULL CHECK (target_duration_seconds > 0), priority TEXT NOT NULL DEFAULT 'MEDIUM',
                status TEXT NOT NULL DEFAULT 'PLANNED', scheduled_date TEXT, scheduled_time TEXT, notes TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, completed_at INTEGER)""");
            s.execute("""
                CREATE TABLE study_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE RESTRICT,
                start_timestamp INTEGER NOT NULL, end_timestamp INTEGER, active_duration_ms INTEGER NOT NULL DEFAULT 0, paused_duration_ms INTEGER NOT NULL DEFAULT 0,
                target_duration_seconds INTEGER NOT NULL DEFAULT 0, timer_mode TEXT NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '',
                recovered_after_interruption INTEGER NOT NULL DEFAULT 0, last_checkpoint INTEGER NOT NULL)""");
            s.execute("CREATE TABLE app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            s.execute("INSERT INTO tasks(title, skill, target_duration_seconds, created_at, updated_at) VALUES('Grammar drill','GRAMMAR',900,1,1)");
            s.execute("INSERT INTO tasks(title, skill, target_duration_seconds, created_at, updated_at) VALUES('Essay','WRITING',900,1,1)");
            s.execute("INSERT INTO study_sessions(task_id,start_timestamp,end_timestamp,active_duration_ms,timer_mode,status,last_checkpoint) VALUES(1,1000,2000,600000,'STOPWATCH','COMPLETED',2000)");
            s.execute("INSERT INTO app_settings VALUES('exam_date','2026-12-09')");
            s.execute("PRAGMA user_version = 1");
        }
        Database db1 = new Database(old);
        db1.migrate();
        assertEquals(2, db1.schemaVersion());
        var all = new TaskRepository(db1).listSummaries();
        assertEquals(2, all.size());
        assertEquals(Skill.VOCABULARY, all.stream().filter(t -> t.task().title().equals("Grammar drill")).findFirst().orElseThrow().task().skill());
        assertEquals(600_000L, all.stream().filter(t -> t.task().title().equals("Grammar drill")).findFirst().orElseThrow().focusedMillis());
        assertEquals("2026-12-09", new SettingsRepository(db1).get("exam_date").orElseThrow());
        assertNull(new SessionRepository(db1).list(SessionRepository.Filter.none()).get(0).preset());
    }
}
