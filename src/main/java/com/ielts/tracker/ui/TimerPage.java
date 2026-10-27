package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.MockPreset;
import com.ielts.tracker.model.Priority;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.model.TaskSummary;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.repository.SessionRepository;
import com.ielts.tracker.service.GoalService;
import com.ielts.tracker.timer.FocusTimer;
import com.ielts.tracker.util.Fmt;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The focus screen: start, pause, resume, finish and cancel for the single active session, plus exam-timing presets. */
public final class TimerPage implements Page {
    private static final String FREE = "Free session";

    private final AppContext ctx;
    private final Navigator nav;

    private final ComboBox<StudyTask> taskBox = new ComboBox<>();
    private final ComboBox<TimerMode> modeBox = new ComboBox<>();
    private final ComboBox<Object> presetBox = new ComboBox<>();
    private final Spinner<Integer> minutes = new Spinner<>(1, 1440, 25, 5);
    private final TextArea notes = new TextArea();

    private final Label clock = Ui.label("00:00:00", "timer-clock");
    private final Label clockCaption = Ui.label("", "muted");
    private final Label taskTitle = Ui.label("", "timer-task");
    private final Label focused = Ui.label("", "timer-detail");
    private final Label target = Ui.label("", "timer-detail");
    private final Label segment = Ui.label("", "segment-label");
    private final Label status = Ui.label("", "status-pill");
    private final Label banner = Ui.wrapped("", "banner");
    private final ProgressRing ring = new ProgressRing(140, 10);

    private final Button start = Ui.primary("Start");
    private final Button pause = Ui.secondary("Pause");
    private final Button resume = Ui.primary("Resume");
    private final Button finish = Ui.primary("Finish session");
    private final Button cancel = Ui.danger("Cancel session");
    private final HBox lapRow = new HBox(8);
    private final Label lapList = Ui.wrapped("", "muted-small");

    private final HBox chipRow = new HBox();
    private final VBox root;
    private final List<SessionRepository.Lap> laps = new ArrayList<>();
    private long lapsFor = -1;

