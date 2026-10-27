package com.ielts.tracker.ui;

import com.ielts.tracker.model.Priority;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Create / edit dialog for a task. Validation errors keep the dialog open. */
public final class TaskDialog {
    private TaskDialog() {}

    /**
     * @param existing the task to edit, or null to create
     * @param preset   defaults for a new task (date, skill); may be null
     */
    public static Optional<StudyTask> show(StudyTask existing, StudyTask preset, int defaultMinutes) {
        Dialog<StudyTask> dialog = new Dialog<>();
        Ui.styleDialog(dialog);
        dialog.setTitle(existing == null ? "New task" : "Edit task");
        dialog.setHeaderText(existing == null ? "Add a study task" : "Edit task");
        ButtonType save = new ButtonType(existing == null ? "Add task" : "Save changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        StudyTask base = existing != null ? existing : preset;

        TextField title = new TextField(base == null ? "" : base.title());
        title.setPromptText("e.g. Cambridge IELTS 18, Reading Test 2");
        ComboBox<Skill> skill = new ComboBox<>();
        skill.getItems().addAll(Skill.values());
        skill.setValue(base == null ? Skill.READING : base.skill());
        TextField category = new TextField(base == null ? "" : base.category());
        category.setPromptText("e.g. Timed practice (optional)");
        Spinner<Integer> minutes = new Spinner<>(1, 1440, base == null ? defaultMinutes : (int) Math.max(1, base.targetSeconds() / 60), 5);
        minutes.setEditable(true);
        ComboBox<Priority> priority = new ComboBox<>();
        priority.getItems().addAll(Priority.values());
        priority.setValue(base == null ? Priority.MEDIUM : base.priority());
        DatePicker date = new DatePicker(base == null ? null : base.scheduledDate());
        date.setPromptText("optional");
        TextField time = new TextField(base == null || base.scheduledTime() == null ? "" : base.scheduledTime().toString());
        time.setPromptText("HH:mm (optional)");
        TextArea description = new TextArea(base == null ? "" : base.description());
        description.setPromptText("Resource, goal, what to focus on (optional)");
        description.setPrefRowCount(3);
        description.setWrapText(true);
        TextArea notes = new TextArea(base == null ? "" : base.notes());
        notes.setPromptText("Notes or outcome (optional)");
        notes.setPrefRowCount(2);
        notes.setWrapText(true);
        Label error = Ui.label("", "error-text");
        error.setWrapText(true);

        GridPane grid = Ui.form();
        grid.setPadding(new Insets(8, 4, 4, 4));
        int r = 0;
        grid.addRow(r++, Ui.label("Title", "form-label"), title);
        grid.addRow(r++, Ui.label("IELTS skill", "form-label"), skill);
        grid.addRow(r++, Ui.label("Category", "form-label"), category);
        grid.addRow(r++, Ui.label("Target (minutes)", "form-label"), minutes);
        grid.addRow(r++, Ui.label("Priority", "form-label"), priority);
        grid.addRow(r++, Ui.label("Scheduled date", "form-label"), date);
        grid.addRow(r++, Ui.label("Start time", "form-label"), time);
        grid.addRow(r++, Ui.label("Description", "form-label"), description);
        grid.addRow(r++, Ui.label("Notes", "form-label"), notes);
        grid.add(error, 1, r);
        for (var n : new javafx.scene.Node[]{title, skill, category, minutes, priority, date, time, description, notes}) {
            GridPane.setHgrow(n, javafx.scene.layout.Priority.ALWAYS);
            if (n instanceof Region rg) rg.setMaxWidth(Double.MAX_VALUE);
        }
        grid.setPrefWidth(520);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> bt == save ? build(existing, title, skill, category, minutes, priority, date, time, description, notes) : null);

        Button ok = (Button) dialog.getDialogPane().lookupButton(save);
        ok.addEventFilter(ActionEvent.ACTION, e -> {
            try {
                build(existing, title, skill, category, minutes, priority, date, time, description, notes);
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                e.consume();
            }
        });
        title.requestFocus();
        return dialog.showAndWait();
    }

    private static StudyTask build(StudyTask existing, TextField title, ComboBox<Skill> skill, TextField category,
                                   Spinner<Integer> minutes, ComboBox<Priority> priority, DatePicker date, TextField time,
                                   TextArea description, TextArea notes) {
        if (title.getText().isBlank()) throw new IllegalArgumentException("Please enter a task title.");
        minutes.commitValue();
        Integer mins = minutes.getValue();
        if (mins == null || mins < 1) throw new IllegalArgumentException("The target must be at least 1 minute.");

        LocalTime start = null;
        String t = time.getText().trim();
        if (!t.isEmpty()) {
            try {
                start = LocalTime.parse(t.length() == 4 && t.charAt(1) == ':' ? "0" + t : t);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Enter the start time as HH:mm, for example 09:30.");
            }
        }
        if (start != null && date.getValue() == null) {
            throw new IllegalArgumentException("Choose a scheduled date to go with the start time.");
        }
        return new StudyTask(existing == null ? 0 : existing.id(), title.getText().trim(), description.getText(), skill.getValue(),
                category.getText().trim(), mins * 60L, priority.getValue(),
                existing == null ? TaskStatus.PLANNED : existing.status(),
                date.getValue(), start, notes.getText(),
                existing == null ? null : existing.createdAt(), existing == null ? null : existing.updatedAt(),
                existing == null ? null : existing.completedAt());
    }
}
