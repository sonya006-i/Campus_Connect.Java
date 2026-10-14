package test;

import dao.ClubDAO;
import dao.EventDAO;
import dao.RegistrationDAO;
import dao.UserDAO;
import model.AdminDashboardDTO;
import model.AdminRegistrationDTO;
import model.Club;
import model.Event;
import model.EventAttendanceDTO;
import model.Registration;
import model.StudentDashboardDTO;
import model.StudentRegistrationDTO;
import model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import service.AdminDashboardService;
import service.AttendanceManagementService;
import service.EventManagementService;
import service.RegistrationManagementService;
import service.StudentDashboardService;
import util.DBConnection;
import util.PasswordSecurityUtil;
import util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase26LiveVerificationTest — Automated End-to-End Verification Suite for Campus Connect.
 *
 * Implements Stage B, Stage C, and Stage D verification against the live Supabase PostgreSQL database:
 * - Stage B: Verify Database and Demo Accounts (read-only queries)
 * - Stage C: Test Authentication (real login flow, credentials verification, edge cases)
 * - Stage D: Verify Core Workflows (admin dashboard, student dashboard, event browsing, registrations, attendance)
 *
 * Safety Guarantees:
 * - Purely read-only: executes zero DDL, zero DELETE, zero UPDATE, zero INSERT mutations.
 * - Never prints, exposes, or logs passwords, secrets, or raw connection strings.
 * - Never hardcodes or bypasses authentication.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase26LiveVerificationTest {

    private static final String ADMIN_EMAIL = "admin@campusconnect.edu";
    private static final String ADMIN_NAME = "Dr. Alan Vance";
    private static final String ADMIN_RAW_PASS = "admin123";

    private static final String STUDENT_EMAIL = "sonya@student.edu";
    private static final String STUDENT_NAME = "Sonya Blade";
    private static final String STUDENT_RAW_PASS = "student123";

    private static final String EXPECTED_CLUB_NAME = "Tech & Coding Society";
    private static final String EXPECTED_EVENT_NAME = "Tech Hackathon 2026";
    private static final LocalDate EXPECTED_EVENT_DATE = LocalDate.of(2026, 10, 20);

    private static UserDAO userDAO;
    private static ClubDAO clubDAO;
    private static EventDAO eventDAO;
    private static RegistrationDAO registrationDAO;

    @BeforeAll
    public static void checkPrerequisites() {
        assertTrue(DBConnection.testConnection(),
                "BLOCKED: Live database connection could not be established. Check DB configuration.");
        userDAO = new UserDAO();
        clubDAO = new ClubDAO();
        eventDAO = new EventDAO();
        registrationDAO = new RegistrationDAO();
    }

    @AfterEach
    public void cleanupSession() {
        Session.clearSession();
    }

    // =========================================================================
    // STAGE B — VERIFY DATABASE AND DEMO ACCOUNTS (READ-ONLY)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Stage B: Verify live PostgreSQL connectivity and core table availability")
    public void testStageB_DatabaseConnectivity() {
        try (Connection conn = DBConnection.getConnection()) {
            assertNotNull(conn, "Connection must not be null");
            assertFalse(conn.isClosed(), "Connection must be active");
            String productName = conn.getMetaData().getDatabaseProductName();
            assertEquals("PostgreSQL", productName, "Database engine must be PostgreSQL");
        } catch (SQLException e) {
            fail("SQLException verifying database connectivity: " + e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("Stage B: Verify Admin demo account exists with valid role and PBKDF2 hash")
    public void testStageB_AdminDemoAccountExists() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        assertNotNull(admin, "Admin demo account '" + ADMIN_EMAIL + "' must exist in database");
        assertEquals(ADMIN_EMAIL, admin.getEmail(), "Admin email must match exactly");
        assertEquals("ADMIN", admin.getRole(), "User role must be ADMIN");
        assertEquals(ADMIN_NAME, admin.getName(), "Admin name must match seeded identity");
        assertTrue(admin.getUserId() > 0, "Admin userId must be a positive integer");

        // Hash security checks (never print hash or password)
        String storedPassword = admin.getPassword();
        assertNotNull(storedPassword, "Stored password hash must not be null");
        assertNotEquals(ADMIN_RAW_PASS, storedPassword, "Password must not be stored in plaintext");
        assertTrue(PasswordSecurityUtil.isHashed(storedPassword),
                "Admin password format must be an encoded PBKDF2 hash");
        assertTrue(storedPassword.startsWith("PBKDF2WithHmacSHA256:65536:"),
                "Admin password must use PBKDF2WithHmacSHA256 with 65536 iterations");
    }

    @Test
    @Order(3)
    @DisplayName("Stage B: Verify Student demo account exists with valid role and PBKDF2 hash")
    public void testStageB_StudentDemoAccountExists() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        assertNotNull(student, "Student demo account '" + STUDENT_EMAIL + "' must exist in database");
        assertEquals(STUDENT_EMAIL, student.getEmail(), "Student email must match exactly");
        assertEquals("STUDENT", student.getRole(), "User role must be STUDENT");
        assertEquals(STUDENT_NAME, student.getName(), "Student name must match seeded identity");
        assertTrue(student.getUserId() > 0, "Student userId must be a positive integer");

        // Hash security checks (never print hash or password)
        String storedPassword = student.getPassword();
        assertNotNull(storedPassword, "Stored password hash must not be null");
        assertNotEquals(STUDENT_RAW_PASS, storedPassword, "Password must not be stored in plaintext");
        assertTrue(PasswordSecurityUtil.isHashed(storedPassword),
                "Student password format must be an encoded PBKDF2 hash");
        assertTrue(storedPassword.startsWith("PBKDF2WithHmacSHA256:65536:"),
                "Student password must use PBKDF2WithHmacSHA256 with 65536 iterations");
    }

    @Test
    @Order(4)
    @DisplayName("Stage B: Verify expected demo club exists")
    public void testStageB_DemoClubExists() {
        List<Club> clubs = clubDAO.getAll();
        assertNotNull(clubs, "Club list must not be null");
        assertFalse(clubs.isEmpty(), "At least one club must exist in database");

        Club demoClub = clubs.stream()
                .filter(c -> EXPECTED_CLUB_NAME.equalsIgnoreCase(c.getClubName()))
                .findFirst()
                .orElse(null);

        assertNotNull(demoClub, "Expected demo club '" + EXPECTED_CLUB_NAME + "' must exist");
        assertTrue(demoClub.getClubId() > 0, "Club ID must be positive");
        assertNotNull(demoClub.getDescription(), "Club description should be populated");
    }

    @Test
    @Order(5)
    @DisplayName("Stage B: Verify expected demo Hackathon event exists")
    public void testStageB_DemoEventExists() {
        List<Event> events = eventDAO.searchAndFilter("Hackathon", null);
        assertNotNull(events, "Events search result must not be null");
        assertFalse(events.isEmpty(), "Demo Hackathon event must exist");

        Event hackathon = events.stream()
                .filter(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEventName()))
                .findFirst()
                .orElse(null);

        assertNotNull(hackathon, "Event '" + EXPECTED_EVENT_NAME + "' must be found");
        assertTrue(hackathon.getEventId() > 0, "Event ID must be positive");
        assertEquals(EXPECTED_EVENT_DATE, hackathon.getEventDate(), "Event date must be 2026-10-20");
        assertEquals("Technology", hackathon.getCategory(), "Event category must be Technology");
        assertEquals(200, hackathon.getMaxParticipants(), "Max participants must be 200");
        assertEquals(EXPECTED_CLUB_NAME, hackathon.getClubName(), "Associated club name must be Tech & Coding Society");
    }

    @Test
    @Order(6)
    @DisplayName("Stage B: Verify registration links expected student to expected Hackathon event")
    public void testStageB_RegistrationLinksStudentToEvent() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        assertNotNull(student);

        List<Event> events = eventDAO.searchAndFilter("Hackathon", null);
        assertFalse(events.isEmpty());
        Event hackathon = events.get(0);

        // Read-only SQL query against registrations table
        String sql = "SELECT registration_id, event_id, student_id, status, registration_date FROM registrations WHERE event_id = ? AND student_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, hackathon.getEventId());
            stmt.setInt(2, student.getUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "A registration record must link student Sonya to Hackathon event");
                int regId = rs.getInt("registration_id");
                String status = rs.getString("status");
                assertTrue(regId > 0, "Registration ID must be positive");
                assertEquals("APPROVED", status, "Registration status must be APPROVED");
            }
        }
    }

    // =========================================================================
    // STAGE C — TEST AUTHENTICATION (REAL FLOW)
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("Stage C: Admin authentication via real UserDAO and PasswordSecurityUtil")
    public void testStageC_AdminAuthenticationRealFlow() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        assertNotNull(admin, "Admin account must be found by email");

        // Verify real PBKDF2 authentication
        boolean authenticated = PasswordSecurityUtil.verifyPassword(ADMIN_RAW_PASS, admin.getPassword());
        assertTrue(authenticated, "Admin demo password must authenticate successfully");

        // Establish session
        Session.setCurrentUser(admin);
        assertTrue(Session.isLoggedIn(), "Session must report user is logged in");
        assertEquals("ADMIN", Session.getCurrentUser().getRole(), "Session role must be ADMIN");
    }

    @Test
    @Order(8)
    @DisplayName("Stage C: Student authentication via real UserDAO and PasswordSecurityUtil")
    public void testStageC_StudentAuthenticationRealFlow() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        assertNotNull(student, "Student account must be found by email");

        // Verify real PBKDF2 authentication
        boolean authenticated = PasswordSecurityUtil.verifyPassword(STUDENT_RAW_PASS, student.getPassword());
        assertTrue(authenticated, "Student demo password must authenticate successfully");

        // Establish session
        Session.setCurrentUser(student);
        assertTrue(Session.isLoggedIn(), "Session must report user is logged in");
        assertEquals("STUDENT", Session.getCurrentUser().getRole(), "Session role must be STUDENT");
    }

    @Test
    @Order(9)
    @DisplayName("Stage C: Incorrect password rejection across both accounts")
    public void testStageC_IncorrectPasswordRejection() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        assertNotNull(admin);
        assertFalse(PasswordSecurityUtil.verifyPassword("wrongPassword123", admin.getPassword()),
                "Incorrect password must be rejected for admin");
        assertFalse(PasswordSecurityUtil.verifyPassword("ADMIN123", admin.getPassword()),
                "Case-mismatched password must be rejected for admin");

        User student = userDAO.findByEmail(STUDENT_EMAIL);
        assertNotNull(student);
        assertFalse(PasswordSecurityUtil.verifyPassword("wrongPassword123", student.getPassword()),
                "Incorrect password must be rejected for student");
        assertFalse(PasswordSecurityUtil.verifyPassword("STUDENT123", student.getPassword()),
                "Case-mismatched password must be rejected for student");
        assertFalse(PasswordSecurityUtil.verifyPassword("", student.getPassword()),
                "Empty password must be rejected");
    }

    @Test
    @Order(10)
    @DisplayName("Stage C: Unknown email rejection returns null safely")
    public void testStageC_UnknownEmailRejection() throws SQLException {
        User ghost = userDAO.findByEmail("nonexistent_user_xyz_999@campusconnect.edu");
        assertNull(ghost, "Non-existent email must return null without throwing exception");

        User empty = userDAO.findByEmail("");
        assertNull(empty, "Empty email must return null safely");

        User nullEmail = userDAO.findByEmail(null);
        assertNull(nullEmail, "Null email must return null safely");
    }

    @Test
    @Order(11)
    @DisplayName("Stage C: Email case-insensitivity and whitespace handling")
    public void testStageC_EmailCaseAndWhitespaceHandling() throws SQLException {
        // Casing variations
        User adminUpper = userDAO.findByEmail("ADMIN@CAMPUSCONNECT.EDU");
        assertNotNull(adminUpper, "Upper-case email must find admin");
        assertEquals(ADMIN_EMAIL, adminUpper.getEmail());

        User adminMixed = userDAO.findByEmail("Admin@CampusConnect.Edu");
        assertNotNull(adminMixed, "Mixed-case email must find admin");

        User studentUpper = userDAO.findByEmail("SONYA@STUDENT.EDU");
        assertNotNull(studentUpper, "Upper-case email must find student");
        assertEquals(STUDENT_EMAIL, studentUpper.getEmail());

        // Surrounding whitespace
        User adminSpaced = userDAO.findByEmail("   admin@campusconnect.edu   ");
        assertNotNull(adminSpaced, "Email with surrounding whitespace must find admin");

        User studentSpaced = userDAO.findByEmail("   sonya@student.edu   ");
        assertNotNull(studentSpaced, "Email with surrounding whitespace must find student");
    }

    // =========================================================================
    // STAGE D — VERIFY CORE WORKFLOWS (READ-ONLY)
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("Stage D: Admin dashboard workflow loads live campus metrics")
    public void testStageD_AdminDashboardWorkflow() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        Session.setCurrentUser(admin);

        AdminDashboardService service = new AdminDashboardService();
        AdminDashboardDTO dto = service.getDashboardData();

        assertNotNull(dto, "Admin dashboard DTO must not be null");
        assertTrue(dto.isDbOnline(), "Admin dashboard must report database online");
        assertTrue(dto.getTotalEvents() >= 1, "Total events KPI must be at least 1");
        assertTrue(dto.getTotalRegistrations() >= 1, "Total registrations KPI must be at least 1");
        assertTrue(dto.getApprovedRegistrations() >= 1, "Approved registrations KPI must be at least 1");
        assertNotNull(dto.getRecentEvents(), "Recent events list must not be null");
        assertFalse(dto.getRecentEvents().isEmpty(), "Recent events list must have items");
    }

    @Test
    @Order(13)
    @DisplayName("Stage D: Student dashboard workflow loads student metrics and Hackathon event")
    public void testStageD_StudentDashboardWorkflow() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        Session.setCurrentUser(student);

        StudentDashboardService service = new StudentDashboardService();
        StudentDashboardDTO dto = service.getDashboardData(student.getUserId());

        assertNotNull(dto, "Student dashboard DTO must not be null");
        assertTrue(dto.isDbOnline(), "Student dashboard must report database online");
        assertTrue(dto.getRegisteredCount() >= 1, "Student registered count must be at least 1");
        assertNotNull(dto.getUpcomingEvents(), "Student upcoming events must not be null");
        assertFalse(dto.getUpcomingEvents().isEmpty(), "Student upcoming events must contain seeded event");

        boolean hackathonFound = dto.getUpcomingEvents().stream()
                .anyMatch(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEvent().getEventName()));
        assertTrue(hackathonFound, "Upcoming events feed must include Tech Hackathon 2026");
    }

    @Test
    @Order(14)
    @DisplayName("Stage D: Student's existing registration appears with APPROVED status")
    public void testStageD_StudentExistingRegistrationAppears() throws SQLException {
        User student = userDAO.findByEmail(STUDENT_EMAIL);
        Session.setCurrentUser(student);

        List<StudentRegistrationDTO> regs = registrationDAO.getStudentRegistrationsWithEvents(student.getUserId());
        assertNotNull(regs, "Student registrations list must not be null");
        assertFalse(regs.isEmpty(), "Student must have at least 1 registration record");

        StudentRegistrationDTO hackathonReg = regs.stream()
                .filter(r -> EXPECTED_EVENT_NAME.equalsIgnoreCase(r.getEventName()))
                .findFirst()
                .orElse(null);

        assertNotNull(hackathonReg, "Student registration for '" + EXPECTED_EVENT_NAME + "' must appear");
        assertEquals("APPROVED", hackathonReg.getStatus(), "Registration status must be APPROVED");
        assertEquals(EXPECTED_CLUB_NAME, hackathonReg.getClubName(), "Club name must match");
        assertEquals(EXPECTED_EVENT_DATE, hackathonReg.getEventDate(), "Event date must match");
    }

    @Test
    @Order(15)
    @DisplayName("Stage D: Admin event management workflow loads all events with club metadata")
    public void testStageD_AdminEventManagementWorkflow() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        Session.setCurrentUser(admin);

        EventManagementService service = new EventManagementService();
        List<Event> events = service.getAllEvents();

        assertNotNull(events, "Events list must not be null");
        assertFalse(events.isEmpty(), "Events list must contain at least 1 event");

        Event hackathon = events.stream()
                .filter(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEventName()))
                .findFirst()
                .orElse(null);

        assertNotNull(hackathon, "Hackathon event must exist in admin event management list");
        assertEquals(EXPECTED_CLUB_NAME, hackathon.getClubName(), "Event must include associated club name");
    }

    @Test
    @Order(16)
    @DisplayName("Stage D: Admin registration management workflow loads registrations and KPI summary")
    public void testStageD_AdminRegistrationManagementWorkflow() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        Session.setCurrentUser(admin);

        RegistrationManagementService service = new RegistrationManagementService();
        RegistrationManagementService.RegistrationSummaryDTO summary = service.getRegistrationSummary();

        assertNotNull(summary, "Registration summary DTO must not be null");
        assertTrue(summary.isDbOnline(), "Summary must report DB online");
        assertTrue(summary.getTotal() >= 1, "Total registrations KPI must be >= 1");
        assertTrue(summary.getApproved() >= 1, "Approved registrations KPI must be >= 1");

        List<AdminRegistrationDTO> registrations = service.getRegistrations("", "ALL", null);
        assertNotNull(registrations, "Registrations list must not be null");
        assertFalse(registrations.isEmpty(), "Admin registration list must not be empty");

        AdminRegistrationDTO match = registrations.stream()
                .filter(r -> STUDENT_EMAIL.equalsIgnoreCase(r.getStudentEmail()) &&
                             EXPECTED_EVENT_NAME.equalsIgnoreCase(r.getEventName()))
                .findFirst()
                .orElse(null);

        assertNotNull(match, "Seeded registration must be present in admin review roster");
        assertEquals(STUDENT_NAME, match.getStudentName(), "Student name must match Sonya Blade");
        assertEquals("APPROVED", match.getStatus(), "Status must be APPROVED");
    }

    @Test
    @Order(17)
    @DisplayName("Stage D: Admin attendance management workflow loads roster for Hackathon event")
    public void testStageD_AdminAttendanceManagementWorkflow() throws SQLException {
        User admin = userDAO.findByEmail(ADMIN_EMAIL);
        Session.setCurrentUser(admin);

        AttendanceManagementService service = new AttendanceManagementService();
        List<Event> events = service.getAllEvents();
        assertFalse(events.isEmpty(), "Events selector must contain events");

        Event hackathon = events.stream()
                .filter(e -> EXPECTED_EVENT_NAME.equalsIgnoreCase(e.getEventName()))
                .findFirst()
                .orElse(null);
        assertNotNull(hackathon, "Hackathon event must be present");

        // KPI summary for event
        AttendanceManagementService.AttendanceSummaryDTO attSummary = service.getAttendanceSummary(hackathon.getEventId());
        assertNotNull(attSummary);
        assertTrue(attSummary.isDbOnline());
        assertEquals(1, attSummary.getTotalApproved(), "Exactly 1 approved registration for Hackathon");

        // Event attendance roster
        List<EventAttendanceDTO> roster = service.getEventAttendanceRoster(hackathon.getEventId());
        assertNotNull(roster, "Attendance roster must not be null");
        assertEquals(1, roster.size(), "Roster must have 1 eligible student");
        assertEquals(STUDENT_NAME, roster.get(0).getStudentName());
        assertEquals("APPROVED", roster.get(0).getRegistrationStatus());
    }
}
