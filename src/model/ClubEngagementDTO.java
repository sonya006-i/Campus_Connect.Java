package model;

import java.util.Locale;

/**
 * ClubEngagementDTO — Club-level event and participation intelligence
 * for Phase 14: Analytics.
 */
public class ClubEngagementDTO {

    private final int clubId;
    private final String clubName;
    private final int totalEvents;
    private final int totalRegistrations;
    private final int totalAttended;
    private final double averageRating;

    public ClubEngagementDTO(int clubId, String clubName, int totalEvents,
                             int totalRegistrations, int totalAttended, double averageRating) {
        this.clubId = clubId;
        this.clubName = (clubName != null) ? clubName : "Unknown Club";
        this.totalEvents = totalEvents;
        this.totalRegistrations = totalRegistrations;
        this.totalAttended = totalAttended;
        this.averageRating = averageRating;
    }

    public int getClubId() {
        return clubId;
    }

    public String getClubName() {
        return clubName;
    }

    public int getTotalEvents() {
        return totalEvents;
    }

    public int getTotalRegistrations() {
        return totalRegistrations;
    }

    public int getTotalAttended() {
        return totalAttended;
    }

    public double getAverageRating() {
        return averageRating;
    }

    /**
     * Calculates turnout percentage (attended / registrations).
     */
    public double getTurnoutRate() {
        if (totalRegistrations <= 0) return 0.0;
        return Math.min(100.0, (totalAttended * 100.0) / totalRegistrations);
    }

    public String getFormattedRating() {
        if (averageRating <= 0.0) return "N/A";
        return String.format(Locale.US, "%.1f ★", averageRating);
    }

    @Override
    public String toString() {
        return clubName + " (" + totalEvents + " events, " + totalRegistrations + " regs)";
    }
}
