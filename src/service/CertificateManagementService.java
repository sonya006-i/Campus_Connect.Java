package service;

import dao.CertificateDAO;
import dao.EventDAO;
import model.AdminCertificateParticipantDTO;
import model.Event;
import model.StudentCertificateDTO;
import model.User;
import util.DBConnection;
import util.Session;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * CertificateManagementService — Coordinates student certificate retrieval,
 * administrator certificate verification, and atomic credential issuance for Phase 12.
 *
 * Implements Phase 12: Certificate Management per AGENTS.md:
 * - Session role-based authorization for students and administrators
 * - Derives student identity exclusively from authenticated Session
 * - Enforces strict eligibility: requires APPROVED registration + PRESENT attendance
 * - Concurrency and duplicate constraint safety via CertificateDAO
 * - Real-time issuance summaries and batch issuance workflows
 */
public class CertificateManagementService {

    private final CertificateDAO certificateDAO;
    private final EventDAO eventDAO;

    public CertificateManagementService() {
        this.certificateDAO = new CertificateDAO();
        this.eventDAO = new EventDAO();
    }

    public CertificateManagementService(CertificateDAO certificateDAO, EventDAO eventDAO) {
        this.certificateDAO = certificateDAO;
        this.eventDAO = eventDAO;
    }

    /**
     * DTO containing event certificate issuance metrics for the admin KPI summary.
     */
    public static class EventCertificateSummaryDTO {
        private final int totalParticipants;
        private final int eligibleParticipants;
        private final int issuedCertificates;
        private final int awaitingIssuance;
        private final int issuanceRatePercent;
        private final boolean dbOnline;

        public EventCertificateSummaryDTO(int totalParticipants, int eligibleParticipants,
                                          int issuedCertificates, int awaitingIssuance,
                                          int issuanceRatePercent, boolean dbOnline) {
            this.totalParticipants = totalParticipants;
            this.eligibleParticipants = eligibleParticipants;
            this.issuedCertificates = issuedCertificates;
            this.awaitingIssuance = awaitingIssuance;
            this.issuanceRatePercent = issuanceRatePercent;
            this.dbOnline = dbOnline;
        }

        public static EventCertificateSummaryDTO offline() {
            return new EventCertificateSummaryDTO(0, 0, 0, 0, 0, false);
        }

        public int getTotalParticipants() {
            return totalParticipants;
        }

        public int getEligibleParticipants() {
            return eligibleParticipants;
        }

        public int getApprovedPresentCount() {
            return eligibleParticipants;
        }

        public int getIssuedCertificates() {
            return issuedCertificates;
        }

        public int getIssuedCount() {
            return issuedCertificates;
        }

        public int getAwaitingIssuance() {
            return awaitingIssuance;
        }

        public int getEligibleUnissuedCount() {
            return awaitingIssuance;
        }

        public int getIssuanceRatePercent() {
            return issuanceRatePercent;
        }

        public boolean isDbOnline() {
            return dbOnline;
        }
    }

    public static class CertificateSummaryDTO extends EventCertificateSummaryDTO {
        public CertificateSummaryDTO(int totalParticipants, int eligibleParticipants,
                                     int issuedCertificates, int awaitingIssuance,
                                     int issuanceRatePercent, boolean dbOnline) {
            super(totalParticipants, eligibleParticipants, issuedCertificates, awaitingIssuance, issuanceRatePercent, dbOnline);
        }
    }

    /**
     * DTO representing the outcome of a certificate issuance operation.
     */
    public static class OperationResult {
        public enum Code {
            SUCCESS,
            UNAUTHORIZED,
            INVALID_INPUT,
            NOT_ELIGIBLE,
            ALREADY_ISSUED,
            NOT_FOUND,
            DATABASE_ERROR
        }

        private final boolean success;
        private final Code code;
        private final String message;
        private final int certificateId;

        public OperationResult(boolean success, Code code, String message, int certificateId) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.certificateId = certificateId;
        }

        public static OperationResult success(String message, int certificateId) {
            return new OperationResult(true, Code.SUCCESS, message, certificateId);
        }

        public static OperationResult unauthorized(String message) {
            return new OperationResult(false, Code.UNAUTHORIZED, message, -1);
        }

        public static OperationResult invalidInput(String message) {
            return new OperationResult(false, Code.INVALID_INPUT, message, -1);
        }

        public static OperationResult notEligible(String message) {
            return new OperationResult(false, Code.NOT_ELIGIBLE, message, -1);
        }

        public static OperationResult alreadyIssued(String message) {
            return new OperationResult(false, Code.ALREADY_ISSUED, message, -1);
        }

