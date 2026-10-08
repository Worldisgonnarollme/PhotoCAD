package com.example.photocad.report;

/** Pure, Android-independent rules shared by the UI and PDF layout. */
public final class ReportRules {
    private ReportRules() {}
    public static String description(String individual, String pointComment) {
        return individual != null && !individual.trim().isEmpty() ? individual : pointComment;
    }
    public static float fitScale(int width, int height, float availableWidth, float availableHeight) {
        if (width <= 0 || height <= 0 || availableWidth <= 0 || availableHeight <= 0)
            throw new IllegalArgumentException("Invalid image dimensions");
        return Math.min(availableWidth / width, availableHeight / height);
    }
}
