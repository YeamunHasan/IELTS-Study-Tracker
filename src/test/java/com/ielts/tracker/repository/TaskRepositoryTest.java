package com.ielts.tracker.repository;

import com.ielts.tracker.TestSupport;
import com.ielts.tracker.model.Priority;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.service.TaskService;
import com.ielts.tracker.timer.TimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TaskRepositoryTest {
    @TempDir Path dir;
    private Database db;
    private TaskRepository tasks;
    private SessionRepository sessions;
    private TaskService service;
    private final Instant now = Instant.parse("2026-10-28T09:00:00Z");

    @BeforeEach void setUp() {
        db = TestSupport.newDb(dir);
        tasks = new TaskRepository(db);
        sessions = new SessionRepository(db);
        TimeSource fixed = new TimeSource() {
            public long nanoTime() { return 0; }
            public Instant now() { return now; }
        };
        service = new TaskService(tasks, fixed);
    }

    private long taskWithSession(String title) {
        long id = tasks.insert(TestSupport.draft(title, Skill.LISTENING, 30), now);
        long sid = sessions.insertActive(id, now, TimerMode.STOPWATCH, 0);
        sessions.close(sid, com.ielts.tracker.model.SessionStatus.COMPLETED, now.plusSeconds(600), 600_000, 0, "n", false);
        return id;
    }

    @Test void taskRoundTripKeepsAllFields() {
        StudyTask draft = new StudyTask(0, "Cambridge 18 Reading Test 2", "desc", Skill.READING, "Timed Practice", 3600,
                Priority.HIGH, TaskStatus.PLANNED, LocalDate.of(2026, 10, 28), java.time.LocalTime.of(9, 0), "notes", null, null, null);
        long id = tasks.insert(draft, now);
        StudyTask t = tasks.find(id).orElseThrow();
        assertEquals("Cambridge 18 Reading Test 2", t.title());
        assertEquals(Skill.READING, t.skill());
        assertEquals(3600, t.targetSeconds());
        assertEquals(Priority.HIGH, t.priority());
        assertEquals(LocalDate.of(2026, 10, 28), t.scheduledDate());
        assertEquals(java.time.LocalTime.of(9, 0), t.scheduledTime());
        assertEquals(now, t.createdAt());
    }

    @Test void editingATaskKeepsItsSessionHistory() {
        long id = taskWithSession("Old title");
        StudyTask t = tasks.find(id).orElseThrow();
        service.update(new StudyTask(t.id(), "New title", t.description(), Skill.SPEAKING, t.category(), 1800, t.priority(),
                t.status(), null, null, t.notes(), t.createdAt(), t.updatedAt(), null));
        var summary = tasks.listSummaries().get(0);
        assertEquals("New title", summary.task().title());
        assertEquals(1, summary.sessionCount());
        assertEquals(600_000, summary.focusedMillis());
        assertEquals("New title", sessions.list(SessionRepository.Filter.none()).get(0).taskTitle());
    }

    @Test void deletingTaskWithHistoryArchivesItAndKeepsSessions() {
        long id = taskWithSession("Studied");
        assertTrue(service.delete(id), "archived rather than deleted");
        assertTrue(tasks.listSummaries().isEmpty(), "hidden from the task list");
        assertEquals(1, sessions.listCounted(0, Long.MAX_VALUE).size(), "history intact");
        assertEquals(TaskStatus.ARCHIVED, tasks.find(id).orElseThrow().status());
    }

    @Test void deletingTaskWithoutHistoryRemovesIt() {
        long id = tasks.insert(TestSupport.draft("Never studied", Skill.VOCABULARY, 15), now);
        assertFalse(service.delete(id));
        assertTrue(tasks.find(id).isEmpty());
    }

    @Test void databaseRefusesToDeleteTaskThatHasSessions() {
        long id = taskWithSession("Protected");
        assertThrows(DataAccessException.class, () -> db.inTransaction(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
                p.setLong(1, id);
                return p.executeUpdate();
            }
        }));
    }

    @Test void completingATaskIsExplicitAndRecorded() {
        long id = tasks.insert(TestSupport.draft("Vocab", Skill.VOCABULARY, 20), now);
        service.setStatus(id, TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, tasks.find(id).orElseThrow().status());
        assertNotNull(tasks.find(id).orElseThrow().completedAt());
        service.setStatus(id, TaskStatus.PLANNED);
        assertNull(tasks.find(id).orElseThrow().completedAt());
    }

    @Test void validationRejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> service.create(TestSupport.draft("  ", Skill.READING, 10)));
        assertThrows(IllegalArgumentException.class, () -> service.create(TestSupport.draft("x", Skill.READING, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.create(TestSupport.draft("x", Skill.READING, 25 * 60)));
    }

    @Test void duplicateCreatesAFreshPlannedCopyWithoutHistory() {
        long id = taskWithSession("Original");
        service.setStatus(id, TaskStatus.COMPLETED);
        long copyId = service.duplicate(id);
        StudyTask copy = tasks.find(copyId).orElseThrow();
        assertEquals("Original (copy)", copy.title());
        assertEquals(TaskStatus.PLANNED, copy.status());
        assertEquals(0, tasks.listSummaries().stream().filter(s -> s.task().id() == copyId).findFirst().orElseThrow().sessionCount());
    }

    @Test void overdueListsOnlyPlannedTasksBeforeToday() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        long pastId = service.create(new StudyTask(0, "Past", "", Skill.READING, "", 600, Priority.LOW, TaskStatus.PLANNED, today.minusDays(2), null, "", null, null, null));
        long doneId = service.create(new StudyTask(0, "Done", "", Skill.READING, "", 600, Priority.LOW, TaskStatus.PLANNED, today.minusDays(1), null, "", null, null, null));
        service.setStatus(doneId, TaskStatus.COMPLETED);
        service.create(new StudyTask(0, "Today", "", Skill.READING, "", 600, Priority.LOW, TaskStatus.PLANNED, today, null, "", null, null, null));
        var overdue = service.overdue(today);
        assertEquals(1, overdue.size());
        assertEquals(pastId, overdue.get(0).task().id());
        service.reschedule(pastId, today);
        assertTrue(service.overdue(today).isEmpty());
    }
}
