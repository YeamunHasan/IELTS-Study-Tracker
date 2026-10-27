package com.ielts.tracker.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Minimal RFC 4180 CSV writer. */
public final class Csv {
    private Csv() {}

    public static String escape(String value) {
        if (value == null) return "";
        boolean quote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String v = value.replace("\"", "\"\"");
        return quote ? "\"" + v + "\"" : v;
    }

    public static void write(Path file, List<String> header, List<List<String>> rows) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write('﻿'); // BOM so Excel on Windows reads UTF-8 correctly
            writeRow(w, header);
            for (List<String> row : rows) writeRow(w, row);
        }
    }

    private static void writeRow(Writer w, List<String> row) throws IOException {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) w.write(',');
            w.write(escape(row.get(i)));
        }
        w.write("\r\n");
    }
}
