package com.ielts.tracker.ui;

public enum PageId {
    DASHBOARD("Dashboard"), TIMER("Focus Timer"), TASKS("Tasks"), HISTORY("Study History"), PRACTICE("Practice Scores"),
    MISTAKES("Mistake Log"), WRITING("Writing & Speaking"), WEEKLY("Weekly Review"), SETTINGS("Settings");

    public final String title;
    PageId(String title) { this.title = title; }
}
