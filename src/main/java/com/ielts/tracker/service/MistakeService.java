package com.ielts.tracker.service;

import com.ielts.tracker.model.Mistake;
import com.ielts.tracker.model.Priority;
import com.ielts.tracker.model.QuestionTypes;
import com.ielts.tracker.model.ReviewStatus;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.repository.MistakeRepository;
import com.ielts.tracker.timer.TimeSource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The error log. A logged mistake can suggest a review task a few days later; nothing is created until the user accepts. */
public final class MistakeService {
    public static final int DEFAULT_REVIEW_DAYS = 3;

    /** Suggested reviews that share a skill and question type, offered as one review task. */
    public record ReviewGroup(Skill skill, String questionType, List<Mistake> mistakes, LocalDate due) {
        public List<Long> ids() { return mistakes.stream().map(Mistake::id).toList(); }
    }

    private final MistakeRepository repo;
    private final TaskService tasks;
    private final TimeSource time;
    private final ZoneId zone;

    public MistakeService(MistakeRepository repo, TaskService tasks, TimeSource time, ZoneId zone) {
        this.repo = repo;
        this.tasks = tasks;
        this.time = time;
        this.zone = zone;
    }

    private LocalDate today() { return time.now().atZone(zone).toLocalDate(); }

    /** @param reviewInDays days until the suggested review; 0 or less means do not suggest one */
    public long log(Skill skill, String questionType, String reason, int marksLost, String source, Long practiceResultId, int reviewInDays) {
        if (skill == null || !skill.hasBand()) throw new IllegalArgumentException("Choose Listening, Reading, Writing or Speaking.");
        if (questionType == null || !QuestionTypes.forSkill(skill).contains(questionType)) throw new IllegalArgumentException("Choose a question type.");
        if (marksLost < 1 || marksLost > 40) throw new IllegalArgumentException("Marks lost must be between 1 and 40.");
        boolean review = reviewInDays > 0;
        return repo.insert(new Mistake(0, time.now(), skill, questionType, reason == null ? "" : reason, marksLost,
                source == null ? "" : source, practiceResultId, review ? today().plusDays(reviewInDays) : null,
                review ? ReviewStatus.SUGGESTED : ReviewStatus.NONE, null));
    }

    public List<Mistake> list(Skill skill) { return repo.list(skill); }

    public void delete(long id) { repo.delete(id); }

    public List<ReviewGroup> dueReviews() {
        Map<String, List<Mistake>> groups = new LinkedHashMap<>();
        for (Mistake m : repo.dueReviews(today())) groups.computeIfAbsent(m.skill() + "|" + m.questionType(), k -> new ArrayList<>()).add(m);
        List<ReviewGroup> out = new ArrayList<>();
        for (List<Mistake> l : groups.values()) {
            LocalDate due = l.stream().map(Mistake::reviewDue).min(LocalDate::compareTo).orElse(today());
            out.add(new ReviewGroup(l.get(0).skill(), l.get(0).questionType(), l, due));
        }
        return out;
    }

    /** Creates one planned review task for the group (15 to 30 minutes, scheduled for its due date or today). Returns its id. */
    public long accept(ReviewGroup g) {
        StringBuilder desc = new StringBuilder("Mistakes to review:");
        for (Mistake m : g.mistakes()) desc.append("\n- ").append(m.reason().isBlank() ? "(no reason written)" : m.reason());
        int minutes = Math.min(30, 10 + 5 * g.mistakes().size());
        LocalDate date = g.due().isBefore(today()) ? today() : g.due();
        long id = tasks.create(new StudyTask(0, "Review: " + g.questionType() + " (" + g.skill().label() + ")", desc.toString(), g.skill(),
                "Review", minutes * 60L, Priority.MEDIUM, TaskStatus.PLANNED, date, null, "", null, null, null));
        repo.resolveReview(g.ids(), ReviewStatus.ACCEPTED, id);
        return id;
    }

    public void ignore(ReviewGroup g) { repo.resolveReview(g.ids(), ReviewStatus.IGNORED, null); }

    /** Marks lost per question type for the dates (inclusive), most costly first. */
    public List<MistakeRepository.TypeCost> cost(LocalDate from, LocalDate to) {
        return repo.costByType(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
