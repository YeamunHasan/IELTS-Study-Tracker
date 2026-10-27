#!/usr/bin/env bash
# Starts IELTS Study Tracker from source. Needs JDK 21+ and Maven.
cd "$(dirname "$0")" && exec mvn -q -B javafx:run
