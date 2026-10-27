package com.ielts.tracker.model;

import java.time.Instant;
import java.time.LocalDate;

public record Mistake(long id, Instant loggedAt, Skill skill, String questionType, String reason, int marksLost, String source,
                      Long practiceResultId, LocalDate reviewDue, ReviewStatus reviewStatus, Long reviewTaskId) {
}
