package model;

/**
 * Attendance — Model representing an event attendance record for a student.
 */
public class Attendance {

    private int attendanceId;
    private int eventId;
    private int studentId;
    private boolean present;

    public Attendance() {
    }

    public Attendance(int attendanceId, int eventId, int studentId, boolean present) {
        this.attendanceId = attendanceId;
        this.eventId = eventId;
        this.studentId = studentId;
        this.present = present;
    }

    public Attendance(int eventId, int studentId, boolean present) {
        this.eventId = eventId;
        this.studentId = studentId;
        this.present = present;
    }

    public int getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(int attendanceId) {
        this.attendanceId = attendanceId;
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

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    @Override
    public String toString() {
        return "Attendance{" +
                "attendanceId=" + attendanceId +
                ", eventId=" + eventId +
                ", studentId=" + studentId +
                ", present=" + present +
                '}';
    }
}
