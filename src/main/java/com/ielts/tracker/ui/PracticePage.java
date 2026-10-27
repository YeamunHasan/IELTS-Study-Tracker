package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.PracticeResult;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.util.Fmt;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Band history per skill with the target as a dashed line, and next to it, as a separate chart, minutes studied per week.
 * The two are shown together for you to compare; the app makes no claim about one causing the other.
 */
public final class PracticePage implements Page {
    private final AppContext ctx;
    private final Navigator nav;
    private final VBox root;

    public PracticePage(AppContext ctx, Navigator nav) {
        this.ctx = ctx;
        this.nav = nav;
        root = Ui.pageBox("Practice Scores", "One record per mock or section test. Bands are entered by you; this app does not convert raw scores.");

        Button add = Ui.primary("Add result");
        add.setOnAction(e -> { if (ScoreDialog.show(ctx, null, null, null, false).isPresent()) nav.open(PageId.PRACTICE); });
        var overall = ctx.practice.overallEstimate();
        var overallLabel = Ui.label(overall.isPresent()
                ? "Estimated overall from your latest four results: " + String.format("%.1f", overall.getAsDouble()) + "  (a study estimate, not an official result)"
                : "An overall estimate appears once you have a result for each of the four skills.", "muted");
        HBox head = new HBox(12, overallLabel, Ui.grow(), add);
        head.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().add(head);

        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(18);
        int i = 0;
        for (Skill s : Skill.BAND_SKILLS) {
            VBox card = skillCard(s);
            GridPane.setHgrow(card, Priority.ALWAYS);
            grid.add(card, i % 2, i / 2);
            card.setMaxWidth(Double.MAX_VALUE);
            i++;
        }
        root.getChildren().add(grid);
        root.getChildren().add(table());
    }

    @Override public Node root() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    private VBox skillCard(Skill skill) {
        List<PracticeResult> results = ctx.practice.history(skill);
        double target = ctx.settings.targetBand(skill);
        String line = results.isEmpty() ? "No results yet · target " + String.format("%.1f", target)
                : "Latest " + String.format("%.1f", results.get(results.size() - 1).band()) + " · target " + String.format("%.1f", target);
        VBox card = Ui.card(new HBox(10, Ui.skillChip(skill), Ui.label(line, "muted")));
        if (results.isEmpty()) {
            card.getChildren().add(Ui.emptyState("No " + skill.label() + " results", "Add a result to see the trend against your target."));
        } else {
            card.getChildren().add(bandChart(results, target));
        }
        card.getChildren().add(Ui.label("Minutes studied per week (separate from scores)", "section-label"));
        card.getChildren().add(minutesChart(skill));
        return card;
    }

    private Node bandChart(List<PracticeResult> results, double target) {
        int n = results.size();
        NumberAxis x = new NumberAxis(0.5, n + 0.5, Math.max(1, Math.ceil(n / 6.0)));
        x.setMinorTickVisible(false);
        x.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number v) {
                int idx = (int) Math.round(v.doubleValue());
                if (Math.abs(v.doubleValue() - idx) > 1e-6 || idx < 1 || idx > n) return "";
                return results.get(idx - 1).testDate().format(DateTimeFormatter.ofPattern("d MMM"));
            }
            @Override public Number fromString(String s) { return 0; }
        });
        double min = results.stream().mapToDouble(PracticeResult::band).min().orElse(target);
        NumberAxis y = new NumberAxis(Math.max(0, Math.min(min, target) - 1), Math.min(9, Math.max(results.stream().mapToDouble(PracticeResult::band).max().orElse(target), target) + 1), 0.5);
        y.setMinorTickVisible(false);
        y.setLabel("Band");
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(210);
        XYChart.Series<Number, Number> scores = new XYChart.Series<>();
        for (int i = 0; i < n; i++) scores.getData().add(new XYChart.Data<>(i + 1, results.get(i).band()));
        XYChart.Series<Number, Number> goal = new XYChart.Series<>();
        goal.getData().add(new XYChart.Data<>(0.5, target));
        goal.getData().add(new XYChart.Data<>(n + 0.5, target));
        chart.getData().addAll(scores, goal);
        return chart;
    }

    private Node minutesChart(Skill skill) {
        Map<LocalDate, Long> weeks = ctx.stats.skillWeeklyMillis(skill, 6);
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(130);
        chart.setCategoryGap(10);
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        weeks.forEach((d, ms) -> s.getData().add(new XYChart.Data<>(d.format(DateTimeFormatter.ofPattern("d MMM")), ms / 60000.0)));
        chart.getData().add(s);
        return chart;
    }

    private Node table() {
        TableView<PracticeResult> table = new TableView<>();
        TableSupport.column(table, "Date", 100, r -> Fmt.date(r.testDate()));
        TableSupport.column(table, "Skill", 100, r -> r.skill().label());
        TableSupport.column(table, "Source", 220, PracticeResult::source);
        TableSupport.column(table, "Raw", 60, r -> r.rawScore() == null ? "" : r.rawScore() + "/40");
        TableSupport.column(table, "Band", 60, r -> String.format("%.1f", r.band()));
        TableSupport.column(table, "Time taken", 90, r -> r.secondsTaken() == null ? "" : Fmt.compact(r.secondsTaken() * 1000));
        TableSupport.column(table, "Note", 100, r -> r.note() == null ? "" : r.note().replace('\n', ' '));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        List<PracticeResult> all = ctx.practice.history(null).reversed();
        table.setItems(FXCollections.observableArrayList(all));
        table.setPlaceholder(Ui.emptyState("No results yet", "Finish an exam-timing session or use Add result."));
        table.setPrefHeight(Math.min(340, 70 + all.size() * 34));
        table.setFixedCellSize(34);
        Button delete = Ui.danger("Delete selected result");
        delete.setOnAction(e -> {
            PracticeResult r = table.getSelectionModel().getSelectedItem();
            if (r == null) { nav.toast("Select a result first."); return; }
            if (Ui.confirm("Delete this result?", r.skill().label() + " band " + String.format("%.1f", r.band()) + " on " + Fmt.date(r.testDate())
                    + ". Mistakes logged from it are kept.", "Delete result")) {
                Ui.guarded(() -> ctx.practice.delete(r.id()));
                nav.open(PageId.PRACTICE);
            }
        });
        return Ui.card(Ui.label("All results", "card-title"), table, new HBox(delete));
    }
}
