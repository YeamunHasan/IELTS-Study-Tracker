package com.ielts.tracker.model;

import java.time.Instant;

/** A saved piece of Writing with self ratings (bands 0..9 in half steps, null when not rated) on the four official criteria. */
public record Essay(long id, Instant createdAt, String taskType, String title, String body, int wordCount, Long secondsTaken,
                    Double taskResponse, Double coherence, Double lexical, Double grammar, String note) {

    /** Mean of the ratings given, or null if none. Not an official score. */
    public Double selfAverage() {
        double sum = 0;
        int n = 0;
        for (Double d : new Double[]{taskResponse, coherence, lexical, grammar}) {
            if (d != null) { sum += d; n++; }
        }
        return n == 0 ? null : sum / n;
    }
}
