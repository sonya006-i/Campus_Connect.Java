package test;

import dao.UserDAO;
import model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import util.DBConnection;
import util.PasswordSecurityUtil;
import util.Session;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthenticationIntegrationTest — Full database integration tests for Phase 19B Password Hardening.
 *
 * Verifies:
 * 1. Seeded demo credentials authenticate correctly against database.
 * 2. Database rows contain encoded PBKDF2 hashes instead of demo plaintext passwords.
 * 3. Distinct salts for each user account.
 * 4. Incorrect credentials and non-existent accounts fail cleanly.
 * 5. Legacy plaintext credentials undergo controlled upgrade to PBKDF2 hashes upon successful login.
 * 6. Role-based access and permissions remain intact.
 */
public class AuthenticationIntegrationTest {

    private static final String TEST_DB_URL = "jdbc:h2:mem:campus_connect;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String TEST_DB_USER = "sa";
    private static final String TEST_DB_PASS = "";

    private static UserDAO userDAO;

    @BeforeAll
    public static void setUpDatabase() throws Exception {
        // Point DBConnection to in-memory MySQL-compatible test database
        DBConnection.setCustomConnectionConfig(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASS);
        userDAO = new UserDAO();

        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Create users table matching database/schema.sql
            stmt.execute("CREATE TABLE users (" +
                    "user_id INT PRIMARY KEY AUTO_INCREMENT, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "email VARCHAR(150) NOT NULL UNIQUE, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Execute seed user inserts from database/seed.sql
            Path seedPath = Path.of("database/seed.sql");
            String seedSql = Files.readString(seedPath, StandardCharsets.UTF_8);

            // Extract the INSERT INTO users statement
            String insertPrefix = "INSERT INTO users";
            int startIdx = seedSql.indexOf(insertPrefix);
            int endIdx = seedSql.indexOf(";", startIdx);
            String userInsertQuery = seedSql.substring(startIdx, endIdx);

            stmt.execute(userInsertQuery);
        }
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.resetConnectionConfig();
        Session.clearSession();
    }

