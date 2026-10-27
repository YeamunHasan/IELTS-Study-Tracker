package com.ielts.tracker.app;

import javafx.application.Platform;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;

/** System tray icon with a menu to show the window, start or pause the timer, and quit. The tooltip shows the running time. */
final class TrayController {
    private final TrayIcon icon;

    private TrayController(TrayIcon icon) { this.icon = icon; }

    /** Returns null where the system has no tray (or it cannot be used). */
    static TrayController create(Runnable show, Runnable toggle, Runnable quit) {
        try {
            if (!SystemTray.isSupported()) return null;
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open IELTS Study Tracker");
            open.addActionListener(e -> Platform.runLater(show));
            MenuItem startPause = new MenuItem("Start / pause timer");
            startPause.addActionListener(e -> Platform.runLater(toggle));
            MenuItem exit = new MenuItem("Quit");
            exit.addActionListener(e -> Platform.runLater(quit));
            menu.add(open);
            menu.add(startPause);
            menu.addSeparator();
            menu.add(exit);

            TrayIcon icon = new TrayIcon(AppIcon.awt(64), "IELTS Study Tracker", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> Platform.runLater(show)); // double click
            SystemTray.getSystemTray().add(icon);
            return new TrayController(icon);
        } catch (AWTException | RuntimeException | UnsatisfiedLinkError e) {
            return null;
        }
    }

    void setTooltip(String text) { icon.setToolTip(text); }

    void remove() {
        try {
            SystemTray.getSystemTray().remove(icon);
        } catch (RuntimeException ignored) {
            // Already gone.
        }
    }
}
