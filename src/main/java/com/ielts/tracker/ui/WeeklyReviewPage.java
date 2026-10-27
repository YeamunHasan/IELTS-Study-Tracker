package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.service.GoalService;
import com.ielts.tracker.service.WeeklyReviewService;
import com.ielts.tracker.util.Fmt;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/** A one-minute weekly review: hours against plan, best and worst day, weakest question types, and one sentence about next week. */
public final class WeeklyReviewPage implements Page {
    private final AppContext ctx;
    private final Navigator nav;
    private final LocalDate weekStart;
    private final VBox root;

    public WeeklyReviewPage(AppContext ctx, Navigator nav) {
        this(ctx, nav, GoalService.weekStart(ctx.stats.today()));
    }

    private WeeklyReviewPage(AppContext ctx, Navigator nav, LocalDate weekStart) {
        this.ctx = ctx;
        this.nav = nav;
        this.weekStart = weekStart;
        root = Ui.pageBox("Weekly Review", "Week of " + Fmt.date(weekStart) + " to " + Fmt.date(weekStart.plusDays(6)));
        build();
    }

    @Override public Node root() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    private void build() {
        WeeklyReviewService.Review r = ctx.weekly.build(weekStart);

        Button prev = Ui.secondary("‹ Previous week");
        Button next = Ui.secondary("Next week ›");
        Button thisWeek = Ui.secondary("This week");
        LocalDate current = GoalService.weekStart(ctx.stats.today());
        next.setDisable(!weekStart.isBefore(current));
        prev.setOnAction(e -> swap(weekStart.minusWeeks(1)));
        next.setOnAction(e -> swap(weekStart.plusWeeks(1)));
        thisWeek.setOnAction(e -> swap(current));
        HBox nav1 = new HBox(8, prev, thisWeek, next);
        nav1.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().add(nav1);

        // hours by skill against plan
        VBox hours = Ui.card(Ui.label("Hours by skill against plan", "card-title"));
        for (WeeklyReviewService.SkillLine l : r.skills()) {
            double frac = l.plannedMillis() == 0 ? 0 : Math.min(1, (double) l.actualMillis() / l.plannedMillis());
            ProgressBar bar = new ProgressBar(frac);
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setStyle("-fx-accent: " + l.skill().color() + ";");
            bar.getStyleClass().add("skill-bar");
            HBox line = new HBox(8, Ui.label(l.skill().label(), "row-title-small"), Ui.grow(),
                    Ui.label(Fmt.compact(l.actualMillis()) + " of " + Fmt.hoursMinutes(l.plannedMillis()), "muted"));
            hours.getChildren().add(new VBox(4, line, bar));
        }
        hours.getChildren().add(Ui.wrapped("Total " + Fmt.compact(r.totalMillis()) + " of your " + Fmt.hoursMinutes(r.goalMillis())
                + " weekly goal. The plan column is a suggested split of that goal, giving more time to skills further below target. "
                + "It only suggests; nothing in your task list was changed.", "muted-small"));
        root.getChildren().add(hours);

        // best / worst day
        VBox days = Ui.card(Ui.label("Best and worst day", "card-title"));
        if (r.bestDay() != null) {
            days.getChildren().add(Ui.label("Best: " + dayName(r.bestDay().getKey()) + ", " + Fmt.compact(r.bestDay().getValue()), "detail-value"));
            days.getChildren().add(Ui.label("Worst: " + dayName(r.worstDay().getKey()) + ", " + Fmt.compact(r.worstDay().getValue()), "detail-value"));
        } else {
            days.getChildren().add(Ui.label("No days in this week yet.", "muted"));
        }
        root.getChildren().add(days);

        // weakest question types
        VBox weak = Ui.card(Ui.label("Weakest question types this week", "card-title"));
        if (r.weakestTypes().isEmpty()) {
            weak.getChildren().add(Ui.label("No mistakes logged this week.", "muted"));
        } else {
            r.weakestTypes().forEach(c -> weak.getChildren().add(Ui.label(c.skill().label() + " · " + c.questionType() + ": "
                    + c.marksLost() + (c.marksLost() == 1 ? " mark" : " marks") + " over " + c.count() + (c.count() == 1 ? " mistake" : " mistakes"), "detail-value")));
        }
        root.getChildren().add(weak);

        // one sentence
        LocalDate lastWeek = weekStart.minusWeeks(1);
        String previous = ctx.weekly.build(lastWeek).answer().orElse(null);
        TextArea answer = new TextArea(r.answer().orElse(""));
        answer.setPromptText("In one sentence: what will I change next week?");
        answer.setPrefRowCount(2);
        answer.setWrapText(true);
        Button save = Ui.primary(r.answer().isPresent() ? "Update" : "Save");
        save.setOnAction(e -> Ui.guarded(() -> {
            ctx.weekly.saveAnswer(weekStart, answer.getText());
            nav.toast("Weekly review saved.");
        }));
        VBox box = Ui.card(Ui.label("What will you change next week?", "card-title"));
        if (previous != null) box.getChildren().add(Ui.wrapped("Last week you wrote: “" + previous + "”", "muted"));
        box.getChildren().addAll(answer, new HBox(save));
        root.getChildren().add(box);
    }

    private void swap(LocalDate week) {
        nav.openWeeklyFor(week);
    }

    private static String dayName(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + d.getDayOfMonth();
    }

    /** Used by the shell to open a specific week. */
    static Page forWeek(AppContext ctx, Navigator nav, LocalDate week) { return new WeeklyReviewPage(ctx, nav, week); }
}
