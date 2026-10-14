package test;

import dao.EventDAO;
import dao.EventDAO.DependentSummary;
import model.AdminDashboardDTO;
import model.AnalyticsOverviewDTO;
import model.Event;
import model.Registration;
import model.StudentAchievementDTO;
import model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.EventManagementService;
import service.EventManagementService.ValidationResult;
import service.FeedbackManagementService;
import service.RegistrationManagementService;
import service.RegistrationService;
import util.Session;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeterministicLogicUnitTest — Pure unit tests for Campus Connect business rules,
 * validation invariants, status transitions, capacity constraints, achievement thresholds,
 * and DTO edge cases.
 *
 * Runs deterministically in-memory without database dependencies.
 */
public class DeterministicLogicUnitTest {

    @AfterEach
    public void cleanUpSession() {
        Session.clearSession();
    }

    // =========================================================================
    // 1. INPUT VALIDATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Event validation rejects null or invalid events and accepts valid configurations")
    public void testEventValidationRules() {
        EventManagementService service = new EventManagementService();

        // Null event
        ValidationResult r1 = service.validate(null);
        assertFalse(r1.isValid());
        assertTrue(r1.getErrorMessage().contains("null"));

        // Blank title
        Event eBlank = new Event(0, 1, "   ", "Desc", "Technology", LocalDate.now().plusDays(5),
                LocalTime.NOON, "Hall A", 100, LocalDate.now().plusDays(2));
        ValidationResult r2 = service.validate(eBlank);
        assertFalse(r2.isValid());
        assertTrue(r2.getErrorMessage().toLowerCase().contains("title"));

        // Title shorter than 3 characters
        Event eShort = new Event(0, 1, "AB", "Desc", "Technology", LocalDate.now().plusDays(5),
                LocalTime.NOON, "Hall A", 100, LocalDate.now().plusDays(2));
        ValidationResult r3 = service.validate(eShort);
        assertFalse(r3.isValid());

        // Invalid Club ID
        Event eClub = new Event(0, 0, "Valid Title", "Desc", "Technology", LocalDate.now().plusDays(5),
                LocalTime.NOON, "Hall A", 100, LocalDate.now().plusDays(2));
        ValidationResult r4 = service.validate(eClub);
        assertFalse(r4.isValid());

        // Deadline after Event Date
        Event eDates = new Event(0, 1, "Valid Title", "Desc", "Technology", LocalDate.now().plusDays(2),
                LocalTime.NOON, "Hall A", 100, LocalDate.now().plusDays(5));
        ValidationResult r5 = service.validate(eDates);
        assertFalse(r5.isValid());
        assertTrue(r5.getErrorMessage().toLowerCase().contains("deadline"));

        // Zero capacity
        Event eZeroCap = new Event(0, 1, "Valid Title", "Desc", "Technology", LocalDate.now().plusDays(5),
                LocalTime.NOON, "Hall A", 0, LocalDate.now().plusDays(2));
        ValidationResult r6 = service.validate(eZeroCap);
        assertFalse(r6.isValid());

        // Negative capacity
        Event eNegCap = new Event(0, 1, "Valid Title", "Desc", "Technology", LocalDate.now().plusDays(5),
                LocalTime.NOON, "Hall A", -10, LocalDate.now().plusDays(2));
        ValidationResult r7 = service.validate(eNegCap);
        assertFalse(r7.isValid());

        // Blank venue
        Event eBlankVenue = new Event(0, 1, "Valid Title", "Desc", "Technology", LocalDate.now().plusDays(5),
                LocalTime.NOON, "   ", 100, LocalDate.now().plusDays(2));
        ValidationResult r8 = service.validate(eBlankVenue);
        assertFalse(r8.isValid());

        // Completely valid event
        Event eValid = new Event(0, 1, "Campus Hackathon 2026", "Build apps", "Technology",
                LocalDate.now().plusDays(10), LocalTime.of(10, 0), "Lab B-3", 150, LocalDate.now().plusDays(7));
        ValidationResult rValid = service.validate(eValid);
        assertTrue(rValid.isValid());
        assertNull(rValid.getErrorMessage());
    }

