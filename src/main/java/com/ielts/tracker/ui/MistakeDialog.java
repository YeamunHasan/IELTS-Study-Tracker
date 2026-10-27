package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.QuestionTypes;
import com.ielts.tracker.model.Skill;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;

/** Quick form for one mistake: skill, question type, one line of reason, marks lost, and when to suggest a review. */
public final class MistakeDialog {
    private MistakeDialog() {}

    /**
     * Logs mistakes until the user stops. Each save writes to the database immediately.
     *
     * @return how many mistakes were logged
     */
    public static int logLoop(AppContext ctx, Skill skill, String source, Long practiceResultId, Navigator nav) {
        int logged = 0;
        Skill current = skill;
        while (true) {
            Optional<Boolean> again = showOnce(ctx, current, source, practiceResultId);
            if (again.isEmpty()) break;
            logged++;
            if (!again.get()) break;
        }
        if (logged > 0) nav.toast(logged == 1 ? "Mistake logged." : logged + " mistakes logged.");
        return logged;
    }

    /** Empty if cancelled; otherwise whether the user asked to add another. */
    private static Optional<Boolean> showOnce(AppContext ctx, Skill skill, String source, Long practiceResultId) {
        Dialog<Boolean> d = new Dialog<>();
        Ui.styleDialog(d);
        d.setTitle("Log a mistake");
        d.setHeaderText("Log a mistake");
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<Skill> skillBox = new ComboBox<>();
        skillBox.getItems().addAll(Skill.BAND_SKILLS);
        skillBox.setValue(skill == null ? Skill.READING : skill);
        ComboBox<String> type = new ComboBox<>();
        type.setMaxWidth(Double.MAX_VALUE);
        Runnable fill = () -> {
            type.getItems().setAll(QuestionTypes.forSkill(skillBox.getValue()));
            type.setValue(null);
        };
        skillBox.valueProperty().addListener((o, a, b) -> fill.run());
        fill.run();
        TextField reason = new TextField();
        reason.setPromptText("One line: why did you lose the mark?");
        Spinner<Integer> marks = new Spinner<>(1, 40, 1);
        marks.setEditable(true);
        Spinner<Integer> reviewDays = new Spinner<>(0, 30, 3);
        reviewDays.setEditable(true);
        TextField src = new TextField(source == null ? "" : source);
        src.setPromptText("Test or material (optional)");
        CheckBox another = new CheckBox("Add another mistake");
        Label error = Ui.label("", "error-text");

        GridPane g = Ui.form();
        int r = 0;
        g.addRow(r++, Ui.label("Skill", "form-label"), skillBox);
        g.addRow(r++, Ui.label("Question type", "form-label"), type);
        g.addRow(r++, Ui.label("Reason", "form-label"), reason);
        g.addRow(r++, Ui.label("Marks lost", "form-label"), marks);
        g.addRow(r++, Ui.label("Review after (days)", "form-label"), reviewDays);
        g.addRow(r++, Ui.label("Source", "form-label"), src);
        g.add(Ui.wrapped("A review is only suggested. A task is created if you accept it on the dashboard. Use 0 for no review.", "muted-small"), 1, r++);
        g.add(another, 1, r++);
        g.add(error, 1, r);
        g.setPrefWidth(500);
        d.getDialogPane().setContent(g);
        d.setResultConverter(bt -> bt == save ? another.isSelected() : null);

        d.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                marks.commitValue();
                reviewDays.commitValue();
                ctx.mistakes.log(skillBox.getValue(), type.getValue(), reason.getText(), marks.getValue(), src.getText(),
                        practiceResultId, reviewDays.getValue());
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                e.consume();
            }
        });
        return d.showAndWait();
    }
}
