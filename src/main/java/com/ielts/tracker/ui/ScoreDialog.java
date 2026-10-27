package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.PracticeResult;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.util.Fmt;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.util.Optional;

/** One form for a mock or section test result: date, source, raw score (Listening and Reading), band and a note. */
public final class ScoreDialog {
    private ScoreDialog() {}

    /** What the user entered, plus whether to continue straight into logging mistakes or adding the essay. */
    public record Entry(PracticeResult result, boolean logMistakes, boolean addEssay) {}

    private static String lastSource = "";

    /**
     * @param fixedSkill   when set (after an exam-mode session) the skill cannot be changed
     * @param secondsTaken focused time of the timed session, saved in the same record; null for a manual entry
     * @param sessionId    the timed session this result belongs to; null for a manual entry
     * @param followUps    offer the "log mistakes" and "add essay" check boxes
     */
    public static Optional<Entry> show(AppContext ctx, Skill fixedSkill, Long secondsTaken, Long sessionId, boolean followUps) {
        Dialog<Entry> d = new Dialog<>();
        Ui.styleDialog(d);
        d.setTitle("Practice result");
        d.setHeaderText(secondsTaken != null ? "Test finished in " + Fmt.clock(secondsTaken * 1000) + ". Enter your result."
                : "Record a mock or section test");
        ButtonType save = new ButtonType("Save result", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        ((Button) d.getDialogPane().lookupButton(ButtonType.CANCEL)).setText(followUps ? "Skip scoring" : "Cancel");

        DatePicker date = new DatePicker(ctx.stats.today());
        TextField source = new TextField(lastSource);
        source.setPromptText("e.g. Cambridge IELTS 18, Test 2");
        ComboBox<Skill> skill = new ComboBox<>();
        skill.getItems().addAll(Skill.BAND_SKILLS);
        skill.setValue(fixedSkill != null ? fixedSkill : Skill.READING);
        skill.setDisable(fixedSkill != null);
        TextField raw = new TextField();
        raw.setPromptText("0 to 40 (Listening and Reading)");
        Label rawLabel = Ui.label("Raw score", "form-label");
        ComboBox<String> band = Ui.bandBox(null, false);
        TextArea note = new TextArea();
        note.setPromptText("What went well or badly (optional)");
        note.setPrefRowCount(2);
        note.setWrapText(true);
        CheckBox mistakes = new CheckBox("Log mistakes from this test next");
        CheckBox essay = new CheckBox("Save the essay text next");
        Label error = Ui.label("", "error-text");

        Runnable skillChanged = () -> {
            boolean rawSkill = skill.getValue() == Skill.LISTENING || skill.getValue() == Skill.READING;
            raw.setDisable(!rawSkill);
            rawLabel.setDisable(!rawSkill);
            if (!rawSkill) raw.clear();
            essay.setVisible(followUps && skill.getValue() == Skill.WRITING);
            essay.setManaged(essay.isVisible());
            ctx.practiceRepository.latest(skill.getValue()).ifPresent(r -> band.setValue(String.format("%.1f", r.band())));
        };
        skill.valueProperty().addListener((o, a, b) -> skillChanged.run());
        skillChanged.run();

        GridPane g = Ui.form();
        int r = 0;
        g.addRow(r++, Ui.label("Test date", "form-label"), date);
        g.addRow(r++, Ui.label("Source", "form-label"), source);
        g.addRow(r++, Ui.label("Skill", "form-label"), skill);
        g.addRow(r++, rawLabel, raw);
        g.addRow(r++, Ui.label("Band", "form-label"), band);
        g.addRow(r++, Ui.label("Note", "form-label"), note);
        if (followUps) {
            g.add(mistakes, 1, r++);
            g.add(essay, 1, r++);
        }
        g.add(error, 1, r);
        g.setPrefWidth(480);
        d.getDialogPane().setContent(g);

        long[] savedId = {0};
        d.setResultConverter(bt -> {
            if (bt != save) return null;
            PracticeResult r0 = build(date, source, skill, raw, band, note, secondsTaken, sessionId);
            PracticeResult saved = new PracticeResult(savedId[0], r0.testDate(), r0.source(), r0.skill(), r0.rawScore(), r0.band(),
                    r0.secondsTaken(), r0.sessionId(), r0.note());
            return new Entry(saved, mistakes.isSelected(), essay.isVisible() && essay.isSelected());
        });
        d.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                PracticeResult pr = build(date, source, skill, raw, band, note, secondsTaken, sessionId);
                savedId[0] = ctx.practice.record(pr);   // validates and saves
                lastSource = pr.source();
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                e.consume();
            }
        });
        return d.showAndWait();
    }

    private static PracticeResult build(DatePicker date, TextField source, ComboBox<Skill> skill, TextField raw, ComboBox<String> band,
                                        TextArea note, Long seconds, Long sessionId) {
        Integer rawScore = null;
        if (!raw.isDisabled() && !raw.getText().isBlank()) {
            try { rawScore = Integer.parseInt(raw.getText().trim()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("The raw score must be a whole number from 0 to 40."); }
        }
        return new PracticeResult(0, date.getValue(), source.getText(), skill.getValue(), rawScore, Ui.bandValue(band), seconds, sessionId, note.getText());
    }
}
