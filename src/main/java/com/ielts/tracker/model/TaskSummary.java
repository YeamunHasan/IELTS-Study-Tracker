package com.ielts.tracker.model;

import java.time.Instant;

/** A task plus totals derived from its saved sessions. */
public record TaskSummary(StudyTask task, long focusedMillis, int sessionCount, Instant lastStudied) {
}
