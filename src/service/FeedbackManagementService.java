package service;

import dao.AttendanceDAO;
import dao.EventDAO;
import dao.FeedbackDAO;
import dao.RegistrationDAO;
import model.AdminFeedbackDTO;
import model.Attendance;
import model.Feedback;
import model.FeedbackSummaryDTO;
import model.Registration;
import model.StudentFeedbackDTO;
import model.User;
import util.DBConnection;
import util.Session;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FeedbackManagementService — Coordinates student feedback submissions,
 * eligibility verification, rating validation, and administrator overview for Phase 13.
 *
 * Implements Phase 13: Feedback Management per AGENTS.md:
 * - Strictly enforces session role-based authorization for students and administrators
 * - Derives student identity exclusively from authenticated Session (never trusted from UI)
 * - Enforces verified eligibility: requires APPROVED registration + PRESENT attendance
 * - Validates ratings (1–5) and comments (length & sanitization)
 * - Prohibits duplicate submissions via service pre-check and database UNIQUE constraint
 * - Comprehensive filtering and search capabilities for administrators
 * - Resilient offline database fallback and error handling
 */
public class FeedbackManagementService {

    private final FeedbackDAO feedbackDAO;
    private final AttendanceDAO attendanceDAO;
    private final RegistrationDAO registrationDAO;
    private final EventDAO eventDAO;

    public FeedbackManagementService() {
        this.feedbackDAO = new FeedbackDAO();
        this.attendanceDAO = new AttendanceDAO();
        this.registrationDAO = new RegistrationDAO();
        this.eventDAO = new EventDAO();
    }

    public FeedbackManagementService(FeedbackDAO feedbackDAO, AttendanceDAO attendanceDAO,
                                     RegistrationDAO registrationDAO, EventDAO eventDAO) {
        this.feedbackDAO = feedbackDAO;
        this.attendanceDAO = attendanceDAO;
        this.registrationDAO = registrationDAO;
        this.eventDAO = eventDAO;
    }

    /**
     * DTO representing the outcome of a feedback submission operation.
     */
    public static class OperationResult {
        public enum Code {
            SUCCESS,
            UNAUTHORIZED,
            INVALID_INPUT,
            NOT_ELIGIBLE,
            ALREADY_SUBMITTED,
            DATABASE_ERROR
        }

        private final boolean success;
        private final Code code;
        private final String message;
        private final int feedbackId;

        public OperationResult(boolean success, Code code, String message, int feedbackId) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.feedbackId = feedbackId;
        }

        public static OperationResult success(String message, int feedbackId) {
            return new OperationResult(true, Code.SUCCESS, message, feedbackId);
        }

        public static OperationResult unauthorized(String message) {
            return new OperationResult(false, Code.UNAUTHORIZED, message, 0);
        }

        public static OperationResult invalidInput(String message) {
            return new OperationResult(false, Code.INVALID_INPUT, message, 0);
        }

        public static OperationResult notEligible(String message) {
            return new OperationResult(false, Code.NOT_ELIGIBLE, message, 0);
        }

        public static OperationResult alreadySubmitted(String message) {
            return new OperationResult(false, Code.ALREADY_SUBMITTED, message, 0);
        }

        public static OperationResult databaseError(String message) {
            return new OperationResult(false, Code.DATABASE_ERROR, message, 0);
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

        public int getFeedbackId() {
            return feedbackId;
        }
    }

