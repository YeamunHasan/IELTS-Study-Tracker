package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.SessionStatus;
import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.util.Fmt;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

import java.util.List;
import java.util.Optional;

/** Startup prompt for sessions left RUNNING or PAUSED by a crash, power loss or forced close. */
public final class RecoveryDialog {
    private RecoveryDialog() {}

    /** Returns true if a session was resumed (the caller should show the timer). */
    public static boolean resolveAll(AppContext ctx) {
        boolean resumed = false;
        for (StudySession s : ctx.sessions.pendingRecovery()) {
            resumed |= resolve(ctx, s);
        }
        return resumed;
    }

    private static boolean resolve(AppContext ctx, StudySession s) {
        while (true) {
            boolean wasPaused = s.status() == SessionStatus.PAUSED;
            String message = "“" + s.taskTitle() + "” (" + s.skill().label() + ") was still "
                    + (wasPaused ? "paused" : "running") + " when the application last closed.\n\n"
                    + "Started: " + Fmt.dateTime(s.start(), ctx.zone) + "\n"
                    + "Focused time recorded: " + Fmt.clock(s.activeMillis()) + "\n"
                    + "Last saved: " + Fmt.dateTime(s.lastCheckpoint(), ctx.zone) + "\n\n"
                    + (wasPaused
                    ? "The session was paused, so the recorded time is exact."
                    : "The app saves its progress every few seconds, so up to a few seconds before the shutdown may be missing. "
                    + "Time after the last save is unknown and is not counted; the time the computer was off is never added.");

            ButtonType resume = new ButtonType("Recover and continue", ButtonBar.ButtonData.OK_DONE);
            ButtonType save = new ButtonType("Save recorded time", ButtonBar.ButtonData.APPLY);
            ButtonType discard = new ButtonType("Discard…", ButtonBar.ButtonData.LEFT);
            Dialog<ButtonType> d = new Dialog<>();
            Ui.styleDialog(d);
            d.setTitle("Unfinished session found");
            d.setHeaderText("Recover your unfinished study session?");
            d.setContentText(message);
            d.getDialogPane().getButtonTypes().addAll(resume, save, discard);
            d.getDialogPane().setMinWidth(520);
            Optional<ButtonType> choice = d.showAndWait();

            ButtonType picked = choice.orElse(save); // closing the dialog never discards data
            try {
                if (picked == resume) {
                    ctx.sessions.resumeRecovered(s);
                    return true;
                }
                if (picked == discard) {
                    if (Ui.confirm("Discard this session?",
                            "The " + Fmt.clock(s.activeMillis()) + " of recorded focused time will be excluded from all totals.", "Discard session")) {
                        ctx.sessions.discardRecovered(s);
                        return false;
                    }
                    continue; // user backed out of discarding; ask again
                }
                ctx.sessions.saveRecoveredAsInterrupted(s);
                return false;
            } catch (RuntimeException e) {
                Ui.error("Could not complete recovery: " + e.getMessage());
                return false;
            }
        }
    }
}
