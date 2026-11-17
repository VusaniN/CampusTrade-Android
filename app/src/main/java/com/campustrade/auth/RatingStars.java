package com.campustrade.auth;

import java.util.Locale;

/** Renders a 0-5 rating as filled and empty stars. */
final class RatingStars {
    private RatingStars() { }

    static String of(double rating) {
        int filled = (int) Math.round(Math.max(0, Math.min(5, rating)));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 5; i++) out.append(i < filled ? "★" : "☆");
        return out.toString();
    }

    static String label(String prefix, double rating) {
        return prefix + " " + of(rating);
    }

    static String percent(double rating) {
        return String.format(Locale.ROOT, "%.0f%%", Math.max(0, Math.min(5, rating)) / 5.0 * 100);
    }
}