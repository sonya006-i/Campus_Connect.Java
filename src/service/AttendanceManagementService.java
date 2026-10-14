package service;

import dao.AttendanceDAO;
import dao.EventDAO;
import dao.RegistrationDAO;
import model.Event;
import model.EventAttendanceDTO;
import model.User;
import util.DBConnection;
import util.Session;

import java.util.Collections;
import java.util.List;

/**
 * AttendanceManagementService — Coordinates administrator-only event attendance
 * tracking, roster review, and presence marking for Phase 11.
 *
 * Implements Phase 11: Attendance Management per AGENTS.md:
 * - Strict administrator role authorization checks using Session
 * - Verification that only students with APPROVED registrations are eligible for attendance
 * - Concurrency and duplicate constraint safety via AttendanceDAO
 * - Accurate event attendance summaries and participation percentages
 * - Ability to mark present, mark absent, and unmark accidental records
 * - Graceful database-offline handling
 */
public class AttendanceManagementService {

    private final AttendanceDAO attendanceDAO;
    private final EventDAO eventDAO;
    private final RegistrationDAO registrationDAO;

    public AttendanceManagementService() {
        this.attendanceDAO = new AttendanceDAO();
        this.eventDAO = new EventDAO();
        this.registrationDAO = new RegistrationDAO();
    }

    public AttendanceManagementService(AttendanceDAO attendanceDAO, EventDAO eventDAO, RegistrationDAO registrationDAO) {
        this.attendanceDAO = attendanceDAO;
        this.eventDAO = eventDAO;
        this.registrationDAO = registrationDAO;
    }

    /**
     * DTO containing event attendance metrics for the KPI summary bar.
     */
    public static class AttendanceSummaryDTO {
        private final int totalApproved;
        private final int markedPresent;
        private final int markedAbsent;
        private final int unmarked;
        private final int attendanceRatePercent;
        private final boolean dbOnline;

        public AttendanceSummaryDTO(int totalApproved, int markedPresent, int markedAbsent,
                                    int unmarked, int attendanceRatePercent, boolean dbOnline) {
            this.totalApproved = totalApproved;
            this.markedPresent = markedPresent;
            this.markedAbsent = markedAbsent;
            this.unmarked = unmarked;
            this.attendanceRatePercent = attendanceRatePercent;
            this.dbOnline = dbOnline;
        }

        public static AttendanceSummaryDTO offline() {
            return new AttendanceSummaryDTO(0, 0, 0, 0, 0, false);
        }

        public int getTotalApproved() {
            return totalApproved;
        }

        public int getMarkedPresent() {
            return markedPresent;
        }

        public int getMarkedAbsent() {
            return markedAbsent;
        }

        public int getUnmarked() {
            return unmarked;
        }

        public int getAttendanceRatePercent() {
            return attendanceRatePercent;
        }

        public boolean isDbOnline() {
            return dbOnline;
        }
    }

    /**
     * DTO representing the outcome of an administrative attendance operation.
     */
    public static class OperationResult {
        public enum Code {
            SUCCESS,
            UNAUTHORIZED,
            INVALID_INPUT,
            NOT_ELIGIBLE,
            NOT_FOUND,
            DATABASE_ERROR
        }

        private final boolean success;
        private final Code code;
        private final String message;

        public OperationResult(boolean success, Code code, String message) {
            this.success = success;
            this.code = code;
            this.message = message;
        }

        public static OperationResult success(String message) {
            return new OperationResult(true, Code.SUCCESS, message);
        }

        public static OperationResult unauthorized(String message) {
            return new OperationResult(false, Code.UNAUTHORIZED, message);
        }

        public static OperationResult invalidInput(String message) {
            return new OperationResult(false, Code.INVALID_INPUT, message);
        }

        public static OperationResult notEligible(String message) {
            return new OperationResult(false, Code.NOT_ELIGIBLE, message);
        }

        public static OperationResult notFound(String message) {
            return new OperationResult(false, Code.NOT_FOUND, message);
        }

