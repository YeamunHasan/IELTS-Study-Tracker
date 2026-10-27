package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;

import java.time.LocalDate;

/** Task operations shared by the dashboard, planner and tasks page. Each one confirms or reports errors itself. */
final class TaskActions {
    private TaskActions() {}

    /** Opens the create dialog. Returns true if a task was added. */
    static boolean create(AppContext ctx, StudyTask preset) {
        var result = TaskDialog.show(null, preset, ctx.settings.defaultSessionMinutes());
        if (result.isEmpty()) return false;
        Ui.guarded(() -> ctx.tasks.create(result.get()));
        return true;
    }

    static boolean edit(AppContext ctx, long id) {
        var task = ctx.tasks.find(id);
        if (task.isEmpty()) return false;
        var result = TaskDialog.show(task.get(), null, ctx.settings.defaultSessionMinutes());
        if (result.isEmpty()) return false;
        Ui.guarded(() -> ctx.tasks.update(result.get()));
        return true;
    }

    static void duplicate(AppContext ctx, long id) { Ui.guarded(() -> ctx.tasks.duplicate(id)); }

    static void setStatus(AppContext ctx, long id, TaskStatus status) { Ui.guarded(() -> ctx.tasks.setStatus(id, status)); }

    static void moveTo(AppContext ctx, long id, LocalDate date) { Ui.guarded(() -> ctx.tasks.reschedule(id, date)); }

    /** Returns true if the task was removed or archived. */
    static boolean delete(AppContext ctx, StudyTask task, boolean hasSessions) {
        String message = hasSessions
                ? "“" + task.title() + "” has saved study sessions. It will be archived: hidden from your task lists, "
                + "while its recorded time stays in your history and reports."
                : "Delete “" + task.title() + "”? This cannot be undone.";
        if (!Ui.confirm(hasSessions ? "Archive task?" : "Delete task?", message, hasSessions ? "Archive" : "Delete")) return false;
        Ui.guarded(() -> ctx.tasks.delete(task.id()));
        return true;
    }
}