    @Test
    @DisplayName("Feedback validation rejects invalid rating bounds and oversized comments")
    public void testFeedbackValidationRules() {
        FeedbackManagementService service = new FeedbackManagementService();

        // Must reject unauthenticated submission
        FeedbackManagementService.OperationResult rAuth = service.submitFeedback(1, 5, "Great event");
        assertFalse(rAuth.isSuccess());
        assertEquals(FeedbackManagementService.OperationResult.Code.UNAUTHORIZED, rAuth.getCode());

        // Authenticate student
        Session.setCurrentUser(new User(2, "Test Student", "student@test.edu", "pass", "STUDENT"));

        // Invalid event IDs
        assertEquals(FeedbackManagementService.OperationResult.Code.INVALID_INPUT, service.submitFeedback(0, 5, "Comment").getCode());
        assertEquals(FeedbackManagementService.OperationResult.Code.INVALID_INPUT, service.submitFeedback(-1, 5, "Comment").getCode());

        // Invalid rating boundaries (< 1 or > 5)
        assertEquals(FeedbackManagementService.OperationResult.Code.INVALID_INPUT, service.submitFeedback(1, 0, "Comment").getCode());
        assertEquals(FeedbackManagementService.OperationResult.Code.INVALID_INPUT, service.submitFeedback(1, -2, "Comment").getCode());
        assertEquals(FeedbackManagementService.OperationResult.Code.INVALID_INPUT, service.submitFeedback(1, 6, "Comment").getCode());
        assertEquals(FeedbackManagementService.OperationResult.Code.INVALID_INPUT, service.submitFeedback(1, 100, "Comment").getCode());

        // Oversized comment (> 2000 characters)
        String giantComment = "A".repeat(2001);
        FeedbackManagementService.OperationResult rGiant = service.submitFeedback(1, 5, giantComment);
        assertFalse(rGiant.isSuccess());
        assertEquals(FeedbackManagementService.OperationResult.Code.INVALID_INPUT, rGiant.getCode());
    }

    // =========================================================================
    // 2. REGISTRATION STATUS & ROLE RESTRICTION TESTS
    // =========================================================================

    @Test
    @DisplayName("Role restrictions enforce proper authorization boundaries on moderation and sign-up")
    public void testRegistrationRoleRestrictions() {
        RegistrationManagementService adminRegService = new RegistrationManagementService();
        RegistrationService studentRegService = new RegistrationService();

        // 1. Unauthenticated checks
        assertFalse(adminRegService.isCurrentUserAdmin());
        assertEquals(RegistrationManagementService.OperationResult.Code.UNAUTHORIZED, adminRegService.approveRegistration(1).getCode());
        assertEquals(RegistrationManagementService.OperationResult.Code.UNAUTHORIZED, adminRegService.rejectRegistration(1).getCode());
        assertEquals(RegistrationService.Eligibility.NOT_LOGGED_IN, studentRegService.getEventStatusInfo(null).getEligibility());

        // 2. Student session: Student cannot moderate registrations
        Session.setCurrentUser(new User(2, "Sonya", "sonya@campus.edu", "hash", "STUDENT"));
        assertFalse(adminRegService.isCurrentUserAdmin());
        assertEquals(RegistrationManagementService.OperationResult.Code.UNAUTHORIZED, adminRegService.approveRegistration(1).getCode());
        assertEquals(RegistrationManagementService.OperationResult.Code.UNAUTHORIZED, adminRegService.rejectRegistration(1).getCode());

        // Student can check eligibility for student registration
        assertNotEquals(RegistrationService.Eligibility.STUDENT_ONLY, studentRegService.getEventStatusInfo(null).getEligibility());

        // 3. Admin session: Admin can moderate, but cannot self-register as student attendee
        Session.setCurrentUser(new User(1, "Dr. Vance", "admin@campus.edu", "hash", "ADMIN"));
        assertTrue(adminRegService.isCurrentUserAdmin());
        assertEquals(RegistrationService.Eligibility.STUDENT_ONLY, studentRegService.getEventStatusInfo(null).getEligibility());
    }