        public static OperationResult dbError(String message) {
            return new OperationResult(false, Code.DATABASE_ERROR, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public Code getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Verifies that the currently authenticated session belongs to an administrator.
     */
    public boolean isCurrentUserAdmin() {
        User user = Session.getCurrentUser();
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    /**
     * Retrieves all events for the event selector dropdown.
     */
    public List<Event> getAllEvents() {
        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }
        return eventDAO.getAll();
    }

    /**
     * Retrieves the attendance roster for a specific event with administrator authorization.
     */
    public List<EventAttendanceDTO> getEventAttendanceRoster(int eventId) {
        if (!isCurrentUserAdmin()) {
            System.err.println("[AttendanceManagementService] Access denied: current user is not an administrator.");
            return Collections.emptyList();
        }

        if (eventId <= 0 || !DBConnection.testConnection()) {
            return Collections.emptyList();
        }

        return attendanceDAO.getEventAttendanceRoster(eventId);
    }

    /**
     * Computes the live attendance KPI summary metrics for an event.
     */
    public AttendanceSummaryDTO getAttendanceSummary(int eventId) {
        if (eventId <= 0 || !DBConnection.testConnection()) {
            return AttendanceSummaryDTO.offline();
        }

        try {
            int approvedCount = registrationDAO.countApprovedForEvent(eventId);
            int presentCount = attendanceDAO.countPresentForEvent(eventId);
            int absentCount = attendanceDAO.countAbsentForEvent(eventId);
            int totalMarked = presentCount + absentCount;
            int unmarked = Math.max(0, approvedCount - totalMarked);
            int ratePercent = (approvedCount > 0) ? (presentCount * 100 / approvedCount) : 0;

            return new AttendanceSummaryDTO(approvedCount, presentCount, absentCount, unmarked, ratePercent, true);
        } catch (Exception e) {
            System.err.println("[AttendanceManagementService Error] Failed to aggregate attendance summary: " + e.getMessage());
            return AttendanceSummaryDTO.offline();
        }
    }

    /**
     * Marks student attendance (Present or Absent) for an event.
     * Enforces that only students with APPROVED registrations are eligible.
     */
    public OperationResult markAttendance(int eventId, int studentId, boolean present) {
        // 1. Role verification
        if (!isCurrentUserAdmin()) {
            return OperationResult.unauthorized("Access denied. Only administrators may record attendance.");
        }

        // 2. Input validation
        if (eventId <= 0 || studentId <= 0) {
            return OperationResult.invalidInput("Invalid event or student ID.");
        }

        // 3. Database connection check
        if (!DBConnection.testConnection()) {
            return OperationResult.dbError("Database connection is currently offline.");
        }

        // 4. Verify student has an APPROVED registration for this event
        boolean approved = attendanceDAO.isStudentApproved(eventId, studentId);
        if (!approved) {
            return OperationResult.notEligible("Attendance can only be marked for participants with APPROVED registrations.");
        }

        // 5. Safely record attendance via DAO
        boolean saved = attendanceDAO.recordAttendance(eventId, studentId, present);
        if (saved) {
            String statusWord = present ? "PRESENT" : "ABSENT";
            return OperationResult.success("Participant attendance marked as " + statusWord + ".");
        } else {
            return OperationResult.dbError("Failed to update attendance record in database.");
        }
    }

    /**
     * Clears an attendance record, resetting the participant back to 'Unmarked'.
     * Used to correct accidental marks as permitted by Phase 11 requirements.
     */
    public OperationResult unmarkAttendance(int eventId, int studentId) {
        // 1. Role verification
        if (!isCurrentUserAdmin()) {
            return OperationResult.unauthorized("Access denied. Only administrators may modify attendance.");
        }

        // 2. Input validation
        if (eventId <= 0 || studentId <= 0) {
            return OperationResult.invalidInput("Invalid event or student ID.");
        }

        // 3. Database connection check
        if (!DBConnection.testConnection()) {
            return OperationResult.dbError("Database connection is currently offline.");
        }

        // 4. Safely remove attendance record
        boolean removed = attendanceDAO.removeAttendance(eventId, studentId);
        if (removed) {
            return OperationResult.success("Attendance record cleared. Participant is now Unmarked.");
        } else {
            return OperationResult.notFound("No active attendance record found to clear.");
        }
    }

    /**
     * Convenience batch action: Marks all currently approved attendees of an event as Present.
     */
    public OperationResult markAllApprovedPresent(int eventId) {
        if (!isCurrentUserAdmin()) {
            return OperationResult.unauthorized("Access denied. Only administrators may record attendance.");
        }

        if (eventId <= 0) {
            return OperationResult.invalidInput("Invalid event ID.");
        }

        if (!DBConnection.testConnection()) {
            return OperationResult.dbError("Database connection is currently offline.");
        }

        List<EventAttendanceDTO> roster = attendanceDAO.getEventAttendanceRoster(eventId);
        if (roster.isEmpty()) {
            return OperationResult.notFound("No participants found for the selected event.");
        }

        int updated = 0;
        for (EventAttendanceDTO p : roster) {
            if (p.isApproved()) {
                if (attendanceDAO.recordAttendance(eventId, p.getStudentId(), true)) {
                    updated++;
                }
            }
        }

        return OperationResult.success("Marked " + updated + " approved participants as PRESENT.");
    }
}
