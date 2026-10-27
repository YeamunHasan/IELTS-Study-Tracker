package com.ielts.tracker.model;

import java.time.Instant;

/** A Speaking practice recording: where it is (file path or link) plus a short note. The app never copies the audio. */
public record Recording(long id, Instant createdAt, String part, String location, String note) {
}
