package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.model.TaskSummary;
import com.ielts.tracker.util.Fmt;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Predicate;

public final class TasksPage implements Page {
    private static final String ANY = "All";

    private final AppContext ctx;
    private final Navigator nav;
    private final VBox root;
    private final TableView<TaskSummary> table = new TableView<>();
    private final FilteredList<TaskSummary> filtered;
    private final ComboBox<Object> skillFilter = new ComboBox<>();
    private final ComboBox<Object> statusFilter = new ComboBox<>();
    private final Label total = Ui.label("", "muted");

    public TasksPage(AppContext ctx, Navigator nav) {
        this.ctx = ctx;
        this.nav = nav;
        filtered = new FilteredList<>(FXCollections.observableArrayList(ctx.tasks.list()));

        skillFilter.getItems().add(ANY);
        skillFilter.getItems().addAll(Skill.values());
        skillFilter.setValue(ANY);
        statusFilter.getItems().add(ANY);
        statusFilter.getItems().addAll(TaskStatus.PLANNED, TaskStatus.COMPLETED, TaskStatus.SKIPPED);
        statusFilter.setValue(ANY);
        skillFilter.valueProperty().addListener((o, a, b) -> applyFilter());
        statusFilter.valueProperty().addListener((o, a, b) -> applyFilter());

        Button add = Ui.primary("New task");
        add.setOnAction(e -> { if (TaskActions.create(ctx, null)) reload(); });
        Button start = Ui.primary("Start timer");
        Button edit = Ui.secondary("Edit");
        Button duplicate = Ui.secondary("Duplicate");
        Button done = Ui.secondary("Mark completed");
        Button reopen = Ui.secondary("Reopen");
        Button skip = Ui.secondary("Skip");
        Button delete = Ui.danger("Delete");

        start.setOnAction(e -> selected(s -> nav.startTimerFor(s.task().id())));
        edit.setOnAction(e -> selected(s -> { if (TaskActions.edit(ctx, s.task().id())) reload(); }));
        duplicate.setOnAction(e -> selected(s -> { TaskActions.duplicate(ctx, s.task().id()); reload(); }));
        done.setOnAction(e -> selected(s -> { TaskActions.setStatus(ctx, s.task().id(), TaskStatus.COMPLETED); reload(); }));
        reopen.setOnAction(e -> selected(s -> { TaskActions.setStatus(ctx, s.task().id(), TaskStatus.PLANNED); reload(); }));
        skip.setOnAction(e -> selected(s -> { TaskActions.setStatus(ctx, s.task().id(), TaskStatus.SKIPPED); reload(); }));
        delete.setOnAction(e -> selected(s -> { if (TaskActions.delete(ctx, s.task(), s.sessionCount() > 0)) reload(); }));

        buildTable();
        table.setItems(filtered);
        table.setPlaceholder(Ui.emptyState("No tasks yet", "Create your first task, for example “Cambridge IELTS 18, Reading Test 2”."));
        VBox.setVgrow(table, Priority.ALWAYS);

        HBox filters = new HBox(10, Ui.label("Skill", "form-label"), skillFilter, Ui.label("Status", "form-label"), statusFilter, Ui.grow(), total);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(8, start, edit, duplicate, done, reopen, skip, delete);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox header = new HBox(add);
        header.setAlignment(Pos.CENTER_RIGHT);

        root = Ui.pageBox("Tasks", "Targets, accumulated focused time and session counts per task.");
        root.getChildren().addAll(header, filters, table, actions);
        applyFilter();
    }

    private void buildTable() {
        TableSupport.column(table, "Task", 230, s -> s.task().title());
        TableSupport.column(table, "Skill", 105, s -> s.task().skill().label());
        TableSupport.column(table, "Priority", 80, s -> s.task().priority().toString());
        TableSupport.column(table, "Scheduled", 150, s -> s.task().scheduledDate() == null ? ""
                : Fmt.date(s.task().scheduledDate()) + (s.task().scheduledTime() == null ? "" : " " + Fmt.time(s.task().scheduledTime())));
        TableSupport.column(table, "Target", 70, s -> Fmt.hoursMinutes(s.task().targetSeconds() * 1000));
        TableSupport.column(table, "Focused", 85, s -> Fmt.hoursMinutes(s.focusedMillis()));
        TableSupport.column(table, "Sessions", 80, s -> Integer.toString(s.sessionCount()));
        TableSupport.column(table, "Last studied", 115, s -> s.lastStudied() == null ? "Never" : Fmt.shortDate(s.lastStudied(), ctx.zone));
        TableSupport.column(table, "Status", 90, s -> s.task().status().toString());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void selected(java.util.function.Consumer<TaskSummary> action) {
        TaskSummary s = table.getSelectionModel().getSelectedItem();
        if (s == null) {
            nav.toast("Select a task in the table first.");
            return;
        }
        action.accept(s);
    }

    private void reload() { nav.open(PageId.TASKS); }

    private void applyFilter() {
        Object skill = skillFilter.getValue();
        Object status = statusFilter.getValue();
        Predicate<TaskSummary> p = s -> (skill == ANY || s.task().skill() == skill) && (status == ANY || s.task().status() == status);
        filtered.setPredicate(p);
        total.setText(filtered.size() + " task" + (filtered.size() == 1 ? "" : "s"));
    }

    @Override public Node root() { return root; }
}
