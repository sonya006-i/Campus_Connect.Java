package model;

import java.time.LocalDate;

/**
 * AdminCertificateParticipantDTO — Data transfer object representing a participant's
 * eligibility and certificate issuance status for Phase 12 admin management.
 */
public class AdminCertificateParticipantDTO {

    private int studentId;
    private String studentName;
    private String studentEmail;
    private int eventId;
    private String eventName;
    private int registrationId;
    private String registrationStatus; // "APPROVED", "PENDING", "REJECTED"

    private boolean attendanceRecorded;
    private boolean attended; // true if present = true

    private boolean certificateIssued;
    private Integer certificateId;
    private String certificatePath;
    private LocalDate generatedDate;

    public AdminCertificateParticipantDTO() {
    }

    public AdminCertificateParticipantDTO(int studentId, String studentName, String studentEmail,
                                         int eventId, String eventName, int registrationId,
                                         String registrationStatus, boolean attendanceRecorded,
                                         boolean attended, boolean certificateIssued,
                                         Integer certificateId, String certificatePath,
                                         LocalDate generatedDate) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.eventId = eventId;
        this.eventName = eventName;
        this.registrationId = registrationId;
        this.registrationStatus = registrationStatus;
        this.attendanceRecorded = attendanceRecorded;
        this.attended = attended;
        this.certificateIssued = certificateIssued;
        this.certificateId = certificateId;
        this.certificatePath = certificatePath;
        this.generatedDate = generatedDate;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
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

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return (eventName != null) ? eventName : "Campus Event";
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
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

    public String getRegistrationStatus() {
        return (registrationStatus != null) ? registrationStatus.toUpperCase() : "PENDING";
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    public boolean isAttendanceRecorded() {
        return attendanceRecorded;
    }

    public void setAttendanceRecorded(boolean attendanceRecorded) {
        this.attendanceRecorded = attendanceRecorded;
    }

    public boolean isAttended() {
        return attendanceRecorded && attended;
    }

    public boolean isPresent() {
        return isAttended();
    }

    public void setAttended(boolean attended) {
        this.attended = attended;
        this.attendanceRecorded = true;
    }

    public void setPresent(boolean present) {
        setAttended(present);
    }

    public boolean isCertificateIssued() {
        return certificateIssued;
    }

    public void setCertificateIssued(boolean certificateIssued) {
        this.certificateIssued = certificateIssued;
    }

    public Integer getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(Integer certificateId) {
        this.certificateId = certificateId;
    }

    public String getFormattedCertificateId() {
        return (certificateId != null && certificateId > 0) ? String.format("CERT-%06d", certificateId) : "";
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

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(registrationStatus);
    }

    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(registrationStatus);
    }

    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(registrationStatus);
    }

    /**
     * Participant is eligible for certificate issuance ONLY if:
     * 1. Registration is APPROVED
     * 2. Attendance is recorded with present = TRUE
     * 3. Certificate has not already been issued
     */
    public boolean isEligible() {
        return isApproved() && isAttended() && !certificateIssued;
    }

    public String getEligibilityStatusDisplay() {
        if (certificateIssued) {
            return "ISSUED";
        }
        if (!isApproved()) {
            return "NOT APPROVED";
        }
        if (!isAttended()) {
            return "ABSENT / UNMARKED";
        }
        return "ELIGIBLE";
    }
}
