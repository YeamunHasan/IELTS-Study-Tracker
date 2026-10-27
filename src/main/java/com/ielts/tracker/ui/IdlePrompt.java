package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.util.Fmt;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * "Still studying?" prompt shown after a long stretch without keyboard or mouse input. It never decides for the user:
 * it waits for an answer, and closing it changes nothing.
 */
final class IdlePrompt {
    private static Stage open;

    private IdlePrompt() {}

    static void show(AppContext ctx, long idleMillis, Navigator nav, String stylesheet) {
        if (open != null && open.isShowing()) return;
        Stage st = new Stage(StageStyle.UTILITY);
        open = st;
        st.setTitle("Still studying?");
        st.setAlwaysOnTop(true);

        String idleText = Fmt.hoursMinutes(idleMillis);
        Label q = Ui.label("Still studying?", "card-title");
        Label detail = Ui.wrapped("No keyboard or mouse activity for " + idleText + ". If you were reading on paper, choose the first option. "
                + "If you walked away, choose the second and the time since you stopped is removed.", "muted");
        Button yes = Ui.primary("Yes, I'm still studying");
        Button no = Ui.secondary("No, I stopped about " + idleText + " ago");
        Button pause = Ui.secondary("Pause now");
        yes.setOnAction(e -> { ctx.idle.onInput(); st.close(); });
        no.setOnAction(e -> {
            Ui.guarded(() -> {
                if (ctx.sessions.timer().state() == com.ielts.tracker.timer.FocusTimer.State.RUNNING) {
                    long removed = ctx.sessions.pauseBackdated(idleMillis);
                    nav.toast("Paused. Removed " + Fmt.hoursMinutes(removed) + " of idle time from the session.");
                }
            });
            st.close();
        });
        pause.setOnAction(e -> {
            Ui.guarded(() -> { if (ctx.sessions.timer().state() == com.ielts.tracker.timer.FocusTimer.State.RUNNING) ctx.sessions.pause(); });
            st.close();
        });
        for (Button b : new Button[]{yes, no, pause}) b.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(10, q, detail, yes, no, pause);
        box.setPadding(new Insets(18));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("mini-window");
        box.setPrefWidth(430);
        Scene scene = new Scene(box);
        scene.getStylesheets().add(stylesheet);
        st.setScene(scene);
        st.sizeToScene();
        st.show();
        st.toFront();
    }
}
