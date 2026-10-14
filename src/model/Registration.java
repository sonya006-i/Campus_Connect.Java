package model;

import java.time.LocalDate;

/**
 * Registration — Model representing a student's registration for an event.
 */
public class Registration {

    private int registrationId;
    private int eventId;
    private int studentId;
    private String status; // "PENDING", "APPROVED", "REJECTED"
    private LocalDate registrationDate;

    public Registration() {
    }

    public Registration(int registrationId, int eventId, int studentId, String status, LocalDate registrationDate) {
        this.registrationId = registrationId;
        this.eventId = eventId;
        this.studentId = studentId;
        this.status = status;
        this.registrationDate = registrationDate;
    }

    public Registration(int eventId, int studentId, String status, LocalDate registrationDate) {
        this.eventId = eventId;
        this.studentId = studentId;
        this.status = status;
        this.registrationDate = registrationDate;
    }

    public int getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(int registrationId) {
        this.registrationId = registrationId;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }

    @Override
    public String toString() {
        return "Registration{" +
                "registrationId=" + registrationId +
                ", eventId=" + eventId +
                ", studentId=" + studentId +
                ", status='" + status + '\'' +
                ", registrationDate=" + registrationDate +
                '}';
    }
}
