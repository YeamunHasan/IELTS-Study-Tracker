package com.ielts.tracker.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreaksTest {
    private static final long MIN = 60_000;
    private final LocalDate today = LocalDate.of(2026, 10, 28);

    private Map<LocalDate, Long> days(Object... pairs) {
        Map<LocalDate, Long> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put(today.minusDays((Integer) pairs[i]), (Integer) pairs[i + 1] * MIN);
        return m;
    }

    @Test void noDataMeansNoStreak() {
        assertEquals(new Streaks.Result(0, 0), Streaks.compute(Map.of(), 30 * MIN, today));
    }

    @Test void consecutiveDaysIncludingToday() {
        assertEquals(new Streaks.Result(3, 3), Streaks.compute(days(0, 40, 1, 30, 2, 90), 30 * MIN, today));
    }

    @Test void todayInProgressDoesNotBreakTheStreak() {
        assertEquals(new Streaks.Result(2, 2), Streaks.compute(days(0, 5, 1, 30, 2, 31), 30 * MIN, today));
    }

    @Test void aMissedDayBreaksTheCurrentStreakButNotTheLongest() {
        // yesterday missed; days 2,3,4,5 form the longest run
        assertEquals(new Streaks.Result(0, 4), Streaks.compute(days(2, 30, 3, 30, 4, 30, 5, 30), 30 * MIN, today));
    }

    @Test void thresholdIsInclusiveAndConfigurable() {
        assertEquals(new Streaks.Result(1, 1), Streaks.compute(days(0, 30), 30 * MIN, today));
        assertEquals(new Streaks.Result(0, 0), Streaks.compute(days(0, 29), 30 * MIN, today));
        assertEquals(new Streaks.Result(1, 1), Streaks.compute(days(0, 29), 15 * MIN, today));
    }

    @Test void futureDatesAreIgnored() {
        Map<LocalDate, Long> m = days(0, 30);
        m.put(today.plusDays(1), 99 * MIN);
        assertEquals(new Streaks.Result(1, 1), Streaks.compute(m, 30 * MIN, today));
    }
}
