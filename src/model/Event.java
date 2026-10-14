package model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Event — Model representing an event hosted by a club.
 */
public class Event {

    private int eventId;
    private int clubId;
    private String eventName;
    private String description;
    private String category;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private String venue;
    private int maxParticipants;
    private LocalDate registrationDeadline;
    private String clubName;

    public Event() {
    }

    public Event(int eventId, int clubId, String eventName, String description, String category,
                 LocalDate eventDate, LocalTime eventTime, String venue, int maxParticipants,
                 LocalDate registrationDeadline) {
        this.eventId = eventId;
        this.clubId = clubId;
        this.eventName = eventName;
        this.description = description;
        this.category = category;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.venue = venue;
        this.maxParticipants = maxParticipants;
        this.registrationDeadline = registrationDeadline;
    }

    public Event(int clubId, String eventName, String description, String category,
                 LocalDate eventDate, LocalTime eventTime, String venue, int maxParticipants,
                 LocalDate registrationDeadline) {
        this.clubId = clubId;
        this.eventName = eventName;
        this.description = description;
        this.category = category;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.venue = venue;
        this.maxParticipants = maxParticipants;
        this.registrationDeadline = registrationDeadline;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getClubId() {
        return clubId;
    }

    public void setClubId(int clubId) {
        this.clubId = clubId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(int maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public LocalDate getRegistrationDeadline() {
        return registrationDeadline;
    }

    public void setRegistrationDeadline(LocalDate registrationDeadline) {
        this.registrationDeadline = registrationDeadline;
    }

    public String getClubName() {
        return clubName;
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
    }

    @Override
    public String toString() {
        return "Event{" +
                "eventId=" + eventId +
                ", eventName='" + eventName + '\'' +
                ", eventDate=" + eventDate +
                ", venue='" + venue + '\'' +
                '}';
    }
}
