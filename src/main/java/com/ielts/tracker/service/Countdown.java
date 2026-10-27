package com.ielts.tracker.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Time left until the exam, never negative. */
public record Countdown(State state, long days, int hours, int minutes, int seconds) {

    public enum State { NOT_SET, UPCOMING, EXAM_DAY, PASSED }

    private static final Countdown NOT_SET = new Countdown(State.NOT_SET, 0, 0, 0, 0);

    public static Countdown of(ExamInfo exam, Instant now, ZoneId zone) {
        LocalDate date = exam.date();
        if (date == null) return NOT_SET;

        LocalTime time = exam.time();
        ZonedDateTime target = date.atTime(time == null ? LocalTime.MIDNIGHT : time).atZone(zone);
        Duration left = Duration.between(now, target.toInstant());

        if (left.isNegative() || left.isZero()) {
            boolean sameDayAndTimeUnknown = time == null && now.atZone(zone).toLocalDate().equals(date);
            return new Countdown(sameDayAndTimeUnknown ? State.EXAM_DAY : State.PASSED, 0, 0, 0, 0);
        }
        long total = left.getSeconds();
        return new Countdown(State.UPCOMING, total / 86_400, (int) (total % 86_400 / 3600), (int) (total % 3600 / 60), (int) (total % 60));
    }
}
