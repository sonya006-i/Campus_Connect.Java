package service;

import dao.EventDAO;
import dao.RegistrationDAO;
import model.Event;
import model.Registration;
import model.User;
import util.DBConnection;
import util.Session;

import java.sql.SQLException;
import java.time.LocalDate;

/**
 * RegistrationService — Coordinates business logic and validation for event registrations.
 *
 * Implements Phase 6: Event Registration.
 * Follows AGENTS.md rules:
 * - Simple, robust validation pipeline (Checks 1-7)
 * - Safe transaction handling & atomic database insert
 * - Database-generated registration_id propagation
 * - Graceful handling of duplicate key violations and offline database
 */
public class RegistrationService {

    private final RegistrationDAO registrationDAO;
    private final EventDAO eventDAO;

    public RegistrationService() {
        this.registrationDAO = new RegistrationDAO();
        this.eventDAO = new EventDAO();
    }

    public RegistrationService(RegistrationDAO registrationDAO, EventDAO eventDAO) {
        this.registrationDAO = registrationDAO;
        this.eventDAO = eventDAO;
    }

    /**
     * Eligibility status for displaying event registration UI.
     */
    public enum Eligibility {
        AVAILABLE,
        ALREADY_REGISTERED,
        DEADLINE_PASSED,
        EVENT_FULL,
        STUDENT_ONLY,
        NOT_LOGGED_IN,
        DB_OFFLINE
    }

    /**
     * DTO containing event registration status for the UI.
     */
    public static class EventStatusInfo {
        private final Eligibility eligibility;
        private final String registrationStatus; // "PENDING", "APPROVED", "REJECTED", or null
        private final int registrationId;
        private final String formattedRegId;
        private final LocalDate registrationDate;
        private final int occupiedSpots;
        private final int maxParticipants;
        private final String message;

        public EventStatusInfo(Eligibility eligibility, String registrationStatus, int registrationId,
                               LocalDate registrationDate, int occupiedSpots, int maxParticipants, String message) {
            this.eligibility = eligibility;
            this.registrationStatus = registrationStatus;
            this.registrationId = registrationId;
            this.formattedRegId = (registrationId > 0) ? String.format("REG-%06d", registrationId) : null;
            this.registrationDate = registrationDate;
            this.occupiedSpots = occupiedSpots;
            this.maxParticipants = maxParticipants;
            this.message = message;
        }

        public Eligibility getEligibility() {
            return eligibility;
        }

        public String getRegistrationStatus() {
            return registrationStatus;
        }

        public int getRegistrationId() {
            return registrationId;
        }

        public String getFormattedRegId() {
            return formattedRegId;
        }

        public LocalDate getRegistrationDate() {
            return registrationDate;
        }

        public int getOccupiedSpots() {
            return occupiedSpots;
        }

