package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.Recording;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;

/** Saves where a Speaking recording lives (a file path or a link) plus a short note. The audio itself is never copied. */
public final class RecordingDialog {
    private RecordingDialog() {}

    public static boolean show(AppContext ctx) {
        Dialog<Boolean> d = new Dialog<>();
        Ui.styleDialog(d);
        d.setTitle("Speaking recording");
        d.setHeaderText("Add a speaking recording");
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<String> part = new ComboBox<>();
        part.getItems().addAll("Part 1", "Part 2", "Part 3", "Full test");
        part.setValue("Part 2");
        TextField location = new TextField();
        location.setPromptText("File path or link");
        location.setPrefWidth(300);
        Button browse = Ui.secondary("Browse…");
        browse.setOnAction(e -> {
            File f = new FileChooser().showOpenDialog(d.getDialogPane().getScene().getWindow());
            if (f != null) location.setText(f.getAbsolutePath());
        });
        TextArea note = new TextArea();
        note.setPromptText("Short note: what to improve, a question you struggled with");
        note.setPrefRowCount(3);
        note.setWrapText(true);
        Label error = Ui.label("", "error-text");

        GridPane g = Ui.form();
        g.addRow(0, Ui.label("Part", "form-label"), part);
        g.addRow(1, Ui.label("Recording", "form-label"), new HBox(8, location, browse));
        g.addRow(2, Ui.label("Note", "form-label"), note);
        g.add(error, 1, 3);
        g.setPrefWidth(520);
        d.getDialogPane().setContent(g);
        d.setResultConverter(bt -> bt == save);
        d.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
            if (location.getText().isBlank()) {
                error.setText("Enter the file path or link to the recording.");
                e.consume();
                return;
            }
            ctx.writing.addRecording(new Recording(0, null, part.getValue(), location.getText(), note.getText()), ctx.time.now());
        });
        return d.showAndWait().orElse(false);
    }
}
