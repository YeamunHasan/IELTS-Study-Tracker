package com.ielts.tracker.model;

import java.util.List;

/**
 * Exam-accurate timings. A preset runs as one ordinary countdown session whose length is the sum of its
 * segments, so recovery, pausing and totals work exactly as for any other session. The segment list only
 * decides what the screen says is happening now. "Laps" are the buttons for marking a passage or section done.
 */
public enum MockPreset {
    LISTENING("listening", "Listening test (30 min + 10 min transfer)", Skill.LISTENING,
            List.of(new Segment("Listening", 30 * 60), new Segment("Transfer answers to the answer sheet", 10 * 60)),
            List.of("Section 1", "Section 2", "Section 3", "Section 4")),
    READING("reading", "Reading test (60 min)", Skill.READING,
            List.of(new Segment("Reading", 60 * 60)),
            List.of("Passage 1", "Passage 2", "Passage 3")),
    WRITING("writing", "Writing test (60 min)", Skill.WRITING,
            List.of(new Segment("Writing (aim to finish Task 1 by 20:00)", 60 * 60)),
            List.of("Task 1 finished")),
    SPEAKING_1("speaking1", "Speaking Part 1 (5 min)", Skill.SPEAKING,
            List.of(new Segment("Part 1: introduction and interview", 5 * 60)), List.of()),
    SPEAKING_2("speaking2", "Speaking Part 2 (1 min prep + 2 min talk)", Skill.SPEAKING,
            List.of(new Segment("Part 2: preparation", 60), new Segment("Part 2: long turn", 2 * 60)), List.of()),
    SPEAKING_3("speaking3", "Speaking Part 3 (5 min)", Skill.SPEAKING,
            List.of(new Segment("Part 3: discussion", 5 * 60)), List.of()),
    SPEAKING_FULL("speakingfull", "Speaking full test (13 min)", Skill.SPEAKING,
            List.of(new Segment("Part 1: introduction and interview", 5 * 60), new Segment("Part 2: preparation", 60),
                    new Segment("Part 2: long turn", 2 * 60), new Segment("Part 3: discussion", 5 * 60)), List.of());

    public record Segment(String name, int seconds) {}

    private final String id;
    private final String label;
    private final Skill skill;
    private final List<Segment> segments;
    private final List<String> laps;

    MockPreset(String id, String label, Skill skill, List<Segment> segments, List<String> laps) {
        this.id = id;
        this.label = label;
        this.skill = skill;
        this.segments = segments;
        this.laps = laps;
    }

    public String id() { return id; }
    public Skill skill() { return skill; }
    public List<Segment> segments() { return segments; }
    public List<String> laps() { return laps; }

    public int totalSeconds() { return segments.stream().mapToInt(Segment::seconds).sum(); }

    /** Index of the segment that is current after {@code activeSeconds} of focused time (the last one once over). */
    public int segmentIndexAt(long activeSeconds) {
        long edge = 0;
        for (int i = 0; i < segments.size(); i++) {
            edge += segments.get(i).seconds();
            if (activeSeconds < edge) return i;
        }
        return segments.size() - 1;
    }

    /** Seconds left in the segment that is current after {@code activeSeconds}; zero once the preset is over. */
    public long segmentRemainingSeconds(long activeSeconds) {
        long edge = 0;
        for (Segment s : segments) {
            edge += s.seconds();
            if (activeSeconds < edge) return edge - activeSeconds;
        }
        return 0;
    }

    public static MockPreset byId(String id) {
        if (id == null) return null;
        for (MockPreset p : values()) if (p.id.equals(id)) return p;
        return null;
    }

    @Override public String toString() { return label; }
}
