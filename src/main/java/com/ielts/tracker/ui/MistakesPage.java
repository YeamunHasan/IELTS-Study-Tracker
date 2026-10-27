package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.Mistake;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.repository.MistakeRepository;
import com.ielts.tracker.util.Fmt;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/** The error log: which question types cost the most marks, and the list of logged mistakes. */
public final class MistakesPage implements Page {
    private final AppContext ctx;
    private final Navigator nav;
    private final VBox root;
    private final VBox costBox = new VBox(10);
    private final ComboBox<String> period = new ComboBox<>();

    public MistakesPage(AppContext ctx, Navigator nav) {
        this.ctx = ctx;
        this.nav = nav;
        root = Ui.pageBox("Mistake Log", "Log what went wrong, by question type. The pattern shows where your marks go.");

        Button log = Ui.primary("Log a mistake");
        log.setOnAction(e -> { if (MistakeDialog.logLoop(ctx, null, "", null, nav) > 0) nav.open(PageId.MISTAKES); });
        HBox head = new HBox(log);
        head.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(head);

        var due = ctx.mistakes.dueReviews();
        if (!due.isEmpty()) root.getChildren().add(ReviewCard.build(ctx, nav, due, PageId.MISTAKES));

        period.getItems().addAll("Last 7 days", "Last 30 days", "All time");
        period.setValue("Last 30 days");
        period.valueProperty().addListener((o, a, b) -> renderCost());
        VBox costCard = Ui.card(new HBox(12, Ui.label("What costs you the most marks", "card-title"), Ui.grow(), period), costBox);
        root.getChildren().add(costCard);
        renderCost();
        root.getChildren().add(listCard());
    }

    @Override public Node root() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    private void renderCost() {
        LocalDate today = ctx.stats.today();
        LocalDate from = switch (period.getValue()) {
            case "Last 7 days" -> today.minusDays(6);
            case "Last 30 days" -> today.minusDays(29);
            default -> LocalDate.of(2000, 1, 1);
        };
        List<MistakeRepository.TypeCost> cost = ctx.mistakes.cost(from, today);
        costBox.getChildren().clear();
        if (cost.isEmpty()) {
            costBox.getChildren().add(Ui.emptyState("No mistakes logged in this period", "After a mock or practice, log what you got wrong. A few weeks of entries will show your real pattern."));
            return;
        }
        int max = cost.get(0).marksLost();
        for (MistakeRepository.TypeCost c : cost.stream().limit(10).toList()) {
            ProgressBar bar = new ProgressBar((double) c.marksLost() / max);
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setStyle("-fx-accent: " + c.skill().color() + ";");
            bar.getStyleClass().add("skill-bar");
            HBox line = new HBox(8, Ui.label(c.skill().label() + " · " + c.questionType(), "row-title-small"), Ui.grow(),
                    Ui.label(c.marksLost() + (c.marksLost() == 1 ? " mark" : " marks") + " · " + c.count() + (c.count() == 1 ? " mistake" : " mistakes"), "muted"));
            costBox.getChildren().add(new VBox(4, line, bar));
        }
        costBox.getChildren().add(Ui.label("Marks lost, summed over the mistakes you logged. This shows what you logged, not every mark you lost.", "muted-small"));
    }

    private Node listCard() {
        TableView<Mistake> table = new TableView<>();
        TableSupport.column(table, "Date", 100, m -> Fmt.shortDate(m.loggedAt(), ctx.zone));
        TableSupport.column(table, "Skill", 90, m -> m.skill().label());
        TableSupport.column(table, "Question type", 200, Mistake::questionType);
        TableSupport.column(table, "Marks", 60, m -> Integer.toString(m.marksLost()));
        TableSupport.column(table, "Reason", 100, Mistake::reason);
        TableSupport.column(table, "Review", 110, m -> switch (m.reviewStatus()) {
            case NONE -> "";
            case SUGGESTED -> "Due " + Fmt.date(m.reviewDue());
            case ACCEPTED -> "Added";
            case IGNORED -> "Ignored";
        });
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        List<Mistake> all = ctx.mistakes.list(null);
        table.setItems(FXCollections.observableArrayList(all));
        table.setPlaceholder(Ui.emptyState("Nothing logged yet", "Use Log a mistake after a practice test."));
        table.setPrefHeight(Math.min(420, 70 + Math.max(1, all.size()) * 34));
        table.setFixedCellSize(34);
        Button delete = Ui.danger("Delete selected");
        delete.setOnAction(e -> {
            Mistake m = table.getSelectionModel().getSelectedItem();
            if (m == null) { nav.toast("Select a mistake first."); return; }
            if (Ui.confirm("Delete this entry?", m.questionType() + ": " + m.reason(), "Delete")) {
                Ui.guarded(() -> ctx.mistakes.delete(m.id()));
                nav.open(PageId.MISTAKES);
            }
        });
        return Ui.card(Ui.label("Logged mistakes", "card-title"), table, new HBox(delete));
    }
}
