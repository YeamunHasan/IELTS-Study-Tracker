package com.ielts.tracker.app;

/**
 * Entry point for the packaged application. It deliberately does not extend javafx.application.Application:
 * starting JavaFX from a plain main class lets it run from the classpath (jar / jpackage) without module flags.
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        TrackerApp.main(args);
    }
}
