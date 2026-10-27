package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.SessionStatus;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.model.TaskSummary;
import com.ielts.tracker.repository.SessionRepository;
import com.ielts.tracker.service.Countdown;
import com.ielts.tracker.service.ExamInfo;
import com.ielts.tracker.service.GoalService;
import com.ielts.tracker.service.MistakeService;
import com.ielts.tracker.service.StatsService;
import com.ielts.tracker.service.Streaks;
import com.ielts.tracker.util.Fmt;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Home screen. The first thing on it is how far each skill is from its target band, and what to do about it. */
public final class DashboardPage implements Page {
    private final AppContext ctx;
    private final Navigator nav;
    private final LocalDate today;

    private final ExamInfo exam;
    private final Label examDays = Ui.label("--", "count-value");
    private final Label examHours = Ui.label("--", "count-value");
    private final Label examMinutes = Ui.label("--", "count-value");
    private final Label examSeconds = Ui.label("--", "count-value");
    private final Label examSummary = Ui.label("", "muted");

    private final StatsService.DaySummary base;
    private final Streaks.Result streaks;
    private final Label focusedValue = Ui.label("", "big-number");
    private final Label goalText = Ui.label("", "muted");
    private final ProgressBar goalBar = new ProgressBar(0);
    private final Label tGoal = new Label(), tRemaining = new Label(), tPercent = new Label();
    private final Label tTasks = new Label(), tSessions = new Label(), tStreak = new Label();
    private final Label liveNote = Ui.wrapped("", "muted-small");

    private final VBox root;

    public DashboardPage(AppContext ctx, Navigator nav) {
        this.ctx = ctx;
        this.nav = nav;
        this.today = ctx.stats.today();
        this.exam = ctx.settings.exam();
        this.base = ctx.stats.today(0);
        this.streaks = ctx.stats.streaks();

        root = Ui.pageBox("Dashboard", "Calculated from your saved sessions and practice results.");
        if (ctx.weekly.dueToday()) root.getChildren().add(sundayBanner());
        root.getChildren().add(headlineCard());
        root.getChildren().add(twoColumns(examCard(), todayCard(), 400, 580));
        List<MistakeService.ReviewGroup> reviews = ctx.mistakes.dueReviews();
        if (!reviews.isEmpty()) root.getChildren().add(reviewCard(reviews));
        root.getChildren().add(twoColumns(scheduleCard(), recentCard(), 600, 380));
        tick();
    }

