package com.ielts.tracker.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Fmt {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private Fmt() {}

    /** 3725000 ms -> "1h 02m". Seconds are dropped, never rounded up. */
    public static String hoursMinutes(long millis) {
        long totalMinutes = Math.max(0, millis) / 60_000;
        return totalMinutes >= 60
                ? String.format("%dh %02dm", totalMinutes / 60, totalMinutes % 60)
                : totalMinutes + "m";
    }

    /** Like {@link #hoursMinutes} but shows seconds below one minute, so short sessions never read as "0m". */
    public static String compact(long millis) {
        return millis < 60_000 ? (Math.max(0, millis) / 1000) + "s" : hoursMinutes(millis);
    }

    /** 3725000 ms -> "01:02:05". */
    public static String clock(long millis) {
        long s = Math.max(0, millis) / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    public static String date(java.time.LocalDate d) { return d == null ? "" : DATE.format(d); }
    public static String time(java.time.LocalTime t) { return t == null ? "" : TIME.format(t); }
    public static String dateTime(Instant i, ZoneId zone) { return i == null ? "" : DATE_TIME.format(i.atZone(zone)); }
    public static String clockTime(Instant i, ZoneId zone) { return i == null ? "" : TIME.format(i.atZone(zone)); }
    public static String shortDate(Instant i, ZoneId zone) { return i == null ? "" : DATE.format(i.atZone(zone)); }

    public static String percent(double fraction) { return Math.round(fraction * 100) + "%"; }
}
