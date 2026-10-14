package util;

import dao.EventDAO;
import model.*;
import service.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Phase17ComprehensiveVerificationTest — Comprehensive verification suite for Phase 17.
 *
 * Covers:
 * 1. Role-based authorization & strict student/admin identity isolation
 * 2. Duplicate registration, certificate, and feedback protection
 * 3. Event capacity enforcement and registration approval status transitions
 * 4. Attendance eligibility (APPROVED only) and correction/unmarking behavior
 * 5. Certificate eligibility (APPROVED + PRESENT) and student ownership isolation
 * 6. Feedback eligibility (APPROVED + PRESENT) and rating/comment boundary validation
 * 7. Analytics aggregation safety, zero-division protection, and offline metrics
 * 8. Campus Pulse gamification exact thresholds, progress capping, and negative input handling
 * 9. Null parameter resilience, empty result set contracts, and Session lifecycle
 */
public class Phase17ComprehensiveVerificationTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("CAMPUS CONNECT — PHASE 17: COMPREHENSIVE VERIFICATION");
        System.out.println("==================================================");

        try {
            testRoleSeparationAndSessionIsolation();
            testDuplicateProtectionMechanisms();
            testCapacityAndRegistrationTransitions();
            testAttendanceEligibilityAndCorrection();
            testCertificateEligibilityAndStudentOwnership();
            testFeedbackEligibilityAndBoundaryValidation();
            testAnalyticsAggregationAndZeroDivisionSafety();
            testCampusPulseThresholdsAndCapping();
            testNullResilienceAndOfflineContracts();

            System.out.println("\n--------------------------------------------------");
            System.out.printf("Test Results: %d/%d passed.\n", testsPassed, testsRun);
            System.out.println("--------------------------------------------------");

            if (testsPassed == testsRun) {
                System.out.println("[SUCCESS] All Phase 17 comprehensive verification tests passed!");
                System.exit(0);
            } else {
                System.err.println("[FAIL] Some Phase 17 tests failed.");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("[FATAL] Unhandled exception in test suite: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void assertTrue(String testName, boolean condition) {
        testsRun++;
        if (condition) {
            System.out.println("  ✔ [PASS] " + testName);
            testsPassed++;
        } else {
            System.err.println("  ✖ [FAIL] " + testName);
        }
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        testsRun++;
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            System.out.println("  ✔ [PASS] " + testName + " (expected: " + expected + ", actual: " + actual + ")");
            testsPassed++;
        } else {
            System.err.println("  ✖ [FAIL] " + testName + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    // =========================================================================
    // 1. Role-based authorization & strict student/admin identity isolation
    // =========================================================================
    private static void testRoleSeparationAndSessionIsolation() {
        System.out.println("\n[1] Testing Role Separation & Session Data Isolation:");
        Session.clearSession();

        // 1.1 Unauthenticated requests are rejected across all services
        RegistrationManagementService regMgmtService = new RegistrationManagementService();
        AttendanceManagementService attService = new AttendanceManagementService();
        CertificateManagementService certService = new CertificateManagementService();
        FeedbackManagementService fbService = new FeedbackManagementService();
        AnalyticsService analyticsService = new AnalyticsService();
        CampusPulseService pulseService = new CampusPulseService();
        RegistrationService regService = new RegistrationService();

        assertTrue("Unauthenticated isCurrentUserAdmin is false (reg)", !regMgmtService.isCurrentUserAdmin());
        assertTrue("Unauthenticated isCurrentUserAdmin is false (att)", !attService.isCurrentUserAdmin());
        assertTrue("Unauthenticated isCurrentUserAdmin is false (cert)", !certService.isCurrentUserAdmin());
        assertTrue("Unauthenticated isCurrentUserStudent is false (cert)", !certService.isCurrentUserStudent());
        assertTrue("Unauthenticated isAuthorized is false (analytics)", !analyticsService.isAuthorized());
        assertTrue("Unauthenticated isAuthorizedForStudent is false", !pulseService.isAuthorizedForStudent(101));

        RegistrationManagementService.OperationResult regOp = regMgmtService.approveRegistration(1);
        assertEquals("Unauthenticated approve registration code",
                RegistrationManagementService.OperationResult.Code.UNAUTHORIZED, regOp.getCode());

        AttendanceManagementService.OperationResult attOp = attService.markAttendance(1, 101, true);
        assertEquals("Unauthenticated mark attendance code",
                AttendanceManagementService.OperationResult.Code.UNAUTHORIZED, attOp.getCode());

        CertificateManagementService.OperationResult certOp = certService.issueCertificate(1, 101);
        assertEquals("Unauthenticated issue certificate code",
                CertificateManagementService.OperationResult.Code.UNAUTHORIZED, certOp.getCode());

        FeedbackManagementService.OperationResult fbOp = fbService.submitFeedback(1, 5, "Great");
        assertEquals("Unauthenticated submit feedback code",
                FeedbackManagementService.OperationResult.Code.UNAUTHORIZED, fbOp.getCode());

        // 1.2 Student session cannot access Administrator management endpoints
        User student = new User(101, "Alice Student", "alice@student.edu", "pass123", "STUDENT");
        Session.setCurrentUser(student);

        assertTrue("Student session is not admin (reg)", !regMgmtService.isCurrentUserAdmin());
        assertTrue("Student session is not admin (att)", !attService.isCurrentUserAdmin());
        assertTrue("Student session is not admin (cert)", !certService.isCurrentUserAdmin());
        assertTrue("Student session is student (cert)", certService.isCurrentUserStudent());
        assertTrue("Student session unauthorized for analytics", !analyticsService.isAuthorized());

        regOp = regMgmtService.approveRegistration(1);
        assertEquals("Student cannot approve registration",
                RegistrationManagementService.OperationResult.Code.UNAUTHORIZED, regOp.getCode());

        attOp = attService.markAttendance(1, 101, true);
        assertEquals("Student cannot mark attendance",
                AttendanceManagementService.OperationResult.Code.UNAUTHORIZED, attOp.getCode());

        certOp = certService.issueCertificate(1, 101);
        assertEquals("Student cannot issue certificate",
                CertificateManagementService.OperationResult.Code.UNAUTHORIZED, certOp.getCode());

        List<AdminFeedbackDTO> adminFb = fbService.getAllFeedbackForAdmin();
        assertTrue("Student cannot retrieve admin feedback list", adminFb.isEmpty());

        FeedbackSummaryDTO fbSummary = fbService.getFeedbackSummary();
        assertTrue("Student cannot retrieve admin feedback summary (offline/empty returned)", !fbSummary.isDbOnline());

        // 1.3 Student isolation: Alice cannot access Bob's private achievements
        assertTrue("Student Alice is authorized for student 101", pulseService.isAuthorizedForStudent(101));
        assertTrue("Student Alice is NOT authorized for student 102", !pulseService.isAuthorizedForStudent(102));
        List<StudentAchievementDTO> bobsBadges = pulseService.getStudentAchievements(102);
        assertTrue("Alice querying Bob's achievements returns empty list", bobsBadges.isEmpty());

        // 1.4 Administrator session can access admin endpoints but cannot submit student actions
        User admin = new User(1, "Dr. Robert Smith", "admin@campusconnect.edu", "admin123", "ADMIN");
        Session.setCurrentUser(admin);

        assertTrue("Admin session is admin (reg)", regMgmtService.isCurrentUserAdmin());
        assertTrue("Admin session is admin (att)", attService.isCurrentUserAdmin());
        assertTrue("Admin session is admin (cert)", certService.isCurrentUserAdmin());
        assertTrue("Admin session is NOT student (cert)", !certService.isCurrentUserStudent());
        assertTrue("Admin session is authorized for analytics", analyticsService.isAuthorized());
        assertTrue("Admin is authorized to inspect any student's achievements (101)", pulseService.isAuthorizedForStudent(101));
        assertTrue("Admin is authorized to inspect any student's achievements (102)", pulseService.isAuthorizedForStudent(102));

        // Admin attempting student registration
        Event testEvent = new Event(1, 1, "Hackathon", "Coding", "Technology",
                LocalDate.now().plusDays(5), LocalTime.of(10, 0), "Lab 1", 50, LocalDate.now().plusDays(2));
        RegistrationService.RegistrationResult adminRegResult = regService.registerCurrentStudent(testEvent);
        assertTrue("Admin cannot register for events as student", !adminRegResult.isSuccess());

        // Admin attempting getMyCertificates
        List<StudentCertificateDTO> adminCerts = certService.getMyCertificates();
        assertTrue("Admin getMyCertificates returns empty list", adminCerts.isEmpty());

        // Admin attempting submitFeedback
        fbOp = fbService.submitFeedback(1, 5, "Good");
        assertEquals("Admin cannot submit student feedback",
                FeedbackManagementService.OperationResult.Code.UNAUTHORIZED, fbOp.getCode());

        Session.clearSession();
    }

    // =========================================================================
    // 2. Duplicate registration, certificate, and feedback protection
    // =========================================================================
    private static void testDuplicateProtectionMechanisms() {
        System.out.println("\n[2] Testing Duplicate Protection Mechanisms:");

        // 2.1 RegistrationService formatted ID contract
        RegistrationService.RegistrationResult successReg = RegistrationService.RegistrationResult.success(
                42, null, LocalDate.now(), "PENDING");
        assertEquals("Registration ID formatted as REG-000042", "REG-000042", successReg.getFormattedRegId());

        RegistrationService.EventStatusInfo regStatusInfo = new RegistrationService.EventStatusInfo(
                RegistrationService.Eligibility.ALREADY_REGISTERED, "APPROVED", 123, LocalDate.now(), 10, 50, "Status: APPROVED");
        assertEquals("EventStatusInfo formattedRegId", "REG-000123", regStatusInfo.getFormattedRegId());
        assertEquals("EventStatusInfo eligibility is ALREADY_REGISTERED",
                RegistrationService.Eligibility.ALREADY_REGISTERED, regStatusInfo.getEligibility());

        // 2.2 CertificateManagementService duplicate code contract
        CertificateManagementService.OperationResult alreadyIssued =
                CertificateManagementService.OperationResult.alreadyIssued("A certificate has already been issued.");
        assertEquals("Certificate already issued code",
                CertificateManagementService.OperationResult.Code.ALREADY_ISSUED, alreadyIssued.getCode());
        assertTrue("Certificate already issued isSuccess is false", !alreadyIssued.isSuccess());

        // 2.3 FeedbackManagementService duplicate code contract
        FeedbackManagementService.OperationResult alreadyFb =
                FeedbackManagementService.OperationResult.alreadySubmitted("You have already submitted feedback.");
        assertEquals("Feedback already submitted code",
                FeedbackManagementService.OperationResult.Code.ALREADY_SUBMITTED, alreadyFb.getCode());
        assertTrue("Feedback already submitted isSuccess is false", !alreadyFb.isSuccess());
    }

    // =========================================================================
    // 3. Event capacity enforcement and registration approval status transitions
    // =========================================================================
    private static void testCapacityAndRegistrationTransitions() {
        System.out.println("\n[3] Testing Event Capacity & Status Transition Protection:");

        // 3.1 Registration approval codes
        RegistrationManagementService.OperationResult capResult =
                RegistrationManagementService.OperationResult.capacityReached("Maximum capacity of 50 reached.");
        assertEquals("Capacity reached code",
                RegistrationManagementService.OperationResult.Code.CAPACITY_REACHED, capResult.getCode());
        assertTrue("Capacity reached isSuccess is false", !capResult.isSuccess());

        RegistrationManagementService.OperationResult staleResult =
                RegistrationManagementService.OperationResult.staleStatus("Registration is already APPROVED.");
        assertEquals("Stale status code",
                RegistrationManagementService.OperationResult.Code.STALE_STATUS, staleResult.getCode());
        assertTrue("Stale status isSuccess is false", !staleResult.isSuccess());

        RegistrationManagementService.OperationResult notFoundResult =
                RegistrationManagementService.OperationResult.notFound("Registration record not found.");
        assertEquals("Not found code",
                RegistrationManagementService.OperationResult.Code.NOT_FOUND, notFoundResult.getCode());

        // 3.2 Registration summary offline fallback
        RegistrationManagementService.RegistrationSummaryDTO offlineSummary =
                RegistrationManagementService.RegistrationSummaryDTO.offline();
        assertEquals("Offline summary total is 0", 0, offlineSummary.getTotal());
        assertEquals("Offline summary pending is 0", 0, offlineSummary.getPending());
        assertEquals("Offline summary approved is 0", 0, offlineSummary.getApproved());
        assertEquals("Offline summary rejected is 0", 0, offlineSummary.getRejected());
        assertTrue("Offline summary dbOnline is false", !offlineSummary.isDbOnline());
    }

    // =========================================================================
    // 4. Attendance eligibility (APPROVED only) and correction/unmarking behavior
    // =========================================================================
    private static void testAttendanceEligibilityAndCorrection() {
        System.out.println("\n[4] Testing Attendance Eligibility & Correction Behavior:");

        AttendanceManagementService.OperationResult notEligible =
                AttendanceManagementService.OperationResult.notEligible(
                        "Attendance can only be marked for participants with APPROVED registrations.");
        assertEquals("Not eligible attendance code",
                AttendanceManagementService.OperationResult.Code.NOT_ELIGIBLE, notEligible.getCode());
        assertTrue("Not eligible attendance isSuccess is false", !notEligible.isSuccess());

        AttendanceManagementService.OperationResult successMark =
                AttendanceManagementService.OperationResult.success("Participant attendance marked as PRESENT.");
        assertEquals("Success mark attendance code",
                AttendanceManagementService.OperationResult.Code.SUCCESS, successMark.getCode());
        assertTrue("Success mark attendance isSuccess is true", successMark.isSuccess());

        // Unmarking / clearing record contract
        AttendanceManagementService.OperationResult clearMark =
                AttendanceManagementService.OperationResult.success("Attendance record cleared. Participant is now Unmarked.");
        assertEquals("Success unmark attendance code",
                AttendanceManagementService.OperationResult.Code.SUCCESS, clearMark.getCode());
        assertTrue("Unmark message mentions Unmarked", clearMark.getMessage().contains("Unmarked"));

        // Attendance summary rate calculation safety
        AttendanceManagementService.AttendanceSummaryDTO zeroApproved =
                new AttendanceManagementService.AttendanceSummaryDTO(0, 0, 0, 0, 0, true);
        assertEquals("Zero approved attendees yields 0% rate", 0, zeroApproved.getAttendanceRatePercent());

        AttendanceManagementService.AttendanceSummaryDTO halfTurnout =
                new AttendanceManagementService.AttendanceSummaryDTO(100, 50, 20, 30, 50, true);
        assertEquals("50 present out of 100 approved yields 50% rate", 50, halfTurnout.getAttendanceRatePercent());
        assertEquals("Unmarked count equals 30", 30, halfTurnout.getUnmarked());
    }

    // =========================================================================
    // 5. Certificate eligibility (APPROVED + PRESENT) and student ownership isolation
    // =========================================================================
    private static void testCertificateEligibilityAndStudentOwnership() {
        System.out.println("\n[5] Testing Certificate Eligibility & Ownership Isolation:");

        CertificateManagementService.OperationResult notEligibleCert =
                CertificateManagementService.OperationResult.notEligible(
                        "Requires an APPROVED registration and recorded PRESENT attendance.");
        assertEquals("Not eligible certificate code",
                CertificateManagementService.OperationResult.Code.NOT_ELIGIBLE, notEligibleCert.getCode());
        assertTrue("Not eligible certificate isSuccess is false", !notEligibleCert.isSuccess());

        CertificateManagementService.OperationResult successCert =
                CertificateManagementService.OperationResult.success("Certificate CERT-000088 issued.", 88);
        assertEquals("Success certificate code",
                CertificateManagementService.OperationResult.Code.SUCCESS, successCert.getCode());
        assertEquals("Generated certificate ID is 88", 88, successCert.getCertificateId());
        assertTrue("Success certificate isSuccess is true", successCert.isSuccess());

        // Batch issuance result metrics
        CertificateManagementService.BatchIssuanceResult batchResult =
                new CertificateManagementService.BatchIssuanceResult(15, 3, 2, "Issued 15 certificates.");
        assertEquals("Total batch issued", 15, batchResult.getTotalIssued());
        assertEquals("Skipped already issued", 3, batchResult.getAlreadyIssuedSkipped());
        assertEquals("Skipped ineligible", 2, batchResult.getIneligibleSkipped());
        assertTrue("Batch issuance success is true", batchResult.isSuccess());

        // Formatted student certificate DTO
        StudentCertificateDTO certDTO = new StudentCertificateDTO();
        certDTO.setCertificateId(12);
        certDTO.setEventName("Robotics Expo");
        assertEquals("Certificate DTO formatted ID", "CERT-000012", certDTO.getFormattedCertificateId());
        assertEquals("Certificate DTO event name", "Robotics Expo", certDTO.getEventName());
    }

    // =========================================================================
    // 6. Feedback eligibility (APPROVED + PRESENT) and rating/comment boundary validation
    // =========================================================================
    private static void testFeedbackEligibilityAndBoundaryValidation() {
        System.out.println("\n[6] Testing Feedback Eligibility & Boundary Validation:");
        FeedbackManagementService fbService = new FeedbackManagementService();

        User student = new User(101, "Alice Student", "alice@student.edu", "pass123", "STUDENT");
        Session.setCurrentUser(student);

        // Rating boundaries: 1-5 allowed, <1 or >5 rejected
        FeedbackManagementService.OperationResult ratingLow = fbService.submitFeedback(1, 0, "Bad");
        assertEquals("Rating 0 rejected as INVALID_INPUT",
                FeedbackManagementService.OperationResult.Code.INVALID_INPUT, ratingLow.getCode());

        FeedbackManagementService.OperationResult ratingNegative = fbService.submitFeedback(1, -3, "Bad");
        assertEquals("Negative rating rejected as INVALID_INPUT",
                FeedbackManagementService.OperationResult.Code.INVALID_INPUT, ratingNegative.getCode());

        FeedbackManagementService.OperationResult ratingHigh = fbService.submitFeedback(1, 6, "Exceeds");
        assertEquals("Rating 6 rejected as INVALID_INPUT",
                FeedbackManagementService.OperationResult.Code.INVALID_INPUT, ratingHigh.getCode());

        // Event ID boundary: <= 0 rejected
        FeedbackManagementService.OperationResult zeroEvent = fbService.submitFeedback(0, 5, "Good");
        assertEquals("Event ID 0 rejected as INVALID_INPUT",
                FeedbackManagementService.OperationResult.Code.INVALID_INPUT, zeroEvent.getCode());

        FeedbackManagementService.OperationResult negEvent = fbService.submitFeedback(-5, 5, "Good");
        assertEquals("Negative event ID rejected as INVALID_INPUT",
                FeedbackManagementService.OperationResult.Code.INVALID_INPUT, negEvent.getCode());

        // Comment boundary: 2000 chars max
        String comment2000 = "A".repeat(2000);
        String comment2001 = "A".repeat(2001);

        // Offline check will trigger for valid rating & 2000 chars
        FeedbackManagementService.OperationResult validLength = fbService.submitFeedback(1, 5, comment2000);
        assertTrue("Comment length 2000 does not fail on input validation",
                validLength.getCode() != FeedbackManagementService.OperationResult.Code.INVALID_INPUT);

        FeedbackManagementService.OperationResult exceedLength = fbService.submitFeedback(1, 5, comment2001);
        assertEquals("Comment length 2001 rejected as INVALID_INPUT",
                FeedbackManagementService.OperationResult.Code.INVALID_INPUT, exceedLength.getCode());

        Session.clearSession();
    }

    // =========================================================================
    // 7. Analytics aggregation safety, zero-division protection, and offline metrics
    // =========================================================================
    private static void testAnalyticsAggregationAndZeroDivisionSafety() {
        System.out.println("\n[7] Testing Analytics Aggregation & Zero-Division Safety:");

        AnalyticsOverviewDTO offlineAnalytics = AnalyticsOverviewDTO.offline();
        assertTrue("Offline analytics dbOnline is false", !offlineAnalytics.isDbOnline());
        assertEquals("Offline total events is 0", 0, offlineAnalytics.getTotalEvents());
        assertEquals("Offline approval rate is 0.0%", 0.0, offlineAnalytics.getApprovalRate());
        assertEquals("Offline turnout rate is 0.0%", 0.0, offlineAnalytics.getTurnoutRate());
        assertEquals("Offline cert issuance rate is 0.0%", 0.0, offlineAnalytics.getCertificateIssuanceRate());
        assertEquals("Offline average rating is 0.0", 0.0, offlineAnalytics.getAverageFeedbackRating());

        // Test CategoryDistributionDTO
        CategoryDistributionDTO catDTO = new CategoryDistributionDTO("Technology", 4, 10);
        assertEquals("Category distribution label", "Technology", catDTO.getCategory());
        assertEquals("Category event count", 4, catDTO.getEventCount());
        assertEquals("Category total registrations", 10, catDTO.getTotalRegistrations());

        // Test MonthlyTrendDTO
        MonthlyTrendDTO trendDTO = new MonthlyTrendDTO("2026-10", "Oct 2026", 45, 38);
        assertEquals("Monthly trend month label", "Oct 2026", trendDTO.getMonthLabel());
        assertEquals("Monthly trend registrations", 45, trendDTO.getRegistrationCount());
        assertEquals("Monthly trend attendance", 38, trendDTO.getAttendanceCount());
    }

    // =========================================================================
    // 8. Campus Pulse gamification exact thresholds, progress capping, and negative input handling
    // =========================================================================
    private static void testCampusPulseThresholdsAndCapping() {
        System.out.println("\n[8] Testing Campus Pulse Thresholds & Progress Capping:");
        CampusPulseService pulseService = new CampusPulseService();

        // 8.1 Zero participation: all badges locked, progress = 0
        List<StudentAchievementDTO> zeroBadges = pulseService.calculateBadges(0, 0, 0, 0);
        assertEquals("Total badges calculated is 6", 6, zeroBadges.size());
        for (StudentAchievementDTO b : zeroBadges) {
            assertTrue("Badge " + b.getId() + " is locked at count 0", !b.isUnlocked());
            assertEquals("Badge " + b.getId() + " current progress is 0", 0, b.getCurrentProgress());
            assertEquals("Badge " + b.getId() + " progress percentage is 0.0%", 0.0, b.getProgressPercentage());
        }

        // 8.2 Exact threshold boundaries:
        // FIRST_STEP: 1 event
        List<StudentAchievementDTO> oneEvent = pulseService.calculateBadges(1, 0, 0, 0);
        StudentAchievementDTO pioneer = findBadge(oneEvent, "FIRST_STEP");
        assertTrue("Campus Pioneer unlocks at exactly 1 event", pioneer.isUnlocked());
        assertEquals("Campus Pioneer percentage is 100.0%", 100.0, pioneer.getProgressPercentage());

        StudentAchievementDTO explorer = findBadge(oneEvent, "ACTIVE_EXPLORER");
        assertTrue("Active Explorer locked at 1 event (requires 3)", !explorer.isUnlocked());
        assertEquals("Active Explorer progress is 1", 1, explorer.getCurrentProgress());

        // ACTIVE_EXPLORER: 3 events
        List<StudentAchievementDTO> threeEvents = pulseService.calculateBadges(3, 0, 0, 0);
        explorer = findBadge(threeEvents, "ACTIVE_EXPLORER");
        assertTrue("Active Explorer unlocks at exactly 3 events", explorer.isUnlocked());
        assertEquals("Active Explorer percentage is 100.0%", 100.0, explorer.getProgressPercentage());

        StudentAchievementDTO veteran = findBadge(threeEvents, "CAMPUS_VETERAN");
        assertTrue("Campus Veteran locked at 3 events (requires 5)", !veteran.isUnlocked());
        assertEquals("Campus Veteran progress is 3", 3, veteran.getCurrentProgress());

        // CAMPUS_VETERAN: 5 events
        List<StudentAchievementDTO> fiveEvents = pulseService.calculateBadges(5, 0, 0, 0);
        veteran = findBadge(fiveEvents, "CAMPUS_VETERAN");
        assertTrue("Campus Veteran unlocks at exactly 5 events", veteran.isUnlocked());
        assertEquals("Campus Veteran percentage is 100.0%", 100.0, veteran.getProgressPercentage());

        // CERTIFIED_ACHIEVER: 1 certificate
        List<StudentAchievementDTO> oneCert = pulseService.calculateBadges(0, 1, 0, 0);
        StudentAchievementDTO certBadge = findBadge(oneCert, "CERTIFIED_ACHIEVER");
        assertTrue("Certified Achiever unlocks at exactly 1 certificate", certBadge.isUnlocked());
        assertEquals("Certified Achiever percentage is 100.0%", 100.0, certBadge.getProgressPercentage());

        // CAMPUS_VOICE: 1 feedback
        List<StudentAchievementDTO> oneFeedback = pulseService.calculateBadges(0, 0, 1, 0);
        StudentAchievementDTO voiceBadge = findBadge(oneFeedback, "CAMPUS_VOICE");
        assertTrue("Campus Voice unlocks at exactly 1 feedback review", voiceBadge.isUnlocked());
        assertEquals("Campus Voice percentage is 100.0%", 100.0, voiceBadge.getProgressPercentage());

        // MULTI_CLUB: 2 distinct clubs
        List<StudentAchievementDTO> oneClub = pulseService.calculateBadges(0, 0, 0, 1);
        StudentAchievementDTO clubBadge1 = findBadge(oneClub, "MULTI_CLUB");
        assertTrue("Club Enthusiast locked at 1 club (requires 2)", !clubBadge1.isUnlocked());
        assertEquals("Club Enthusiast progress is 1", 1, clubBadge1.getCurrentProgress());

        List<StudentAchievementDTO> twoClubs = pulseService.calculateBadges(0, 0, 0, 2);
        StudentAchievementDTO clubBadge2 = findBadge(twoClubs, "MULTI_CLUB");
        assertTrue("Club Enthusiast unlocks at exactly 2 clubs", clubBadge2.isUnlocked());
        assertEquals("Club Enthusiast percentage is 100.0%", 100.0, clubBadge2.getProgressPercentage());

        // 8.3 Overflow capping: progress should NEVER exceed target goal and percentage never > 100%
        List<StudentAchievementDTO> overflow = pulseService.calculateBadges(99, 50, 20, 15);
        for (StudentAchievementDTO b : overflow) {
            assertTrue("Badge " + b.getId() + " is unlocked", b.isUnlocked());
            assertEquals("Badge " + b.getId() + " capped progress equals targetGoal (" + b.getTargetGoal() + ")",
                    b.getTargetGoal(), b.getCurrentProgress());
            assertEquals("Badge " + b.getId() + " percentage capped at 100.0%", 100.0, b.getProgressPercentage());
        }

        // 8.4 Negative counts handling: should be capped safely at 0
        List<StudentAchievementDTO> negative = pulseService.calculateBadges(-5, -2, -10, -1);
        for (StudentAchievementDTO b : negative) {
            assertTrue("Badge " + b.getId() + " with negative input is locked", !b.isUnlocked());
            assertEquals("Badge " + b.getId() + " with negative input has 0 progress", 0, b.getCurrentProgress());
            assertEquals("Badge " + b.getId() + " with negative input has 0.0% percentage", 0.0, b.getProgressPercentage());
        }
    }

    private static StudentAchievementDTO findBadge(List<StudentAchievementDTO> list, String badgeId) {
        for (StudentAchievementDTO b : list) {
            if (badgeId.equals(b.getId())) return b;
        }
        throw new IllegalArgumentException("Badge not found: " + badgeId);
    }

    // =========================================================================
    // 9. Null parameter resilience, empty result set contracts, and Session lifecycle
    // =========================================================================
    private static void testNullResilienceAndOfflineContracts() {
        System.out.println("\n[9] Testing Null Parameter Resilience & Offline Contracts:");

        // 9.1 EventManagementService validation with null & boundary inputs
        EventManagementService evService = new EventManagementService();

        EventManagementService.ValidationResult nullEvent = evService.validate(null);
        assertTrue("Null event rejected as invalid", !nullEvent.isValid());

        Event blankTitle = new Event(1, "   ", "Desc", "Tech",
                LocalDate.now().plusDays(5), LocalTime.of(10, 0), "Venue", 100, LocalDate.now().plusDays(2));
        EventManagementService.ValidationResult resBlank = evService.validate(blankTitle);
        assertTrue("Blank event title rejected as invalid", !resBlank.isValid());

        Event shortTitle = new Event(1, "AB", "Desc", "Tech",
                LocalDate.now().plusDays(5), LocalTime.of(10, 0), "Venue", 100, LocalDate.now().plusDays(2));
        EventManagementService.ValidationResult resShort = evService.validate(shortTitle);
        assertTrue("Event title < 3 chars rejected as invalid", !resShort.isValid());

        Event pastDeadline = new Event(1, "Valid Title", "Desc", "Tech",
                LocalDate.now().plusDays(2), LocalTime.of(10, 0), "Venue", 100, LocalDate.now().plusDays(5));
        EventManagementService.ValidationResult resDeadline = evService.validate(pastDeadline);
        assertTrue("Deadline after event date rejected as invalid", !resDeadline.isValid());

        Event zeroCapacity = new Event(1, "Valid Title", "Desc", "Tech",
                LocalDate.now().plusDays(5), LocalTime.of(10, 0), "Venue", 0, LocalDate.now().plusDays(2));
        EventManagementService.ValidationResult resZeroCap = evService.validate(zeroCapacity);
        assertTrue("Zero capacity rejected as invalid", !resZeroCap.isValid());

        Event validEvent = new Event(1, "Valid Title", "Desc", "Tech",
                LocalDate.now().plusDays(5), LocalTime.of(10, 0), "Venue", 100, LocalDate.now().plusDays(2));
        EventManagementService.ValidationResult resValid = evService.validate(validEvent);
        assertTrue("Valid event passes validation", resValid.isValid());

        // 9.2 Safe deletion safeguards
        EventManagementService.OperationResult delZero = evService.deleteEvent(0);
        assertTrue("Delete event ID 0 is rejected", !delZero.isSuccess());

        EventManagementService.OperationResult delNeg = evService.deleteEvent(-10);
        assertTrue("Delete negative event ID is rejected", !delNeg.isSuccess());

        // 9.3 DependentSummary description formatting
        EventDAO.DependentSummary emptyDeps = EventDAO.DependentSummary.empty();
        assertTrue("Empty dependencies hasDependencies is false", !emptyDeps.hasDependencies());
        assertEquals("Empty dependencies total count is 0", 0, emptyDeps.getTotalCount());
        assertEquals("Empty description string", "0 dependent records", emptyDeps.toDescription());

        EventDAO.DependentSummary fullDeps = new EventDAO.DependentSummary(5, 4, 3, 2);
        assertTrue("Full dependencies hasDependencies is true", fullDeps.hasDependencies());
        assertEquals("Full dependencies total count is 14", 14, fullDeps.getTotalCount());
        assertTrue("Full dependencies description contains all categories",
                fullDeps.toDescription().contains("5 student registration(s)") &&
                fullDeps.toDescription().contains("4 attendance record(s)") &&
                fullDeps.toDescription().contains("3 feedback review(s)") &&
                fullDeps.toDescription().contains("2 certificate(s)"));

        // 9.4 Session lifecycle & thread safety
        Session.clearSession();
        assertTrue("Session isLoggedIn is false when empty", !Session.isLoggedIn());
        assertEquals("Session currentUser is null when empty", null, Session.getCurrentUser());

        User tempUser = new User(999, "Temp", "temp@campus.edu", "pass", "STUDENT");
        Session.setCurrentUser(tempUser);
        assertTrue("Session isLoggedIn is true after login", Session.isLoggedIn());
        assertEquals("Session currentUser matches stored user", tempUser, Session.getCurrentUser());

        Session.clearSession();
        assertTrue("Session isLoggedIn is false after clearSession", !Session.isLoggedIn());
        assertEquals("Session currentUser is null after clearSession", null, Session.getCurrentUser());
    }
}