    @Override public Node root() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    private static Node twoColumns(VBox left, VBox right, double leftWidth, double rightWidth) {
        left.setPrefWidth(leftWidth);
        right.setPrefWidth(rightWidth);
        HBox row = new HBox(18, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        return row;
    }

    // ---- headline: gap to target, and the nudge ----

    private Node sundayBanner() {
        Button open = Ui.primary("Open weekly review");
        open.setOnAction(e -> nav.open(PageId.WEEKLY));
        HBox box = new HBox(12, Ui.wrapped("It's Sunday. Your weekly review is ready: it takes a minute and ends with one sentence about next week.", "banner-text"), Ui.grow(), open);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("banner");
        return box;
    }

    private VBox headlineCard() {
        List<GoalService.SkillGap> gaps = ctx.goals.gaps();
        var furthest = ctx.goals.furthest();
        boolean anyResult = gaps.stream().anyMatch(GoalService.SkillGap::hasResult);

        Label headline;
        if (furthest.isPresent()) {
            var g = furthest.get();
            headline = Ui.wrapped(g.skill().label() + " is furthest from your target: " + Bands(g.latestBand()) + " now, target " + Bands(g.target())
                    + " (gap " + Bands(g.gap()) + "). " + g.minutesThisWeek() + " min this week.", "headline");
        } else if (anyResult) {
            headline = Ui.wrapped("Every skill with a result is at or above its target. Keep practising to stay there.", "headline");
        } else {
            headline = Ui.wrapped("Add a practice result to see how far each skill is from your target band.", "headline");
        }

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        if (ctx.sessions.hasActiveSession()) {
            Button open = Ui.primary("Session in progress: open timer");
            open.setOnAction(e -> nav.open(PageId.TIMER));
            actions.getChildren().add(open);
        } else {
            if (furthest.isPresent()) {
                Skill s = furthest.get().skill();
                Button start = Ui.primary("Start " + s.label());
                start.setOnAction(e -> startSkill(s));
                actions.getChildren().add(start);
            }
            var last = ctx.sessionService.lastTimedSession();
            Button resume = last.isPresent() ? Ui.secondary("Resume: " + shorten(last.get().taskTitle())) : Ui.secondary("Start a session");
            resume.setOnAction(e -> { if (last.isPresent()) nav.resumeLastTask(); else nav.open(PageId.TIMER); });
            if (furthest.isEmpty()) {
                resume.getStyleClass().remove("btn-secondary");
                resume.getStyleClass().add("btn-primary");
            }
            actions.getChildren().add(resume);
        }
        if (!anyResult) {
            Button add = Ui.secondary("Add a practice result");
            add.setOnAction(e -> nav.open(PageId.PRACTICE));
            actions.getChildren().add(add);
        }

        FlowPane cards = new FlowPane(12, 12);
        for (GoalService.SkillGap g : gaps) cards.getChildren().add(gapCard(g));

        return Ui.card(Ui.label("TARGET BAND GAP", "eyebrow"), headline, actions, cards);
    }

    private static String Bands(double v) { return String.format("%.1f", v); }

    private static String shorten(String s) { return s.length() > 34 ? s.substring(0, 33) + "…" : s; }

    private void startSkill(Skill s) {
        TaskSummary next = ctx.tasks.list().stream()
                .filter(t -> t.task().status() == TaskStatus.PLANNED && t.task().skill() == s)
                .min(java.util.Comparator.comparing((TaskSummary t) -> t.task().scheduledDate() == null ? LocalDate.MAX : t.task().scheduledDate()))
                .orElse(null);
        if (next != null) {
            nav.startTimerFor(next.task().id());
        } else {
            nav.toast("No planned " + s.label() + " task yet. Add one on the Tasks page.");
            nav.open(PageId.TASKS);
        }
    }

    private VBox gapCard(GoalService.SkillGap g) {
        Label values = Ui.label(g.hasResult() ? Bands(g.latestBand()) + "  →  " + Bands(g.target()) : "no result  →  " + Bands(g.target()), "gap-values");
        Label gap;
        if (!g.hasResult()) gap = Ui.label("Log a practice result", "muted-small");
        else if (g.belowTarget()) gap = Ui.label("Gap " + Bands(g.gap()) + " below target", "gap-below");
        else gap = Ui.label("On target", "gap-ok");
        VBox card = new VBox(4, Ui.skillChip(g.skill()), values, gap, Ui.label(g.minutesThisWeek() + " min this week", "muted-small"));
        card.getStyleClass().add("gap-card");
        card.setPrefWidth(200);
        if (g.belowTarget()) card.getStyleClass().add("gap-card-below");
        return card;
    }

    // ---- exam + today ----

    private VBox examCard() {
        VBox card = Ui.card(Ui.label("IELTS EXAMINATION", "eyebrow"));
        HBox units = new HBox(10, unit(examDays, "DAYS"), unit(examHours, "HOURS"), unit(examMinutes, "MINUTES"), unit(examSeconds, "SECONDS"));
        card.getChildren().addAll(units, examSummary);
        Countdown.State state = Countdown.of(exam, ctx.time.now(), ctx.zone).state();
        if (state == Countdown.State.NOT_SET || state == Countdown.State.PASSED) {
            Button b = Ui.secondary(state == Countdown.State.NOT_SET ? "Set exam date" : "Set a new exam date");
            b.setOnAction(e -> nav.open(PageId.SETTINGS));
            card.getChildren().add(b);
        }
        return card;
    }

    private static VBox unit(Label value, String caption) {
        VBox box = new VBox(2, value, Ui.label(caption, "stat-caption"));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("count-unit");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox todayCard() {
        goalBar.setMaxWidth(Double.MAX_VALUE);
        goalBar.getStyleClass().add("goal-bar");
        HBox head = new HBox(12, focusedValue, goalText);
        head.setAlignment(Pos.BASELINE_LEFT);

        FlowPane tiles = new FlowPane(10, 10);
        tiles.getChildren().addAll(Ui.stat("Daily target", tGoal), Ui.stat("Remaining", tRemaining), Ui.stat("Goal complete", tPercent),
                Ui.stat("Tasks done", tTasks), Ui.stat("Sessions", tSessions), Ui.stat("Current streak", tStreak));
        tiles.getChildren().forEach(n -> ((VBox) n).setPrefWidth(120));

        VBox card = Ui.card(Ui.label("TODAY", "eyebrow"), head, goalBar, tiles);

        Map<Skill, Long> bySkill = ctx.stats.skillTotals(today, today);
        var byTask = ctx.stats.taskTotals(today, today);
        if (!bySkill.isEmpty()) {
            long total = Math.max(1, bySkill.values().stream().mapToLong(Long::longValue).sum());
            card.getChildren().add(Ui.label("Time by skill", "section-label"));
            for (Skill s : Skill.values()) {
                long ms = bySkill.getOrDefault(s, 0L);
                if (ms > 0) card.getChildren().add(skillBar(s, ms, (double) ms / total));
            }
            card.getChildren().add(Ui.label("Time by task", "section-label"));
            for (var t : byTask) {
                HBox r = new HBox(8, Ui.label(t.title(), "row-title-small"), Ui.grow(), Ui.label(Fmt.compact(t.millis()), "muted"));
                r.setAlignment(Pos.CENTER_LEFT);
                card.getChildren().add(r);
            }
        } else {
            card.getChildren().add(Ui.label("No study time saved today yet.", "muted"));
        }
        card.getChildren().add(liveNote);
        return card;
    }

    static Node skillBar(Skill s, long ms, double fraction) {
        ProgressBar bar = new ProgressBar(fraction);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-accent: " + s.color() + ";");
        bar.getStyleClass().add("skill-bar");
        HBox head = new HBox(8, Ui.label(s.label(), "row-title-small"), Ui.grow(), Ui.label(Fmt.compact(ms), "muted"));
        return new VBox(4, head, bar);
    }

    // ---- reviews, schedule, recent ----

    private VBox reviewCard(List<MistakeService.ReviewGroup> groups) {
        return ReviewCard.build(ctx, nav, groups, PageId.DASHBOARD);
    }

    private VBox scheduleCard() {
        VBox card = Ui.card(Ui.label("Today’s schedule", "card-title"));
        List<TaskSummary> tasks = ctx.tasks.scheduled(today, today);
        List<TaskSummary> overdue = ctx.tasks.overdue(today);
        if (tasks.isEmpty() && overdue.isEmpty()) {
            Button add = Ui.secondary("Add a task");
            add.setOnAction(e -> nav.open(PageId.TASKS));
            VBox empty = Ui.emptyState("Nothing scheduled for today", "Give a task today’s date on the Tasks page and it will appear here.");
            empty.getChildren().add(add);
            card.getChildren().add(empty);
            return card;
        }
        Runnable refresh = () -> nav.open(PageId.DASHBOARD);
        if (!overdue.isEmpty()) {
            card.getChildren().add(Ui.label(overdue.size() + " overdue", "section-warn"));
            overdue.forEach(ts -> card.getChildren().add(TaskRows.row(ctx, nav, ts, today, refresh)));
            if (!tasks.isEmpty()) card.getChildren().add(Ui.label("Today", "section-label"));
        }
        tasks.forEach(ts -> card.getChildren().add(TaskRows.row(ctx, nav, ts, today, refresh)));
        return card;
    }

    private VBox recentCard() {
        VBox card = Ui.card(Ui.label("Recent sessions", "card-title"));
        List<StudySession> recent = ctx.sessionRepository.list(SessionRepository.Filter.none()).stream()
                .filter(s -> s.status() == SessionStatus.COMPLETED || s.status() == SessionStatus.INTERRUPTED).limit(6).toList();
        if (recent.isEmpty()) {
            card.getChildren().add(Ui.emptyState("No sessions yet", "Finished sessions appear here."));
            return card;
        }
        for (StudySession s : recent) {
            Label detail = Ui.label(Fmt.compact(s.activeMillis()) + " · " + Fmt.dateTime(s.start(), ctx.zone) + " · "
                    + (s.isManual() ? "Manual" : s.status().toString()), "muted-small");
            VBox box = new VBox(2, Ui.label(s.taskTitle(), "row-title-small"), detail);
            box.setMinWidth(0);
            HBox r = new HBox(8, box, Ui.grow(), Ui.skillChip(s.skill()));
            r.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(r);
        }
        return card;
    }

    // ---- live updates ----

    @Override public void tick() {
        if (!ctx.stats.today().equals(today)) { // the app stayed open past midnight
            nav.open(PageId.DASHBOARD);
            return;
        }
        Countdown c = Countdown.of(exam, ctx.time.now(), ctx.zone);
        switch (c.state()) {
            case NOT_SET -> {
                setCountdown("--", "--", "--", "--");
                examSummary.setText("No exam date set. Add it in Settings to start the countdown.");
            }
            case UPCOMING -> {
                setCountdown(Long.toString(c.days()), two(c.hours()), two(c.minutes()), two(c.seconds()));
                examSummary.setText(c.days() + " days, " + two(c.hours()) + " hours, " + two(c.minutes()) + " minutes remaining · "
                        + exam.type() + " · " + Fmt.date(exam.date()) + (exam.time() == null ? "" : " at " + Fmt.time(exam.time()))
                        + (exam.notes().isBlank() ? "" : "\n" + exam.notes()));
            }
            case EXAM_DAY -> {
                setCountdown("0", "00", "00", "00");
                examSummary.setText("Your exam is today. Good luck!");
            }
            case PASSED -> {
                setCountdown("0", "00", "00", "00");
                examSummary.setText("The exam date (" + Fmt.date(exam.date()) + ") has passed.");
            }
        }
        examSummary.setWrapText(true);

        long live = ctx.liveMillis();
        liveNote.setVisible(live > 0);
        liveNote.setManaged(live > 0);
        liveNote.setText("Plus " + Fmt.compact(live) + " in the session in progress. It is added once you finish it.");
        long focused = base.focusedMillis() + live;
        long goal = base.goalMillis();
        double fraction = goal <= 0 ? 0 : (double) focused / goal;
        focusedValue.setText(Fmt.compact(focused));
        goalText.setText("of " + Fmt.hoursMinutes(goal) + " daily target");
        goalBar.setProgress(Math.min(1, fraction));
        tGoal.setText(Fmt.hoursMinutes(goal));
        tRemaining.setText(fraction >= 1 ? "Goal met" : Fmt.hoursMinutes(goal - focused));
        tPercent.setText(Fmt.percent(fraction));
        tTasks.setText(base.completedTasks() + " / " + base.scheduledTasks());
        tSessions.setText(Integer.toString(base.sessions()));
        tStreak.setText(streaks.current() + (streaks.current() == 1 ? " day" : " days"));
    }

    private void setCountdown(String d, String h, String m, String s) {
        examDays.setText(d);
        examHours.setText(h);
        examMinutes.setText(m);
        examSeconds.setText(s);
    }

    private static String two(long v) { return String.format("%02d", v); }
}