    /**
     * Submits feedback and rating for an event on behalf of the currently authenticated student.
     *
     * @param eventId the target event identifier
     * @param rating star rating between 1 and 5 (inclusive)
     * @param comment optional student written comment / review
     * @return OperationResult representing success or failure cause
     */
    public OperationResult submitFeedback(int eventId, int rating, String comment) {
        User user = Session.getCurrentUser();
        if (user == null) {
            return OperationResult.unauthorized("Authentication required. Please sign in to submit feedback.");
        }
        if (!"STUDENT".equalsIgnoreCase(user.getRole())) {
            return OperationResult.unauthorized("Access denied: only student accounts can submit event feedback.");
        }

        int studentId = user.getUserId();

        // 1. Validate Event ID
        if (eventId <= 0) {
            return OperationResult.invalidInput("Invalid event identifier.");
        }

        // 2. Validate Rating (Scale 1 to 5)
        if (rating < 1 || rating > 5) {
            return OperationResult.invalidInput("Rating must be between 1 and 5 stars.");
        }

        // 3. Validate & sanitize comment
        String sanitizedComment = (comment != null) ? comment.trim() : "";
        if (sanitizedComment.length() > 2000) {
            return OperationResult.invalidInput("Written comments cannot exceed 2000 characters.");
        }

        // 4. Verify database connectivity
        if (!DBConnection.testConnection()) {
            return OperationResult.databaseError("Database is currently offline. Unable to record feedback.");
        }

        // 5. Check Eligibility (Requires APPROVED registration + PRESENT attendance)
        if (!isEligibleForFeedback(eventId, studentId)) {
            return OperationResult.notEligible("Feedback can only be submitted for events you attended with verified presence.");
        }

        // 6. Check Duplicate Submission
        if (hasStudentSubmitted(eventId, studentId)) {
            return OperationResult.alreadySubmitted("You have already submitted feedback for this event.");
        }

        // 7. Insert feedback record
        try {
            Feedback feedback = new Feedback(eventId, studentId, rating, sanitizedComment);
            int newId = feedbackDAO.insertFeedback(feedback);
            return OperationResult.success("Thank you! Your feedback has been recorded successfully.", newId);
        } catch (SQLException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("duplicate") || errorMsg.contains("unique") || errorMsg.contains("uq_feedback")) {
                return OperationResult.alreadySubmitted("You have already submitted feedback for this event.");
            }
            return OperationResult.databaseError("Unable to save feedback due to a database error. Please try again.");
        }
    }

    /**
     * Checks if a student is eligible to submit feedback for a specific event.
     * Criteria: Must hold an APPROVED registration AND be marked PRESENT in attendance records.
     */
    public boolean isEligibleForFeedback(int eventId, int studentId) {
        if (eventId <= 0 || studentId <= 0) {
            return false;
        }

        // Check registration status
        Registration reg = registrationDAO.findByEventAndStudent(eventId, studentId);
        if (reg == null || !"APPROVED".equalsIgnoreCase(reg.getStatus())) {
            return false;
        }

        // Check attendance presence
        Attendance att = attendanceDAO.findByEventAndStudent(eventId, studentId);
        return att != null && att.isPresent();
    }

    /**
     * Checks if a student has already submitted feedback for an event.
     */
    public boolean hasStudentSubmitted(int eventId, int studentId) {
        if (eventId <= 0 || studentId <= 0) {
            return false;
        }
        return feedbackDAO.hasStudentSubmitted(eventId, studentId);
    }

    /**
     * Retrieves all events eligible for feedback for the currently authenticated student,
     * including their feedback submission status and existing ratings.
     */
    public List<StudentFeedbackDTO> getMyFeedbackEvents() {
        User user = Session.getCurrentUser();
        if (user == null || !"STUDENT".equalsIgnoreCase(user.getRole())) {
            return Collections.emptyList();
        }
        return feedbackDAO.getStudentFeedbackEvents(user.getUserId());
    }

    /**
     * Retrieves all campus-wide feedback entries for administrator review.
     */
    public List<AdminFeedbackDTO> getAllFeedbackForAdmin() {
        User user = Session.getCurrentUser();
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            return Collections.emptyList();
        }
        return feedbackDAO.getAllFeedbackWithDetails();
    }

    /**
     * Retrieves campus-wide feedback summary metrics for administrators.
     */
    public FeedbackSummaryDTO getFeedbackSummary() {
        User user = Session.getCurrentUser();
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            return FeedbackSummaryDTO.offline();
        }
        return feedbackDAO.getFeedbackSummary();
    }

    /**
     * Filters a list of AdminFeedbackDTO entries in-memory by event, rating, and search query.
     *
     * @param feedbackList source feedback list
     * @param eventIdFilter optional event ID filter (null or <= 0 for all)
     * @param ratingFilter optional rating filter (null or <= 0 for all)
     * @param searchQuery optional text query searching event name, student name, email, or comment
     * @return filtered list
     */
    public List<AdminFeedbackDTO> filterFeedback(List<AdminFeedbackDTO> feedbackList,
                                                 Integer eventIdFilter,
                                                 Integer ratingFilter,
                                                 String searchQuery) {
        if (feedbackList == null) {
            return Collections.emptyList();
        }

        final String query = (searchQuery != null) ? searchQuery.trim().toLowerCase() : "";

        return feedbackList.stream()
                .filter(f -> {
                    // Filter by event ID
                    if (eventIdFilter != null && eventIdFilter > 0) {
                        if (f.getEventId() != eventIdFilter) return false;
                    }

                    // Filter by rating
                    if (ratingFilter != null && ratingFilter > 0) {
                        if (f.getRating() != ratingFilter) return false;
                    }

                    // Filter by search query
                    if (!query.isEmpty()) {
                        boolean matchesEvent = f.getEventName().toLowerCase().contains(query);
                        boolean matchesStudent = f.getStudentName().toLowerCase().contains(query);
                        boolean matchesEmail = f.getStudentEmail().toLowerCase().contains(query);
                        boolean matchesComment = f.getComment().toLowerCase().contains(query);
                        boolean matchesClub = f.getClubName().toLowerCase().contains(query);
                        if (!matchesEvent && !matchesStudent && !matchesEmail && !matchesComment && !matchesClub) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper to verify if the current user is an authenticated student.
     */
    public boolean isCurrentUserStudent() {
        User user = Session.getCurrentUser();
        return user != null && "STUDENT".equalsIgnoreCase(user.getRole());
    }

    /**
     * Helper to verify if the current user is an authenticated admin.
     */
    public boolean isCurrentUserAdmin() {
        User user = Session.getCurrentUser();
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }
}
