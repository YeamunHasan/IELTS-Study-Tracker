package com.ielts.tracker.model;

public enum ExamType {
    ACADEMIC("Academic"), GENERAL("General Training");

    private final String label;
    ExamType(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
