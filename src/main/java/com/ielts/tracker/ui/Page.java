package com.ielts.tracker.ui;

import javafx.scene.Node;

/** A screen. Pages are rebuilt each time they are opened, so they always show the stored data. */
public interface Page {
    Node root();

    /** Called once a second while the page is visible. */
    default void tick() {}
}
