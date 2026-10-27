@echo off
rem Builds (first run only) and starts IELTS Study Tracker from source. Needs JDK 21+ and Maven on PATH.
cd /d "%~dp0"
call mvn -q -B javafx:run
