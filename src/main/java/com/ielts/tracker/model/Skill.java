package com.ielts.tracker.model;

import java.util.List;

/** The four IELTS skills plus two fixed study categories. The hex colour is used for badges and bars. */
public enum Skill {
    LISTENING("Listening", "#4FD1C5"),
    READING("Reading", "#7C8CFF"),
    WRITING("Writing", "#F6AD55"),
    SPEAKING("Speaking", "#F687B3"),
    VOCABULARY("Vocabulary", "#9AE6B4"),
    MOCK_TEST("Mock Test", "#FC8181");

    /** The skills that have their own band score and target. */
    public static final List<Skill> BAND_SKILLS = List.of(LISTENING, READING, WRITING, SPEAKING);

    private final String label;
    private final String color;

    Skill(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String label() { return label; }
    public String color() { return color; }
    public boolean hasBand() { return BAND_SKILLS.contains(this); }

    @Override public String toString() { return label; }
}