        public int getMaxParticipants() {
            return maxParticipants;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Result of an event registration attempt.
     */
    public static class RegistrationResult {
        private final boolean success;
        private final String message;
        private final int registrationId;
        private final String formattedRegId;
        private final String status;
        private final LocalDate registrationDate;
        private final Event event;

        private RegistrationResult(boolean success, String message, int registrationId,
                                   String status, LocalDate registrationDate, Event event) {
            this.success = success;
            this.message = message;
            this.registrationId = registrationId;
            this.formattedRegId = (registrationId > 0) ? String.format("REG-%06d", registrationId) : "";
            this.status = status;
            this.registrationDate = registrationDate;
            this.event = event;
        }

        public static RegistrationResult success(int registrationId, Event event, LocalDate date, String status) {
            return new RegistrationResult(true, "Registration successful!", registrationId, status, date, event);
        }

        public static RegistrationResult failure(String message) {
            return new RegistrationResult(false, message, 0, null, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public int getRegistrationId() {
            return registrationId;
        }

        public String getFormattedRegId() {
            return formattedRegId;
        }

        public String getStatus() {
            return status;
        }

        public LocalDate getRegistrationDate() {
            return registrationDate;
        }

        public Event getEvent() {
            return event;
        }
    }

    /**
     * Evaluates current registration eligibility and details for a given event.
     */
    public EventStatusInfo getEventStatusInfo(Event event) {
        if (!Session.isLoggedIn() || Session.getCurrentUser() == null) {
            return new EventStatusInfo(Eligibility.NOT_LOGGED_IN, null, 0, null, 0,
                    event != null ? event.getMaxParticipants() : 0, "Please log in to register.");
        }

        User user = Session.getCurrentUser();
        String role = user.getRole() != null ? user.getRole().toUpperCase() : "";
        if (!"STUDENT".equals(role)) {
            return new EventStatusInfo(Eligibility.STUDENT_ONLY, null, 0, null, 0,
                    event != null ? event.getMaxParticipants() : 0, "Registration is available to students.");
        }

        if (event == null) {
            return new EventStatusInfo(Eligibility.AVAILABLE, null, 0, null, 0, 0, "Event data unavailable.");
        }

        // Check deadline first (in-memory on event)
        LocalDate today = LocalDate.now();
        if (event.getRegistrationDeadline() != null && today.isAfter(event.getRegistrationDeadline())) {
            return new EventStatusInfo(Eligibility.DEADLINE_PASSED, null, 0, null, 0, event.getMaxParticipants(),
                    "Registration has closed for this event.");
        }

        // Test database availability
        if (!DBConnection.testConnection()) {
            return new EventStatusInfo(Eligibility.DB_OFFLINE, null, 0, null, 0, event.getMaxParticipants(),
                    "Unable to verify registration status. Database is currently offline.");
        }

        // Check if student is already registered
        Registration existing = registrationDAO.findByEventAndStudent(event.getEventId(), user.getUserId());
        if (existing != null) {
            String status = existing.getStatus() != null ? existing.getStatus().toUpperCase() : "PENDING";
            String msg = "Status: " + status;
            return new EventStatusInfo(Eligibility.ALREADY_REGISTERED, status, existing.getRegistrationId(),
                    existing.getRegistrationDate(), 0, event.getMaxParticipants(), msg);
        }

        // Check capacity
        int activeSlots = registrationDAO.countActiveForEvent(event.getEventId());
        if (event.getMaxParticipants() > 0 && activeSlots >= event.getMaxParticipants()) {
            return new EventStatusInfo(Eligibility.EVENT_FULL, null, 0, null, activeSlots, event.getMaxParticipants(),
                    "This event has reached its registration limit.");
        }

        int remaining = Math.max(0, event.getMaxParticipants() - activeSlots);
        String msg = (event.getMaxParticipants() > 0)
                ? remaining + " spots remaining out of " + event.getMaxParticipants()
                : "Open admission";

        return new EventStatusInfo(Eligibility.AVAILABLE, null, 0, null, activeSlots, event.getMaxParticipants(), msg);
    }

    /**
     * Executes the registration pipeline for the currently logged-in student.
     */
    public RegistrationResult registerCurrentStudent(Event event) {
        // CHECK 1: User is logged in
        if (!Session.isLoggedIn() || Session.getCurrentUser() == null) {
            return RegistrationResult.failure("Please log in as a student to register.");
        }

        // CHECK 2: User role is STUDENT
        User user = Session.getCurrentUser();
        String role = user.getRole() != null ? user.getRole().toUpperCase() : "";
        if (!"STUDENT".equals(role)) {
            return RegistrationResult.failure("Registration is available to students.");
        }

        // CHECK 3: Event exists
        if (event == null || event.getEventId() <= 0) {
            return RegistrationResult.failure("Selected event is invalid or does not exist.");
        }

        // CHECK 4: Event registration deadline has not passed
        LocalDate today = LocalDate.now();
        if (event.getRegistrationDeadline() != null && today.isAfter(event.getRegistrationDeadline())) {
            return RegistrationResult.failure("Registration has closed for this event.");
        }

        // Database connectivity check
        if (!DBConnection.testConnection()) {
            return RegistrationResult.failure("Unable to register at this time. Please check your database connection.");
        }

        // CHECK 5: Student is not already registered for this event
        if (registrationDAO.isRegistered(event.getEventId(), user.getUserId())) {
            return RegistrationResult.failure("You are already registered for this event.");
        }

        // CHECK 6: Event has available capacity
        if (event.getMaxParticipants() > 0) {
            int occupied = registrationDAO.countActiveForEvent(event.getEventId());
            if (occupied >= event.getMaxParticipants()) {
                return RegistrationResult.failure("This event has reached its registration limit.");
            }
        }

        // CHECK 7: Insert registration atomically & retrieve generated ID
        try {
            int regId = registrationDAO.registerStudent(event.getEventId(), user.getUserId());
            return RegistrationResult.success(regId, event, today, "PENDING");
        } catch (SQLException e) {
            System.err.println("[RegistrationService Error] Failed to register: " + e.getMessage());

            // Handle duplicate-key constraint violation gracefully
            String sqlState = e.getSQLState();
            int errorCode = e.getErrorCode();
            String msg = (e.getMessage() != null) ? e.getMessage().toLowerCase() : "";

            if (errorCode == 1062 || (sqlState != null && sqlState.startsWith("23")) || msg.contains("duplicate")) {
                return RegistrationResult.failure("You are already registered for this event.");
            }

            return RegistrationResult.failure("Unable to complete registration. Please check your database connection.");
        } catch (Exception e) {
            System.err.println("[RegistrationService Error] Unexpected failure: " + e.getMessage());
            return RegistrationResult.failure("An unexpected error occurred during registration. Please try again.");
        }
    }
}
