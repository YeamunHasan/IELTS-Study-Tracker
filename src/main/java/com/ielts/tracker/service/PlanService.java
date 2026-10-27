package com.ielts.tracker.service;

import com.ielts.tracker.model.Skill;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Proposes a weekly split of study time across the four skills. It only suggests; it never creates or moves tasks. */
public final class PlanService {
    private PlanService() {}

    /**
     * Splits the weekly goal across the four skills in proportion to {@code 1 + gap}, so a skill 1.0 band below target
     * gets twice the share of one on target. Skills without a result count as on target. Rounded to 5 minutes.
     */
    public static Map<Skill, Integer> suggestedSplit(int weeklyGoalMinutes, List<GoalService.SkillGap> gaps) {
        Map<Skill, Double> weights = new EnumMap<>(Skill.class);
        double total = 0;
        for (GoalService.SkillGap g : gaps) {
            double w = 1 + (g.gap() == null ? 0 : Math.max(0, g.gap()));
            weights.put(g.skill(), w);
            total += w;
        }
        Map<Skill, Integer> out = new EnumMap<>(Skill.class);
        for (Skill s : Skill.BAND_SKILLS) {
            double share = total == 0 ? 0 : weights.getOrDefault(s, 1.0) / total;
            out.put(s, (int) (Math.round(weeklyGoalMinutes * share / 5.0) * 5));
        }
        return out;
    }
}