        public static OperationResult notFound(String message) {
            return new OperationResult(false, Code.NOT_FOUND, message, -1);
        }

        public static OperationResult dbError(String message) {
            return new OperationResult(false, Code.DATABASE_ERROR, message, -1);
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

        public int getCertificateId() {
            return certificateId;
        }
    }

    /**
     * DTO representing the outcome of bulk certificate issuance for an event.
     */
    public static class BatchIssuanceResult {
        private final int totalIssued;
        private final int alreadyIssuedSkipped;
        private final int ineligibleSkipped;
        private final String message;

        public BatchIssuanceResult(int totalIssued, int alreadyIssuedSkipped, int ineligibleSkipped, String message) {
            this.totalIssued = totalIssued;
            this.alreadyIssuedSkipped = alreadyIssuedSkipped;
            this.ineligibleSkipped = ineligibleSkipped;
            this.message = message;
        }

        public int getTotalIssued() {
            return totalIssued;
        }

        public int getAlreadyIssuedSkipped() {
            return alreadyIssuedSkipped;
        }

        public int getIneligibleSkipped() {
            return ineligibleSkipped;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return totalIssued > 0 || (alreadyIssuedSkipped > 0 && ineligibleSkipped == 0);
        }
    }

    public static class BulkIssuanceResult extends BatchIssuanceResult {
        public BulkIssuanceResult(int totalIssued, int alreadyIssuedSkipped, int ineligibleSkipped, String message) {
            super(totalIssued, alreadyIssuedSkipped, ineligibleSkipped, message);
        }
    }

    /**
     * Checks if the active session is an authenticated Administrator.
     */
    public boolean isCurrentUserAdmin() {
        User user = Session.getCurrentUser();
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    /**
     * Checks if the active session is an authenticated Student.
     */
    public boolean isCurrentUserStudent() {
        User user = Session.getCurrentUser();
        return user != null && "STUDENT".equalsIgnoreCase(user.getRole());
    }

    // =========================================================================
    // STUDENT CERTIFICATE OPERATIONS
    // =========================================================================

    /**
     * Retrieves all certificates issued to the currently authenticated student.
     * Identity is strictly derived from the session, never trusting client parameters.
     */
    public List<StudentCertificateDTO> getMyCertificates() {
        if (!isCurrentUserStudent()) {
            System.err.println("[CertificateManagementService] Access denied: student credentials required.");
            return Collections.emptyList();
        }

        User currentStudent = Session.getCurrentUser();
        if (currentStudent == null || !DBConnection.testConnection()) {
            return Collections.emptyList();
        }

        return certificateDAO.getCertificatesForStudent(currentStudent.getUserId());
    }

    /**
     * Retrieves details for a specific certificate for preview, verifying ownership if called by a student.
     */
    public StudentCertificateDTO getCertificatePreview(int certificateId) {
        if (!Session.isLoggedIn()) {
            return null;
        }

        StudentCertificateDTO cert = certificateDAO.getCertificateDetailsById(certificateId);
        if (cert == null) {
            return null;
        }

        User user = Session.getCurrentUser();
        if ("STUDENT".equalsIgnoreCase(user.getRole()) && cert.getStudentId() != user.getUserId()) {
            System.err.println("[CertificateManagementService] Security violation: student cannot view another student's certificate.");
            return null;
        }

        return cert;
    }

    // =========================================================================
    // ADMIN CERTIFICATE OPERATIONS
    // =========================================================================

    /**
     * Retrieves all campus events for the event selector dropdown.
     */
    public List<Event> getAllEvents() {
        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }
        return eventDAO.getAll();
    }

    /**
     * Retrieves participant roster and certificate issuance status for an event.
     */
    public List<AdminCertificateParticipantDTO> getParticipantRoster(int eventId) {
        if (!isCurrentUserAdmin()) {
            System.err.println("[CertificateManagementService] Access denied: admin authorization required.");
            return Collections.emptyList();
        }

        if (eventId <= 0 || !DBConnection.testConnection()) {
            return Collections.emptyList();
        }

        return certificateDAO.getParticipantCertificateRoster(eventId);
    }

