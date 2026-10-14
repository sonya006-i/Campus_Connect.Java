package model;

import java.time.LocalDate;
import java.util.Locale;

/**
 * EventPerformanceDTO — Individual event efficiency, turnout, and feedback metrics
 * for Phase 14: Analytics.
 */
public class EventPerformanceDTO {

    private final int eventId;
    private final String eventName;
    private final String clubName;
    private final String category;
    private final LocalDate eventDate;
    private final int capacity;
    private final int totalRegistrations;
    private final int approvedRegistrations;
    private final int totalAttended;
    private final double averageRating;
    private final int feedbackCount;

    public EventPerformanceDTO(int eventId, String eventName, String clubName,
                               String category, LocalDate eventDate, int capacity,
                               int totalRegistrations, int approvedRegistrations,
                               int totalAttended, double averageRating, int feedbackCount) {
        this.eventId = eventId;
        this.eventName = (eventName != null) ? eventName : "Untitled Event";
        this.clubName = (clubName != null) ? clubName : "General";
        this.category = (category != null) ? category : "General";
        this.eventDate = eventDate;
        this.capacity = capacity;
        this.totalRegistrations = totalRegistrations;
        this.approvedRegistrations = approvedRegistrations;
        this.totalAttended = totalAttended;
        this.averageRating = averageRating;
        this.feedbackCount = feedbackCount;
    }

    public int getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public String getClubName() {
        return clubName;
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getTotalRegistrations() {
        return totalRegistrations;
    }

    public int getApprovedRegistrations() {
        return approvedRegistrations;
    }

    public int getTotalAttended() {
        return totalAttended;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public int getFeedbackCount() {
        return feedbackCount;
    }

    /**
     * Capacity utilization rate (approved / capacity).
     */
    public double getCapacityUtilizationRate() {
        if (capacity <= 0) return 100.0; // Unlimited or open capacity
        return Math.min(100.0, (approvedRegistrations * 100.0) / capacity);
    }

    /**
     * Turnout rate (attended / approved).
     */
    public double getTurnoutRate() {
        if (approvedRegistrations <= 0) return 0.0;
        return Math.min(100.0, (totalAttended * 100.0) / approvedRegistrations);
    }

    public String getFormattedRating() {
        if (feedbackCount <= 0 || averageRating <= 0.0) return "N/A";
        return String.format(Locale.US, "%.1f ★ (%d)", averageRating, feedbackCount);
    }
}
