package com.ielts.tracker.ui;

import javafx.scene.layout.Pane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;

/** Circular progress indicator for the focus timer. */
public final class ProgressRing extends Pane {
    private final Arc arc;

    public ProgressRing(double radius, double stroke) {
        double size = (radius + stroke) * 2;
        setPrefSize(size, size);
        setMinSize(size, size);
        setMaxSize(size, size);

        Circle track = new Circle(size / 2, size / 2, radius);
        track.getStyleClass().add("ring-track");
        track.setStrokeWidth(stroke);

        arc = new Arc(size / 2, size / 2, radius, radius, 90, 0);
        arc.setType(ArcType.OPEN);
        arc.getStyleClass().add("ring-arc");
        arc.setStrokeWidth(stroke);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);

        getChildren().addAll(track, arc);
    }

    /** 0..1, drawn clockwise from the top. */
    public void setFraction(double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        arc.setLength(-360 * f);
        arc.setVisible(f > 0.002);
    }

    public void setOverrun(boolean over) {
        arc.getStyleClass().remove("ring-over");
        if (over) arc.getStyleClass().add("ring-over");
    }
}
