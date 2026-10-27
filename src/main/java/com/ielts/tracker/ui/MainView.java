package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.MockPreset;
import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.timer.FocusTimer;
import com.ielts.tracker.util.ErrorLog;
import com.ielts.tracker.util.Fmt;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;

/** Application shell: sidebar, page area, mini timers, idle prompt and toast messages. */
public final class MainView implements Navigator {
    private final AppContext ctx;
    private final Stage stage;
    private final String stylesheet;
    private final BorderPane root = new BorderPane();
    private final StackPane pageArea = new StackPane();
    private final Label toast = Ui.label("", "toast");
    private final PauseTransition toastTimer = new PauseTransition(Duration.seconds(5));
    private final Map<PageId, Button> navButtons = new EnumMap<>(PageId.class);
    private final Label dateLabel = Ui.label("", "muted-small");
    private final Timeline ticker;
    private final MiniTimerWindow miniWindow;
    private Consumer<String> trayTooltip = s -> { };

    private final VBox mini = new VBox(6);
    private final Label miniTitle = Ui.label("", "mini-title");
    private final Label miniClock = Ui.label("", "mini-clock");
    private final Label miniState = Ui.label("", "mini-state");
    private final Button miniToggle = Ui.secondary("Pause");
    private long miniTaskId = -1;
    private boolean miniPinned;
    private int lastSegment = -1;
    private long lastSegmentSession = -1;

    private Page current;
    private PageId currentId;
    private boolean tickErrorShown;

    public MainView(AppContext ctx, Stage stage, String stylesheet) {
        this.ctx = ctx;
        this.stage = stage;
        this.stylesheet = stylesheet;
        this.miniWindow = new MiniTimerWindow(ctx, this::showMain, stylesheet);

        root.setLeft(sidebar());
        StackPane center = new StackPane(pageArea, toast);
        StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toast, new Insets(0, 24, 24, 0));
        toast.setVisible(false);
        toast.setMouseTransparent(true);
        toastTimer.setOnFinished(e -> toast.setVisible(false));
        root.setCenter(center);
        root.getStyleClass().add("app-root");

