package com.ielts.tracker.service;

import com.ielts.tracker.model.Skill;
import com.ielts.tracker.repository.MistakeRepository;
import com.ielts.tracker.repository.ReviewRepository;
import com.ielts.tracker.timer.TimeSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds the short Sunday review: hours against plan, best and worst day, weakest question types, and one written answer. */
public final class WeeklyReviewService {

    public record SkillLine(Skill skill, long actualMillis, long plannedMillis) {}

    public record Review(LocalDate weekStart, List<SkillLine> skills, long totalMillis, long goalMillis,
                         Map.Entry<LocalDate, Long> bestDay, Map.Entry<LocalDate, Long> worstDay,
                         List<MistakeRepository.TypeCost> weakestTypes, Optional<String> answer) {}

    private final StatsService stats;
    private final GoalService goals;
    private final AppSettings settings;
    private final MistakeService mistakes;
    private final ReviewRepository reviews;
    private final TimeSource time;

    public WeeklyReviewService(StatsService stats, GoalService goals, AppSettings settings, MistakeService mistakes,
                               ReviewRepository reviews, TimeSource time) {
        this.stats = stats;
        this.goals = goals;
        this.settings = settings;
        this.mistakes = mistakes;
        this.reviews = reviews;
        this.time = time;
    }

    public Review build(LocalDate weekStart) {
        LocalDate today = stats.today();
        LocalDate weekEnd = weekStart.plusDays(6);
        Map<Skill, Long> actual = stats.skillTotals(weekStart, weekEnd);
        Map<Skill, Integer> plan = PlanService.suggestedSplit(settings.weeklyGoalMinutes(), goals.gaps());
        List<SkillLine> lines = Skill.BAND_SKILLS.stream()
                .map(s -> new SkillLine(s, actual.getOrDefault(s, 0L), plan.get(s) * 60_000L)).toList();
        long total = stats.summary(weekStart, weekEnd).totalMillis();

        // Only days that have already started can be the "worst" day.
        Map<LocalDate, Long> days = stats.dailyTotals(weekStart, weekEnd);
        Map.Entry<LocalDate, Long> best = null, worst = null;
        for (Map.Entry<LocalDate, Long> e : days.entrySet()) {
            if (e.getKey().isAfter(today)) continue;
            if (best == null || e.getValue() > best.getValue()) best = e;
            if (worst == null || e.getValue() < worst.getValue()) worst = e;
        }
        var weakest = mistakes.cost(weekStart, weekEnd).stream().limit(3).toList();
        return new Review(weekStart, lines, total, settings.weeklyGoalMinutes() * 60_000L, best, worst, weakest, reviews.answer(weekStart));
    }

    public void saveAnswer(LocalDate weekStart, String answer) {
        if (answer == null || answer.isBlank()) throw new IllegalArgumentException("Write a sentence first.");
        reviews.save(weekStart, answer.trim(), time.now());
    }

    /** True on Sunday when this week's review has not been answered yet. */
    public boolean dueToday() {
        LocalDate today = stats.today();
        return today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY && reviews.answer(GoalService.weekStart(today)).isEmpty();
    }
}