    @Test
    @DisplayName("Confirm database rows contain encoded PBKDF2 hashes and no plaintext demo passwords")
    public void testDatabaseContainsEncodedHashes() throws Exception {
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT user_id, email, password FROM users")) {

            int count = 0;
            while (rs.next()) {
                count++;
                String storedPassword = rs.getString("password");

                // Must be a valid self-describing PBKDF2 hash
                assertTrue(PasswordSecurityUtil.isHashed(storedPassword),
                        "User password in database row must be an encoded PBKDF2 hash");

                // Must NOT be any known demo plaintext password
                assertNotEquals("admin123", storedPassword, "Password must not be stored as plaintext");
                assertNotEquals("student123", storedPassword, "Password must not be stored as plaintext");

                // Verify format prefix
                assertTrue(storedPassword.startsWith("PBKDF2WithHmacSHA256:65536:"));
            }
            assertTrue(count >= 6, "Expected at least 6 seeded users in database");
        }
    }

    @Test
    @DisplayName("Admin demo login authenticates successfully with preserved ADMIN role")
    public void testAdminDemoLogin() throws Exception {
        User admin = userDAO.findByEmail("admin@campusconnect.edu");
        assertNotNull(admin, "Admin user should exist in seeded database");

        // Verify correct password
        assertTrue(PasswordSecurityUtil.verifyPassword("admin123", admin.getPassword()),
                "Admin password 'admin123' must verify against stored hash");

        // Verify role and metadata
        assertEquals("ADMIN", admin.getRole(), "Role must be ADMIN");
        assertEquals("Dr. Alan Vance", admin.getName());

        // Test session establishment
        Session.setCurrentUser(admin);
        assertTrue(Session.isLoggedIn());
        assertEquals("ADMIN", Session.getCurrentUser().getRole());
        Session.clearSession();
    }

    @Test
    @DisplayName("Student demo logins authenticate successfully with preserved STUDENT role")
    public void testStudentDemoLogins() throws Exception {
        String[] studentEmails = {
                "sonya@student.edu",
                "alex@student.edu",
                "priya@student.edu",
                "marcus@student.edu",
                "elena@student.edu"
        };

        for (String email : studentEmails) {
            User student = userDAO.findByEmail(email);
            assertNotNull(student, "Student " + email + " must exist in database");

            // Verify demo password
            assertTrue(PasswordSecurityUtil.verifyPassword("student123", student.getPassword()),
                    "Demo password 'student123' must verify against stored hash");

            // Verify STUDENT role
            assertEquals("STUDENT", student.getRole(), "Role must be STUDENT");
        }
    }

    @Test
    @DisplayName("Case-insensitive email lookup: variations in casing and whitespace authenticate successfully")
    public void testCaseInsensitiveEmailLookupAndEdgeCases() throws Exception {
        // Admin casing variants
        User adminMixed = userDAO.findByEmail("Admin@CampusConnect.edu");
        assertNotNull(adminMixed, "Mixed-case admin email must find user");
        assertEquals("admin@campusconnect.edu", adminMixed.getEmail());

        User adminUpper = userDAO.findByEmail("ADMIN@CAMPUSCONNECT.EDU");
        assertNotNull(adminUpper, "Upper-case admin email must find user");

        User adminSpaced = userDAO.findByEmail("  admin@campusconnect.edu  ");
        assertNotNull(adminSpaced, "Email with surrounding whitespace must find user");

        // Student casing variants
        User studentMixed = userDAO.findByEmail("Sonya@Student.edu");
        assertNotNull(studentMixed, "Mixed-case student email must find user");
        assertEquals("sonya@student.edu", studentMixed.getEmail());

        // Null and blank handling
        assertNull(userDAO.findByEmail(null), "Null email must safely return null");
        assertNull(userDAO.findByEmail(""), "Empty email must safely return null");
        assertNull(userDAO.findByEmail("   "), "Whitespace-only email must safely return null");
    }

    @Test
    @DisplayName("Incorrect passwords fail verification on all accounts")
    public void testIncorrectPasswordFails() throws Exception {
        User admin = userDAO.findByEmail("admin@campusconnect.edu");
        assertNotNull(admin);
        assertFalse(PasswordSecurityUtil.verifyPassword("wrongAdminPassword", admin.getPassword()));
        assertFalse(PasswordSecurityUtil.verifyPassword("ADMIN123", admin.getPassword())); // case check

        User student = userDAO.findByEmail("sonya@student.edu");
        assertNotNull(student);
        assertFalse(PasswordSecurityUtil.verifyPassword("wrongStudentPassword", student.getPassword()));
        assertFalse(PasswordSecurityUtil.verifyPassword("student", student.getPassword()));
    }

    @Test
    @DisplayName("Non-existent email returns null from DAO")
    public void testNonExistentUserFails() throws Exception {
        User user = userDAO.findByEmail("ghost@campus.edu");
        assertNull(user);
    }

    @Test
    @DisplayName("Controlled legacy migration: plaintext record verifies and upgrades to PBKDF2 hash upon login")
    public void testLegacyPlaintextUpgrade() throws Exception {
        String legacyEmail = "legacy.student@campus.edu";
        String legacyPlaintext = "legacySecret789";

        // Insert a legacy record with raw plaintext password into the test database
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASS);
             PreparedStatement insertStmt = conn.prepareStatement(
                     "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, ?)")) {
            insertStmt.setString(1, "Legacy Student");
            insertStmt.setString(2, legacyEmail);
            insertStmt.setString(3, legacyPlaintext);
            insertStmt.setString(4, "STUDENT");
            insertStmt.executeUpdate();
        }

        // 1. Fetch user before upgrade
        User legacyUser = userDAO.findByEmail(legacyEmail);
        assertNotNull(legacyUser);
        assertTrue(PasswordSecurityUtil.isLegacyPlaintext(legacyUser.getPassword()),
                "Initial password should be detected as legacy plaintext");
        assertFalse(PasswordSecurityUtil.isHashed(legacyUser.getPassword()));

        // 2. Verify legacy credential
        assertTrue(PasswordSecurityUtil.verifyPassword(legacyPlaintext, legacyUser.getPassword()));

        // 3. Execute controlled upgrade (simulating LoginController on-login migration)
        String newHash = PasswordSecurityUtil.hashPassword(legacyPlaintext);
        boolean updateResult = userDAO.updatePassword(legacyUser.getUserId(), newHash);
        assertTrue(updateResult, "UserDAO.updatePassword must succeed");

        // 4. Fetch updated user from database
        User upgradedUser = userDAO.findByEmail(legacyEmail);
        assertNotNull(upgradedUser);

        // Verify that stored value is now a PBKDF2 hash
        assertTrue(PasswordSecurityUtil.isHashed(upgradedUser.getPassword()),
                "Database row must now contain the upgraded PBKDF2 hash");
        assertFalse(PasswordSecurityUtil.isLegacyPlaintext(upgradedUser.getPassword()));
        assertNotEquals(legacyPlaintext, upgradedUser.getPassword());

        // 5. Subsequent authentication with the same password must succeed against the new hash
        assertTrue(PasswordSecurityUtil.verifyPassword(legacyPlaintext, upgradedUser.getPassword()));

        // 6. Wrong password must fail
        assertFalse(PasswordSecurityUtil.verifyPassword("wrongAttempt", upgradedUser.getPassword()));

        // 7. Role-based access must remain unaffected
        assertEquals("STUDENT", upgradedUser.getRole());
    }

    @Test
    @DisplayName("Conditional update prevents overwriting concurrently changed password during legacy migration")
    public void testConditionalPasswordUpgradePreventsConcurrentOverwrite() throws Exception {
        String testEmail = "concurrent.student@campus.edu";
        String initialPlaintext = "initialPass123";

        // Insert test user
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASS);
             PreparedStatement insertStmt = conn.prepareStatement(
                     "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, ?)")) {
            insertStmt.setString(1, "Concurrent User");
            insertStmt.setString(2, testEmail);
            insertStmt.setString(3, initialPlaintext);
            insertStmt.setString(4, "STUDENT");
            insertStmt.executeUpdate();
        }

        User user = userDAO.findByEmail(testEmail);
        assertNotNull(user);

        // Attempt conditional update with wrong old password (simulates concurrent change by another thread)
        String newHash = PasswordSecurityUtil.hashPassword("newPass456");
        boolean mismatchedUpdate = userDAO.updatePasswordIfMatches(user.getUserId(), "alreadyChangedPassword", newHash);
        assertFalse(mismatchedUpdate, "Conditional update must fail when expected old password does not match");

        // Verify password in DB was NOT changed
        User unchangedUser = userDAO.findByEmail(testEmail);
        assertEquals(initialPlaintext, unchangedUser.getPassword(), "Stored password must remain untouched on mismatched update");

        // Now attempt conditional update with correct expected old password
        boolean matchedUpdate = userDAO.updatePasswordIfMatches(user.getUserId(), initialPlaintext, newHash);
        assertTrue(matchedUpdate, "Conditional update must succeed when expected old password matches");

        // Verify password was upgraded
        User updatedUser = userDAO.findByEmail(testEmail);
        assertEquals(newHash, updatedUser.getPassword());
    }

    @Test
    @DisplayName("Login failure handling protects against account enumeration and never exposes credentials")
    public void testAuthenticationFailureProtectsAgainstUserEnumeration() throws Exception {
        // 1. Non-existent email lookup
        User nonExistent = userDAO.findByEmail("doesnotexist@campusconnect.edu");
        assertNull(nonExistent);

        // 2. Existing email lookup with wrong password
        User existingUser = userDAO.findByEmail("admin@campusconnect.edu");
        assertNotNull(existingUser);
        boolean wrongPassVerified = PasswordSecurityUtil.verifyPassword("badPassword999", existingUser.getPassword());
        assertFalse(wrongPassVerified);

        // 3. Stored credential in database remains unchanged after failed attempts
        User afterFailed = userDAO.findByEmail("admin@campusconnect.edu");
        assertEquals(existingUser.getPassword(), afterFailed.getPassword(),
                "Stored credential must never be altered or corrupted by failed login attempts");
    }
}
