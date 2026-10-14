package model;

/**
 * EventAttendanceDTO — Comprehensive data transfer object for participant
 * attendance tracking and roster management in Phase 11.
 *
 * Joins registration data, student user profile, and event attendance status.
 */
public class EventAttendanceDTO {

    private int registrationId;
    private int eventId;
    private String eventName;
    private int studentId;
    private String studentName;
    private String studentEmail;
    private String registrationStatus; // "APPROVED", "PENDING", "REJECTED"

    private Integer attendanceId; // null if not yet marked
    private boolean attendanceRecorded;
    private boolean present;

    public EventAttendanceDTO() {
    }

    public EventAttendanceDTO(int registrationId, int eventId, String eventName,
                              int studentId, String studentName, String studentEmail,
                              String registrationStatus, Integer attendanceId,
                              boolean attendanceRecorded, boolean present) {
        this.registrationId = registrationId;
        this.eventId = eventId;
        this.eventName = eventName;
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.registrationStatus = registrationStatus;
        this.attendanceId = attendanceId;
        this.attendanceRecorded = attendanceRecorded;
        this.present = present;
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

    public String getEventName() {
        return (eventName != null) ? eventName : "Campus Event";
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
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

    public String getRegistrationStatus() {
        return (registrationStatus != null) ? registrationStatus.toUpperCase() : "PENDING";
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    public Integer getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(Integer attendanceId) {
        this.attendanceId = attendanceId;
    }

    public boolean isAttendanceRecorded() {
        return attendanceRecorded;
    }

    public void setAttendanceRecorded(boolean attendanceRecorded) {
        this.attendanceRecorded = attendanceRecorded;
    }

    public boolean isPresent() {
        return attendanceRecorded && present;
    }

    public boolean isAbsent() {
        return attendanceRecorded && !present;
    }

    public boolean isUnmarked() {
        return !attendanceRecorded;
    }

    public void setPresent(boolean present) {
        this.present = present;
        this.attendanceRecorded = true;
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

    public String getAttendanceStatusDisplay() {
        if (!attendanceRecorded) {
            return "UNMARKED";
        }
        return present ? "PRESENT" : "ABSENT";
    }
}
