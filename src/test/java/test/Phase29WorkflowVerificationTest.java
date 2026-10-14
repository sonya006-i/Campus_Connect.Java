package test;

import dao.AttendanceDAO;
import dao.CertificateDAO;
import dao.ClubDAO;
import dao.EventDAO;
import dao.FeedbackDAO;
import dao.RegistrationDAO;
import dao.UserDAO;
import model.AdminCertificateParticipantDTO;
import model.AdminFeedbackDTO;
import model.AdminRegistrationDTO;
import model.AnalyticsOverviewDTO;
import model.Event;
import model.EventAttendanceDTO;
import model.FeedbackSummaryDTO;
import model.Registration;
import model.StudentCertificateDTO;
import model.StudentRegistrationDTO;
import model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import service.AnalyticsService;
import service.AttendanceManagementService;
import service.CertificateManagementService;
import service.CertificateManagementService.EventCertificateSummaryDTO;
import service.EventManagementService;
import service.FeedbackManagementService;
import service.RegistrationManagementService;
import service.RegistrationService;
import util.DBConnection;
import util.PasswordSecurityUtil;
import util.Session;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase29WorkflowVerificationTest — End-to-End Workflow & Business Rules Verification Suite.
 *
 * Implements Phase 29 per AGENTS.md and safe testing requirements:
 *
 * WORKFLOW 1: Student
 * 1. Log in as existing demo student (sonya@student.edu)
 * 2. Browse and open event (Tech Hackathon 2026)
 * 3. Inspect registration status and existing registration duplicate prevention
 * 4. Verify My Events and event details
 * 5. Check certificate and feedback eligibility behavior (ineligible prior to attendance)
 * 6. Verify logout clears the session
 *
 * WORKFLOW 2: Admin
 * 1. Log in as existing demo admin (admin@campusconnect.edu)
 * 2. Open all 6 admin screens and verify real data loading:
 *    - Event Management (Tech Hackathon 2026)
 *    - Registration Moderation (Sonya Blade's APPROVED registration)
 *    - Attendance Management (Tech Hackathon 2026 roster)
 *    - Certificate Management (participant roster)
 *    - Feedback Management (feedback roster)
 *    - Campus Analytics (KPI metrics and trends)
 * 3. Test eligibility rules (certificate requires APPROVED registration + PRESENT attendance)
 * 4. Test capacity limits and duplicate registration constraints
 * 5. Verify logout clears the session
 *
 * DISPOSABLE FIXTURES E2E:
 * Tests the complete positive lifecycle (register -> approve -> capacity rejection -> attendance ->
 * feedback submission + duplicate prevention -> certificate issuance + duplicate prevention)
 * using uniquely tagged disposable records that are cleanly removed in @AfterAll / finally.
 *
 * Zero mutations to live demo records.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase29WorkflowVerificationTest {

    private static final String ADMIN_EMAIL = "admin@campusconnect.edu";
    private static final String ADMIN_RAW_PASS = "admin123";

    private static final String STUDENT_EMAIL = "sonya@student.edu";
    private static final String STUDENT_NAME = "Sonya Blade";
    private static final String STUDENT_RAW_PASS = "student123";

    private static final String EXPECTED_CLUB_NAME = "Tech & Coding Society";
    private static final String EXPECTED_EVENT_NAME = "Tech Hackathon 2026";
    private static final LocalDate EXPECTED_EVENT_DATE = LocalDate.of(2026, 10, 20);

    // DAOs
    private static UserDAO userDAO;
    private static ClubDAO clubDAO;
    private static EventDAO eventDAO;
    private static RegistrationDAO registrationDAO;
    private static AttendanceDAO attendanceDAO;
    private static CertificateDAO certificateDAO;
    private static FeedbackDAO feedbackDAO;

    // Services
    private static RegistrationService registrationService;
    private static RegistrationManagementService regMgmtService;
    private static AttendanceManagementService attendanceService;
    private static CertificateManagementService certificateService;
    private static FeedbackManagementService feedbackService;
    private static AnalyticsService analyticsService;
    private static EventManagementService eventMgmtService;

    // Track disposable IDs for strictly verified cleanup
    private static final List<Integer> disposableUserIds = new ArrayList<>();
    private static final List<Integer> disposableEventIds = new ArrayList<>();
    private static final List<Integer> disposableRegIds = new ArrayList<>();
    private static final List<Integer> disposableAttIds = new ArrayList<>();
    private static final List<Integer> disposableFbIds = new ArrayList<>();
    private static final List<Integer> disposableCertIds = new ArrayList<>();

    @BeforeAll
    public static void setUpSuite() {
        assertTrue(DBConnection.testConnection(),
                "Database connection must be active for Phase 29 verification.");

        userDAO = new UserDAO();
        clubDAO = new ClubDAO();
        eventDAO = new EventDAO();
        registrationDAO = new RegistrationDAO();
        attendanceDAO = new AttendanceDAO();
        certificateDAO = new CertificateDAO();
        feedbackDAO = new FeedbackDAO();

        registrationService = new RegistrationService();
        regMgmtService = new RegistrationManagementService();
        attendanceService = new AttendanceManagementService();
        certificateService = new CertificateManagementService();
        feedbackService = new FeedbackManagementService();
        analyticsService = new AnalyticsService();
        eventMgmtService = new EventManagementService();
    }

    @AfterEach
    public void clearActiveSession() {
        Session.clearSession();
    }

    @AfterAll
    public static void tearDownDisposableFixtures() {
        // Strict teardown: delete all disposable test fixtures in reverse FK dependency order
        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null) {
                // 1. Delete certificates
                for (int certId : disposableCertIds) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM certificates WHERE certificate_id = ?")) {
                        ps.setInt(1, certId);
                        ps.executeUpdate();
                    } catch (SQLException ignored) {}
                }

                // 2. Delete feedback
                for (int fbId : disposableFbIds) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM feedback WHERE feedback_id = ?")) {
                        ps.setInt(1, fbId);
                        ps.executeUpdate();
                    } catch (SQLException ignored) {}
                }

                // 3. Delete attendance
                for (int attId : disposableAttIds) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM attendance WHERE attendance_id = ?")) {
                        ps.setInt(1, attId);
                        ps.executeUpdate();
                    } catch (SQLException ignored) {}
                }

                // 4. Delete registrations
                for (int regId : disposableRegIds) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM registrations WHERE registration_id = ?")) {
                        ps.setInt(1, regId);
                        ps.executeUpdate();
                    } catch (SQLException ignored) {}
                }

                // 5. Delete events
                for (int eventId : disposableEventIds) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM events WHERE event_id = ?")) {
                        ps.setInt(1, eventId);
                        ps.executeUpdate();
                    } catch (SQLException ignored) {}
                }

                // 6. Delete users
                for (int userId : disposableUserIds) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
                        ps.setInt(1, userId);
                        ps.executeUpdate();
                    } catch (SQLException ignored) {}
                }

                // Safety sweep for any tagged emails
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE email LIKE 'p29_fixture_%'")) {
                    ps.executeUpdate();
                } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            System.err.println("[Phase29 Cleanup Warning] " + e.getMessage());
        }
    }

    // =========================================================================
    // WORKFLOW 1: STUDENT WORKFLOW (LIVE DB ON DEMO DATA)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Workflow 1.1: Student authenticates and establishes valid session")
    public void testWorkflow1_1_StudentLoginAndSession() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        assertNotNull(student, "Demo student account must exist");
        assertTrue(PasswordSecurityUtil.verifyPassword(STUDENT_RAW_PASS, student.getPassword()),
                "Demo student password verification must succeed");

        Session.setCurrentUser(student);
        assertTrue(Session.isLoggedIn(), "Session must be logged in");
        assertEquals("STUDENT", Session.getCurrentUser().getRole(), "Session role must be student");
        assertEquals(STUDENT_EMAIL, Session.getCurrentUser().getEmail());
        assertEquals(STUDENT_NAME, Session.getCurrentUser().getName());
    }

    @Test
    @Order(2)
    @DisplayName("Workflow 1.2: Student browses and opens Tech Hackathon 2026 event")
    public void testWorkflow1_2_BrowseAndOpenEvent() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        Session.setCurrentUser(student);

        List<Event> events = eventDAO.getAll();
        assertNotNull(events, "Events list must not be null");
        assertFalse(events.isEmpty(), "Events list must contain at least 1 event");

        Event hackathon = events.stream()
                .filter(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEventName()))
                .findFirst()
                .orElse(null);

        assertNotNull(hackathon, "Tech Hackathon 2026 must be present in discovery feed");
        assertEquals(EXPECTED_EVENT_DATE, hackathon.getEventDate());
        assertEquals("Main Auditorium & Innovation Hall", hackathon.getVenue());
        assertEquals(200, hackathon.getMaxParticipants());
        assertNotNull(hackathon.getRegistrationDeadline());
    }

    @Test
    @Order(3)
    @DisplayName("Workflow 1.3: Inspect registration status and enforce duplicate registration prevention")
    public void testWorkflow1_3_InspectStatusAndDuplicatePrevention() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        Session.setCurrentUser(student);

        Event hackathon = eventDAO.getAll().stream()
                .filter(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEventName()))
                .findFirst()
                .orElse(null);
        assertNotNull(hackathon);

        // 1. Check status info
        RegistrationService.EventStatusInfo statusInfo = registrationService.getEventStatusInfo(hackathon);
        assertNotNull(statusInfo);
        assertEquals(RegistrationService.Eligibility.ALREADY_REGISTERED, statusInfo.getEligibility(),
                "Student already registered must be recognized as ALREADY_REGISTERED");
        assertEquals("APPROVED", statusInfo.getRegistrationStatus(),
                "Student's registration status must be APPROVED");

        // 2. Attempt duplicate registration
        RegistrationService.RegistrationResult result = registrationService.registerCurrentStudent(hackathon);
        assertFalse(result.isSuccess(), "Duplicate registration must be rejected");
        assertTrue(result.getMessage().toLowerCase().contains("already registered"),
                "Rejection message must indicate student is already registered");
    }

    @Test
    @Order(4)
    @DisplayName("Workflow 1.4: Verify My Events displays approved registration and event details")
    public void testWorkflow1_4_VerifyMyEventsAndDetails() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        Session.setCurrentUser(student);

        List<StudentRegistrationDTO> regs = registrationDAO.getStudentRegistrationsWithEvents(student.getUserId());
        assertNotNull(regs);
        assertFalse(regs.isEmpty(), "My Events must contain registered events");

        StudentRegistrationDTO hackathonReg = regs.stream()
                .filter(r -> EXPECTED_EVENT_NAME.equalsIgnoreCase(r.getEventName()))
                .findFirst()
                .orElse(null);

        assertNotNull(hackathonReg, "Tech Hackathon 2026 must appear in My Events");
        assertEquals("APPROVED", hackathonReg.getStatus());
        assertEquals(EXPECTED_CLUB_NAME, hackathonReg.getClubName());
        assertEquals(EXPECTED_EVENT_DATE, hackathonReg.getEventDate());
    }

    @Test
    @Order(5)
    @DisplayName("Workflow 1.5: Verify certificate and feedback eligibility requires verified attendance")
    public void testWorkflow1_5_CertificateAndFeedbackIneligibilityPriorToAttendance() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        Session.setCurrentUser(student);

        Event hackathon = eventDAO.getAll().stream()
                .filter(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEventName()))
                .findFirst()
                .orElse(null);
        assertNotNull(hackathon);

        // 1. Certificate Eligibility
        boolean certEligible = certificateDAO.isEligibleForCertificate(hackathon.getEventId(), student.getUserId());
        assertFalse(certEligible,
                "Student without recorded PRESENT attendance must NOT be eligible for a certificate");

        List<StudentCertificateDTO> certs = certificateService.getMyCertificates();
        assertNotNull(certs);
        assertTrue(certs.isEmpty(), "Student with no attended events must have 0 certificates");

        // 2. Feedback Eligibility
        assertFalse(feedbackService.isEligibleForFeedback(hackathon.getEventId(), student.getUserId()),
                "Service layer must confirm feedback ineligibility prior to attendance");

        // 3. Attempt feedback submission without attendance
        FeedbackManagementService.OperationResult fbResult =
                feedbackService.submitFeedback(hackathon.getEventId(), 5, "Premature feedback submission");
        assertFalse(fbResult.isSuccess(), "Premature feedback submission must be rejected");
        assertEquals(FeedbackManagementService.OperationResult.Code.NOT_ELIGIBLE, fbResult.getCode(),
                "Error code must be NOT_ELIGIBLE");
        assertTrue(fbResult.getMessage().toLowerCase().contains("attended") ||
                   fbResult.getMessage().toLowerCase().contains("presence"),
                "Error message must specify presence requirement");
    }

    @Test
    @Order(6)
    @DisplayName("Workflow 1.6: Verify student logout clears session completely")
    public void testWorkflow1_6_StudentLogoutClearsSession() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        Session.setCurrentUser(student);
        assertTrue(Session.isLoggedIn());

        // Perform logout
        Session.clearSession();
        assertFalse(Session.isLoggedIn(), "Session.isLoggedIn() must be false after logout");
        assertNull(Session.getCurrentUser(), "Session.getCurrentUser() must be null after logout");
    }

    // =========================================================================
    // WORKFLOW 2: ADMIN WORKFLOW (LIVE DB ON DEMO DATA)
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("Workflow 2.1: Admin authenticates and establishes valid session")
    public void testWorkflow2_1_AdminLoginAndSession() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        assertNotNull(admin, "Demo admin account must exist");
        assertTrue(PasswordSecurityUtil.verifyPassword(ADMIN_RAW_PASS, admin.getPassword()),
                "Demo admin password verification must succeed");

        Session.setCurrentUser(admin);
        assertTrue(Session.isLoggedIn(), "Session must be logged in");
        assertEquals("ADMIN", Session.getCurrentUser().getRole(), "Session role must be admin");
        assertEquals(ADMIN_EMAIL, Session.getCurrentUser().getEmail());
    }

    @Test
    @Order(8)
    @DisplayName("Workflow 2.2: All 6 admin screens load real data and active records")
    public void testWorkflow2_2_AllAdminScreensLoadRealData() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        Session.setCurrentUser(admin);

        // Screen 1: Event Management
        List<Event> adminEvents = eventMgmtService.getAllEvents();
        assertNotNull(adminEvents);
        assertFalse(adminEvents.isEmpty(), "Event Management must load campus events");
        assertTrue(adminEvents.stream().anyMatch(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEventName())));

        Event hackathon = adminEvents.stream()
                .filter(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEventName()))
                .findFirst()
                .orElse(null);
        assertNotNull(hackathon);

        // Screen 2: Registration Moderation
        List<AdminRegistrationDTO> regs = regMgmtService.getRegistrations("", "ALL", null);
        assertNotNull(regs);
        assertFalse(regs.isEmpty(), "Registration Management must load registrations");
        assertTrue(regs.stream().anyMatch(r -> STUDENT_EMAIL.equalsIgnoreCase(r.getStudentEmail()) &&
                                              "APPROVED".equalsIgnoreCase(r.getStatus())));

        RegistrationManagementService.RegistrationSummaryDTO regSummary = regMgmtService.getRegistrationSummary();
        assertTrue(regSummary.isDbOnline());
        assertTrue(regSummary.getTotal() >= 1);
        assertTrue(regSummary.getApproved() >= 1);

        // Screen 3: Attendance Management
        List<EventAttendanceDTO> roster = attendanceService.getEventAttendanceRoster(hackathon.getEventId());
        assertNotNull(roster);
        assertFalse(roster.isEmpty(), "Attendance Management must load roster for Hackathon");
        EventAttendanceDTO attParticipant = roster.stream()
                .filter(p -> STUDENT_EMAIL.equalsIgnoreCase(p.getStudentEmail()))
                .findFirst()
                .orElse(null);
        assertNotNull(attParticipant, "Sonya Blade must be in attendance roster");
        assertTrue(attParticipant.isApproved(), "Sonya Blade's registration must be APPROVED");
        assertFalse(attParticipant.isPresent(), "Sonya Blade's attendance must be unmarked/not present");

        AttendanceManagementService.AttendanceSummaryDTO attSummary = attendanceService.getAttendanceSummary(hackathon.getEventId());
        assertTrue(attSummary.isDbOnline());
        assertEquals(1, attSummary.getTotalApproved());

        // Screen 4: Certificate Management
        List<AdminCertificateParticipantDTO> certRoster = certificateService.getParticipantRoster(hackathon.getEventId());
        assertNotNull(certRoster);
        assertFalse(certRoster.isEmpty(), "Certificate roster must load participants");
        AdminCertificateParticipantDTO certParticipant = certRoster.stream()
                .filter(p -> STUDENT_EMAIL.equalsIgnoreCase(p.getStudentEmail()))
                .findFirst()
                .orElse(null);
        assertNotNull(certParticipant);
        assertTrue(certParticipant.isApproved());
        assertFalse(certParticipant.isAttended());
        assertFalse(certParticipant.isCertificateIssued());

        EventCertificateSummaryDTO certSummary = certificateService.getEventCertificateSummary(hackathon.getEventId());
        assertTrue(certSummary.isDbOnline());
        assertEquals(1, certSummary.getTotalParticipants());
        assertEquals(0, certSummary.getIssuedCertificates());

        // Screen 5: Feedback Management
        List<AdminFeedbackDTO> fbList = feedbackService.getAllFeedbackForAdmin();
        assertNotNull(fbList, "Feedback Management must load feedback list");
        FeedbackSummaryDTO fbSummary = feedbackService.getFeedbackSummary();
        assertNotNull(fbSummary);
        assertTrue(fbSummary.isDbOnline());

        // Screen 6: Campus Analytics
        assertTrue(analyticsService.isAuthorized(), "Admin must be authorized for Analytics");
        AnalyticsOverviewDTO analyticsOverview = analyticsService.getOverviewMetrics();
        assertNotNull(analyticsOverview);
        assertTrue(analyticsOverview.isDbOnline());
        assertTrue(analyticsOverview.getTotalRegistrations() >= 1);
        assertTrue(analyticsOverview.getTotalClubs() >= 1);
        assertTrue(analyticsOverview.getTotalEvents() >= 1);
    }

    @Test
    @Order(9)
    @DisplayName("Workflow 2.3: Admin certificate issuance enforces attendance prerequisite")
    public void testWorkflow2_3_CertificateIssuancePrerequisiteEnforcement() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        Session.setCurrentUser(admin);

        Event hackathon = eventDAO.getAll().stream()
                .filter(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEventName()))
                .findFirst()
                .orElse(null);
        assertNotNull(hackathon);

        // Attempt to issue certificate to student without PRESENT attendance
        CertificateManagementService.OperationResult result =
                certificateService.issueCertificate(hackathon.getEventId(), student.getUserId());

        assertFalse(result.isSuccess(), "Certificate issuance must be rejected for unattended participant");
        assertEquals(CertificateManagementService.OperationResult.Code.NOT_ELIGIBLE, result.getCode(),
                "Error code must be NOT_ELIGIBLE");
        assertTrue(result.getMessage().toLowerCase().contains("approved") &&
                   result.getMessage().toLowerCase().contains("present"),
                "Error message must specify requirement of approved registration and present attendance");
    }

    @Test
    @Order(10)
    @DisplayName("Workflow 2.4: Admin cannot register for events as student")
    public void testWorkflow2_4_AdminRoleConstraintOnRegistration() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        Session.setCurrentUser(admin);

        Event hackathon = eventDAO.getAll().stream()
                .filter(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEventName()))
                .findFirst()
                .orElse(null);
        assertNotNull(hackathon);

        RegistrationService.EventStatusInfo statusInfo = registrationService.getEventStatusInfo(hackathon);
        assertEquals(RegistrationService.Eligibility.STUDENT_ONLY, statusInfo.getEligibility(),
                "Admin accessing student registration must receive STUDENT_ONLY status");

        RegistrationService.RegistrationResult result = registrationService.registerCurrentStudent(hackathon);
        assertFalse(result.isSuccess(), "Admin registration must be rejected");
        assertTrue(result.getMessage().toLowerCase().contains("student"),
                "Rejection message must indicate student role required");
    }

    // =========================================================================
    // DISPOSABLE END-TO-END TEST FIXTURES (FULL LIFECYCLE ON LIVE DB)
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("Workflow 3: Full End-to-End positive & negative lifecycle with disposable test fixtures")
    public void testWorkflow3_FullLifecycleWithDisposableFixtures() throws Exception {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        String student1Email = "p29_fixture_s1_" + testSuffix + "@test.edu";
        String student2Email = "p29_fixture_s2_" + testSuffix + "@test.edu";
        String testEventName = "E2E Disposable Event " + testSuffix;

        int student1Id = -1;
        int student2Id = -1;
        int testEventId = -1;
        int testReg1Id = -1;

        try {
            // STEP 1: Create disposable test students directly in users table
            String passHash = PasswordSecurityUtil.hashPassword("fixturePass123");
            try (Connection conn = DBConnection.getConnection()) {
                assertNotNull(conn);

                // Insert Student 1
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, ?) RETURNING user_id")) {
                    ps.setString(1, "Fixture Student 1");
                    ps.setString(2, student1Email);
                    ps.setString(3, passHash);
                    ps.setString(4, "STUDENT");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        student1Id = rs.getInt(1);
                        disposableUserIds.add(student1Id);
                    }
                }

                // Insert Student 2
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, ?) RETURNING user_id")) {
                    ps.setString(1, "Fixture Student 2");
                    ps.setString(2, student2Email);
                    ps.setString(3, passHash);
                    ps.setString(4, "STUDENT");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        student2Id = rs.getInt(1);
                        disposableUserIds.add(student2Id);
                    }
                }
            }

            List<model.Club> existingClubs = clubDAO.getAll();
            assertFalse(existingClubs.isEmpty(), "At least one club must exist in database");
            int validClubId = existingClubs.get(0).getClubId();

            // STEP 2: Create disposable test event with capacity = 1
            Event disposableEvent = new Event(
                    0, validClubId, testEventName, "E2E disposable verification event", "Technology",
                    LocalDate.now().plusDays(10), LocalTime.of(14, 0), "Test Lab",
                    1, // maxParticipants = 1 (strictly constrained)
                    LocalDate.now().plusDays(5)
            );
            testEventId = eventDAO.insert(disposableEvent);
            assertTrue(testEventId > 0, "Disposable event creation must succeed");
            disposableEventIds.add(testEventId);
            disposableEvent.setEventId(testEventId);

            // STEP 3: Student 1 registers for disposable event
            User student1 = userDAO.findById(student1Id);
            assertNotNull(student1);
            Session.setCurrentUser(student1);

            RegistrationService.RegistrationResult regResult = registrationService.registerCurrentStudent(disposableEvent);
            assertTrue(regResult.isSuccess(), "Student 1 registration must succeed: " + regResult.getMessage());
            testReg1Id = regResult.getRegistrationId();
            assertTrue(testReg1Id > 0);
            disposableRegIds.add(testReg1Id);
            assertEquals("PENDING", regResult.getStatus());

            // STEP 4: Duplicate registration prevention on disposable event
            RegistrationService.RegistrationResult dupResult = registrationService.registerCurrentStudent(disposableEvent);
            assertFalse(dupResult.isSuccess(), "Duplicate registration by Student 1 must be rejected");
            assertTrue(dupResult.getMessage().toLowerCase().contains("already registered"));

            // STEP 5: Admin reviews & approves registration
            User admin = userDAO.findByEmail(ADMIN_EMAIL);
            Session.setCurrentUser(admin);

            RegistrationManagementService.OperationResult approveResult = regMgmtService.approveRegistration(testReg1Id);
            assertTrue(approveResult.isSuccess(), "Admin approval must succeed: " + approveResult.getMessage());

            Registration approvedReg = registrationDAO.findById(testReg1Id);
            assertNotNull(approvedReg);
            assertEquals("APPROVED", approvedReg.getStatus());

            // STEP 6: Capacity limit enforcement: Event capacity is 1, occupied is 1 (approved).
            // Student 2 attempts to register -> must be rejected as full!
            User student2 = userDAO.findById(student2Id);
            assertNotNull(student2);
            Session.setCurrentUser(student2);

            RegistrationService.EventStatusInfo statusInfo2 = registrationService.getEventStatusInfo(disposableEvent);
            assertEquals(RegistrationService.Eligibility.EVENT_FULL, statusInfo2.getEligibility(),
                    "Event with capacity=1 and 1 approved registration must be recognized as EVENT_FULL");

            RegistrationService.RegistrationResult fullRegResult = registrationService.registerCurrentStudent(disposableEvent);
            assertFalse(fullRegResult.isSuccess(), "Registration beyond capacity limit must be rejected");
            assertTrue(fullRegResult.getMessage().toLowerCase().contains("limit") ||
                       fullRegResult.getMessage().toLowerCase().contains("full"));

            // STEP 7: Test ineligibility for feedback and certificate before attendance is recorded
            Session.setCurrentUser(student1);
            assertFalse(feedbackService.isEligibleForFeedback(testEventId, student1Id),
                    "Student 1 must NOT be eligible for feedback prior to attendance");
            FeedbackManagementService.OperationResult prematureFb = feedbackService.submitFeedback(testEventId, 5, "Test");
            assertFalse(prematureFb.isSuccess());

            Session.setCurrentUser(admin);
            CertificateManagementService.OperationResult prematureCert = certificateService.issueCertificate(testEventId, student1Id);
            assertFalse(prematureCert.isSuccess());

            // STEP 8: Admin marks Student 1 as PRESENT in attendance
            AttendanceManagementService.OperationResult attMarkResult =
                    attendanceService.markAttendance(testEventId, student1Id, true);
            assertTrue(attMarkResult.isSuccess(), "Marking attendance present must succeed: " + attMarkResult.getMessage());

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT attendance_id, present FROM attendance WHERE event_id = ? AND student_id = ?")) {
                ps.setInt(1, testEventId);
                ps.setInt(2, student1Id);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    disposableAttIds.add(rs.getInt("attendance_id"));
                    assertTrue(rs.getBoolean("present"), "Attendance must be recorded as present");
                }
            }

            // STEP 9: Student 1 submits feedback and tests duplicate prevention
            Session.setCurrentUser(student1);
            assertTrue(feedbackService.isEligibleForFeedback(testEventId, student1Id),
                    "Student 1 must now be ELIGIBLE for feedback after verified attendance");

            FeedbackManagementService.OperationResult fbSuccess =
                    feedbackService.submitFeedback(testEventId, 5, "Verified E2E workflow feedback comment.");
            assertTrue(fbSuccess.isSuccess(), "Feedback submission must succeed: " + fbSuccess.getMessage());
            int fbId = fbSuccess.getFeedbackId();
            assertTrue(fbId > 0);
            disposableFbIds.add(fbId);

            // Duplicate feedback attempt
            FeedbackManagementService.OperationResult fbDup =
                    feedbackService.submitFeedback(testEventId, 4, "Duplicate feedback attempt");
            assertFalse(fbDup.isSuccess(), "Duplicate feedback submission must be rejected");
            assertEquals(FeedbackManagementService.OperationResult.Code.ALREADY_SUBMITTED, fbDup.getCode());

            // STEP 10: Admin issues certificate and tests duplicate prevention
            Session.setCurrentUser(admin);
            assertTrue(certificateDAO.isEligibleForCertificate(testEventId, student1Id),
                    "Student 1 must now be ELIGIBLE for certificate after approved registration and present attendance");

            CertificateManagementService.OperationResult certSuccess =
                    certificateService.issueCertificate(testEventId, student1Id);
            assertTrue(certSuccess.isSuccess(), "Certificate issuance must succeed: " + certSuccess.getMessage());
            int certId = certSuccess.getCertificateId();
            assertTrue(certId > 0);
            disposableCertIds.add(certId);

            // Duplicate certificate issuance attempt
            CertificateManagementService.OperationResult certDup =
                    certificateService.issueCertificate(testEventId, student1Id);
            assertFalse(certDup.isSuccess(), "Duplicate certificate issuance must be rejected");
            assertEquals(CertificateManagementService.OperationResult.Code.ALREADY_ISSUED, certDup.getCode());

            // STEP 11: Student views their newly issued certificate in My Certificates
            Session.setCurrentUser(student1);
            List<StudentCertificateDTO> studentCerts = certificateService.getMyCertificates();
            assertEquals(1, studentCerts.size(), "Student 1 must now see exactly 1 certificate in My Certificates");
            assertEquals(testEventName, studentCerts.get(0).getEventName());

        } finally {
            // Clean up exact disposable fixtures immediately
            tearDownDisposableFixtures();
        }
    }

    // =========================================================================
    // CONNECTION POOL INTEGRITY CHECK (PHASE 28 REGRESSION PREVENTION)
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("Pool Integrity: Verify Phase 28 connection pool remains stable across workflows")
    public void testPoolIntegrityAfterWorkflows() throws SQLException {
        assertTrue(DBConnection.testConnection(), "Connection pool must remain active");
        assertTrue(DBConnection.MAX_POOL_SIZE >= 1, "Pool capacity must be valid");
        assertTrue(DBConnection.getActiveConnectionsCount() <= DBConnection.MAX_POOL_SIZE,
                "Active connections must not exceed pool capacity");

        try (Connection conn = DBConnection.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
            assertTrue(conn.getAutoCommit(), "Returned connection autoCommit must be true");
        }
    }
}
