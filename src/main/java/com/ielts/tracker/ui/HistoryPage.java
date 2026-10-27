package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.SessionStatus;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskSummary;
import com.ielts.tracker.model.TimerMode;
import com.ielts.tracker.repository.SessionRepository;
import com.ielts.tracker.util.Fmt;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class HistoryPage implements Page {
    private static final String ANY = "All";

    private final AppContext ctx;
    private final Navigator nav;
    private final VBox root;
    private final TableView<StudySession> table = new TableView<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final ComboBox<Object> skill = new ComboBox<>();
    private final ComboBox<Object> task = new ComboBox<>();
    private final ComboBox<Object> status = new ComboBox<>();
    private final ComboBox<Object> mode = new ComboBox<>();
    private final Label summary = Ui.label("", "muted");

    public HistoryPage(AppContext ctx, Navigator nav) {
        this.ctx = ctx;
        this.nav = nav;

        from.setPromptText("From date");
        to.setPromptText("To date");
        from.setPrefWidth(140);
        to.setPrefWidth(140);
        for (ComboBox<Object> c : List.of(skill, task, status, mode)) c.getItems().add(ANY);
        skill.getItems().addAll(Skill.values());
        task.getItems().addAll(ctx.tasks.list().stream().map(TaskSummary::task).toList());
        status.getItems().addAll(SessionStatus.COMPLETED, SessionStatus.INTERRUPTED, SessionStatus.CANCELLED);
        mode.getItems().addAll(TimerMode.values());
        for (ComboBox<Object> c : List.of(skill, task, status, mode)) {
            c.setValue(ANY);
            c.valueProperty().addListener((o, a, b) -> refresh());
        }
        from.valueProperty().addListener((o, a, b) -> refresh());
        to.valueProperty().addListener((o, a, b) -> refresh());

        Button clear = Ui.secondary("Clear filters");
        clear.setOnAction(e -> {
            from.setValue(null);
            to.setValue(null);
            for (ComboBox<Object> c : List.of(skill, task, status, mode)) c.setValue(ANY);
        });
        HBox filters = new HBox(8, from, to, skill, task, status, mode, clear);
        filters.setAlignment(Pos.CENTER_LEFT);
        task.setMaxWidth(200);

        TableSupport.column(table, "Date", 105, s -> Fmt.shortDate(s.start(), ctx.zone));
        TableSupport.column(table, "Task", 200, StudySession::taskTitle);
        TableSupport.column(table, "Skill", 100, s -> s.skill().label());
        TableSupport.column(table, "Start", 60, s -> Fmt.clockTime(s.start(), ctx.zone));
        TableSupport.column(table, "Focused", 80, s -> duration(s.activeMillis()));
        TableSupport.column(table, "Target", 70, s -> s.targetSeconds() == 0 ? "-" : Fmt.hoursMinutes(s.targetSeconds() * 1000));
        TableSupport.column(table, "Mode", 95, s -> s.mode().toString());
        TableSupport.column(table, "Status", 130, s -> s.isManual() ? "Manual" : s.status() + (s.recovered() ? " (recovered)" : ""));
        TableSupport.column(table, "Notes", 100, s -> s.notes() == null ? "" : s.notes().replace('\n', ' '));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.emptyState("No sessions found", "Finish a focus session, or loosen the filters, to see records here."));
        table.setRowFactory(tv -> {
            TableRow<StudySession> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) openDetails(row.getItem()); });
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        Button details = Ui.secondary("Details / edit notes");
        details.setOnAction(e -> selected(this::openDetails));
        Button delete = Ui.danger("Delete record");
        delete.setOnAction(e -> selected(this::delete));
        Button export = Ui.secondary("Export CSV");
        export.setOnAction(e -> exportCsv());
        Button manual = Ui.primary("Add session done elsewhere");
        manual.setOnAction(e -> { if (ManualSessionDialog.show(ctx)) { nav.toast("Manual session added."); refresh(); } });
        HBox actions = new HBox(8, manual, details, delete, Ui.grow(), summary, export);
        actions.setAlignment(Pos.CENTER_LEFT);

        root = Ui.pageBox("Study History", "Every saved session. Manual entries are marked Manual. Double-click a row for details.");
        root.getChildren().addAll(filters, table, actions);
        refresh();
    }

    private static String duration(long ms) {
        return Fmt.compact(ms);
    }

    private SessionRepository.Filter currentFilter() {
        Long fromMs = from.getValue() == null ? null : from.getValue().atStartOfDay(ctx.zone).toInstant().toEpochMilli();
        Long toMs = to.getValue() == null ? null : to.getValue().plusDays(1).atStartOfDay(ctx.zone).toInstant().toEpochMilli();
        return new SessionRepository.Filter(fromMs, toMs,
                skill.getValue() instanceof Skill s ? s : null,
                task.getValue() instanceof StudyTask t ? t.id() : null,
                status.getValue() instanceof SessionStatus st ? st : null,
                mode.getValue() instanceof TimerMode m ? m : null);
    }

    private void refresh() {
        Ui.guarded(() -> {
            List<StudySession> rows = ctx.sessionRepository.list(currentFilter());
            table.setItems(FXCollections.observableArrayList(rows));
            long counted = rows.stream().filter(s -> s.status().countsTowardsTotals()).mapToLong(StudySession::activeMillis).sum();
            summary.setText(rows.size() + " record" + (rows.size() == 1 ? "" : "s") + " · " + Fmt.hoursMinutes(counted) + " counted focused time");
        });
    }

    private void selected(java.util.function.Consumer<StudySession> action) {
        StudySession s = table.getSelectionModel().getSelectedItem();
        if (s == null) nav.toast("Select a session first."); else action.accept(s);
    }

    private void openDetails(StudySession s) {
        Dialog<ButtonType> d = new Dialog<>();
        Ui.styleDialog(d);
        d.setTitle("Session details");
        d.setHeaderText(s.taskTitle());
        ButtonType save = new ButtonType("Save notes", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CLOSE);

        TextArea notes = new TextArea(s.notes());
        notes.setWrapText(true);
        notes.setPrefRowCount(5);
        VBox box = new VBox(8,
                line("Skill", s.skill().label()),
                line("Started", Fmt.dateTime(s.start(), ctx.zone)),
                line("Ended", s.end() == null ? "-" : Fmt.dateTime(s.end(), ctx.zone)),
                line("Focused time", Fmt.clock(s.activeMillis())),
                line("Paused time", Fmt.clock(s.pausedMillis())),
                line("Target", s.targetSeconds() == 0 ? "-" : Fmt.clock(s.targetSeconds() * 1000)),
                line("Over target", s.overrunMillis() == 0 ? "-" : Fmt.clock(s.overrunMillis())),
                line("Mode", s.mode().toString()),
                lapLines(s),
                line("Status", s.status() + (s.recovered() ? " (recovered after an interruption)" : "")),
                Ui.label("Notes", "form-label"), notes);
        box.setPadding(new Insets(8, 4, 4, 4));
        box.setPrefWidth(460);
        d.getDialogPane().setContent(box);
        Optional<ButtonType> r = d.showAndWait();
        if (r.isPresent() && r.get() == save) {
            Ui.guarded(() -> ctx.sessionRepository.updateNotes(s.id(), notes.getText()));
            refresh();
        }
    }

    /** Time per passage or section for exam-mode sessions. */
    private Node lapLines(StudySession s) {
        var laps = ctx.sessionRepository.laps(s.id());
        if (laps.isEmpty()) return new VBox();
        StringBuilder sb = new StringBuilder();
        long prev = 0;
        for (var l : laps) {
            if (sb.length() > 0) sb.append("   ");
            sb.append(l.label()).append(" ").append(Fmt.clock(l.atActiveMillis() - prev).substring(3));
            prev = l.atActiveMillis();
        }
        return line("Time per part", sb.toString());
    }

    private static Node line(String k, String v) {
        Label key = Ui.label(k, "form-label");
        key.setMinWidth(110);
        HBox h = new HBox(10, key, Ui.label(v, "detail-value"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private void delete(StudySession s) {
        String msg = "Delete this " + Fmt.hoursMinutes(s.activeMillis()) + " session on “" + s.taskTitle() + "” ("
                + Fmt.dateTime(s.start(), ctx.zone) + ")? Totals and reports will be recalculated without it. This cannot be undone.";
        if (Ui.confirm("Delete session record?", msg, "Delete record")) {
            Ui.guarded(() -> ctx.sessionRepository.delete(s.id()));
            refresh();
        }
    }

    private void exportCsv() {
        List<StudySession> rows = table.getItems();
        if (rows.isEmpty()) {
            nav.toast("There are no records to export.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export study history");
        fc.setInitialFileName("ielts-study-history-" + LocalDate.now() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File f = fc.showSaveDialog(root.getScene().getWindow());
        if (f == null) return;
        Ui.guarded(() -> {
            try {
                ctx.export.exportHistory(f.toPath(), rows);
                nav.toast("Exported " + rows.size() + " records to " + f.getName());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not write the file: " + e.getMessage(), e);
            }
        });
    }

    @Override public Node root() { return root; }
}
