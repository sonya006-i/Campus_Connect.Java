package model;

/**
 * CategoryDistributionDTO — Distribution of events and registrations across categories
 * for Phase 14: Analytics.
 */
public class CategoryDistributionDTO {

    private final String category;
    private final int eventCount;
    private final int totalRegistrations;

    public CategoryDistributionDTO(String category, int eventCount, int totalRegistrations) {
        this.category = (category != null && !category.trim().isEmpty()) ? category.trim() : "General";
        this.eventCount = eventCount;
        this.totalRegistrations = totalRegistrations;
    }

    public String getCategory() {
        return category;
    }

    public int getEventCount() {
        return eventCount;
    }

    public int getTotalRegistrations() {
        return totalRegistrations;
    }

    @Override
    public String toString() {
        return category + ": " + eventCount + " events, " + totalRegistrations + " registrations";
    }
}
