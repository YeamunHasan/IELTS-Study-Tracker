package com.ielts.tracker.model;

import java.util.List;
import java.util.Map;

/** Fixed lists of mistake types per skill, so patterns can be counted. */
public final class QuestionTypes {
    private QuestionTypes() {}

    public static final Map<Skill, List<String>> BY_SKILL = Map.of(
            Skill.READING, List.of("True/False/Not Given", "Yes/No/Not Given", "Matching headings", "Matching information",
                    "Multiple choice", "Sentence completion", "Summary completion", "Short answer", "Ran out of time", "Other"),
            Skill.LISTENING, List.of("Spelling", "Singular/plural or grammar of answer", "Distractor or changed answer",
                    "Multiple choice", "Map or diagram labelling", "Form or note completion", "Lost my place", "Other"),
            Skill.WRITING, List.of("Task 1: overview missing", "Task 1: data accuracy", "Task 2: task response", "Coherence and cohesion",
                    "Lexical resource", "Grammatical range and accuracy", "Under word count", "Ran out of time", "Other"),
            Skill.SPEAKING, List.of("Fluency and hesitation", "Part 2: structure", "Part 3: developing ideas", "Vocabulary range",
                    "Grammar accuracy", "Pronunciation", "Other"));

    public static List<String> forSkill(Skill s) { return BY_SKILL.getOrDefault(s, List.of("Other")); }
}
