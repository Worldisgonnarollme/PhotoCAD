package com.example.photocad.report;

/** Pure, Android-independent rules shared by the UI and PDF layout. */
public final class ReportRules {
    private ReportRules() {}
    public static int pageCount(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative photo count");
        return count;
    }
    public static String description(String individual, String pointComment) {
        return individual != null ? individual : pointComment;
    }
    public static String caption(int number, String description) {
        if (number < 1) throw new IllegalArgumentException("Invalid photo number");
        return "Фотография №" + number;
    }
    public static String caption(int number, int pointNumber, int drawingPage, String description) {
        if (number < 1 || pointNumber < 1 || drawingPage < 1) throw new IllegalArgumentException("Invalid report numbering");
        String metadata = "Фотография №" + number + " • Точка №" + pointNumber + " • Страница " + drawingPage;
        return description == null || description.trim().isEmpty() ? metadata : metadata + " — " + description.trim();
    }
    public static float fitScale(int width, int height, float availableWidth, float availableHeight) {
        if (width <= 0 || height <= 0 || availableWidth <= 0 || availableHeight <= 0)
            throw new IllegalArgumentException("Invalid image dimensions");
        return Math.min(availableWidth / width, availableHeight / height);
    }
}
