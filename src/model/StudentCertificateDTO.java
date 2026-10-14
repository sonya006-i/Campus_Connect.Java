package model;

import java.time.LocalDate;

/**
 * StudentCertificateDTO — Data transfer object representing a verified certificate
 * issued to a student, including full event, club, and verification details.
 *
 * Implements Phase 12: Certificate Management for the student experience.
 */
public class StudentCertificateDTO {

    private int certificateId;
    private int eventId;
    private int studentId;
    private String certificatePath;
    private LocalDate generatedDate;

    // Joined event and club metadata
    private String eventName;
    private String category;
    private LocalDate eventDate;
    private String venue;
    private String clubName;

    // Student identity
    private String studentName;
    private String studentEmail;

    public StudentCertificateDTO() {
    }

    public StudentCertificateDTO(int certificateId, int eventId, int studentId,
                                 String certificatePath, LocalDate generatedDate,
                                 String eventName, String category, LocalDate eventDate,
                                 String venue, String clubName, String studentName, String studentEmail) {
        this.certificateId = certificateId;
        this.eventId = eventId;
        this.studentId = studentId;
        this.certificatePath = certificatePath;
        this.generatedDate = generatedDate;
        this.eventName = eventName;
        this.category = category;
        this.eventDate = eventDate;
        this.venue = venue;
        this.clubName = clubName;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
    }

    public int getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(int certificateId) {
        this.certificateId = certificateId;
    }

    public String getFormattedCertificateId() {
        return (certificateId > 0) ? String.format("CERT-%06d", certificateId) : "";
    }

    public String getFormattedCertId() {
        return getFormattedCertificateId();
    }

    public String getVerificationCode() {
        return (certificateId > 0 && eventId > 0)
                ? String.format("CC-V%04d-%04d", eventId, certificateId)
                : getFormattedCertificateId();
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

    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
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

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getVenue() {
        return (venue != null) ? venue : "Campus Grounds";
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getClubName() {
        return (clubName != null) ? clubName : "Campus Club";
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
    }

    public String getStudentName() {
        return (studentName != null) ? studentName : "Student Member";
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
}