        stage.iconifiedProperty().addListener((o, a, minimised) -> updateMiniWindow());
        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        open(PageId.DASHBOARD);
    }

    public BorderPane root() { return root; }

    public void start() { ticker.play(); }

    public void stop() {
        ticker.stop();
        miniWindow.hide();
    }

    /** The tray icon shows the running time in its tooltip. */
    public void setTrayTooltip(Consumer<String> consumer) { this.trayTooltip = consumer; }

    /** Brings the main window back (from the tray or the mini timer). */
    public void showMain() {
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    // ---- sidebar ----

    private Node sidebar() {
        Label logo = Ui.label("IELTS", "logo");
        Label sub = Ui.label("Study Tracker", "logo-sub");
        VBox brand = new VBox(0, logo, sub);
        brand.setPadding(new Insets(0, 0, 14, 8));

        VBox items = new VBox(4);
        for (PageId id : PageId.values()) {
            Button b = new Button(id.title);
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setOnAction(e -> open(id));
            navButtons.put(id, b);
            items.getChildren().add(b);
        }

        miniToggle.setMaxWidth(Double.MAX_VALUE);
        miniToggle.setOnAction(e -> Ui.guarded(() -> {
            if (ctx.sessions.timer().state() == FocusTimer.State.RUNNING) ctx.sessions.pause();
            else { ctx.sessions.resume(); ctx.idle.onInput(); }
            updateMini();
            if (current != null) current.tick();
        }));
        Button openTimer = Ui.secondary("Open timer");
        openTimer.setMaxWidth(Double.MAX_VALUE);
        openTimer.setOnAction(e -> open(PageId.TIMER));
        mini.getChildren().addAll(miniState, miniTitle, miniClock, miniToggle, openTimer);
        mini.getStyleClass().add("mini-timer");
        mini.setVisible(false);
        mini.setManaged(false);

        VBox footer = new VBox(4, Ui.label("Offline · data stored on this computer", "muted-small"), dateLabel);
        footer.setPadding(new Insets(0, 0, 0, 8));

        VBox side = new VBox(10, brand, items, Ui.grow(), mini, footer);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(236);
        side.setMinWidth(210);
        return side;
    }

    // ---- navigation ----

    @Override public void open(PageId id) { show(id, build(id, null)); }

    @Override public void startTimerFor(long taskId) {
        if (ctx.sessions.hasActiveSession()) {
            toast("A session is already active. Finish or cancel it before starting another.");
            open(PageId.TIMER);
            return;
        }
        show(PageId.TIMER, build(PageId.TIMER, taskId));
    }

    @Override public void resumeLastTask() {
        if (ctx.sessions.hasActiveSession()) {
            open(PageId.TIMER);
            return;
        }
        Ui.guarded(() -> {
            var last = ctx.sessionService.lastTimedSession();
            if (last.isEmpty()) {
                toast("No earlier session to resume. Pick a task to start.");
                open(PageId.TIMER);
                return;
            }
            StudySession s = last.get();
            MockPreset preset = MockPreset.byId(s.preset());
            TimerMode mode = s.mode() == TimerMode.STOPWATCH ? TimerMode.STOPWATCH : TimerMode.COUNTDOWN;
            long target = preset != null ? preset.totalSeconds() : s.targetSeconds() > 0 ? s.targetSeconds() : ctx.settings.defaultSessionMinutes() * 60L;
            ctx.sessions.start(s.taskId(), mode, target, preset);
            ctx.idle.onInput();
            toast("Started “" + s.taskTitle() + "”");
            open(PageId.TIMER);
        });
    }

    @Override public void toggleTimer() {
        FocusTimer.State state = ctx.sessions.timer().state();
        Ui.guarded(() -> {
            switch (state) {
                case RUNNING -> {
                    ctx.sessions.pause();
                    toast("Paused");
                }
                case PAUSED -> {
                    ctx.sessions.resume();
                    ctx.idle.onInput();
                    toast("Resumed");
                }
                default -> resumeLastTask();
            }
        });
        updateMini();
        if (current != null) current.tick();
    }

    @Override public void openWeeklyFor(java.time.LocalDate weekStart) {
        show(PageId.WEEKLY, WeeklyReviewPage.forWeek(ctx, this, weekStart));
    }

    @Override public void toggleMiniTimer() {
        miniPinned = !miniPinned;
        updateMiniWindow();
        toast(miniPinned ? "Mini timer pinned on top." : "Mini timer hidden.");
    }

    private Page build(PageId id, Long taskId) {
        try {
            return switch (id) {
                case DASHBOARD -> new DashboardPage(ctx, this);
                case TIMER -> new TimerPage(ctx, this, taskId);
                case TASKS -> new TasksPage(ctx, this);
                case HISTORY -> new HistoryPage(ctx, this);
                case PRACTICE -> new PracticePage(ctx, this);
                case MISTAKES -> new MistakesPage(ctx, this);
                case WRITING -> new WritingSpeakingPage(ctx, this);
                case WEEKLY -> new WeeklyReviewPage(ctx, this);
                case SETTINGS -> new SettingsPage(ctx, this);
            };
        } catch (RuntimeException e) {
            ErrorLog.log("Could not open " + id.title, e);
            return new ErrorPage(id.title, e);
        }
    }

    private void show(PageId id, Page page) {
        current = page;
        currentId = id;
        pageArea.getChildren().setAll(page.root());
        navButtons.forEach((k, b) -> {
            b.getStyleClass().remove("nav-active");
            if (k == id) b.getStyleClass().add("nav-active");
        });
        updateMini();
    }

    // ---- ticking ----

    private void tick() {
        try {
            if (ctx.sessions.tick()) onTargetReached();
            announceSegmentChange();
            checkIdle();
            tickErrorShown = false;
        } catch (RuntimeException e) {
            ErrorLog.log("Timer checkpoint failed", e);
            if (!tickErrorShown) {
                tickErrorShown = true;
                toast("Warning: could not save timer progress (" + e.getMessage() + "). The timer keeps running; saving will be retried.");
            }
        }
        dateLabel.setText(ctx.time.now().atZone(ctx.zone).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)));
        updateMini();
        updateMiniWindow();
        if (current != null) current.tick();
    }

    /** "Still studying?" is only asked for ordinary sessions; paper-based mock tests would trigger it constantly. */
    private void checkIdle() {
        boolean running = ctx.sessions.timer().state() == FocusTimer.State.RUNNING && ctx.sessions.preset() == null;
        OptionalLong idle = ctx.idle.poll(running);
        if (idle.isPresent()) IdlePrompt.show(ctx, idle.getAsLong(), this, stylesheet);
    }

    private void announceSegmentChange() {
        MockPreset p = ctx.sessions.preset();
        FocusTimer t = ctx.sessions.timer();
        if (p == null || t.state() != FocusTimer.State.RUNNING) return;
        long secs = t.activeMillis() / 1000;
        int idx = p.segmentIndexAt(secs);
        long sid = ctx.sessions.sessionId();
        if (lastSegmentSession != sid) {
            lastSegmentSession = sid;
            lastSegment = idx;
            return;
        }
        if (idx != lastSegment) {
            lastSegment = idx;
            if (p.segmentRemainingSeconds(secs) > 0) {
                toast("Next: " + p.segments().get(idx).name() + " (" + Fmt.hoursMinutes(p.segments().get(idx).seconds() * 1000L) + ")");
                beep();
            }
        }
    }

    private void onTargetReached() {
        String title = ctx.tasks.find(ctx.sessions.timer().taskId()).map(StudyTask::title).orElse("your task");
        toast("Target reached for “" + title + "”. Finish the session, or keep going; extra time is tracked.");
        beep();
    }

    private static void beep() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Throwable ignored) {
            // No audio device or headless: the on-screen message is enough.
        }
    }

    private void updateMini() {
        FocusTimer t = ctx.sessions.timer();
        boolean active = t.isActive();
        boolean show = active && currentId != PageId.TIMER;
        mini.setVisible(show);
        mini.setManaged(show);
        if (!active) {
            trayTooltip.accept("IELTS Study Tracker");
            return;
        }
        if (miniTaskId != t.taskId()) {
            miniTaskId = t.taskId();
            miniTitle.setText(ctx.tasks.find(miniTaskId).map(StudyTask::title).orElse("Study session"));
        }
        boolean running = t.state() == FocusTimer.State.RUNNING;
        miniState.setText(running ? "SESSION RUNNING" : "SESSION PAUSED");
        miniToggle.setText(running ? "Pause" : "Resume");
        String clock = t.mode() == TimerMode.COUNTDOWN
                ? (t.targetReached() ? "+" + Fmt.clock(t.overrunMillis()) : Fmt.clock(t.remainingMillis() + 999))
                : Fmt.clock(t.activeMillis());
        miniClock.setText(clock);
        trayTooltip.accept((running ? "" : "Paused: ") + miniTitle.getText() + "  " + clock);
    }

    /** The floating timer shows while a session is active and either pinned or the main window is minimised. */
    private void updateMiniWindow() {
        boolean active = ctx.sessions.timer().isActive();
        boolean wanted = active && (miniPinned || stage.isIconified());
        if (wanted) miniWindow.show();
        else if (miniWindow.isShowing()) miniWindow.hide();
        if (wanted) miniWindow.update();
        if (!active) miniPinned = false;
    }

    @Override public void toast(String message) {
        toast.setText(message);
        toast.setVisible(true);
        toastTimer.playFromStart();
    }

    private static final class ErrorPage implements Page {
        private final VBox box;

        ErrorPage(String title, Throwable e) {
            box = Ui.pageBox(title, "This page could not be opened.");
            box.getChildren().add(Ui.card(Ui.emptyState("Something went wrong", String.valueOf(e.getMessage()))));
        }

        @Override public Node root() { return box; }
    }
}