    /**
     * Computes the live certificate summary metrics for an event.
     */
    public EventCertificateSummaryDTO getEventCertificateSummary(int eventId) {
        if (eventId <= 0 || !DBConnection.testConnection()) {
            return EventCertificateSummaryDTO.offline();
        }

        try {
            List<AdminCertificateParticipantDTO> roster = certificateDAO.getParticipantCertificateRoster(eventId);
            int totalParticipants = roster.size();
            int eligibleParticipants = 0;
            int issuedCertificates = 0;

            for (AdminCertificateParticipantDTO p : roster) {
                if (p.isCertificateIssued()) {
                    issuedCertificates++;
                } else if (p.isApproved() && p.isAttended()) {
                    eligibleParticipants++;
                }
            }

            int awaitingIssuance = eligibleParticipants;
            int totalEligibleOrIssued = issuedCertificates + awaitingIssuance;
            int ratePercent = (totalEligibleOrIssued > 0)
                    ? (issuedCertificates * 100 / totalEligibleOrIssued)
                    : 0;

            return new EventCertificateSummaryDTO(
                    totalParticipants, totalEligibleOrIssued, issuedCertificates,
                    awaitingIssuance, ratePercent, true
            );
        } catch (Exception e) {
            System.err.println("[CertificateManagementService Error] Failed to aggregate summary: " + e.getMessage());
            return EventCertificateSummaryDTO.offline();
        }
    }

    /**
     * Issues an official certificate to an eligible participant.
     * Enforces that the participant must have an APPROVED registration and PRESENT attendance,
     * and prevents duplicate certificate generation.
     */
    public OperationResult issueCertificate(int eventId, int studentId) {
        // 1. Role verification
        if (!isCurrentUserAdmin()) {
            return OperationResult.unauthorized("Access denied. Only administrators may issue event certificates.");
        }

        // 2. Input validation
        if (eventId <= 0 || studentId <= 0) {
            return OperationResult.invalidInput("Invalid event or student ID.");
        }

        // 3. Database connection check
        if (!DBConnection.testConnection()) {
            return OperationResult.dbError("Database connection is currently offline.");
        }

        // 4. Duplicate prevention check
        if (certificateDAO.isCertificateIssued(eventId, studentId)) {
            return OperationResult.alreadyIssued("A certificate has already been issued to this student for this event.");
        }

        // 5. Re-check eligibility in service layer
        if (!certificateDAO.isEligibleForCertificate(eventId, studentId)) {
            return OperationResult.notEligible(
                    "Student is ineligible for a certificate. Requires an APPROVED registration and recorded PRESENT attendance."
            );
        }

        // 6. Generate canonical path and issue certificate
        String canonicalPath = "/certificates/cert_event" + eventId + "_user" + studentId + ".pdf";
        LocalDate issueDate = LocalDate.now();

        int generatedId = certificateDAO.issueCertificate(eventId, studentId, canonicalPath, issueDate);

        if (generatedId == -2) {
            return OperationResult.alreadyIssued("A certificate has already been issued to this student for this event.");
        } else if (generatedId > 0) {
            String formattedId = String.format("CERT-%06d", generatedId);
            return OperationResult.success("Certificate " + formattedId + " successfully issued.", generatedId);
        } else {
            return OperationResult.dbError("Database error occurred while issuing certificate.");
        }
    }

    /**
     * Issues certificates in bulk to all currently eligible participants for an event.
     * Skips participants who already have a certificate or are ineligible.
     */
    public BatchIssuanceResult issueAllEligibleCertificates(int eventId) {
        if (!isCurrentUserAdmin()) {
            return new BatchIssuanceResult(0, 0, 0, "Access denied. Administrator privileges required.");
        }

        if (eventId <= 0) {
            return new BatchIssuanceResult(0, 0, 0, "Invalid event ID.");
        }

        if (!DBConnection.testConnection()) {
            return new BatchIssuanceResult(0, 0, 0, "Database connection is currently offline.");
        }

        List<AdminCertificateParticipantDTO> roster = certificateDAO.getParticipantCertificateRoster(eventId);
        if (roster.isEmpty()) {
            return new BatchIssuanceResult(0, 0, 0, "No participants found for the selected event.");
        }

        int issuedCount = 0;
        int alreadyIssuedCount = 0;
        int ineligibleCount = 0;

        for (AdminCertificateParticipantDTO p : roster) {
            if (p.isCertificateIssued()) {
                alreadyIssuedCount++;
            } else if (p.isApproved() && p.isAttended()) {
                String path = "/certificates/cert_event" + eventId + "_user" + p.getStudentId() + ".pdf";
                int certId = certificateDAO.issueCertificate(eventId, p.getStudentId(), path, LocalDate.now());
                if (certId > 0) {
                    issuedCount++;
                } else if (certId == -2) {
                    alreadyIssuedCount++;
                }
            } else {
                ineligibleCount++;
            }
        }

        String summary = String.format("Issued %d certificates. Skipped %d already issued and %d ineligible participants.",
                issuedCount, alreadyIssuedCount, ineligibleCount);

        return new BatchIssuanceResult(issuedCount, alreadyIssuedCount, ineligibleCount, summary);
    }
}
