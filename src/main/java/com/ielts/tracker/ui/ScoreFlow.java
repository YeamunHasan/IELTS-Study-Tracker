package com.ielts.tracker.ui;

import com.ielts.tracker.app.AppContext;
import com.ielts.tracker.model.MockPreset;
import com.ielts.tracker.model.StudySession;
import com.ielts.tracker.util.Fmt;

/** What happens when an exam-mode session ends: ask for the result right away, and save time, raw score and band as one record. */
final class ScoreFlow {
    private ScoreFlow() {}

    static void run(AppContext ctx, Navigator nav, StudySession saved) {
        MockPreset preset = MockPreset.byId(saved.preset());
        if (preset == null) return;
        long seconds = saved.activeMillis() / 1000;
        var entry = ScoreDialog.show(ctx, preset.skill(), seconds, saved.id(), true);
        if (entry.isEmpty()) {
            nav.toast("Session saved without a score. Add one later from Practice Scores.");
            return;
        }
        var result = entry.get().result();
        nav.toast("Saved " + preset.skill().label() + " band " + String.format("%.1f", result.band())
                + (result.rawScore() == null ? "" : " (" + result.rawScore() + "/40)") + " in " + Fmt.compact(saved.activeMillis()));
        if (entry.get().logMistakes()) {
            MistakeDialog.logLoop(ctx, preset.skill(), result.source(), result.id(), nav);
        }
        if (entry.get().addEssay()) {
            EssayDialog.show(ctx, null, Math.max(1, seconds / 60));
        }
    }
}
