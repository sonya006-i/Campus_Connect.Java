package model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * StudentRegistrationDTO — Encapsulates joined registration and event data
 * for the authenticated student in Phase 7: My Events.
 */
public class StudentRegistrationDTO {

    private Registration registration;
    private Event event;

    public StudentRegistrationDTO() {
    }

    public StudentRegistrationDTO(Registration registration, Event event) {
        this.registration = registration;
        this.event = event;
    }

    public Registration getRegistration() {
        return registration;
    }

    public void setRegistration(Registration registration) {
        this.registration = registration;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public int getRegistrationId() {
        return registration != null ? registration.getRegistrationId() : 0;
    }

    public String getFormattedRegistrationId() {
        return (getRegistrationId() > 0) ? String.format("REG-%06d", getRegistrationId()) : "";
    }

    public String getStatus() {
        return (registration != null && registration.getStatus() != null)
                ? registration.getStatus().toUpperCase()
                : "PENDING";
    }

    public LocalDate getRegistrationDate() {
        return registration != null ? registration.getRegistrationDate() : null;
    }

    public String getEventName() {
        return event != null ? event.getEventName() : "Campus Event";
    }

    public String getCategory() {
        return event != null ? event.getCategory() : "General";
    }

    public LocalDate getEventDate() {
        return event != null ? event.getEventDate() : null;
    }

    public LocalTime getEventTime() {
        return event != null ? event.getEventTime() : null;
    }

    public String getVenue() {
        return (event != null && event.getVenue() != null && !event.getVenue().isEmpty())
                ? event.getVenue()
                : "Campus Grounds";
    }

    public String getClubName() {
        return (event != null && event.getClubName() != null && !event.getClubName().isEmpty())
                ? event.getClubName()
                : "Campus Society";
    }
}
