package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.ExamType;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.service.AppSettings;
import com.ielts.tracker.service.BackupService;
import com.ielts.tracker.service.ExamInfo;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Only what is worth changing: exam, target bands and routine, idle check, and the backup folder. */
public final class SettingsPage implements Page {
    private final AppContext ctx;
    private final Navigator nav;
    private final VBox root;

    public SettingsPage(AppContext ctx, Navigator nav) {
        this.ctx = ctx;
        this.nav = nav;
        root = Ui.pageBox("Settings", "Each section has its own Save button.");
        root.getChildren().addAll(examSection(), targetsSection(), backupSection());
    }

    @Override public Node root() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    private Node examSection() {
        ExamInfo exam = ctx.settings.exam();
        DatePicker date = new DatePicker(exam.date());
        date.setPromptText("Exam date");
        date.setPrefWidth(180);
        TextField time = new TextField(exam.time() == null ? "" : exam.time().toString());
        time.setPromptText("HH:mm (optional)");
        time.setPrefWidth(180);
        time.setMaxWidth(180);
        ComboBox<ExamType> type = new ComboBox<>();
        type.getItems().addAll(ExamType.values());
        type.setValue(exam.type());
        TextArea notes = new TextArea(exam.notes());
        notes.setPromptText("Venue, registration number, reminders (optional)");
        notes.setPrefRowCount(2);
        notes.setWrapText(true);

        GridPane g = form();
        g.addRow(0, Ui.label("Exam date", "form-label"), date);
        g.addRow(1, Ui.label("Exam time", "form-label"), time);
        g.addRow(2, Ui.label("Exam type", "form-label"), type);
        g.addRow(3, Ui.label("Venue / notes", "form-label"), notes);

        Button save = Ui.primary("Save exam details");
        save.setOnAction(e -> Ui.guarded(() -> {
            LocalTime t = null;
            String text = time.getText().trim();
            if (!text.isEmpty()) {
                try { t = LocalTime.parse(text.length() == 4 && text.charAt(1) == ':' ? "0" + text : text); }
                catch (DateTimeParseException ex) { throw new IllegalArgumentException("Enter the exam time as HH:mm, for example 09:30."); }
            }
            if (date.getValue() == null && t != null) throw new IllegalArgumentException("Choose an exam date to go with the time.");
            ctx.settings.saveExam(new ExamInfo(date.getValue(), t, type.getValue(), notes.getText()));
            nav.toast(date.getValue() == null ? "Exam details saved (no date set)." : "Exam saved for " + date.getValue() + ".");
        }));
        Button clear = Ui.secondary("Clear exam date");
        clear.setOnAction(e -> {
            if (Ui.confirm("Clear the exam date?", "The countdown will be removed from the dashboard. You can set a new date at any time.", "Clear")) {
                Ui.guarded(() -> ctx.settings.clearExam());
                nav.open(PageId.SETTINGS);
            }
        });
        return Ui.section("IELTS examination", g, new HBox(10, save, clear));
    }

    private Node targetsSection() {
        AppSettings s = ctx.settings;
        Map<Skill, ComboBox<String>> targets = new EnumMap<>(Skill.class);
        GridPane g = form();
        int r = 0;
        for (Skill skill : Skill.BAND_SKILLS) {
            ComboBox<String> box = Ui.bandBox(s.targetBand(skill), false);
            targets.put(skill, box);
            g.addRow(r++, Ui.label("Target band: " + skill.label(), "form-label"), box);
        }
        Spinner<Integer> daily = spinner(1, 1440, s.dailyGoalMinutes(), 15);
        Spinner<Integer> weekly = spinner(1, 10080, s.weeklyGoalMinutes(), 30);
        Spinner<Integer> streak = spinner(1, 1440, s.streakMinutes(), 5);
        Spinner<Integer> idle = spinner(1, 240, s.idleMinutes(), 1);
        g.addRow(r++, Ui.label("Daily study target (min)", "form-label"), daily);
        g.addRow(r++, Ui.label("Weekly study target (min)", "form-label"), weekly);
        g.addRow(r++, Ui.label("Minutes needed for a streak day", "form-label"), streak);
        g.addRow(r++, Ui.label("Ask “still studying?” after (min)", "form-label"), idle);
        g.add(Ui.wrapped("The idle check needs the global keyboard and mouse hook. Changing the idle time takes effect after a restart.", "muted-small"), 1, r);

        Button save = Ui.primary("Save targets and routine");
        save.setOnAction(e -> Ui.guarded(() -> {
            for (Spinner<Integer> sp : List.of(daily, weekly, streak, idle)) sp.commitValue();
            for (Skill skill : Skill.BAND_SKILLS) s.saveTargetBand(skill, Ui.bandValue(targets.get(skill)));
            s.saveStudyPreferences(daily.getValue(), weekly.getValue(), streak.getValue(), s.defaultMode(), s.defaultSessionMinutes());
            s.saveIdleMinutes(idle.getValue());
            nav.toast("Saved.");
        }));
        return Ui.section("Target bands and routine", g, save);
    }

    private Node backupSection() {
        Label db = Ui.label(ctx.database.file().toString(), "detail-value");
        db.setWrapText(true);
        TextField folder = new TextField(ctx.settings.backupFolder().map(Path::toString).orElse(""));
        folder.setPromptText("No folder chosen: automatic backup is off");
        folder.setEditable(false);
        HBox.setHgrow(folder, Priority.ALWAYS);
        Button choose = Ui.secondary("Choose folder…");
        choose.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Folder for backup copies");
            File f = dc.showDialog(root.getScene().getWindow());
            if (f != null) {
                Ui.guarded(() -> ctx.settings.saveBackupFolder(f.toPath()));
                folder.setText(f.getAbsolutePath());
            }
        });
        Button off = Ui.secondary("Turn off");
        off.setOnAction(e -> { ctx.settings.saveBackupFolder(null); folder.clear(); });
        Button now = Ui.secondary("Back up now");
        now.setOnAction(e -> Ui.guarded(() -> {
            Path target = ctx.settings.backupFolder().orElseThrow(() -> new IllegalArgumentException("Choose a backup folder first."));
            try {
                Path made = BackupService.copy(ctx.database, target, ctx.time.now(), ctx.zone, BackupService.KEEP);
                nav.toast("Backup written: " + made.getFileName());
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("Backup failed: " + ex.getMessage(), ex);
            }
        }));
        HBox row = new HBox(8, folder, choose, off, now);
        row.setAlignment(Pos.CENTER_LEFT);
        return Ui.section("Backup",
                Ui.label("Database file", "form-label"), db,
                Ui.label("Copy to this folder every time the app closes (newest " + BackupService.KEEP + " kept)", "form-label"), row,
                Ui.wrapped("Point it at a folder you already sync, such as a cloud drive folder, and your data is protected without this app going online. "
                        + "To restore, close the app, replace tracker.db with a copy (renamed to tracker.db) and delete tracker.db-wal and tracker.db-shm if present.", "muted-small"));
    }

    private static Spinner<Integer> spinner(int min, int max, int value, int step) {
        Spinner<Integer> sp = new Spinner<>(min, max, value, step);
        sp.setEditable(true);
        sp.setPrefWidth(140);
        return sp;
    }

    private static GridPane form() {
        GridPane g = Ui.form();
        g.setHgap(16);
        g.setAlignment(Pos.TOP_LEFT);
        return g;
    }
}
