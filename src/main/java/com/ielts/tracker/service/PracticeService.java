package com.ielts.tracker.service;

import com.ielts.tracker.model.PracticeResult;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.repository.PracticeRepository;
import com.ielts.tracker.timer.TimeSource;
import com.ielts.tracker.util.Bands;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalDouble;

public final class PracticeService {
    private final PracticeRepository repo;
    private final TimeSource time;
    private final ZoneId zone;

    public PracticeService(PracticeRepository repo, TimeSource time, ZoneId zone) {
        this.repo = repo;
        this.time = time;
        this.zone = zone;
    }

    public long record(PracticeResult r) {
        if (r.skill() == null || !r.skill().hasBand()) throw new IllegalArgumentException("Choose Listening, Reading, Writing or Speaking.");
        if (r.testDate() == null) throw new IllegalArgumentException("Choose the test date.");
        if (r.testDate().isAfter(time.now().atZone(zone).toLocalDate())) throw new IllegalArgumentException("The test date is in the future.");
        if (!Bands.isValidBand(r.band())) throw new IllegalArgumentException("The band must be between 0 and 9 in steps of 0.5.");
        if (r.rawScore() != null) {
            if (r.skill() != Skill.LISTENING && r.skill() != Skill.READING) throw new IllegalArgumentException("Raw scores are only used for Listening and Reading.");
            if (r.rawScore() < 0 || r.rawScore() > 40) throw new IllegalArgumentException("A raw score must be between 0 and 40.");
        }
        return repo.insert(r, time.now());
    }

    public void delete(long id) { repo.delete(id); }

    public List<PracticeResult> history(Skill skill) { return repo.list(skill); }

    /** Estimated overall band from the latest result of each skill, or empty until all four exist. An estimate, not an official result. */
    public OptionalDouble overallEstimate() {
        double[] b = new double[4];
        int i = 0;
        for (Skill s : Skill.BAND_SKILLS) {
            var latest = repo.latest(s);
            if (latest.isEmpty()) return OptionalDouble.empty();
            b[i++] = latest.get().band();
        }
        return OptionalDouble.of(Bands.overall(b[0], b[1], b[2], b[3]));
    }
}
