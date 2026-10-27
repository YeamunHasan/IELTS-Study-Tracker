package com.ielts.tracker.ui;

/** What pages can ask of the application shell. */
public interface Navigator {
    void open(PageId page);

    /** Opens the Focus Timer with the given task selected, ready to start. */
    void startTimerFor(long taskId);

    /** Starts the same task as the most recent timed session straight away. */
    void resumeLastTask();

    /** Pause a running session, resume a paused one, or start the last task when idle. Also bound to the global shortcut. */
    void toggleTimer();

    /** Opens the Weekly Review for the week starting on the given Monday. */
    void openWeeklyFor(java.time.LocalDate weekStart);

    /** Pins or unpins the small always-on-top timer window. */
    void toggleMiniTimer();

    /** Shows a short non-blocking message. */
    void toast(String message);
}
