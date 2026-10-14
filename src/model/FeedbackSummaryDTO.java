package model;

import java.util.Locale;

/**
 * FeedbackSummaryDTO — Summary metrics for the feedback overview.
 */
public class FeedbackSummaryDTO {

    private final int totalFeedback;
    private final double averageRating;
    private final int fiveStarCount;
    private final int fourStarCount;
    private final int threeStarCount;
    private final int twoStarCount;
    private final int oneStarCount;
    private final int eventsReviewedCount;
    private final boolean dbOnline;

    public FeedbackSummaryDTO(int totalFeedback, double averageRating,
                              int fiveStarCount, int fourStarCount, int threeStarCount,
                              int twoStarCount, int oneStarCount, int eventsReviewedCount,
                              boolean dbOnline) {
        this.totalFeedback = totalFeedback;
        this.averageRating = averageRating;
        this.fiveStarCount = fiveStarCount;
        this.fourStarCount = fourStarCount;
        this.threeStarCount = threeStarCount;
        this.twoStarCount = twoStarCount;
        this.oneStarCount = oneStarCount;
        this.eventsReviewedCount = eventsReviewedCount;
        this.dbOnline = dbOnline;
    }

    public static FeedbackSummaryDTO offline() {
        return new FeedbackSummaryDTO(0, 0.0, 0, 0, 0, 0, 0, 0, false);
    }

    public int getTotalFeedback() {
        return totalFeedback;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public String getFormattedAverageRating() {
        if (!dbOnline || totalFeedback == 0) {
            return "—";
        }
        return String.format(Locale.US, "%.1f", averageRating);
    }

    public int getFiveStarCount() {
        return fiveStarCount;
    }

    public int getFourStarCount() {
        return fourStarCount;
    }

    public int getThreeStarCount() {
        return threeStarCount;
    }

    public int getTwoStarCount() {
        return twoStarCount;
    }

    public int getOneStarCount() {
        return oneStarCount;
    }

    public int getEventsReviewedCount() {
        return eventsReviewedCount;
    }

    public boolean isDbOnline() {
        return dbOnline;
    }
}
