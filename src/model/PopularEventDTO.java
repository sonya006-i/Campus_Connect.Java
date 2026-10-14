package model;

import java.time.LocalDate;

/**
 * PopularEventDTO — High-interest campus event with verified registration volume
 * for Phase 15: Campus Pulse.
 */
public class PopularEventDTO {

    private final int eventId;
    private final String eventName;
    private final String clubName;
    private final String category;
    private final LocalDate eventDate;
    private final int registrationCount;
    private final int maxParticipants;

    public PopularEventDTO(int eventId, String eventName, String clubName, String category,
                           LocalDate eventDate, int registrationCount, int maxParticipants) {
        this.eventId = eventId;
        this.eventName = (eventName != null) ? eventName : "Untitled Event";
        this.clubName = (clubName != null) ? clubName : "General";
        this.category = (category != null) ? category : "General";
        this.eventDate = eventDate;
        this.registrationCount = registrationCount;
        this.maxParticipants = maxParticipants;
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

    public int getRegistrationCount() {
        return registrationCount;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    @Override
    public String toString() {
        return eventName + " (" + clubName + " • " + registrationCount + " registered)";
    }
}
