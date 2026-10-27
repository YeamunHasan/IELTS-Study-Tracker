package com.ielts.tracker.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Appends unexpected errors to a log file next to the database so problems can be diagnosed later. */
public final class ErrorLog {
    private static Path file;

    private ErrorLog() {}

    public static void init(Path logFile) { file = logFile; }

    public static void log(String context, Throwable t) {
        System.err.println(context + ": " + t);
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            Files.writeString(file, Instant.now() + " " + context + "\n" + sw + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logging must never take the application down.
        }
    }
}
