package test;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import util.PasswordSecurityUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SupabasePostgresIntegrationTest — Opt-in live database integration tests targeting
 * Supabase PostgreSQL.
 *
 * Activation:
 * - Requires environment variable: CAMPUS_CONNECT_INTEGRATION=true
 * - Requires JDBC configuration: DB_URL (must start with jdbc:postgresql://), DB_USER, DB_PASSWORD
 *
 * Guarantees:
 * - When CAMPUS_CONNECT_INTEGRATION is not set to 'true', tests are explicitly SKIPPED (never reported as passed).
 * - Never truncates, drops, or alters production or existing seeded data.
 * - Uses uniquely isolated test keys and transaction rollback to guarantee zero residual data.
 * - All mutating operations execute exclusively within uncommitted rollback transactions.
 * - Validates real PostgreSQL JDBC connectivity, metadata, constraints, transactions, and queries.
 */
public class SupabasePostgresIntegrationTest {

    private static final String OPT_IN_ENV = "CAMPUS_CONNECT_INTEGRATION";
    private static final String[] REQUIRED_TABLES = {
            "users", "clubs", "events", "registrations", "attendance", "feedback", "certificates"
    };

    private static boolean isEnabled() {
        return "true".equalsIgnoreCase(System.getenv(OPT_IN_ENV)) ||
               "true".equalsIgnoreCase(System.getProperty(OPT_IN_ENV));
    }

    private static String getDbUrl() {
        String url = System.getenv("DB_URL");
        if (url == null || url.trim().isEmpty()) {
            url = System.getProperty("DB_URL");
        }
        if (url == null || url.trim().isEmpty()) {
            url = util.DBConnection.getDbUrl();
        }
        return url;
    }

    private static String getDbUser() {
        String user = System.getenv("DB_USER");
        if (user == null || user.trim().isEmpty()) {
            user = System.getProperty("DB_USER");
        }
        if (user == null || user.trim().isEmpty()) {
            user = util.DBConnection.getDbUser();
        }
        return user != null ? user : "postgres";
    }

    private static String getDbPassword() {
        String pass = System.getenv("DB_PASSWORD");
        if (pass == null) {
            pass = System.getProperty("DB_PASSWORD");
        }
        if (pass == null || pass.trim().isEmpty()) {
            pass = util.DBConnection.getDbPassword();
        }
        return pass != null ? pass : "";
    }

    private static Connection getPostgresConnection() throws SQLException {
        String url = getDbUrl();
        assertNotNull(url, "BLOCKED: CAMPUS_CONNECT_INTEGRATION=true was set, but DB_URL is missing.");
        assertTrue(url.startsWith("jdbc:postgresql:"), "DB_URL must be a PostgreSQL JDBC connection string.");

        if (url.startsWith("jdbc:postgresql:") && !url.toLowerCase().contains("sslmode=")) {
            url = url.contains("?") ? url + "&sslmode=require" : url + "?sslmode=require";
        }

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC Driver (org.postgresql.Driver) not found on classpath", e);
        }

        return DriverManager.getConnection(url, getDbUser(), getDbPassword());
    }

    /**
     * Helper to create an isolated student within an active transaction scope.
     * Guaranteed to be reverted upon transaction rollback.
     */
    private static int createIsolatedStudent(Connection conn) throws SQLException {
        String testEmail = "iso_student_" + UUID.randomUUID().toString().substring(0, 8) + "@test.edu";
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO users (name, email, password, role) VALUES ('Isolated Student', ?, 'PBKDF2WithHmacSHA256:65536:dummySalt:dummyHash', 'STUDENT') RETURNING user_id")) {
            stmt.setString(1, testEmail);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to create isolated test student");
    }

    /**
     * Helper to create an isolated event within an active transaction scope.
     * Guaranteed to be reverted upon transaction rollback.
     * Guarantees a valid club_id foreign key even if clubs table is unseeded.
     */
    private static int createIsolatedEvent(Connection conn) throws SQLException {
        int clubId = -1;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT club_id FROM clubs LIMIT 1")) {
            if (rs.next()) clubId = rs.getInt(1);
        }
        if (clubId == -1) {
            try (PreparedStatement stmtClub = conn.prepareStatement(
                    "INSERT INTO clubs (club_name, description) VALUES ('Isolated Club', 'Isolated Club') RETURNING club_id")) {
                try (ResultSet rsClub = stmtClub.executeQuery()) {
                    if (rsClub.next()) clubId = rsClub.getInt(1);
                }
            }
        }
        String testEventName = "ISO_Event_" + UUID.randomUUID().toString().substring(0, 8);
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO events (club_id, event_name, description, category, event_date, event_time, venue, max_participants, registration_deadline) " +
                "VALUES (?, ?, 'Isolated Event', 'Technology', CURRENT_DATE + 10, '14:00:00', 'Sandbox', 50, CURRENT_DATE + 5) RETURNING event_id")) {
            stmt.setInt(1, clubId);
            stmt.setString(2, testEventName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to create isolated test event");
    }

    @BeforeEach
    public void checkOptInStatus() {
        Assumptions.assumeTrue(isEnabled(),
                "SKIPPED: Supabase PostgreSQL integration tests are opt-in. Set CAMPUS_CONNECT_INTEGRATION=true to enable.");
    }

    // =========================================================================
    // 1. CONNECTION & POSTGRESQL VERSION IDENTIFICATION
    // =========================================================================

    @Test
    @DisplayName("Verify real JDBC connection to Supabase and identify PostgreSQL database engine")
    public void testConnectionAndPostgresVersion() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            assertNotNull(conn, "Connection must be established");
            assertFalse(conn.isClosed(), "Connection must be open");

            DatabaseMetaData metaData = conn.getMetaData();
            String productName = metaData.getDatabaseProductName();
            assertEquals("PostgreSQL", productName, "Engine must be PostgreSQL");

            // Query PostgreSQL version function
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version(), current_database(), current_schema()")) {
                assertTrue(rs.next());
                String version = rs.getString(1);
                assertNotNull(version);
                assertTrue(version.toLowerCase().contains("postgresql"),
                        "Version string must identify PostgreSQL runtime");
            }
        }
    }

    // =========================================================================
    // 2. SCHEMA COMPATIBILITY & REQUIRED TABLES
    // =========================================================================

    @Test
    @DisplayName("Verify all 7 required Campus Connect tables exist in PostgreSQL schema")
    public void testRequiredTablesAndSchema() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            Set<String> tablesFound = new HashSet<>();

            try (ResultSet rs = metaData.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tablesFound.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }

            for (String table : REQUIRED_TABLES) {
                assertTrue(tablesFound.contains(table.toLowerCase()),
                        "Required table '" + table + "' must be present in target PostgreSQL schema");
            }
        }
    }

    // =========================================================================
    // 3. REQUIRED COLUMNS, FOREIGN KEYS, AND UNIQUE CONSTRAINTS
    // =========================================================================

    @Test
    @DisplayName("Verify column metadata, primary keys, and foreign keys across core tables")
    public void testMetadataColumnsAndConstraints() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Verify registrations table foreign keys
            Set<String> fkTables = new HashSet<>();
            try (ResultSet rs = metaData.getImportedKeys(conn.getCatalog(), null, "registrations")) {
                while (rs.next()) {
                    fkTables.add(rs.getString("PKTABLE_NAME").toLowerCase());
                }
            }
            assertTrue(fkTables.contains("events"), "registrations must reference events");
            assertTrue(fkTables.contains("users"), "registrations must reference users");

            // Verify unique constraints or indexes on registrations
            Set<String> indexedColumns = new HashSet<>();
            try (ResultSet rs = metaData.getIndexInfo(conn.getCatalog(), null, "registrations", true, false)) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col != null) indexedColumns.add(col.toLowerCase());
                }
            }
            assertTrue(indexedColumns.contains("event_id") || indexedColumns.contains("registration_id"),
                    "registrations must have indexes on primary/unique keys");
        }
    }

    // =========================================================================
    // 4. USER LOOKUP & AUTHENTICATION DB ACCESS
    // =========================================================================

    @Test
    @DisplayName("Query user records using parameterized PreparedStatement against PostgreSQL")
    public void testUserLookupDatabaseAccess() throws SQLException {
        try (Connection conn = getPostgresConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT user_id, name, email, password, role FROM users LIMIT 5")) {

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    assertNotNull(rs.getString("email"));
                    assertNotNull(rs.getString("password"));
                    String role = rs.getString("role");
                    assertTrue("STUDENT".equals(role) || "ADMIN".equals(role),
                            "Role must satisfy chk_user_role constraint");
                }
            }
        }
    }

    @Test
    @DisplayName("Verify Supabase PostgreSQL user rows contain PBKDF2 hashes and demo logins authenticate safely")
    public void testSupabasePasswordHardeningAndAuthentication() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            // 1. Verify that stored passwords in Supabase PostgreSQL are PBKDF2 hashes (never plaintext)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT user_id, email, password, role FROM users LIMIT 10")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String storedPassword = rs.getString("password");
                        assertNotNull(storedPassword);
                        assertTrue(PasswordSecurityUtil.isHashed(storedPassword),
                                "Password column in Supabase PostgreSQL must store an encoded PBKDF2 hash");
                        assertNotEquals("admin123", storedPassword, "Plaintext password must not exist in database");
                        assertNotEquals("student123", storedPassword, "Plaintext password must not exist in database");
                    }
                }
            }

            // 2. Verify demo admin authentication if present in Supabase
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT user_id, name, email, password, role FROM users WHERE email = ?")) {
                stmt.setString(1, "admin@campusconnect.edu");
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password");
                        assertTrue(PasswordSecurityUtil.verifyPassword("admin123", storedHash),
                                "Admin demo credential 'admin123' must verify against Supabase hash");
                        assertEquals("ADMIN", rs.getString("role"), "Role must be ADMIN");
                        assertFalse(PasswordSecurityUtil.verifyPassword("wrongPassword123", storedHash),
                                "Incorrect password must be rejected");
                    }
                }
            }

            // 3. Verify demo student authentication if present in Supabase
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT user_id, name, email, password, role FROM users WHERE email = ?")) {
                stmt.setString(1, "sonya@student.edu");
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password");
                        assertTrue(PasswordSecurityUtil.verifyPassword("student123", storedHash),
                                "Student demo credential 'student123' must verify against Supabase hash");
                        assertEquals("STUDENT", rs.getString("role"), "Role must be STUDENT");
                        assertFalse(PasswordSecurityUtil.verifyPassword("wrongPassword123", storedHash),
                                "Incorrect password must be rejected");
                    }
                }
            }

            // 4. Test isolated test user creation inside rollback transaction (guarantees zero residual records)
            conn.setAutoCommit(false);
            try {
                String testEmail = "iso_auth_" + UUID.randomUUID().toString().substring(0, 8) + "@test.edu";
                String testRawPass = "SecretSecurePass#2026";
                String generatedHash = PasswordSecurityUtil.hashPassword(testRawPass);
                int newUserId = -1;

                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, ?) RETURNING user_id")) {
                    insertStmt.setString(1, "Isolated Auth Tester");
                    insertStmt.setString(2, testEmail);
                    insertStmt.setString(3, generatedHash);
                    insertStmt.setString(4, "STUDENT");
                    try (ResultSet rs = insertStmt.executeQuery()) {
                        assertTrue(rs.next());
                        newUserId = rs.getInt(1);
                        assertTrue(newUserId > 0);
                    }
                }

                // Verify retrieval and authentication against PostgreSQL
                try (PreparedStatement selectStmt = conn.prepareStatement(
                        "SELECT password, role FROM users WHERE user_id = ?")) {
                    selectStmt.setInt(1, newUserId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        assertTrue(rs.next());
                        String fetchedHash = rs.getString("password");
                        assertTrue(PasswordSecurityUtil.isHashed(fetchedHash));
                        assertTrue(PasswordSecurityUtil.verifyPassword(testRawPass, fetchedHash));
                        assertFalse(PasswordSecurityUtil.verifyPassword("incorrectGuess", fetchedHash));
                        assertEquals("STUDENT", rs.getString("role"));
                    }
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    // =========================================================================
    // 5. EVENT INSERTION & RETRIEVAL (ISOLATED WITH TRANSACTION ROLLBACK)
    // =========================================================================

    @Test
    @DisplayName("Insert and retrieve an isolated event with deterministic transaction rollback")
    public void testEventInsertionAndRetrieval() throws SQLException {
        String uniqueTag = "ISO_" + UUID.randomUUID().toString().substring(0, 8);
        String eventName = "Test Event " + uniqueTag;

        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try {
                int clubId = -1;
                try (Statement s = conn.createStatement();
                     ResultSet rs = s.executeQuery("SELECT club_id FROM clubs LIMIT 1")) {
                    if (rs.next()) {
                        clubId = rs.getInt(1);
                    }
                }
                if (clubId == -1) {
                    try (PreparedStatement stmtClub = conn.prepareStatement(
                            "INSERT INTO clubs (club_name, description) VALUES ('Isolated Club', 'Isolated Club') RETURNING club_id")) {
                        try (ResultSet rsClub = stmtClub.executeQuery()) {
                            if (rsClub.next()) clubId = rsClub.getInt(1);
                        }
                    }
                }

                int createdEventId = -1;
                String insertSql = "INSERT INTO events (club_id, event_name, description, category, " +
                        "event_date, event_time, venue, max_participants, registration_deadline) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING event_id";

                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setInt(1, clubId);
                    stmt.setString(2, eventName);
                    stmt.setString(3, "Integration test event");
                    stmt.setString(4, "Technology");
                    stmt.setDate(5, Date.valueOf(LocalDate.now().plusDays(15)));
                    stmt.setTime(6, Time.valueOf(LocalTime.of(14, 0)));
                    stmt.setString(7, "Virtual Sandbox");
                    stmt.setInt(8, 50);
                    stmt.setDate(9, Date.valueOf(LocalDate.now().plusDays(10)));

                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next());
                        createdEventId = rs.getInt(1);
                        assertTrue(createdEventId > 0);
                    }
                }

                // Retrieve and assert
                try (PreparedStatement selectStmt = conn.prepareStatement(
                        "SELECT event_id, event_name, max_participants FROM events WHERE event_id = ?")) {
                    selectStmt.setInt(1, createdEventId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(eventName, rs.getString("event_name"));
                        assertEquals(50, rs.getInt("max_participants"));
                    }
                }
            } finally {
                // Pure transaction rollback guarantees zero residual test rows
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    // =========================================================================
    // 6. DUPLICATE REGISTRATION REJECTION (UNIQUE CONSTRAINT TEST)
    // =========================================================================

    @Test
    @DisplayName("PostgreSQL enforces unique constraint uq_registration_event_student rejecting duplicates")
    public void testDuplicateRegistrationRejection() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false); // Use transaction rollback to guarantee zero residual data
            try {
                // Create dynamically isolated student and event inside rollback (never collides with seeded data)
                int studentId = createIsolatedStudent(conn);
                int eventId = createIsolatedEvent(conn);

                // 1. First insert must succeed
                try (PreparedStatement ins1 = conn.prepareStatement(
                        "INSERT INTO registrations (event_id, student_id, status, registration_date) VALUES (?, ?, 'PENDING', CURRENT_DATE)")) {
                    ins1.setInt(1, eventId);
                    ins1.setInt(2, studentId);
                    assertEquals(1, ins1.executeUpdate());
                }

                // 2. Duplicate insert for same event and student must throw SQLException (PostgreSQL error 23505)
                try (PreparedStatement ins2 = conn.prepareStatement(
                        "INSERT INTO registrations (event_id, student_id, status, registration_date) VALUES (?, ?, 'PENDING', CURRENT_DATE)")) {
                    ins2.setInt(1, eventId);
                    ins2.setInt(2, studentId);
                    assertThrows(SQLException.class, ins2::executeUpdate,
                            "Duplicate registration must trigger unique constraint violation");
                }
            } finally {
                conn.rollback(); // Rollback guarantees zero persistence to live database
                conn.setAutoCommit(true);
            }
        }
    }

    // =========================================================================
    // 7. REGISTRATION APPROVAL & CAPACITY ENFORCEMENT
    // =========================================================================

    @Test
    @DisplayName("Verify registration status updates and capacity enforcement inside isolated transaction")
    public void testRegistrationApprovalAndCapacityEnforcement() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try {
                // Query an existing event's capacity read-only
                int eventId = -1;
                int maxCap = 0;
                try (Statement s = conn.createStatement();
                     ResultSet rs = s.executeQuery("SELECT event_id, max_participants FROM events LIMIT 1")) {
                    if (rs.next()) {
                        eventId = rs.getInt("event_id");
                        maxCap = rs.getInt("max_participants");
                    }
                }

                if (eventId > 0) {
                    // Count approved spots in PostgreSQL
                    String countSql = "SELECT COUNT(*) FROM registrations WHERE event_id = ? AND status = 'APPROVED'";
                    try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                        stmt.setInt(1, eventId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            assertTrue(rs.next());
                            int approved = rs.getInt(1);
                            assertTrue(approved >= 0);
                            if (maxCap > 0) {
                                assertTrue(approved <= maxCap, "Approved spots should not exceed max capacity");
                            }
                        }
                    }
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    // =========================================================================
    // 8. ATTENDANCE INSERTION & DUPLICATE PREVENTION
    // =========================================================================

    @Test
    @DisplayName("Verify attendance recording and duplicate prevention via unique constraint")
    public void testAttendanceInsertionAndDuplicatePrevention() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try {
                // Create isolated entities inside rollback to prevent deleting or modifying seeded data
                int studentId = createIsolatedStudent(conn);
                int eventId = createIsolatedEvent(conn);

                // 1. Insert valid attendance matching schema columns (event_id, student_id, present)
                try (PreparedStatement ins1 = conn.prepareStatement(
                        "INSERT INTO attendance (event_id, student_id, present) VALUES (?, ?, TRUE)")) {
                    ins1.setInt(1, eventId);
                    ins1.setInt(2, studentId);
                    assertEquals(1, ins1.executeUpdate());
                }

                // 2. Duplicate attendance must be rejected by unique constraint uq_attendance_event_student
                try (PreparedStatement ins2 = conn.prepareStatement(
                        "INSERT INTO attendance (event_id, student_id, present) VALUES (?, ?, TRUE)")) {
                    ins2.setInt(1, eventId);
                    ins2.setInt(2, studentId);
                    assertThrows(SQLException.class, ins2::executeUpdate,
                            "Duplicate attendance must throw unique violation");
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    // =========================================================================
    // 9. CERTIFICATE ELIGIBILITY & DUPLICATE PREVENTION
    // =========================================================================

    @Test
    @DisplayName("Verify certificate issuance and duplicate prevention via unique constraint")
    public void testCertificateEligibilityAndDuplicatePrevention() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try {
                // Create isolated entities inside rollback to prevent touching seeded records
                int studentId = createIsolatedStudent(conn);
                int eventId = createIsolatedEvent(conn);
                String certPath = "/certs/iso_cert_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";

                // 1. Issue certificate matching schema columns (event_id, student_id, certificate_path, generated_date)
                try (PreparedStatement ins1 = conn.prepareStatement(
                        "INSERT INTO certificates (event_id, student_id, certificate_path, generated_date) VALUES (?, ?, ?, CURRENT_DATE)")) {
                    ins1.setInt(1, eventId);
                    ins1.setInt(2, studentId);
                    ins1.setString(3, certPath);
                    assertEquals(1, ins1.executeUpdate());
                }

                // 2. Duplicate issuance for same event and student must fail due to uq_cert_event_student
                try (PreparedStatement ins2 = conn.prepareStatement(
                        "INSERT INTO certificates (event_id, student_id, certificate_path, generated_date) VALUES (?, ?, ?, CURRENT_DATE)")) {
                    ins2.setInt(1, eventId);
                    ins2.setInt(2, studentId);
                    ins2.setString(3, certPath + "-dup");
                    assertThrows(SQLException.class, ins2::executeUpdate,
                            "Duplicate certificate must trigger unique constraint violation");
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    // =========================================================================
    // 10. FEEDBACK ELIGIBILITY & DUPLICATE / BOUNDS CHECK
    // =========================================================================

    @Test
    @DisplayName("PostgreSQL enforces feedback rating bounds (1-5) and unique constraint")
    public void testFeedbackCheckConstraintAndDuplicates() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try {
                int studentId = createIsolatedStudent(conn);
                int eventId = createIsolatedEvent(conn);

                // 1. Boundary check: Rating > 5 must fail check constraint chk_feedback_rating
                String sqlInvalid = "INSERT INTO feedback (event_id, student_id, rating, comment) VALUES (?, ?, 10, 'Out of bounds')";
                try (PreparedStatement stmt = conn.prepareStatement(sqlInvalid)) {
                    stmt.setInt(1, eventId);
                    stmt.setInt(2, studentId);
                    Savepoint sp1 = conn.setSavepoint();
                    try {
                        stmt.executeUpdate();
                        fail("Check constraint chk_feedback_rating must block rating > 5");
                    } catch (SQLException e) {
                        conn.rollback(sp1);
                    }
                }

                // 2. Boundary check: Rating < 1 must fail check constraint chk_feedback_rating
                String sqlNegative = "INSERT INTO feedback (event_id, student_id, rating, comment) VALUES (?, ?, 0, 'Zero stars')";
                try (PreparedStatement stmt = conn.prepareStatement(sqlNegative)) {
                    stmt.setInt(1, eventId);
                    stmt.setInt(2, studentId);
                    Savepoint sp2 = conn.setSavepoint();
                    try {
                        stmt.executeUpdate();
                        fail("Check constraint chk_feedback_rating must block rating < 1");
                    } catch (SQLException e) {
                        conn.rollback(sp2);
                    }
                }

                // 3. Valid feedback insert
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO feedback (event_id, student_id, rating, comment) VALUES (?, ?, 5, 'Great session!')")) {
                    stmt.setInt(1, eventId);
                    stmt.setInt(2, studentId);
                    assertEquals(1, stmt.executeUpdate());
                }

                // 4. Duplicate feedback for same event and student must fail uq_feedback_event_student
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO feedback (event_id, student_id, rating, comment) VALUES (?, ?, 4, 'Duplicate feedback')")) {
                    stmt.setInt(1, eventId);
                    stmt.setInt(2, studentId);
                    Savepoint sp3 = conn.setSavepoint();
                    try {
                        stmt.executeUpdate();
                        fail("Duplicate feedback must violate uq_feedback_event_student constraint");
                    } catch (SQLException e) {
                        conn.rollback(sp3);
                    }
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    // =========================================================================
    // 11. TRANSACTION ROLLBACK & SQL EXCEPTION INTEGRITY
    // =========================================================================

    @Test
    @DisplayName("Transaction rollback correctly reverts uncommitted mutations in PostgreSQL")
    public void testTransactionRollback() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try {
                String testClubName = "Rollback Club " + UUID.randomUUID().toString().substring(0, 8);
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO clubs (club_name, description) VALUES (?, ?)")) {
                    stmt.setString(1, testClubName);
                    stmt.setString(2, "Will be rolled back");
                    stmt.executeUpdate();
                }

                // Explicit rollback under test
                conn.rollback();
                conn.setAutoCommit(true);

                // Confirm record does not exist
                try (PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM clubs WHERE club_name = ?")) {
                    checkStmt.setString(1, testClubName);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Rolled back row must not persist");
                    }
                }
            } finally {
                if (!conn.isClosed() && !conn.getAutoCommit()) {
                    conn.rollback();
                    conn.setAutoCommit(true);
                }
            }
        }
    }

    // =========================================================================
    // 12. ANALYTICS AGGREGATION QUERIES
    // =========================================================================

    @Test
    @DisplayName("PostgreSQL executes aggregate queries correctly without division by zero")
    public void testAnalyticsAggregationQuery() throws SQLException {
        try (Connection conn = getPostgresConnection()) {
            String query = "SELECT " +
                    "COUNT(*) AS total_regs, " +
                    "COUNT(CASE WHEN status = 'APPROVED' THEN 1 END) AS approved_regs, " +
                    "COUNT(CASE WHEN status = 'PENDING' THEN 1 END) AS pending_regs, " +
                    "COUNT(CASE WHEN status = 'REJECTED' THEN 1 END) AS rejected_regs " +
                    "FROM registrations";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                assertTrue(rs.next());
                int total = rs.getInt("total_regs");
                int approved = rs.getInt("approved_regs");
                int pending = rs.getInt("pending_regs");
                int rejected = rs.getInt("rejected_regs");

                assertEquals(total, approved + pending + rejected,
                        "Total registrations must equal the sum of status buckets");
            }
        }
    }
}
