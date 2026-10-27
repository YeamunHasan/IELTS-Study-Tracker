package com.ielts.tracker.service;

import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.model.TaskSummary;
import com.ielts.tracker.repository.SessionRepository;
import com.ielts.tracker.repository.TaskRepository;
import com.ielts.tracker.timer.TimeSource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * All totals are computed on demand from saved sessions; nothing is cached or stored twice, so daily,
 * weekly, skill and task figures always reconcile. A session belongs to the local calendar date on which it
 * started (in the configured zone). Cancelled and still-open sessions are excluded.
 */
public final class StatsService {

    public record TaskTotal(long taskId, String title, Skill skill, long millis, int sessions) {}

    public record RangeSummary(long totalMillis, int sessions, int interruptedSessions, int activeDays, long avgPerActiveDayMillis,
                               long longestSessionMillis, long shortestSessionMillis) {}

    public record DaySummary(long focusedMillis, long goalMillis, int sessions, int scheduledTasks, int completedTasks) {
        public long remainingMillis() { return Math.max(0, goalMillis - focusedMillis); }
        public double goalFraction() { return goalMillis <= 0 ? 0 : (double) focusedMillis / goalMillis; }
    }

    public record PlanVsActual(LocalDate date, long plannedMillis, long actualMillis) {}

    private static final LocalDate EPOCH_START = LocalDate.of(2000, 1, 1);

    private final SessionRepository sessions;
    private final TaskRepository tasks;
    private final AppSettings settings;
    private final TimeSource time;
    private final ZoneId zone;

    public StatsService(SessionRepository sessions, TaskRepository tasks, AppSettings settings, TimeSource time, ZoneId zone) {
        this.sessions = sessions;
        this.tasks = tasks;
        this.settings = settings;
        this.time = time;
        this.zone = zone;
    }

    public ZoneId zone() { return zone; }

    public LocalDate today() { return time.now().atZone(zone).toLocalDate(); }

    public List<StudySession> sessions(LocalDate from, LocalDate to) {
        return sessions.listCounted(startMillis(from), startMillis(to.plusDays(1)));
    }

    /** Focused milliseconds per local date, with every date in the range present (zero when no study). */
    public Map<LocalDate, Long> dailyTotals(LocalDate from, LocalDate to) {
        Map<LocalDate, Long> out = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) out.put(d, 0L);
        for (StudySession s : sessions(from, to)) out.merge(dateOf(s), s.activeMillis(), Long::sum);
        return out;
    }

    public Map<Skill, Long> skillTotals(LocalDate from, LocalDate to) {
        Map<Skill, Long> out = new EnumMap<>(Skill.class);
        for (StudySession s : sessions(from, to)) out.merge(s.skill(), s.activeMillis(), Long::sum);
        return out;
    }

    public List<TaskTotal> taskTotals(LocalDate from, LocalDate to) {
        Map<Long, long[]> acc = new LinkedHashMap<>();
        Map<Long, StudySession> sample = new LinkedHashMap<>();
        for (StudySession s : sessions(from, to)) {
            long[] a = acc.computeIfAbsent(s.taskId(), k -> new long[2]);
            a[0] += s.activeMillis();
            a[1]++;
            sample.putIfAbsent(s.taskId(), s);
        }
        List<TaskTotal> out = new ArrayList<>();
        acc.forEach((id, a) -> out.add(new TaskTotal(id, sample.get(id).taskTitle(), sample.get(id).skill(), a[0], (int) a[1])));
        out.sort(Comparator.comparingLong(TaskTotal::millis).reversed());
        return out;
    }

    public RangeSummary summary(LocalDate from, LocalDate to) {
        List<StudySession> list = sessions(from, to);
        long total = 0, longest = 0, shortest = Long.MAX_VALUE;
        int interrupted = 0;
        Map<LocalDate, Long> perDay = new TreeMap<>();
        for (StudySession s : list) {
            total += s.activeMillis();
            longest = Math.max(longest, s.activeMillis());
            shortest = Math.min(shortest, s.activeMillis());
            if (s.status() == com.ielts.tracker.model.SessionStatus.INTERRUPTED) interrupted++;
            perDay.merge(dateOf(s), s.activeMillis(), Long::sum);
        }
        int activeDays = (int) perDay.values().stream().filter(v -> v > 0).count();
        return new RangeSummary(total, list.size(), interrupted, activeDays,
                activeDays == 0 ? 0 : total / activeDays, longest, list.isEmpty() ? 0 : shortest);
    }

    public DaySummary today(long liveMillis) {
        LocalDate today = today();
        long focused = dailyTotals(today, today).get(today) + Math.max(0, liveMillis);
        int count = sessions(today, today).size();
        List<TaskSummary> scheduled = tasks.listScheduled(today, today);
        int completed = (int) scheduled.stream().filter(t -> t.task().status() == TaskStatus.COMPLETED).count();
        return new DaySummary(focused, settings.dailyGoalMinutes() * 60_000L, count, scheduled.size(), completed);
    }

    /** Planned time (sum of targets of tasks scheduled that day, excluding skipped) versus focused time. */
    public List<PlanVsActual> plannedVsActual(LocalDate from, LocalDate to) {
        Map<LocalDate, Long> actual = dailyTotals(from, to);
        Map<LocalDate, Long> planned = new TreeMap<>();
        for (TaskSummary ts : tasks.listScheduled(from, to)) {
            StudyTask t = ts.task();
            if (t.status() == TaskStatus.SKIPPED) continue;
            planned.merge(t.scheduledDate(), t.targetSeconds() * 1000, Long::sum);
        }
        List<PlanVsActual> out = new ArrayList<>();
        actual.forEach((d, ms) -> out.add(new PlanVsActual(d, planned.getOrDefault(d, 0L), ms)));
        return out;
    }

    /** Focused milliseconds for one skill in each of the last {@code weeks} weeks (Monday-based), oldest first. */
    public Map<LocalDate, Long> skillWeeklyMillis(com.ielts.tracker.model.Skill skill, int weeks) {
        LocalDate thisWeek = GoalService.weekStart(today());
        Map<LocalDate, Long> out = new TreeMap<>();
        for (int i = weeks - 1; i >= 0; i--) out.put(thisWeek.minusWeeks(i), 0L);
        for (StudySession s : sessions(thisWeek.minusWeeks(weeks - 1), today())) {
            if (s.skill() == skill) out.merge(GoalService.weekStart(dateOf(s)), s.activeMillis(), Long::sum);
        }
        return out;
    }

    public Streaks.Result streaks() {
        LocalDate today = today();
        Map<LocalDate, Long> all = new TreeMap<>();
        for (StudySession s : sessions(EPOCH_START, today)) all.merge(dateOf(s), s.activeMillis(), Long::sum);
        return Streaks.compute(all, settings.streakMinutes() * 60_000L, today);
    }

    public LocalDate dateOf(StudySession s) { return s.start().atZone(zone).toLocalDate(); }

    private long startMillis(LocalDate d) { return d.atStartOfDay(zone).toInstant().toEpochMilli(); }
}