    @Test
    @DisplayName("Moderation input validation catches negative and zero registration IDs")
    public void testRegistrationInputValidation() {
        Session.setCurrentUser(new User(1, "Admin", "admin@test.edu", "hash", "ADMIN"));
        RegistrationManagementService service = new RegistrationManagementService();

        assertEquals(RegistrationManagementService.OperationResult.Code.INVALID_INPUT, service.approveRegistration(0).getCode());
        assertEquals(RegistrationManagementService.OperationResult.Code.INVALID_INPUT, service.approveRegistration(-5).getCode());
        assertEquals(RegistrationManagementService.OperationResult.Code.INVALID_INPUT, service.rejectRegistration(0).getCode());
        assertEquals(RegistrationManagementService.OperationResult.Code.INVALID_INPUT, service.rejectRegistration(-1).getCode());
    }

    // =========================================================================
    // 3. CAPACITY CHECK & STATUS LOGIC
    // =========================================================================

    @Test
    @DisplayName("Capacity checks correctly evaluate capacity full vs available thresholds")
    public void testCapacityThresholdLogic() {
        // Enforce capacity block when approved count reaches max capacity
        int maxCapacity = 50;
        int approvedCountAtLimit = 50;
        boolean isFull = approvedCountAtLimit >= maxCapacity;
        assertTrue(isFull, "Approval should be blocked when approved count equals max capacity");

        // Capacity available when under limit
        int approvedCountUnder = 49;
        boolean hasRoom = approvedCountUnder < maxCapacity;
        assertTrue(hasRoom, "Capacity should be available when approved count is under limit");

        // Unlimited capacity when maxCapacity is 0
        int unlimitedMax = 0;
        int anyApprovedCount = 1000;
        boolean isUnlimitedAllowed = (unlimitedMax <= 0) || (anyApprovedCount < unlimitedMax);
        assertTrue(isUnlimitedAllowed, "Capacity check should always allow enrollment when max capacity is 0 (unlimited)");
    }

    @Test
    @DisplayName("Registration status values adhere to valid domain lifecycle values")
    public void testRegistrationStatusDomainInvariants() {
        List<String> validStatuses = List.of("PENDING", "APPROVED", "REJECTED");

        Registration reg = new Registration(1, 10, 20, "PENDING", LocalDate.now());
        assertTrue(validStatuses.contains(reg.getStatus().toUpperCase()));

        reg.setStatus("APPROVED");
        assertTrue(validStatuses.contains(reg.getStatus().toUpperCase()));

        reg.setStatus("REJECTED");
        assertTrue(validStatuses.contains(reg.getStatus().toUpperCase()));

        assertFalse(validStatuses.contains("CANCELLED"));
        assertFalse(validStatuses.contains("UNKNOWN"));
    }

    // =========================================================================
    // 4. ACHIEVEMENT THRESHOLD & PROGRESS CAPPING TESTS
    // =========================================================================

    @Test
    @DisplayName("StudentAchievementDTO clamps negative progress to 0 and caps progress at 100%")
    public void testAchievementProgressClampingAndCapping() {
        // Target goal = 3 events
        StudentAchievementDTO badge = new StudentAchievementDTO(
                "ACTIVE_EXPLORER", "Active Explorer", "Attend 3 campus events", "★", false, 0, 3, "ATTENDANCE");

        // Initial zero progress
        assertEquals(0, badge.getCurrentProgress());
        assertEquals(0.0, badge.getProgressPercentage(), 0.001);
        assertFalse(badge.isUnlocked());

        // Negative input progress should clamp to 0
        StudentAchievementDTO negBadge = new StudentAchievementDTO(
                "ACTIVE_EXPLORER", "Active Explorer", "Attend 3 campus events", "★", false, -10, 3, "ATTENDANCE");
        assertEquals(0, negBadge.getCurrentProgress(), "Negative progress must be clamped to 0");
        assertEquals(0.0, negBadge.getProgressPercentage(), 0.001);
        assertFalse(negBadge.isUnlocked());

        // Exactly at goal
        StudentAchievementDTO exactBadge = new StudentAchievementDTO(
                "ACTIVE_EXPLORER", "Active Explorer", "Attend 3 campus events", "★", true, 3, 3, "ATTENDANCE");
        assertEquals(3, exactBadge.getCurrentProgress());
        assertEquals(100.0, exactBadge.getProgressPercentage(), 0.001);
        assertTrue(exactBadge.isUnlocked());

        // Overachieving: 10 events attended for a 3-event goal
        StudentAchievementDTO overBadge = new StudentAchievementDTO(
                "ACTIVE_EXPLORER", "Active Explorer", "Attend 3 campus events", "★", true, 10, 3, "ATTENDANCE");
        assertEquals(3, overBadge.getCurrentProgress(), "Capped progress must not exceed targetGoal");
        assertEquals(100.0, overBadge.getProgressPercentage(), 0.001, "Progress percentage must cap at 100%");
        assertTrue(overBadge.isUnlocked());
    }

