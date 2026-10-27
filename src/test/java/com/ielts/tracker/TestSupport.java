package com.ielts.tracker;

import com.ielts.tracker.model.Priority;
import com.ielts.tracker.model.Skill;
import com.ielts.tracker.model.StudyTask;
import com.ielts.tracker.model.TaskStatus;
import com.ielts.tracker.repository.Database;
import com.ielts.tracker.timer.TimeSource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public final class TestSupport {
    private TestSupport() {}

    /** A clock that only moves when the test says so. Wall-clock and monotonic time advance together. */
    public static final class FakeTime implements TimeSource {
        private long nanos = 1_000_000_000L;
        private Instant now;

        public FakeTime(Instant start) { this.now = start; }

        public void advance(Duration d) {
            nanos += d.toNanos();
            now = now.plus(d);
        }

        /** Moves wall-clock time only, as if the computer was off or the system clock was changed. */
        public void jumpWallClock(Duration d) { now = now.plus(d); }

        @Override public long nanoTime() { return nanos; }
        @Override public Instant now() { return now; }
    }

    public static Database newDb(Path dir) {
        Database db = new Database(dir.resolve("test.db"));
        db.migrate();
        return db;
    }

    public static StudyTask draft(String title, Skill skill, long targetMinutes) {
        return new StudyTask(0, title, "", skill, "", targetMinutes * 60, Priority.MEDIUM, TaskStatus.PLANNED,
                null, null, "", null, null, null);
    }
}
