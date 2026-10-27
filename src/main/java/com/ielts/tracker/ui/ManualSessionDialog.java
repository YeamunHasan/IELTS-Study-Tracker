package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/** Adds a session done away from the laptop. It is saved as a Manual session, never mixed up with timed ones. */
public final class ManualSessionDialog {
    private ManualSessionDialog() {}

    /** Returns true if a session was saved. */
    public static boolean show(AppContext ctx) {
        Dialog<Boolean> d = new Dialog<>();
        Ui.styleDialog(d);
        d.setTitle("Add a session done elsewhere");
        d.setHeaderText("Add a manual session");
        ButtonType save = new ButtonType("Add session", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<StudyTask> task = new ComboBox<>();
        task.getItems().addAll(ctx.tasks.list().stream().map(ts -> ts.task()).filter(t -> t.status() != TaskStatus.ARCHIVED).toList());
        task.setPromptText("Choose a task");
        task.setMaxWidth(Double.MAX_VALUE);
        DatePicker date = new DatePicker(ctx.stats.today());
        TextField time = new TextField("18:00");
        Spinner<Integer> minutes = new Spinner<>(1, 720, 30, 5);
        minutes.setEditable(true);
        TextArea note = new TextArea();
        note.setPromptText("Where or how you studied (optional)");
        note.setPrefRowCount(2);
        note.setWrapText(true);
        Label error = Ui.label("", "error-text");

        GridPane g = Ui.form();
        int r = 0;
        g.addRow(r++, Ui.label("Task", "form-label"), task);
        g.addRow(r++, Ui.label("Date", "form-label"), date);
        g.addRow(r++, Ui.label("Start time", "form-label"), time);
        g.addRow(r++, Ui.label("Minutes studied", "form-label"), minutes);
        g.addRow(r++, Ui.label("Note", "form-label"), note);
        g.add(Ui.wrapped("Manual sessions count towards your totals and are marked Manual in your history.", "muted-small"), 1, r++);
        g.add(error, 1, r);
        g.setPrefWidth(460);
        d.getDialogPane().setContent(g);
        d.setResultConverter(bt -> bt == save);

        d.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                if (task.getValue() == null) throw new IllegalArgumentException("Choose a task.");
                minutes.commitValue();
                LocalTime start;
                try {
                    String t = time.getText().trim();
                    start = LocalTime.parse(t.length() == 4 && t.charAt(1) == ':' ? "0" + t : t);
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException("Enter the start time as HH:mm, for example 18:30.");
                }
                LocalDate day = date.getValue();
                ctx.sessionService.addManual(task.getValue().id(), day, start, minutes.getValue(), note.getText());
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                e.consume();
            }
        });
        return d.showAndWait().orElse(false);
    }
}
