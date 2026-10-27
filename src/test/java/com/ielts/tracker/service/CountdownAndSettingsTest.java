package com.ielts.tracker.service;

import com.ielts.tracker.TestSupport;
import com.ielts.tracker.model.ExamType;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.repository.SettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CountdownAndSettingsTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");
    @TempDir Path dir;

    private static Instant at(int y, int mo, int d, int h, int mi, int s) {
        return ZonedDateTime.of(y, mo, d, h, mi, s, 0, ZONE).toInstant();
    }

    @Test void countsDownInDaysHoursMinutesSeconds() {
        ExamInfo exam = new ExamInfo(LocalDate.of(2026, 12, 9), LocalTime.of(9, 30), ExamType.ACADEMIC, "");
        Countdown c = Countdown.of(exam, at(2026, 10, 28, 1, 5, 25), ZONE);
        // Oct 28 01:05:25 -> Dec 9 09:30:00 = 42 days, 8h 24m 35s
        assertEquals(Countdown.State.UPCOMING, c.state());
        assertEquals(42, c.days());
        assertEquals(8, c.hours());
        assertEquals(24, c.minutes());
        assertEquals(35, c.seconds());
    }

    @Test void missingDateIsNotSet() {
        assertEquals(Countdown.State.NOT_SET, Countdown.of(ExamInfo.none(), Instant.now(), ZONE).state());
    }

    @Test void pastDateIsNeverNegative() {
        ExamInfo exam = new ExamInfo(LocalDate.of(2026, 10, 1), LocalTime.of(9, 0), ExamType.GENERAL, "");
        Countdown c = Countdown.of(exam, at(2026, 10, 28, 12, 0, 0), ZONE);
        assertEquals(Countdown.State.PASSED, c.state());
        assertEquals(0, c.days());
        assertEquals(0, c.hours());
    }

    @Test void examDayWithUnknownTimeIsShownAsExamDay() {
        ExamInfo exam = new ExamInfo(LocalDate.of(2026, 10, 28), null, ExamType.ACADEMIC, "");
        assertEquals(Countdown.State.EXAM_DAY, Countdown.of(exam, at(2026, 10, 28, 7, 0, 0), ZONE).state());
        assertEquals(Countdown.State.PASSED, Countdown.of(exam, at(2026, 10, 29, 7, 0, 0), ZONE).state());
    }

    @Test void examSettingsPersistEditClearAndAcceptANewDate() {
        AppSettings settings = new AppSettings(new SettingsRepository(TestSupport.newDb(dir)));
        assertNull(settings.exam().date());

        settings.saveExam(new ExamInfo(LocalDate.of(2026, 12, 9), LocalTime.of(9, 30), ExamType.GENERAL, "Dhaka centre"));
        ExamInfo e = settings.exam();
        assertEquals(LocalDate.of(2026, 12, 9), e.date());
        assertEquals(LocalTime.of(9, 30), e.time());
        assertEquals(ExamType.GENERAL, e.type());
        assertEquals("Dhaka centre", e.notes());

        settings.saveExam(new ExamInfo(LocalDate.of(2027, 3, 4), null, ExamType.ACADEMIC, ""));
        assertEquals(LocalDate.of(2027, 3, 4), settings.exam().date());
        assertNull(settings.exam().time());

        settings.clearExam();
        assertNull(settings.exam().date());
    }

    @Test void studyPreferencesHaveDefaultsAreValidatedAndPersist() {
        AppSettings settings = new AppSettings(new SettingsRepository(TestSupport.newDb(dir)));
        assertEquals(180, settings.dailyGoalMinutes());
        assertEquals(30, settings.streakMinutes());
        assertEquals(TimerMode.COUNTDOWN, settings.defaultMode());

        assertThrows(IllegalArgumentException.class, () -> settings.saveStudyPreferences(0, 100, 30, TimerMode.COUNTDOWN, 25));
        settings.saveStudyPreferences(240, 1500, 45, TimerMode.STOPWATCH, 50);
        assertEquals(240, settings.dailyGoalMinutes());
        assertEquals(1500, settings.weeklyGoalMinutes());
        assertEquals(45, settings.streakMinutes());
        assertEquals(TimerMode.STOPWATCH, settings.defaultMode());
        assertEquals(50, settings.defaultSessionMinutes());
    }
}
