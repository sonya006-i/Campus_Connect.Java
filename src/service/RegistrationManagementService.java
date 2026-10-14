package service;

import dao.EventDAO;
import dao.RegistrationDAO;
import model.AdminRegistrationDTO;
import model.Event;
import model.User;
import util.DBConnection;
import util.Session;

import java.util.Collections;
import java.util.List;

/**
 * RegistrationManagementService — Coordinates administrator-only registration review,
 * approval, rejection, and capacity management for Phase 10.
 *
 * Implements Phase 10: Registration Management per AGENTS.md:
 * - Strict administrator role authorization checks using Session
 * - Safe status transitions (PENDING -> APPROVED, PENDING -> REJECTED)
 * - Concurrency and stale status overwrite prevention
 * - Event capacity enforcement before approving registrations
 * - Graceful handling of database offline and error states
 * - Data integrity preservation (never deletes registration records)
 */
public class RegistrationManagementService {

    private final RegistrationDAO registrationDAO;
    private final EventDAO eventDAO;

    public RegistrationManagementService() {
        this.registrationDAO = new RegistrationDAO();
        this.eventDAO = new EventDAO();
    }

    public RegistrationManagementService(RegistrationDAO registrationDAO, EventDAO eventDAO) {
        this.registrationDAO = registrationDAO;
        this.eventDAO = eventDAO;
    }

    /**
     * DTO containing registration KPI counts for the admin registration toolbar.
     */
    public static class RegistrationSummaryDTO {
        private final int total;
        private final int pending;
        private final int approved;
        private final int rejected;
        private final boolean dbOnline;

        public RegistrationSummaryDTO(int total, int pending, int approved, int rejected, boolean dbOnline) {
            this.total = total;
            this.pending = pending;
            this.approved = approved;
            this.rejected = rejected;
            this.dbOnline = dbOnline;
        }

        public static RegistrationSummaryDTO offline() {
            return new RegistrationSummaryDTO(0, 0, 0, 0, false);
        }

        public int getTotal() {
            return total;
        }

        public int getPending() {
            return pending;
        }

        public int getApproved() {
            return approved;
        }

        public int getRejected() {
            return rejected;
        }

        public boolean isDbOnline() {
            return dbOnline;
        }
    }

    /**
     * DTO representing the outcome of an administrative registration action (Approve or Reject).
     */
    public static class OperationResult {
        public enum Code {
            SUCCESS,
            CAPACITY_REACHED,
            STALE_STATUS,
            UNAUTHORIZED,
            NOT_FOUND,
            DATABASE_ERROR,
            INVALID_INPUT
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

        public static OperationResult capacityReached(String message) {
            return new OperationResult(false, Code.CAPACITY_REACHED, message);
        }

        public static OperationResult staleStatus(String message) {
            return new OperationResult(false, Code.STALE_STATUS, message);
        }

        public static OperationResult notFound(String message) {
            return new OperationResult(false, Code.NOT_FOUND, message);
        }

        public static OperationResult dbError(String message) {
            return new OperationResult(false, Code.DATABASE_ERROR, message);
        }

        public static OperationResult invalidInput(String message) {
            return new OperationResult(false, Code.INVALID_INPUT, message);
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
     * Retrieves summary KPI counts for registrations (Total, Pending, Approved, Rejected).
     */
    public RegistrationSummaryDTO getRegistrationSummary() {
        if (!DBConnection.testConnection()) {
            return RegistrationSummaryDTO.offline();
        }

        try {
            int total = registrationDAO.countAll();
            int pending = registrationDAO.countByStatus("PENDING");
            int approved = registrationDAO.countByStatus("APPROVED");
            int rejected = registrationDAO.countByStatus("REJECTED");
            return new RegistrationSummaryDTO(total, pending, approved, rejected, true);
        } catch (Exception e) {
            System.err.println("[RegistrationManagementService Error] Failed to aggregate summary: " + e.getMessage());
            return RegistrationSummaryDTO.offline();
        }
    }

    /**
     * Retrieves all events for the event filter dropdown.
     */
    public List<Event> getAllEventsForFilter() {
        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }
        return eventDAO.getAll();
    }

    /**
     * Searches and filters registrations with role protection.
     */
    public List<AdminRegistrationDTO> getRegistrations(String keyword, String status, Integer eventId) {
        if (!isCurrentUserAdmin()) {
            System.err.println("[RegistrationManagementService] Access denied: current user is not an administrator.");
            return Collections.emptyList();
        }

        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }

        return registrationDAO.searchAdminRegistrations(keyword, status, eventId);
    }

    /**
     * Approves a student registration with administrator authorization and event capacity checks.
     */
    public OperationResult approveRegistration(int registrationId) {
        // 1. Role verification
        if (!isCurrentUserAdmin()) {
            return OperationResult.unauthorized("Access denied. Only administrators may approve registrations.");
        }

        // 2. Input validation
        if (registrationId <= 0) {
            return OperationResult.invalidInput("Invalid registration ID.");
        }

        // 3. Database connection check
        if (!DBConnection.testConnection()) {
            return OperationResult.dbError("Database connection is currently offline.");
        }

        // 4. Execute atomic approval with capacity check via DAO
        RegistrationDAO.ApprovalResult daoResult = registrationDAO.approveRegistrationWithCapacityCheck(registrationId);

        if (daoResult.isSuccess()) {
            return OperationResult.success(daoResult.getMessage());
        }

        switch (daoResult.getStatus()) {
            case CAPACITY_REACHED:
                return OperationResult.capacityReached(daoResult.getMessage());
            case STALE_STATUS:
                return OperationResult.staleStatus(daoResult.getMessage());
            case NOT_FOUND:
                return OperationResult.notFound(daoResult.getMessage());
            case DATABASE_ERROR:
            default:
                return OperationResult.dbError(daoResult.getMessage());
        }
    }

    /**
     * Rejects a student registration with administrator authorization and stale status protection.
     */
    public OperationResult rejectRegistration(int registrationId) {
        // 1. Role verification
        if (!isCurrentUserAdmin()) {
            return OperationResult.unauthorized("Access denied. Only administrators may reject registrations.");
        }

        // 2. Input validation
        if (registrationId <= 0) {
            return OperationResult.invalidInput("Invalid registration ID.");
        }

        // 3. Database connection check
        if (!DBConnection.testConnection()) {
            return OperationResult.dbError("Database connection is currently offline.");
        }

        // 4. Execute atomic rejection via DAO
        RegistrationDAO.RejectionResult daoResult = registrationDAO.rejectRegistration(registrationId);

        if (daoResult.isSuccess()) {
            return OperationResult.success(daoResult.getMessage());
        }

        switch (daoResult.getStatus()) {
            case STALE_STATUS:
                return OperationResult.staleStatus(daoResult.getMessage());
            case NOT_FOUND:
                return OperationResult.notFound(daoResult.getMessage());
            case DATABASE_ERROR:
            default:
                return OperationResult.dbError(daoResult.getMessage());
        }
    }
}
