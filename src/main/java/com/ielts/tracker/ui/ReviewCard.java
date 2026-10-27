package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.service.MistakeService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** Review tasks suggested from logged mistakes, each with one-click Accept or Ignore. */
final class ReviewCard {
    private ReviewCard() {}

    static VBox build(AppContext ctx, Navigator nav, List<MistakeService.ReviewGroup> groups, PageId reopen) {
        VBox card = Ui.card(Ui.label("Reviews suggested from your mistakes", "card-title"),
                Ui.label("Nothing is added to your plan unless you accept it.", "muted-small"));
        for (MistakeService.ReviewGroup g : groups) {
            Label title = Ui.label(g.skill().label() + ": " + g.questionType(), "row-title-small");
            String reasons = g.mistakes().stream().map(m -> m.reason().isBlank() ? "(no reason)" : m.reason()).limit(2)
                    .reduce((a, b) -> a + "; " + b).orElse("");
            Label detail = Ui.label(g.mistakes().size() + (g.mistakes().size() == 1 ? " mistake" : " mistakes") + " · " + reasons, "muted-small");
            detail.setWrapText(true);
            Button accept = Ui.primary("Add review task");
            accept.setOnAction(e -> Ui.guarded(() -> {
                ctx.mistakes.accept(g);
                nav.toast("Review task added to your plan.");
                nav.open(reopen);
            }));
            Button ignore = Ui.secondary("Ignore");
            ignore.setOnAction(e -> Ui.guarded(() -> { ctx.mistakes.ignore(g); nav.open(reopen); }));
            VBox text = new VBox(2, title, detail);
            HBox.setHgrow(text, Priority.ALWAYS);
            text.setMinWidth(0);
            HBox row = new HBox(10, text, accept, ignore);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("row");
            card.getChildren().add(row);
        }
        return card;
    }
}
