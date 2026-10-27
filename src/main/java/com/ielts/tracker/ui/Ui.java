package com.ielts.tracker.ui;

import com.ielts.tracker.model.Skill;
import com.ielts.tracker.util.ErrorLog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Optional;

/** Small factory methods so pages stay short and look consistent. */
public final class Ui {
    private static Window owner;
    private static String stylesheet;

    private Ui() {}

    public static void init(Window window, String css) {
        owner = window;
        stylesheet = css;
    }

    public static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    public static Label wrapped(String text, String... styleClasses) {
        Label l = label(text, styleClasses);
        l.setWrapText(true);
        return l;
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("card");
        return box;
    }

    public static VBox section(String title, Node... children) {
        VBox box = card();
        box.getChildren().add(label(title, "card-title"));
        box.getChildren().addAll(children);
        return box;
    }

    public static Button button(String text, String style) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    public static Button primary(String text) { return button(text, "btn-primary"); }
    public static Button secondary(String text) { return button(text, "btn-secondary"); }
    public static Button danger(String text) { return button(text, "btn-danger"); }

    public static void tip(Node node, String text) {
        Tooltip t = new Tooltip(text);
        t.setShowDelay(Duration.millis(300));
        Tooltip.install(node, t);
    }

    public static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Label skillChip(Skill skill) {
        Label l = label(skill.label(), "chip");
        l.setMinWidth(Region.USE_PREF_SIZE);
        l.setStyle("-fx-background-color: " + rgba(skill.color(), 0.16) + "; -fx-text-fill: " + skill.color() + ";");
        return l;
    }

    public static String rgba(String hex, double alpha) {
        Color c = Color.web(hex);
        return String.format("rgba(%d,%d,%d,%.2f)", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255), alpha);
    }

    /** A labelled value tile used for summary figures. */
    public static VBox stat(String caption, Label value) {
        value.getStyleClass().add("stat-value");
        VBox box = new VBox(2, value, label(caption, "stat-caption"));
        box.getStyleClass().add("stat");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Two-column label/field grid: labels keep their full width, fields take the rest. */
    public static javafx.scene.layout.GridPane form() {
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(14);
        g.setVgap(10);
        javafx.scene.layout.ColumnConstraints labels = new javafx.scene.layout.ColumnConstraints();
        labels.setMinWidth(Region.USE_PREF_SIZE);
        javafx.scene.layout.ColumnConstraints fields = new javafx.scene.layout.ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(labels, fields);
        return g;
    }

    /** Band picker in half-band steps. The first item is blank when {@code allowBlank}. Read it with {@link #bandValue}. */
    public static javafx.scene.control.ComboBox<String> bandBox(Double initial, boolean allowBlank) {
        javafx.scene.control.ComboBox<String> box = new javafx.scene.control.ComboBox<>();
        if (allowBlank) box.getItems().add("");
        for (int i = 0; i <= 18; i++) box.getItems().add(String.format("%.1f", i / 2.0));
        box.setValue(initial == null ? (allowBlank ? "" : "6.0") : String.format("%.1f", initial));
        return box;
    }

    public static Double bandValue(javafx.scene.control.ComboBox<String> box) {
        String v = box.getValue();
        return v == null || v.isBlank() ? null : Double.valueOf(v);
    }

    public static VBox emptyState(String title, String message) {
        VBox box = new VBox(6, label(title, "empty-title"), wrapped(message, "muted"));
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(28));
        box.getStyleClass().add("empty");
        return box;
    }

    public static VBox pageBox(String title, String subtitle) {
        VBox box = new VBox(18);
        box.getStyleClass().add("page");
        box.getChildren().add(new VBox(2, label(title, "page-title"), label(subtitle, "muted")));
        return box;
    }

    // ---- dialogs ----

    public static void info(String message) {
        Alert a = alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.showAndWait();
    }

    public static void error(String message) {
        Alert a = alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText("Something went wrong");
        a.showAndWait();
    }

    public static boolean confirm(String header, String message, String confirmText) {
        ButtonType yes = new ButtonType(confirmText, javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        Alert a = alert(Alert.AlertType.CONFIRMATION, message, yes, ButtonType.CANCEL);
        a.setHeaderText(header);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == yes;
    }

    /** Runs an action; any failure is logged and shown to the user instead of being swallowed. */
    public static void guarded(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException e) {
            error(e.getMessage());
        } catch (RuntimeException e) {
            ErrorLog.log("Action failed", e);
            error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static Alert alert(Alert.AlertType type, String message, ButtonType... buttons) {
        Alert a = new Alert(type, message, buttons);
        if (owner != null) a.initOwner(owner);
        a.setTitle("IELTS Study Tracker");
        a.setHeaderText(null);
        if (stylesheet != null) a.getDialogPane().getStylesheets().add(stylesheet);
        a.getDialogPane().getStyleClass().add("dialog");
        return a;
    }

    public static void styleDialog(javafx.scene.control.Dialog<?> d) {
        if (owner != null) d.initOwner(owner);
        if (stylesheet != null) d.getDialogPane().getStylesheets().add(stylesheet);
        d.getDialogPane().getStyleClass().add("dialog");
    }
}
