package com.ielts.tracker.model;

import java.time.LocalDate;

/** One mock or section test. {@code rawScore} (out of 40) is only used for Listening and Reading; band is entered by hand. */
public record PracticeResult(long id, LocalDate testDate, String source, Skill skill, Integer rawScore, double band,
                             Long secondsTaken, Long sessionId, String note) {
}