    public TimerPage(AppContext ctx, Navigator nav, Long preselectTaskId) {
        this.ctx = ctx;
        this.nav = nav;

        // The weakest skill (largest gap to target) comes first, so the default pick follows what needs work.
        List<Skill> order = ctx.goals.gaps().stream().map(GoalService.SkillGap::skill).toList();
        List<StudyTask> choices = ctx.tasks.list().stream().map(TaskSummary::task).filter(t -> t.status() == TaskStatus.PLANNED)
                .sorted(Comparator.comparingInt((StudyTask t) -> order.contains(t.skill()) ? order.indexOf(t.skill()) : order.size()))
                .toList();
        taskBox.getItems().addAll(choices);
        taskBox.setPromptText(choices.isEmpty() ? "No planned tasks, create one first" : "Choose a task (exam timings can make their own)");
        taskBox.setMaxWidth(Double.MAX_VALUE);
        modeBox.getItems().addAll(TimerMode.COUNTDOWN, TimerMode.STOPWATCH);
        modeBox.setMaxWidth(Double.MAX_VALUE);
        modeBox.setValue(ctx.settings.defaultMode() == TimerMode.STOPWATCH ? TimerMode.STOPWATCH : TimerMode.COUNTDOWN);
        presetBox.getItems().add(FREE);
        presetBox.getItems().addAll((Object[]) MockPreset.values());
        presetBox.setValue(FREE);
        presetBox.setMaxWidth(Double.MAX_VALUE);
        minutes.setEditable(true);
        minutes.setMaxWidth(Double.MAX_VALUE);
        minutes.getValueFactory().setValue(ctx.settings.defaultSessionMinutes());
        notes.setPromptText("Session notes: what you covered, mistakes, what to review");
        notes.setWrapText(true);
        notes.setPrefRowCount(5);
        notes.setText(ctx.draftNotes);
        notes.textProperty().addListener((o, a, b) -> ctx.draftNotes = b);

        if (ctx.sessions.hasActiveSession()) {
            ctx.tasks.find(ctx.sessions.timer().taskId()).ifPresent(t -> {
                if (!taskBox.getItems().contains(t)) taskBox.getItems().add(0, t);
                taskBox.setValue(t);
            });
            modeBox.setValue(ctx.sessions.timer().mode());
            minutes.getValueFactory().setValue((int) Math.max(1, ctx.sessions.timer().targetSeconds() / 60));
            if (ctx.sessions.preset() != null) presetBox.setValue(ctx.sessions.preset());
        } else {
            StudyTask pick = preselectTaskId == null ? null : choices.stream().filter(t -> t.id() == preselectTaskId).findFirst().orElse(null);
            if (pick == null && !choices.isEmpty()) pick = choices.get(0);
            if (pick != null) {
                taskBox.setValue(pick);
                minutes.getValueFactory().setValue((int) Math.max(1, pick.targetSeconds() / 60));
            }
        }
        taskBox.valueProperty().addListener((o, a, t) -> {
            if (t != null && !ctx.sessions.hasActiveSession() && !(presetBox.getValue() instanceof MockPreset)) {
                minutes.getValueFactory().setValue((int) Math.max(1, t.targetSeconds() / 60));
            }
            tick();
        });
        presetBox.valueProperty().addListener((o, a, p) -> {
            if (p instanceof MockPreset mp) {
                if (taskBox.getValue() != null && taskBox.getValue().skill() != mp.skill()) taskBox.setValue(null);
                modeBox.setValue(TimerMode.COUNTDOWN);
                minutes.getValueFactory().setValue(mp.totalSeconds() / 60);
            }
            tick();
        });
        modeBox.valueProperty().addListener((o, a, b) -> tick());
        minutes.valueProperty().addListener((o, a, b) -> tick());

        GridPane setup = Ui.form();
        setup.addRow(0, Ui.label("Task", "form-label"), taskBox);
        setup.addRow(1, Ui.label("Exam timing", "form-label"), presetBox);
        setup.addRow(2, Ui.label("Mode", "form-label"), modeBox);
        setup.addRow(3, Ui.label("Countdown (min)", "form-label"), minutes);
        GridPane.setHgrow(taskBox, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(presetBox, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(modeBox, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(minutes, javafx.scene.layout.Priority.ALWAYS);
        Ui.tip(presetBox, "Real exam timings. Listening includes the 10 minute transfer time. When it ends you are asked for your score.");
        Ui.tip(modeBox, "Countdown runs down from the chosen length. Stopwatch counts up until you finish.");

        Button mini = Ui.secondary("Mini timer");
        mini.setOnAction(e -> nav.toggleMiniTimer());
        Ui.tip(mini, "Small always-on-top timer. It also appears by itself when you minimise the window during a session.");
        Label shortcut = Ui.wrapped("Global shortcut: Ctrl+Alt+Space starts the last task, pauses or resumes, even from another window.", "muted-small");

        VBox side = new VBox(16, Ui.section("Session setup", setup), Ui.section("Notes", notes), new VBox(8, mini, shortcut));
        side.setPrefWidth(380);
        side.setMinWidth(320);

        banner.setVisible(false);
        banner.setManaged(false);
        banner.setMaxWidth(440);
        VBox center = new VBox(4, clock, clockCaption);
        center.setAlignment(Pos.CENTER);
        StackPane ringStack = new StackPane(ring, center);
        ringStack.setMaxSize(ring.getPrefWidth(), ring.getPrefHeight());

        chipRow.setAlignment(Pos.CENTER);
        HBox details = new HBox(28, focused, target);
        details.setAlignment(Pos.CENTER);
        lapRow.setAlignment(Pos.CENTER);
        HBox controls = new HBox(12, start, pause, resume, finish, cancel);
        controls.setAlignment(Pos.CENTER);
        lapList.setMaxWidth(440);

        VBox timerCard = new VBox(14, status, taskTitle, chipRow, ringStack, segment, details, lapRow, lapList, banner, controls);
        timerCard.setAlignment(Pos.CENTER);
        timerCard.getStyleClass().addAll("card", "timer-card");
        HBox.setHgrow(timerCard, javafx.scene.layout.Priority.ALWAYS);

        start.setOnAction(e -> Ui.guarded(this::onStart));
        pause.setOnAction(e -> Ui.guarded(() -> { ctx.sessions.pause(); tick(); }));
        resume.setOnAction(e -> Ui.guarded(() -> { ctx.sessions.resume(); ctx.idle.onInput(); tick(); }));
        finish.setOnAction(e -> Ui.guarded(this::onFinish));
        cancel.setOnAction(e -> Ui.guarded(this::onCancel));

        HBox body = new HBox(18, timerCard, side);
        root = Ui.pageBox("Focus Timer", "Only active, unpaused time counts as study time.");
        root.getChildren().add(body);
        tick();
    }

    @Override public Node root() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    private void onStart() {
        MockPreset preset = presetBox.getValue() instanceof MockPreset p ? p : null;
        StudyTask task = taskBox.getValue();
        if (task == null && preset != null) {
            // A mock needs somewhere to record its time; make a finished "Mock" task so no setup is needed.
            long id = ctx.tasks.create(new StudyTask(0, "Mock: " + preset + " (" + ctx.stats.today() + ")", "", preset.skill(), "Mock test",
                    preset.totalSeconds(), Priority.MEDIUM, TaskStatus.PLANNED, null, null, "", null, null, null));
            ctx.tasks.setStatus(id, TaskStatus.COMPLETED);
            ctx.sessions.start(id, TimerMode.COUNTDOWN, preset.totalSeconds(), preset);
            nav.open(PageId.TIMER);
            return;
        }
        if (task == null) throw new IllegalArgumentException("Choose a task first, or create one on the Tasks page.");
        minutes.commitValue();
        TimerMode mode = preset != null ? TimerMode.COUNTDOWN : modeBox.getValue();
        long targetSeconds = preset != null ? preset.totalSeconds() : mode == TimerMode.COUNTDOWN ? minutes.getValue() * 60L : task.targetSeconds();
        ctx.sessions.start(task.id(), mode, targetSeconds, preset);
        ctx.idle.onInput();
        tick();
    }

    private void onFinish() {
        long active = ctx.sessions.timer().activeMillis();
        StudyTask task = taskBox.getValue();
        StudySession saved = ctx.sessions.finish(notes.getText().trim());
        ctx.draftNotes = "";
        notes.clear();
        laps.clear();
        lapsFor = -1;
        nav.toast("Saved " + Fmt.compact(saved.activeMillis()) + " on “" + saved.taskTitle() + "”");
        tick();
        if (saved.preset() != null) {
            ScoreFlow.run(ctx, nav, saved);
            nav.open(PageId.TIMER);
            return;
        }
        if (task != null && task.status() == TaskStatus.PLANNED && active > 0
                && Ui.confirm("Mark this task as completed?",
                "Finishing a session does not complete the task by itself. Is “" + task.title() + "” done?", "Mark completed")) {
            ctx.tasks.setStatus(task.id(), TaskStatus.COMPLETED);
            nav.open(PageId.TIMER);
        }
    }

    private void onCancel() {
        long active = ctx.sessions.timer().activeMillis();
        String msg = active > 0
                ? "This session has " + Fmt.compact(active) + " of unsaved focused time. If you cancel, it will not be counted. To keep it, use Finish session instead."
                : "Cancel this session?";
        if (Ui.confirm("Discard session?", msg, "Discard session")) {
            ctx.sessions.cancel();
            ctx.draftNotes = "";
            notes.clear();
            laps.clear();
            lapsFor = -1;
            tick();
        }
    }

    private void markLap(String label) {
        Ui.guarded(() -> {
            long at = ctx.sessions.timer().activeMillis();
            ctx.sessions.lap(label);
            laps.add(new SessionRepository.Lap(label, at));
            tick();
        });
    }

    private void refreshLaps(FocusTimer t, MockPreset preset) {
        if (!t.isActive() || preset == null) {
            lapRow.getChildren().clear();
            lapList.setText("");
            return;
        }
        long sid = ctx.sessions.sessionId();
        if (lapsFor != sid) {
            laps.clear();
            laps.addAll(ctx.sessionRepository.laps(sid));
            lapsFor = sid;
            lapRow.getChildren().clear();
        }
        if (lapRow.getChildren().isEmpty() && !preset.laps().isEmpty()) {
            for (String label : preset.laps()) {
                Button b = Ui.secondary(label + " done");
                b.setUserData(label);
                b.setOnAction(e -> markLap(label));
                lapRow.getChildren().add(b);
            }
        }
        for (Node n : lapRow.getChildren()) {
            String label = (String) n.getUserData();
            boolean done = laps.stream().anyMatch(l -> l.label().equals(label));
            n.setDisable(done || t.state() != FocusTimer.State.RUNNING);
        }
        StringBuilder sb = new StringBuilder();
        long prev = 0;
        for (SessionRepository.Lap l : laps) {
            if (sb.length() > 0) sb.append("   ");
            sb.append(l.label()).append(": ").append(Fmt.clock(l.atActiveMillis() - prev).substring(3));
            prev = l.atActiveMillis();
        }
        lapList.setText(sb.length() == 0 ? "" : "Time per part: " + sb);
    }

    @Override public void tick() {
        FocusTimer t = ctx.sessions.timer();
        FocusTimer.State state = t.state();
        boolean active = t.isActive();
        MockPreset preset = active ? ctx.sessions.preset() : presetBox.getValue() instanceof MockPreset p ? p : null;

        taskBox.setDisable(active);
        presetBox.setDisable(active);
        modeBox.setDisable(active || preset != null);
        minutes.setDisable(active || modeBox.getValue() == TimerMode.STOPWATCH || preset != null);

        start.setVisible(!active);
        start.setManaged(!active);
        pause.setVisible(state == FocusTimer.State.RUNNING);
        pause.setManaged(state == FocusTimer.State.RUNNING);
        resume.setVisible(state == FocusTimer.State.PAUSED);
        resume.setManaged(state == FocusTimer.State.PAUSED);
        finish.setVisible(active);
        finish.setManaged(active);
        cancel.setVisible(active);
        cancel.setManaged(active);
        start.setDisable(taskBox.getValue() == null && preset == null);

        StudyTask shown = taskBox.getValue();
        taskTitle.setText(shown != null ? shown.title() : preset != null ? preset.toString() : "No task selected");
        Skill chipSkill = shown != null ? shown.skill() : preset != null ? preset.skill() : null;
        chipRow.getChildren().setAll(chipSkill == null ? new Label() : Ui.skillChip(chipSkill));

        TimerMode mode = active ? t.mode() : preset != null ? TimerMode.COUNTDOWN : modeBox.getValue();
        long targetSec = active ? t.targetSeconds()
                : preset != null ? preset.totalSeconds()
                : mode == TimerMode.COUNTDOWN ? minutes.getValue() * 60L : shown == null ? 0 : shown.targetSeconds();
        long activeMs = active ? t.activeMillis() : 0;

        if (mode == TimerMode.COUNTDOWN) {
            if (active && t.targetReached()) {
                clock.setText("+" + Fmt.clock(t.overrunMillis()));
                clockCaption.setText("over target");
            } else {
                long remaining = active ? t.remainingMillis() : targetSec * 1000;
                clock.setText(Fmt.clock(remaining + 999));
                clockCaption.setText("remaining");
            }
        } else {
            clock.setText(Fmt.clock(activeMs));
            clockCaption.setText("elapsed");
        }
        ring.setFraction(targetSec > 0 ? (double) activeMs / (targetSec * 1000) : 0);
        ring.setOverrun(active && t.targetReached());

        focused.setText("Focused  " + Fmt.clock(activeMs));
        target.setText(targetSec > 0 ? "Target  " + Fmt.clock(targetSec * 1000) : "No target");

        if (preset != null) {
            long s = activeMs / 1000;
            int idx = preset.segmentIndexAt(s);
            long left = preset.segmentRemainingSeconds(s);
            segment.setText(active ? (left == 0 ? "Time is up" : "Now: " + preset.segments().get(idx).name() + "  ·  " + Fmt.clock(left * 1000).substring(3) + " left")
                    : preset.segments().stream().map(MockPreset.Segment::name).reduce((a, b) -> a + "  ›  " + b).orElse(""));
            segment.setVisible(true);
            segment.setManaged(true);
        } else {
            segment.setVisible(false);
            segment.setManaged(false);
        }
        refreshLaps(t, preset);

        String text = switch (state) {
            case RUNNING -> "RUNNING";
            case PAUSED -> t.recovered() ? "RECOVERED · PAUSED" : "PAUSED";
            default -> "READY";
        };
        status.setText(text);
        status.getStyleClass().removeAll("status-running", "status-paused", "status-ready");
        status.getStyleClass().add(state == FocusTimer.State.RUNNING ? "status-running" : state == FocusTimer.State.PAUSED ? "status-paused" : "status-ready");

        boolean reached = active && mode == TimerMode.COUNTDOWN && t.targetReached();
        banner.setVisible(reached || (active && t.recovered()));
        banner.setManaged(banner.isVisible());
        if (reached) {
            banner.setText(preset != null ? "Time is up. Finish to save and enter your score."
                    : "Target reached. Finish to save, or keep going; the extra time is tracked as over target.");
        } else if (active && t.recovered()) {
            banner.setText("Recovered session. Only the time recorded before the interruption is counted. Press Resume to continue.");
        }
    }
}
