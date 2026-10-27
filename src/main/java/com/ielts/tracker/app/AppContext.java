package com.ielts.tracker.app;

import com.ielts.tracker.repository.*;
import com.ielts.tracker.service.*;
import com.ielts.tracker.timer.FocusTimer;
import com.ielts.tracker.timer.IdleMonitor;
import com.ielts.tracker.timer.SessionManager;
import com.ielts.tracker.timer.TimeSource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;

/** Wires the application together with plain constructors; pages receive this object. */
public final class AppContext {
    public final Database database;
    public final TimeSource time;
    public final ZoneId zone;
    public final AppSettings settings;
    public final TaskRepository taskRepository;
    public final SessionRepository sessionRepository;
    public final TaskService tasks;
    public final StatsService stats;
    public final ExportService export;
    public final SessionManager sessions;
    public final SessionService sessionService;
    public final PracticeRepository practiceRepository;
    public final PracticeService practice;
    public final GoalService goals;
    public final MistakeService mistakes;
    public final WritingSpeakingRepository writing;
    public final WeeklyReviewService weekly;
    public final IdleMonitor idle;

    /** Text typed in the focus screen's notes box; kept here so it survives switching pages. */
    public String draftNotes = "";

    private AppContext(Path dbFile) {
        this.database = new Database(dbFile);
        this.database.migrate();
        this.time = TimeSource.SYSTEM;
        this.zone = ZoneId.systemDefault();
        this.settings = new AppSettings(new SettingsRepository(database));
        this.taskRepository = new TaskRepository(database);
        this.sessionRepository = new SessionRepository(database);
        this.tasks = new TaskService(taskRepository, time);
        this.stats = new StatsService(sessionRepository, taskRepository, settings, time, zone);
        this.export = new ExportService(zone);
        this.sessions = new SessionManager(new FocusTimer(time), sessionRepository, time);
        this.sessionService = new SessionService(sessionRepository, taskRepository, time, zone);
        this.practiceRepository = new PracticeRepository(database);
        this.practice = new PracticeService(practiceRepository, time, zone);
        this.goals = new GoalService(practiceRepository, settings, stats);
        this.mistakes = new MistakeService(new MistakeRepository(database), tasks, time, zone);
        this.writing = new WritingSpeakingRepository(database);
        this.weekly = new WeeklyReviewService(stats, goals, settings, mistakes, new ReviewRepository(database), time);
        this.idle = new IdleMonitor(time, Duration.ofMinutes(settings.idleMinutes()));
    }

    public static AppContext open(Path dbFile) { return new AppContext(dbFile); }

    /** Focused time of the session in progress (not yet saved, so not part of any stored total). */
    public long liveMillis() { return sessions.hasActiveSession() ? sessions.timer().activeMillis() : 0; }
}
