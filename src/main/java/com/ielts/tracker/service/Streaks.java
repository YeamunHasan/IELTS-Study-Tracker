package com.ielts.tracker.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeSet;

/**
 * A "study day" is a local calendar date whose total focused time reaches the threshold. Only saved focused
 * time counts; scheduled or unsaved time never does. The current streak is not broken by a day that is
 * still in progress: if today has not reached the threshold yet, counting starts from yesterday.
 */
public final class Streaks {
    public record Result(int current, int longest) {}

    private Streaks() {}

    public static Result compute(Map<LocalDate, Long> dailyMillis, long thresholdMillis, LocalDate today) {
        TreeSet<LocalDate> days = new TreeSet<>();
        dailyMillis.forEach((d, ms) -> {
            if (ms >= thresholdMillis && thresholdMillis > 0 && !d.isAfter(today)) days.add(d);
        });

        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : days) {
            run = prev != null && prev.plusDays(1).equals(d) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = d;
        }

        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int current = 0;
        while (days.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        return new Result(current, longest);
    }
}
