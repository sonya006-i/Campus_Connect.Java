package model;

/**
 * MonthlyTrendDTO — Chronological registration and verified attendance telemetry
 * for Phase 14: Analytics.
 */
public class MonthlyTrendDTO {

    private final String monthKey;   // e.g. "2026-09"
    private final String monthLabel; // e.g. "Sep 2026"
    private int registrationCount;
    private int attendanceCount;

    public MonthlyTrendDTO(String monthKey, String monthLabel) {
        this(monthKey, monthLabel, 0, 0);
    }

    public MonthlyTrendDTO(String monthKey, String monthLabel, int registrationCount, int attendanceCount) {
        this.monthKey = monthKey;
        this.monthLabel = (monthLabel != null) ? monthLabel : monthKey;
        this.registrationCount = registrationCount;
        this.attendanceCount = attendanceCount;
    }

    public String getMonthKey() {
        return monthKey;
    }

    public String getMonthLabel() {
        return monthLabel;
    }

    public int getRegistrationCount() {
        return registrationCount;
    }

    public void setRegistrationCount(int registrationCount) {
        this.registrationCount = registrationCount;
    }

    public int getAttendanceCount() {
        return attendanceCount;
    }

    public void setAttendanceCount(int attendanceCount) {
        this.attendanceCount = attendanceCount;
    }

    @Override
    public String toString() {
        return monthLabel + " [Regs: " + registrationCount + ", Attended: " + attendanceCount + "]";
    }
}
