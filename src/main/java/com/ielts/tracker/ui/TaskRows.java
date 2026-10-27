package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.model.TaskSummary;
import com.ielts.tracker.util.Fmt;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

/** One schedule row: time, title, skill, target, time spent, status, with start and more actions. */
final class TaskRows {
    private TaskRows() {}

    static Node row(AppContext ctx, Navigator nav, TaskSummary ts, LocalDate today, Runnable refresh) {
        StudyTask t = ts.task();

        Label time = Ui.label(t.scheduledTime() == null ? "Any time" : Fmt.time(t.scheduledTime()), "row-time");
        time.setMinWidth(70);

        Label title = Ui.label(t.title(), "row-title");
        if (t.status() == TaskStatus.COMPLETED) title.getStyleClass().add("done");
        title.setMaxWidth(Double.MAX_VALUE);
        FlowPane meta = new FlowPane(8, 2, Ui.skillChip(t.skill()),
                Ui.label("Target " + Fmt.hoursMinutes(t.targetSeconds() * 1000), "muted-small"),
                Ui.label("Spent " + Fmt.compact(ts.focusedMillis()), "muted-small"),
                Ui.label(t.priority() + " priority", "muted-small"));
        meta.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(3, title, meta);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        boolean overdue = t.status() == TaskStatus.PLANNED && t.scheduledDate() != null && t.scheduledDate().isBefore(today);
        Label status = Ui.label(overdue ? "Overdue" : t.status().toString(), "badge",
                overdue ? "badge-overdue" : t.status() == TaskStatus.COMPLETED ? "badge-done" : t.status() == TaskStatus.SKIPPED ? "badge-skipped" : "badge-planned");

        status.setMinWidth(Region.USE_PREF_SIZE);
        HBox actions = new HBox(8);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (t.status() == TaskStatus.PLANNED) {
            Button start = Ui.primary("Start");
            start.setOnAction(e -> nav.startTimerFor(t.id()));
            actions.getChildren().add(start);
        }

        MenuButton more = new MenuButton("⋯");
        more.getStyleClass().add("btn-icon");
        more.setPopupSide(javafx.geometry.Side.BOTTOM);
        if (t.status() == TaskStatus.PLANNED) {
            more.getItems().add(item("Mark completed", () -> { TaskActions.setStatus(ctx, t.id(), TaskStatus.COMPLETED); refresh.run(); }));
            more.getItems().add(item("Skip", () -> { TaskActions.setStatus(ctx, t.id(), TaskStatus.SKIPPED); refresh.run(); }));
            more.getItems().add(item("Move to today", () -> { TaskActions.moveTo(ctx, t.id(), today); refresh.run(); }));
            more.getItems().add(item("Move to tomorrow", () -> { TaskActions.moveTo(ctx, t.id(), today.plusDays(1)); refresh.run(); }));
        } else {
            more.getItems().add(item("Reopen", () -> { TaskActions.setStatus(ctx, t.id(), TaskStatus.PLANNED); refresh.run(); }));
        }
        more.getItems().add(item("Edit…", () -> { if (TaskActions.edit(ctx, t.id())) refresh.run(); }));
        more.getItems().add(item("Duplicate", () -> { TaskActions.duplicate(ctx, t.id()); refresh.run(); }));
        more.getItems().add(item(ts.sessionCount() > 0 ? "Archive…" : "Delete…",
                () -> { if (TaskActions.delete(ctx, t, ts.sessionCount() > 0)) refresh.run(); }));
        Ui.tip(more, "More actions");
        actions.getChildren().addAll(status, more);

        HBox row = new HBox(14, time, text, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("row");
        return row;
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem m = new MenuItem(text);
        m.setOnAction(e -> action.run());
        return m;
    }
}
