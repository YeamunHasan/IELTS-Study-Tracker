package com.ielts.tracker.util;

/** IELTS band arithmetic. */
public final class Bands {
    private Bands() {}

    public static boolean isValidBand(double band) {
        return band >= 0 && band <= 9 && Math.abs(band * 2 - Math.rint(band * 2)) < 1e-9;
    }

    /**
     * Overall band from the four skill bands, using the official rule: the mean is rounded to the nearest half band,
     * with .25 rounding up to .5 and .75 rounding up to the next whole band. A study estimate, never an official result.
     */
    public static double overall(double listening, double reading, double writing, double speaking) {
        double mean = (listening + reading + writing + speaking) / 4;
        return Math.floor(mean * 2 + 0.5 + 1e-9) / 2;
    }

    public static String format(double band) { return String.format("%.1f", band); }
}
