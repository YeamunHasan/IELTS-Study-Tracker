package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.Essay;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.Optional;

/** Stores an essay with its word count, time taken and a self rating on the four official Writing criteria. */
public final class EssayDialog {
    private EssayDialog() {}

    public static int wordCount(String text) {
        String t = text == null ? "" : text.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    /** @param existing the essay to edit, or null for a new one; @param minutesTaken prefilled time (may be null) */
    public static boolean show(AppContext ctx, Essay existing, Long minutesTaken) {
        Dialog<Boolean> d = new Dialog<>();
        Ui.styleDialog(d);
        d.setTitle(existing == null ? "New essay" : "Edit essay");
        d.setHeaderText(existing == null ? "Save an essay" : "Edit essay");
        ButtonType save = new ButtonType("Save essay", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll("Task 1", "Task 2");
        type.setValue(existing == null ? "Task 2" : existing.taskType());
        TextField title = new TextField(existing == null ? "" : existing.title());
        title.setPromptText("Question or topic");
        TextArea body = new TextArea(existing == null ? "" : existing.body());
        body.setWrapText(true);
        body.setPrefRowCount(12);
        body.setPromptText("Paste or type the essay here");
        Label words = Ui.label("0 words", "muted");
        body.textProperty().addListener((o, a, b) -> words.setText(wordCount(b) + " words"));
        words.setText(wordCount(body.getText()) + " words");
        Spinner<Integer> minutes = new Spinner<>(0, 300, existing != null && existing.secondsTaken() != null ? (int) (existing.secondsTaken() / 60)
                : minutesTaken != null ? (int) Math.min(300, minutesTaken) : 0);
        minutes.setEditable(true);
        ComboBox<String> tr = Ui.bandBox(existing == null ? null : existing.taskResponse(), true);
        ComboBox<String> cc = Ui.bandBox(existing == null ? null : existing.coherence(), true);
        ComboBox<String> lr = Ui.bandBox(existing == null ? null : existing.lexical(), true);
        ComboBox<String> gra = Ui.bandBox(existing == null ? null : existing.grammar(), true);
        TextArea note = new TextArea(existing == null ? "" : existing.note());
        note.setPromptText("What to fix next time (optional)");
        note.setPrefRowCount(2);
        note.setWrapText(true);
        Label error = Ui.label("", "error-text");

        GridPane g = Ui.form();
        int r = 0;
        g.addRow(r++, Ui.label("Task", "form-label"), type);
        g.addRow(r++, Ui.label("Title", "form-label"), title);
        g.addRow(r++, Ui.label("Essay", "form-label"), new VBox(4, body, words));
        g.addRow(r++, Ui.label("Minutes taken", "form-label"), minutes);
        g.add(Ui.label("Self rating (your own estimate, not an official score)", "section-label"), 0, r++, 2, 1);
        g.addRow(r++, Ui.label("Task Achievement / Response", "form-label"), tr);
        g.addRow(r++, Ui.label("Coherence and Cohesion", "form-label"), cc);
        g.addRow(r++, Ui.label("Lexical Resource", "form-label"), lr);
        g.addRow(r++, Ui.label("Grammatical Range and Accuracy", "form-label"), gra);
        g.addRow(r++, Ui.label("Note", "form-label"), note);
        g.add(error, 1, r);
        g.setPrefWidth(680);
        g.setPadding(new Insets(4));
        d.getDialogPane().setContent(g);
        d.setResultConverter(bt -> bt == save);

        d.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                minutes.commitValue();
                if (body.getText().isBlank()) throw new IllegalArgumentException("Paste or type the essay text first.");
                Essay essay = new Essay(existing == null ? 0 : existing.id(), existing == null ? null : existing.createdAt(), type.getValue(),
                        title.getText().trim(), body.getText(), wordCount(body.getText()),
                        minutes.getValue() > 0 ? minutes.getValue() * 60L : null,
                        Ui.bandValue(tr), Ui.bandValue(cc), Ui.bandValue(lr), Ui.bandValue(gra), note.getText());
                ctx.writing.saveEssay(essay, ctx.time.now());
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                e.consume();
            }
        });
        return d.showAndWait().orElse(false);
    }
}
