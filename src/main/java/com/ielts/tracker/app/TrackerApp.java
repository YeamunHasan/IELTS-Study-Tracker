package com.ielts.tracker.app;

import com.ielts.tracker.repository.Database;
import com.ielts.tracker.service.BackupService;
import com.ielts.tracker.ui.MainView;
import com.ielts.tracker.ui.PageId;
import com.ielts.tracker.ui.RecoveryDialog;
import com.ielts.tracker.ui.Ui;
import com.ielts.tracker.util.ErrorLog;
import com.ielts.tracker.util.Fmt;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Optional;

public class TrackerApp extends Application {
    private AppContext ctx;
    private MainView view;
    private TrayController tray;

    @Override public void start(Stage stage) {
        Path dbFile = Database.defaultPath();
        ErrorLog.init(dbFile.resolveSibling("error.log"));
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> ErrorLog.log("Uncaught exception in " + t.getName(), e));

        try {
            ctx = AppContext.open(dbFile);
        } catch (RuntimeException e) {
            ErrorLog.log("Startup failed", e);
            new Alert(Alert.AlertType.ERROR, "The application could not open its database:\n" + dbFile + "\n\n" + e.getMessage()
                    + "\n\nYour data has not been modified.").showAndWait();
            Platform.exit();
            return;
        }

        String css = getClass().getResource("/com/ielts/tracker/dark.css").toExternalForm();
        Ui.init(stage, css);
        view = new MainView(ctx, stage, css);
        Scene scene = new Scene(view.root(), 1280, 820);
        scene.getStylesheets().add(css);

        stage.getIcons().add(AppIcon.fx(64));
        stage.setTitle("IELTS Study Tracker");
        stage.setMinWidth(1020);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (!prepareExit(stage)) e.consume();
        });
        stage.setOnHidden(e -> shutDown());
        stage.show();
        view.start();

        tray = TrayController.create(view::showMain, view::toggleTimer, stage::close);
        if (tray != null) view.setTrayTooltip(tray::setTooltip);
        boolean hook = GlobalInput.start(ctx.idle, () -> Platform.runLater(view::toggleTimer));

        // Offer recovery only once the window is visible so the dialog has an owner.
        Platform.runLater(() -> {
            if (RecoveryDialog.resolveAll(ctx)) view.open(PageId.TIMER);
            else view.open(PageId.DASHBOARD);
            if (!hook) view.toast("The global shortcut and the idle check are unavailable on this system. Everything else works.");
        });
    }

    /** Pause an active session safely, then copy the database to the backup folder. Returns false if the user wants to stay. */
    private boolean prepareExit(Stage stage) {
        if (ctx.sessions.hasActiveSession()) {
            ButtonType pauseExit = new ButtonType("Pause and exit", ButtonBar.ButtonData.OK_DONE);
            ButtonType finishExit = new ButtonType("Finish, save and exit", ButtonBar.ButtonData.APPLY);
            ButtonType stay = new ButtonType("Keep studying", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                    "A study session is in progress (" + Fmt.clock(ctx.sessions.timer().activeMillis()) + " focused).\n\n"
                            + "Pause and exit keeps it safely so you can resume next time. Finish saves it now.",
                    pauseExit, finishExit, stay);
            a.initOwner(stage);
            a.setHeaderText("Exit with an active session?");
            a.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
            Optional<ButtonType> r = a.showAndWait();
            if (r.isEmpty() || r.get() == stay) return false;
            try {
                if (r.get() == finishExit) ctx.sessions.finish(ctx.draftNotes.trim());
                else ctx.sessions.suspendForExit();
            } catch (RuntimeException ex) {
                ErrorLog.log("Could not save session on exit", ex);
                Ui.error("Could not save the session: " + ex.getMessage() + "\nThe application will stay open so nothing is lost.");
                return false;
            }
        }
        backupOnExit();
        return true;
    }

    /** Copies the database into the chosen folder, if any. A failed backup is reported but never stops the app from closing. */
    private void backupOnExit() {
        Optional<Path> folder = ctx.settings.backupFolder();
        if (folder.isEmpty()) return;
        try {
            BackupService.copy(ctx.database, folder.get(), ctx.time.now(), ctx.zone, BackupService.KEEP);
        } catch (Exception e) {
            ErrorLog.log("Backup on exit failed", e);
            Ui.error("The backup copy could not be made in " + folder.get() + ":\n" + e.getMessage()
                    + "\n\nYour data is safe in the main database. Check the backup folder in Settings.");
        }
    }

    private void shutDown() {
        view.stop();
        if (tray != null) tray.remove();
        GlobalInput.stop();
        Platform.exit();
        System.exit(0); // the native hook and AWT tray threads would otherwise keep the process alive
    }

    public static void main(String[] args) { launch(args); }
}
