package com.ielts.tracker.service;

import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.model.TaskSummary;
import com.ielts.tracker.repository.TaskRepository;
import com.ielts.tracker.timer.TimeSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Validation and business rules for tasks. */
public final class TaskService {
    public static final long MAX_TARGET_SECONDS = 24 * 3600;

    private final TaskRepository tasks;
    private final TimeSource time;

    public TaskService(TaskRepository tasks, TimeSource time) {
        this.tasks = tasks;
        this.time = time;
    }

    public long create(StudyTask draft) {
        validate(draft);
        return tasks.insert(draft, time.now());
    }

    public void update(StudyTask task) {
        validate(task);
        tasks.update(task, time.now());
    }

    public void setStatus(long id, TaskStatus status) { tasks.setStatus(id, status, time.now()); }

    public void reschedule(long id, LocalDate date) { tasks.reschedule(id, date, time.now()); }

    /** Returns true if the task had study history and was archived rather than removed. */
    public boolean delete(long id) { return tasks.deleteOrArchive(id, time.now()); }

    public long duplicate(long id) {
        StudyTask t = tasks.find(id).orElseThrow(() -> new IllegalArgumentException("Task not found."));
        StudyTask copy = new StudyTask(0, t.title() + " (copy)", t.description(), t.skill(), t.category(), t.targetSeconds(),
                t.priority(), TaskStatus.PLANNED, t.scheduledDate(), t.scheduledTime(), t.notes(), null, null, null);
        return tasks.insert(copy, time.now());
    }

    public Optional<StudyTask> find(long id) { return tasks.find(id); }

    public List<TaskSummary> list() { return tasks.listSummaries(); }

    public List<TaskSummary> scheduled(LocalDate from, LocalDate to) { return tasks.listScheduled(from, to); }

    public List<TaskSummary> overdue(LocalDate today) { return tasks.listOverdue(today); }

    private static void validate(StudyTask t) {
        if (t.title() == null || t.title().isBlank()) throw new IllegalArgumentException("Please enter a task title.");
        if (t.skill() == null) throw new IllegalArgumentException("Please choose an IELTS skill.");
        if (t.targetSeconds() < 60) throw new IllegalArgumentException("The target duration must be at least 1 minute.");
        if (t.targetSeconds() > MAX_TARGET_SECONDS) throw new IllegalArgumentException("The target duration cannot exceed 24 hours.");
    }
}
