package com.ielts.tracker.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record StudyTask(
        long id,
        String title,
        String description,
        Skill skill,
        String category,
        long targetSeconds,
        Priority priority,
        TaskStatus status,
        LocalDate scheduledDate,
        LocalTime scheduledTime,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    @Override public String toString() { return title; }
}
