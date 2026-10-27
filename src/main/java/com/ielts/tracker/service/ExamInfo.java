package com.ielts.tracker.service;

import com.ielts.tracker.model.ExamType;

import java.time.LocalDate;
import java.time.LocalTime;

/** The configured IELTS exam. The date may be null (not set) and the time may be null (unknown). */
public record ExamInfo(LocalDate date, LocalTime time, ExamType type, String notes) {
    public static ExamInfo none() { return new ExamInfo(null, null, ExamType.ACADEMIC, ""); }
}
