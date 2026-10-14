package model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * AdminRegistrationDTO — Comprehensive data transfer object for administrator
 * registration review, approval, and management in Phase 10.
 *
 * Joins registration, student user, event, and club details into a single clean object.
 */
public class AdminRegistrationDTO {

    private int registrationId;
    private int eventId;
    private int studentId;
    private String status; // "PENDING", "APPROVED", "REJECTED"
    private LocalDate registrationDate;

    // Student information
    private String studentName;
    private String studentEmail;

    // Event & Club information
    private String eventName;
    private String category;
    private String clubName;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private String venue;
    private int maxParticipants;

    public AdminRegistrationDTO() {
    }

    public AdminRegistrationDTO(int registrationId, int eventId, int studentId, String status,
                                LocalDate registrationDate, String studentName, String studentEmail,
                                String eventName, String category, String clubName,
                                LocalDate eventDate, LocalTime eventTime, String venue, int maxParticipants) {
        this.registrationId = registrationId;
        this.eventId = eventId;
        this.studentId = studentId;
        this.status = status;
        this.registrationDate = registrationDate;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.eventName = eventName;
        this.category = category;
        this.clubName = clubName;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.venue = venue;
        this.maxParticipants = maxParticipants;
    }

    public int getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(int registrationId) {
        this.registrationId = registrationId;
    }

    public String getFormattedRegistrationId() {
        return (registrationId > 0) ? String.format("REG-%06d", registrationId) : "";
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
        return (status != null) ? status.toUpperCase() : "PENDING";
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

    public String getStudentName() {
        return (studentName != null) ? studentName : "Student";
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentEmail() {
        return (studentEmail != null) ? studentEmail : "";
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getEventName() {
        return (eventName != null) ? eventName : "Campus Event";
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getCategory() {
        return (category != null) ? category : "General";
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getClubName() {
        return (clubName != null) ? clubName : "Campus Club";
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
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
        return (venue != null) ? venue : "Campus Grounds";
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

    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(status);
    }

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(status);
    }

    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(status);
    }
}
