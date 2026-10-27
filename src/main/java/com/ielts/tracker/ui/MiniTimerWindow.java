package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.timer.FocusTimer;
import com.ielts.tracker.util.Fmt;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Small always-on-top timer so a session can be paused or resumed without switching windows. */
final class MiniTimerWindow {
    private final AppContext ctx;
    private final Stage stage = new Stage(StageStyle.UTILITY);
    private final Label title = Ui.label("", "mini-title");
    private final Label clock = Ui.label("", "mini-clock");
    private final Button toggle = Ui.primary("Pause");
    private long shownTaskId = -1;

    MiniTimerWindow(AppContext ctx, Runnable openMain, String stylesheet) {
        this.ctx = ctx;
        Button open = Ui.secondary("Open");
        open.setOnAction(e -> openMain.run());
        toggle.setOnAction(e -> Ui.guarded(() -> {
            if (ctx.sessions.timer().state() == FocusTimer.State.RUNNING) ctx.sessions.pause();
            else { ctx.sessions.resume(); ctx.idle.onInput(); }
            update();
        }));
        HBox buttons = new HBox(8, toggle, open);
        buttons.setAlignment(Pos.CENTER);
        VBox box = new VBox(6, title, clock, buttons);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("mini-window");
        Scene scene = new Scene(box, 230, 130);
        scene.getStylesheets().add(stylesheet);
        stage.setScene(scene);
        stage.setTitle("Timer");
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
    }

    void show() {
        if (!stage.isShowing()) {
            stage.show();
            stage.setX(javafx.stage.Screen.getPrimary().getVisualBounds().getMaxX() - 250);
            stage.setY(javafx.stage.Screen.getPrimary().getVisualBounds().getMinY() + 20);
        }
        update();
    }

    void hide() { stage.hide(); }

    boolean isShowing() { return stage.isShowing(); }

    void update() {
        FocusTimer t = ctx.sessions.timer();
        if (!t.isActive()) return;
        if (shownTaskId != t.taskId()) {
            shownTaskId = t.taskId();
            title.setText(ctx.tasks.find(shownTaskId).map(StudyTask::title).orElse("Study session"));
        }
        boolean running = t.state() == FocusTimer.State.RUNNING;
        toggle.setText(running ? "Pause" : "Resume");
        clock.setText(t.mode() == TimerMode.COUNTDOWN
                ? (t.targetReached() ? "+" + Fmt.clock(t.overrunMillis()) : Fmt.clock(t.remainingMillis() + 999))
                : Fmt.clock(t.activeMillis()));
    }
}
