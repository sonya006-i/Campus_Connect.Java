package model;

/**
 * CampusPulseActivityDTO — Database-backed campus life telemetry item (milestone, popular event, or completed event).
 *
 * Implements Phase 15: Campus Pulse per AGENTS.md.
 */
public class CampusPulseActivityDTO {

    public enum ActivityType {
        UPCOMING_EVENT,
        RECENTLY_COMPLETED,
        CAMPUS_MILESTONE,
        TRENDING_CLUB
    }

    private final ActivityType type;
    private final String headline;
    private final String subtext;
    private final String timeOrBadge;
    private final String icon;

    public CampusPulseActivityDTO(ActivityType type, String headline, String subtext,
                                  String timeOrBadge, String icon) {
        this.type = type;
        this.headline = headline;
        this.subtext = subtext;
        this.timeOrBadge = timeOrBadge;
        this.icon = icon;
    }

    public ActivityType getType() {
        return type;
    }

    public String getHeadline() {
        return headline;
    }

    public String getSubtext() {
        return subtext;
    }

    public String getTimeOrBadge() {
        return timeOrBadge;
    }

    public String getIcon() {
        return icon;
    }

    @Override
    public String toString() {
        return "[" + icon + " " + type + "] " + headline + " — " + subtext;
    }
}
