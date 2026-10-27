package com.ielts.tracker.service;

import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.util.Csv;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExportService {
    private final ZoneId zone;

    public ExportService(ZoneId zone) { this.zone = zone; }

    public void exportHistory(Path file, List<StudySession> sessions) throws IOException {
        List<String> header = List.of("Session ID", "Date", "Start", "End", "Task", "Skill", "Focused seconds", "Focused minutes",
                "Target minutes", "Timer mode", "Status", "Recovered after interruption", "Notes");
        List<List<String>> rows = new ArrayList<>();
        for (StudySession s : sessions) {
            var start = s.start().atZone(zone);
            rows.add(List.of(
                    Long.toString(s.id()),
                    start.toLocalDate().toString(),
                    start.toLocalTime().withNano(0).toString(),
                    s.end() == null ? "" : s.end().atZone(zone).toLocalTime().withNano(0).toString(),
                    s.taskTitle(), s.skill().label(),
                    Long.toString(s.activeMillis() / 1000),
                    String.format(Locale.ROOT, "%.2f", s.activeMillis() / 60000.0),
                    String.format(Locale.ROOT, "%.2f", s.targetSeconds() / 60.0),
                    s.mode().name(), s.status().name(), s.recovered() ? "yes" : "no",
                    s.notes() == null ? "" : s.notes()));
        }
        Csv.write(file, header, rows);
    }
}
