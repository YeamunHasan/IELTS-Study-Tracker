# IELTS Study Tracker 0.3.0

**Just want to use it on Windows?** Download `IELTS-Study-Tracker-Windows-0.3.0.zip` from the [latest release](../../releases/latest) and follow the steps there. Java is included, so there is nothing else to install.

An offline desktop app for IELTS preparation, written in Java 21 with JavaFX and a local SQLite file. It never uses the internet. It is built around one idea: time spent is only useful next to how close each skill is to its target band.

## The loop it supports

1. Set a target band for Listening, Reading, Writing and Speaking (Settings; 7.0 until you change it).
2. Record practice results (Practice Scores, or the prompt at the end of an exam-timing session).
3. The dashboard headline shows the skill furthest below target, for example "Writing is furthest from your target: 6.0 now, target 7.0 (gap 1.0). 22 min this week", with a button that starts a task for that skill. The task picker on the timer also puts the weakest skill first.
4. Log mistakes by question type. After a few weeks the Mistake Log shows which types cost you the most marks.
5. A mistake can suggest a review a few days later. It stays a suggestion; a task is only created if you press "Add review task".
6. On Sunday the Weekly Review shows hours against plan, best and worst day, weakest question types, and one box for what to change next week.

Study time and scores are shown next to each other (Practice Scores has a minutes-per-week chart beside each band chart) and the app makes no claim that one caused the other.

## Pages

* Dashboard: target band gaps, exam countdown, today's totals by skill and by task, resume-last-task, due review suggestions, today's schedule, recent sessions.
* Focus Timer: countdown or stopwatch, pause, resume, finish, cancel, notes, and exam timings (below).
* Tasks: create, edit, duplicate, complete, skip, archive or delete. Skills are Listening, Reading, Writing, Speaking, Vocabulary and Mock Test.
* Study History: filters, details, notes, delete, CSV export, and "Add session done elsewhere" for study away from the laptop. Those entries are stored as Manual and always shown as Manual.
* Practice Scores: one record per mock or section test (date, source, raw score for Listening and Reading, band, note, time taken). Band line chart per skill with the target as a dashed line. An overall estimate appears once all four skills have a result; it uses the official half-band rounding and is labelled as an estimate.
* Mistake Log, Writing & Speaking, Weekly Review, Settings.

### Exam timings

In the Focus Timer choose an exam timing: Listening (30 min plus 10 min transfer), Reading 60, Writing 60, Speaking Part 1, Part 2 (1 min preparation plus 2 min talk), Part 3, or the full Speaking test. It runs as an ordinary countdown, so pausing, crash recovery and totals work the same way. The screen shows the current part and the time left in it, and warns when the next part starts. Reading, Listening and Writing have buttons to mark Passage 1, Section 2 and so on, so the time per passage is saved and shown in the session details. When you finish, a form asks for the raw score and band, and the time, raw score and band are saved as one record linked to the session. If you choose an exam timing without picking a task, the app makes a finished "Mock" task to hold the time.

### Writing and Speaking

Essays are stored with their text, word count, time taken, and your own rating on the four official criteria (Task Achievement or Response, Coherence and Cohesion, Lexical Resource, Grammatical Range and Accuracy). Speaking recordings are stored as a file path or link plus a note; the audio is never copied. All ratings are your own estimates.

## Speed

* Global shortcut Ctrl+Alt+Space: pause a running session, resume a paused one, or start the last task. It works from any window.
* Mini timer: a small always-on-top window with the time and a Pause/Resume button. It appears by itself if you minimise the app during a session, or from the "Mini timer" button.
* Tray icon: open the window, start or pause, quit. The tooltip shows the running time.
* Still studying?: after 10 minutes (changeable) with no keyboard or mouse input during a normal session, a small prompt asks. "Yes" changes nothing. "No, I stopped about N min ago" pauses and removes that idle stretch from the session, up to what was actually running. Closing the prompt changes nothing. It is not shown during exam timings, because paper tests would trigger it constantly.

The shortcut and the idle check use a system-wide input hook (JNativeHook). The app only notes that some input happened; it never records which keys. If the hook cannot start, everything else still works and the app says so.

## Accuracy and recovery

Elapsed time comes from the monotonic clock, never from counting screen refreshes. Paused time is never counted. A session is saved in the database when it starts and checkpointed every 5 seconds. After a crash, power loss or forced close, the next start asks what to do: continue the session (it comes back paused), save the recorded time as Interrupted, or discard it after a confirmation. Only the time that was recorded is trusted; the time the computer was off is never added.

Totals are always computed from saved sessions, so task, day, week and skill totals agree. Completed, Interrupted and Manual sessions count; Cancelled ones do not. A session belongs to the local date it started on. A streak day is a day with at least the minimum minutes set in Settings.

## Backup

In Settings choose a backup folder. Each time you close the app, it copies the database there as `ielts-tracker-YYYYMMDD-HHMMSS.db` and keeps the newest ten. Point the folder at something you already sync (a cloud drive folder) and you are protected while the app itself stays offline. There is no restore screen: close the app, copy a backup over `tracker.db` (rename it), and delete `tracker.db-wal` and `tracker.db-shm` if they exist.

## Where your data is

`tracker.db` in `.ielts-study-tracker` inside your user folder (for example `C:\Users\You\.ielts-study-tracker\`). Errors go to `error.log` in the same folder. Use `-Dielts.tracker.home=D:\path` to use another folder.

Upgrading from 0.2.0 keeps your data. Tasks that were in the removed categories (Grammar, General, Review) are moved to Vocabulary.

## Run it

Install JDK 21 or newer and Maven 3.9 or newer, then in this folder:

```
run.bat           Windows: start the app
mvn test          run the tests
```

On Linux and macOS use `./run.sh`.

## Code layout

```
app         entry point, wiring, tray, global input hook, backup on exit
model       records and enums (Skill, MockPreset, PracticeResult, Mistake, Essay, ...)
repository  SQLite access through JDBC prepared statements; numbered migrations
service     goals and gaps, practice, mistakes and reviews, weekly review, stats, backup
timer       TimeSource, FocusTimer (state machine), SessionManager (saving and recovery), IdleMonitor
ui          one class per page plus dialogs
```

The timer, services and repositories do not depend on JavaFX. All of them are tested with a controllable clock and a real SQLite file (`mvn test`, 97 tests): timer behaviour, pause exclusion, recovery after a simulated restart, duplicate prevention, backdated idle pause, the idle monitor, exam presets and laps, manual sessions, resume-last-task, band rounding, gaps and ranking, mistake and review lifecycle, weekly review, essays, recordings, backups keeping ten copies, and upgrading a version 1 database.

## What was verified, and what was not

Verified here by running the real application under a virtual display: every page, the dialogs, the mini timer window and its buttons, the shortcut's toggle path, the idle prompt and its buttons, resume-last-task, and the crash recovery path. Not verified: the global shortcut, the idle detection and the tray icon on Windows. They need the Windows input hook and tray, which could not be run in the environment where this was built. If one does not work for you, the rest of the app is unaffected; check `error.log`.

## Not built (deliberately)

Installer or jpackage, light theme, date and time format options, custom categories, calendar views, recurring tasks, monthly trends, a restore screen, and an automatic weekly plan with "falling behind" warnings. The weekly review does show a suggested split of your weekly goal that favours skills further below target, but it only suggests and never creates or moves tasks. Raw scores are not converted to bands; you enter the band. Session durations cannot be edited, only notes.
