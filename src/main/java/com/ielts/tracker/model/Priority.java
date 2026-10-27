package com.ielts.tracker.model;

public enum Priority {
    LOW("Low"), MEDIUM("Medium"), HIGH("High");

    private final String label;
    Priority(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
