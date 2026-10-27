package com.ielts.tracker.service;

import com.ielts.tracker.model.PracticeResult;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.repository.PracticeRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Compares your latest practice band with your target for each skill. This drives the dashboard headline and the nudges. */
public final class GoalService {

    /** {@code gap} is target minus latest band (positive means below target); null when there is no result yet. */
    public record SkillGap(Skill skill, Double latestBand, double target, Double gap, long minutesThisWeek) {
        public boolean hasResult() { return latestBand != null; }
        public boolean belowTarget() { return gap != null && gap > 0; }
    }

    private final PracticeRepository practice;
    private final AppSettings settings;
    private final StatsService stats;

    public GoalService(PracticeRepository practice, AppSettings settings, StatsService stats) {
        this.practice = practice;
        this.settings = settings;
        this.stats = stats;
    }

    public static LocalDate weekStart(LocalDate d) { return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

    /** The four skills ordered by gap, largest first. Skills without a result come after those with a gap, so they never outrank a measured one. */
    public List<SkillGap> gaps() {
        LocalDate today = stats.today();
        Map<Skill, Long> week = stats.skillTotals(weekStart(today), today);
        List<SkillGap> out = new ArrayList<>();
        for (Skill s : Skill.BAND_SKILLS) {
            double target = settings.targetBand(s);
            Optional<PracticeResult> latest = practice.latest(s);
            Double band = latest.map(PracticeResult::band).orElse(null);
            Double gap = band == null ? null : target - band;
            out.add(new SkillGap(s, band, target, gap, week.getOrDefault(s, 0L) / 60_000));
        }
        out.sort(Comparator.comparingDouble((SkillGap g) -> g.gap() == null ? Double.NEGATIVE_INFINITY : g.gap()).reversed());
        return out;
    }

    /** The skill furthest below its target, if any skill with a result is below target. */
    public Optional<SkillGap> furthest() {
        return gaps().stream().filter(SkillGap::belowTarget).findFirst();
    }
}
