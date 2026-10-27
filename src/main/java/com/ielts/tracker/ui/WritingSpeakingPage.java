package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.Essay;
import com.ielts.tracker.model.Recording;
import com.ielts.tracker.util.Fmt;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

/** The two skills with the least feedback: essays with a self rating on the four criteria, and a list of speaking recordings. */
public final class WritingSpeakingPage implements Page {
    private final AppContext ctx;
    private final Navigator nav;
    private final VBox root;

    public WritingSpeakingPage(AppContext ctx, Navigator nav) {
        this.ctx = ctx;
        this.nav = nav;
        root = Ui.pageBox("Writing & Speaking", "Keep your essays and recordings with your own ratings and notes, so you can review them later.");
        TabPane tabs = new TabPane(new Tab("Essays", essays()), new Tab("Speaking recordings", speaking()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getChildren().add(tabs);
    }

    @Override public Node root() { return root; }

    private Node essays() {
        TableView<Essay> table = new TableView<>();
        TableSupport.column(table, "Date", 100, e -> Fmt.shortDate(e.createdAt(), ctx.zone));
        TableSupport.column(table, "Task", 70, Essay::taskType);
        TableSupport.column(table, "Title", 260, Essay::title);
        TableSupport.column(table, "Words", 70, e -> Integer.toString(e.wordCount()));
        TableSupport.column(table, "Time", 80, e -> e.secondsTaken() == null ? "" : Fmt.hoursMinutes(e.secondsTaken() * 1000));
        TableSupport.column(table, "TA/TR", 60, e -> band(e.taskResponse()));
        TableSupport.column(table, "CC", 50, e -> band(e.coherence()));
        TableSupport.column(table, "LR", 50, e -> band(e.lexical()));
        TableSupport.column(table, "GRA", 50, e -> band(e.grammar()));
        TableSupport.column(table, "Self avg", 80, e -> band(e.selfAverage()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setItems(FXCollections.observableArrayList(ctx.writing.essays()));
        table.setPlaceholder(Ui.emptyState("No essays saved", "Save an essay after writing it, with the time taken and your own rating."));
        table.setRowFactory(tv -> {
            TableRow<Essay> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty() && EssayDialog.show(ctx, row.getItem(), null)) nav.open(PageId.WRITING); });
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        Button add = Ui.primary("New essay");
        add.setOnAction(e -> { if (EssayDialog.show(ctx, null, null)) nav.open(PageId.WRITING); });
        Button edit = Ui.secondary("Open / edit");
        edit.setOnAction(e -> {
            Essay s = table.getSelectionModel().getSelectedItem();
            if (s == null) nav.toast("Select an essay first.");
            else if (EssayDialog.show(ctx, s, null)) nav.open(PageId.WRITING);
        });
        Button delete = Ui.danger("Delete");
        delete.setOnAction(e -> {
            Essay s = table.getSelectionModel().getSelectedItem();
            if (s == null) { nav.toast("Select an essay first."); return; }
            if (Ui.confirm("Delete this essay?", "“" + (s.title().isBlank() ? s.taskType() : s.title()) + "”, " + s.wordCount() + " words. This cannot be undone.", "Delete")) {
                Ui.guarded(() -> ctx.writing.deleteEssay(s.id()));
                nav.open(PageId.WRITING);
            }
        });
        Label note = Ui.label("TA/TR = Task Achievement or Response, CC = Coherence and Cohesion, LR = Lexical Resource, GRA = Grammatical Range and Accuracy. Ratings are your own estimates.", "muted-small");
        VBox box = new VBox(12, table, new HBox(8, add, edit, delete), note);
        box.setPadding(new javafx.geometry.Insets(14, 0, 0, 0));
        return box;
    }

    private static String band(Double v) { return v == null ? "" : String.format("%.1f", v); }

    private Node speaking() {
        TableView<Recording> table = new TableView<>();
        TableSupport.column(table, "Date", 100, r -> Fmt.shortDate(r.createdAt(), ctx.zone));
        TableSupport.column(table, "Part", 80, Recording::part);
        TableSupport.column(table, "Recording", 320, Recording::location);
        TableSupport.column(table, "Note", 100, r -> r.note().replace('\n', ' '));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setItems(FXCollections.observableArrayList(ctx.writing.recordings()));
        table.setPlaceholder(Ui.emptyState("No recordings saved", "Record yourself with any app, then save the file location here with a short note."));
        VBox.setVgrow(table, Priority.ALWAYS);

        Button add = Ui.primary("Add recording");
        add.setOnAction(e -> { if (RecordingDialog.show(ctx)) nav.open(PageId.WRITING); });
        Button open = Ui.secondary("Open recording");
        open.setOnAction(e -> {
            Recording r = table.getSelectionModel().getSelectedItem();
            if (r == null) nav.toast("Select a recording first."); else open(r.location());
        });
        Button delete = Ui.danger("Delete entry");
        delete.setOnAction(e -> {
            Recording r = table.getSelectionModel().getSelectedItem();
            if (r == null) { nav.toast("Select a recording first."); return; }
            if (Ui.confirm("Delete this entry?", "Only the entry is removed; the recording file itself is not touched.", "Delete entry")) {
                Ui.guarded(() -> ctx.writing.deleteRecording(r.id()));
                nav.open(PageId.WRITING);
            }
        });
        VBox box = new VBox(12, table, new HBox(8, add, open, delete));
        box.setPadding(new javafx.geometry.Insets(14, 0, 0, 0));
        return box;
    }

    private void open(String location) {
        try {
            String loc = location.trim();
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Opening files is not supported on this system.");
            if (loc.startsWith("http://") || loc.startsWith("https://")) {
                Desktop.getDesktop().browse(URI.create(loc));
            } else {
                File f = new File(loc);
                if (!f.exists()) throw new IllegalStateException("The file was not found: " + loc);
                Desktop.getDesktop().open(f);
            }
        } catch (Exception e) {
            Ui.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
