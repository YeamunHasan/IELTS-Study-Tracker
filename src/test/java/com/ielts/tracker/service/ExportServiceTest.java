package com.ielts.tracker.service;

import com.ielts.tracker.TestSupport;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.SessionStatus;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.repository.SessionRepository;
import com.ielts.tracker.repository.TaskRepository;
import com.ielts.tracker.util.Csv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExportServiceTest {
    @TempDir Path dir;

    @Test void csvEscapesCommasQuotesAndNewlines() {
        assertEquals("plain", Csv.escape("plain"));
        assertEquals("\"a,b\"", Csv.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""));
        assertEquals("\"line1\nline2\"", Csv.escape("line1\nline2"));
        assertEquals("", Csv.escape(null));
    }

    @Test void exportsValidCsvWithHeaderAndOneRowPerSession() throws Exception {
        var db = TestSupport.newDb(dir);
        long task = new TaskRepository(db).insert(TestSupport.draft("Essay, Task 2 \"Opinion\"", Skill.WRITING, 40), Instant.now());
        SessionRepository sessions = new SessionRepository(db);
        Instant start = Instant.parse("2026-10-28T03:00:00Z");
        long id = sessions.insertActive(task, start, TimerMode.COUNTDOWN, 2400);
        sessions.close(id, SessionStatus.COMPLETED, start.plusSeconds(2500), 2_500_000, 0, "Good,\nbut slow", false);

        Path out = dir.resolve("history.csv");
        new ExportService(ZoneId.of("Asia/Dhaka")).exportHistory(out, sessions.list(SessionRepository.Filter.none()));

        String text = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("﻿Session ID,Date,Start,End,Task,Skill"));
        assertTrue(text.contains("\"Essay, Task 2 \"\"Opinion\"\"\""));
        assertTrue(text.contains("2026-10-28,09:00"), "uses the configured local timezone");
        assertTrue(text.contains(",2500,41.67,40.00,COUNTDOWN,COMPLETED,no,\"Good,\nbut slow\""));
        assertEquals(2, List.of(text.split("\r\n")).stream().filter(l -> !l.startsWith("but")).count());
    }
}
