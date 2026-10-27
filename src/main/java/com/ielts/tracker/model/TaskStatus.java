package com.ielts.tracker.model;

public enum TaskStatus {
    PLANNED("Planned"), COMPLETED("Completed"), SKIPPED("Skipped"),
    /** Soft-deleted: hidden from lists, but its sessions stay in history and reports. */
    ARCHIVED("Archived");

    private final String label;
    TaskStatus(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
