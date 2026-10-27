package com.ielts.tracker.service;

import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.repository.SessionRepository;
import com.ielts.tracker.repository.TaskRepository;
import com.ielts.tracker.timer.TimeSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/** Session operations that are not part of the live timer: manual entry and "resume last task". */
public final class SessionService {
    public static final int MAX_MANUAL_MINUTES = 12 * 60;

    private final SessionRepository sessions;
    private final TaskRepository tasks;
    private final TimeSource time;
    private final ZoneId zone;

    public SessionService(SessionRepository sessions, TaskRepository tasks, TimeSource time, ZoneId zone) {
        this.sessions = sessions;
        this.tasks = tasks;
        this.time = time;
        this.zone = zone;
    }

    /** Records study done away from the laptop. It is stored in MANUAL mode so it is always distinguishable from timed sessions. */
    public long addManual(long taskId, LocalDate date, LocalTime start, int minutes, String notes) {
        if (date == null || start == null) throw new IllegalArgumentException("Choose the date and the start time.");
        if (minutes < 1 || minutes > MAX_MANUAL_MINUTES) {
            throw new IllegalArgumentException("Duration must be between 1 minute and " + MAX_MANUAL_MINUTES / 60 + " hours.");
        }
        var begin = date.atTime(start).atZone(zone).toInstant();
        if (begin.plus(Duration.ofMinutes(minutes)).isAfter(time.now())) {
            throw new IllegalArgumentException("That session would end in the future. Check the date, start time and duration.");
        }
        if (tasks.find(taskId).isEmpty()) throw new IllegalArgumentException("Choose a task.");
        return sessions.insertManual(taskId, begin, minutes * 60_000L, notes);
    }

    /** The task of the most recent timed session, if it is still a planned task. */
    public Optional<StudySession> lastTimedSession() {
        return sessions.list(SessionRepository.Filter.none()).stream()
                .filter(s -> !s.isManual() && s.status() != com.ielts.tracker.model.SessionStatus.CANCELLED)
                .filter(s -> tasks.find(s.taskId()).map(t -> t.status() == com.ielts.tracker.model.TaskStatus.PLANNED).orElse(false))
                .findFirst();
    }
}