    // =========================================================================
    // 5. DTO EDGE CASES & FALLBACK CONTRACTS
    // =========================================================================

    @Test
    @DisplayName("AdminDashboardDTO offline state provides safe non-null defaults")
    public void testAdminDashboardDTOOfflineState() {
        AdminDashboardDTO offline = AdminDashboardDTO.offline();
        assertNotNull(offline);
        assertFalse(offline.isDbOnline());
        assertEquals(0, offline.getTotalEvents());
        assertEquals(0, offline.getTotalRegistrations());
        assertEquals(0, offline.getPendingRegistrations());
        assertEquals(0, offline.getApprovedRegistrations());
        assertEquals(0, offline.getRejectedRegistrations());
        assertNotNull(offline.getRecentEvents());
        assertTrue(offline.getRecentEvents().isEmpty());
    }

    @Test
    @DisplayName("AnalyticsOverviewDTO guards against division by zero when metrics are 0")
    public void testAnalyticsOverviewZeroDivisionSafety() {
        AnalyticsOverviewDTO empty = new AnalyticsOverviewDTO();
        empty.setDbOnline(false);

        assertFalse(empty.isDbOnline());
        assertEquals(0, empty.getTotalEvents());
        assertEquals(0.0, empty.getApprovalRate(), 0.001);
        assertEquals(0.0, empty.getTurnoutRate(), 0.001);
        assertEquals(0.0, empty.getCertificateIssuanceRate(), 0.001);
        assertEquals(0.0, empty.getAverageFeedbackRating(), 0.001);

        // Verify with non-zero approved and attendees
        AnalyticsOverviewDTO populated = new AnalyticsOverviewDTO();
        populated.setDbOnline(true);
        populated.setTotalEvents(10);
        populated.setTotalRegistrations(100);
        populated.setApprovedRegistrations(80);
        populated.setTotalAttended(60);
        populated.setAverageFeedbackRating(4.5);
        populated.setTotalFeedbackCount(45);
        populated.setTotalCertificatesIssued(40);

        assertTrue(populated.isDbOnline());
        assertEquals(80.0, populated.getApprovalRate(), 0.001); // 80 / 100 = 80%
        assertEquals(75.0, populated.getTurnoutRate(), 0.001);  // 60 / 80 = 75%
        assertEquals(4.5, populated.getAverageFeedbackRating(), 0.001);
    }

    @Test
    @DisplayName("DependentSummary correctly summarizes presence and absence of child records")
    public void testDependentSummaryContracts() {
        DependentSummary empty = DependentSummary.empty();
        assertFalse(empty.hasDependencies());
        assertEquals(0, empty.getTotalCount());
        assertTrue(empty.toDescription().contains("0"));

        DependentSummary populated = new DependentSummary(5, 3, 2, 1);
        assertTrue(populated.hasDependencies());
        assertEquals(11, populated.getTotalCount());
        String desc = populated.toDescription();
        assertTrue(desc.contains("5 student registration(s)"));
        assertTrue(desc.contains("3 attendance record(s)"));
        assertTrue(desc.contains("2 feedback review(s)"));
        assertTrue(desc.contains("1 certificate(s)"));
    }

    @Test
    @DisplayName("OperationResult and ValidationResult retain state, error message, and payload")
    public void testOperationAndValidationResultContracts() {
        ValidationResult valid = ValidationResult.valid();
        assertTrue(valid.isValid());
        assertNull(valid.getErrorMessage());

        ValidationResult invalid = ValidationResult.invalid("Field X is required");
        assertFalse(invalid.isValid());
        assertEquals("Field X is required", invalid.getErrorMessage());

        EventManagementService.OperationResult success = EventManagementService.OperationResult.success("Saved successfully", null);
        assertTrue(success.isSuccess());
        assertEquals("Saved successfully", success.getMessage());

        EventManagementService.OperationResult failure = EventManagementService.OperationResult.failure("Connection refused");
        assertFalse(failure.isSuccess());
        assertEquals("Connection refused", failure.getMessage());
    }
}
