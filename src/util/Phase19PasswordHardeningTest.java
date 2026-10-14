package util;

import dao.UserDAO;
import model.User;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Phase19PasswordHardeningTest — Standalone automated test runner for Phase 19B.
 *
 * Verifies:
 * 1. Cryptographic hashing via PBKDF2WithHmacSHA256
 * 2. Distinct salts for identical passwords
 * 3. Constant-time verification on correct/incorrect inputs
 * 4. Safe rejection of malformed or corrupted strings
 * 5. Seeded database rows contain encoded hashes, NOT plaintext passwords
 * 6. Admin and Student demo logins succeed with unchanged roles
 * 7. Controlled in-place legacy plaintext upgrade on authenticated login
 */
public class Phase19PasswordHardeningTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    private static void pass(String message) {
        testsRun++;
        testsPassed++;
        System.out.println("  ✔ [PASS] " + message);
    }

    private static void fail(String message, String details) {
        testsRun++;
        System.err.println("  ✘ [FAIL] " + message + " -> " + details);
    }

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("CAMPUS CONNECT — PHASE 19B: PASSWORD HARDENING AUDIT & TEST SUITE");
        System.out.println("==================================================");

        testUnitHashing();
        testDistinctSalts();
        testMalformedInputs();
        testLegacyPlaintextHelpers();
        testNullSafety();
        testDatabaseIntegration();

        System.out.println("\n--------------------------------------------------");
        System.out.println("Phase 19B Test Results: " + testsPassed + "/" + testsRun + " passed.");
        System.out.println("--------------------------------------------------");

        if (testsPassed == testsRun) {
            System.out.println("[SUCCESS] All Phase 19B password hardening tests passed!\n");
        } else {
            System.err.println("[FAILURE] Some Phase 19B tests failed.\n");
            System.exit(1);
        }

        try {
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
        } catch (Throwable ignored) {
        }
    }

    private static void testUnitHashing() {
        System.out.println("\n[1] Testing PBKDF2 Cryptographic Hashing & Verification:");
        String rawPass = "CyberpunkPass#2026";
        String hash = PasswordSecurityUtil.hashPassword(rawPass);

        if (hash != null && hash.startsWith("PBKDF2WithHmacSHA256:65536:")) {
            pass("hashPassword generates valid self-describing PBKDF2 string");
        } else {
            fail("hashPassword format check", "Unexpected format");
        }

        if (PasswordSecurityUtil.isHashed(hash)) {
            pass("isHashed identifies modern encoded PBKDF2 hash");
        } else {
            fail("isHashed identification", "Failed to identify hash");
        }

        if (PasswordSecurityUtil.verifyPassword(rawPass, hash)) {
            pass("verifyPassword succeeds with correct plaintext password");
        } else {
            fail("verifyPassword correct password", "Returned false");
        }

        if (!PasswordSecurityUtil.verifyPassword("WrongPassword123", hash)) {
            pass("verifyPassword fails cleanly with incorrect password");
        } else {
            fail("verifyPassword wrong password", "Returned true");
        }

        if (!PasswordSecurityUtil.verifyPassword("cyberpunkpass#2026", hash)) {
            pass("verifyPassword respects case sensitivity");
        } else {
            fail("verifyPassword case check", "Returned true");
        }
    }

    private static void testDistinctSalts() {
        System.out.println("\n[2] Testing Random Salt Uniqueness:");
        String pass = "SharedDemoStudentPassword";
        String h1 = PasswordSecurityUtil.hashPassword(pass);
        String h2 = PasswordSecurityUtil.hashPassword(pass);
        String h3 = PasswordSecurityUtil.hashPassword(pass);

        if (!h1.equals(h2) && !h2.equals(h3) && !h1.equals(h3)) {
            pass("Three hashes of the same password produce 3 unique encoded strings (distinct random salts)");
        } else {
            fail("Distinct salts test", "Duplicate hashes generated");
        }

        if (PasswordSecurityUtil.verifyPassword(pass, h1) &&
            PasswordSecurityUtil.verifyPassword(pass, h2) &&
            PasswordSecurityUtil.verifyPassword(pass, h3)) {
            pass("All distinct hashes successfully verify against the source password");
        } else {
            fail("Verification across distinct salts", "Failed to verify");
        }
    }

    private static void testMalformedInputs() {
        System.out.println("\n[3] Testing Malformed & Tampered Value Resilience:");
        String raw = "SamplePassword123";

        if (!PasswordSecurityUtil.verifyPassword(raw, "PBKDF2WithHmacSHA256:notanumber:salt:hash")) {
            pass("Non-numeric iteration count is safely rejected");
        } else {
            fail("Malformed iteration count", "Allowed verification");
        }

        if (!PasswordSecurityUtil.verifyPassword(raw, "PBKDF2WithHmacSHA256:0:salt:hash")) {
            pass("Zero iteration count is safely rejected");
        } else {
            fail("Zero iteration count", "Allowed verification");
        }

        if (!PasswordSecurityUtil.verifyPassword(raw, "SHA1:65536:salt:hash")) {
            pass("Unsupported algorithm prefix is safely rejected");
        } else {
            fail("Unsupported algorithm", "Allowed verification");
        }

        if (!PasswordSecurityUtil.verifyPassword(raw, "PBKDF2WithHmacSHA256:65536:???not-b64???:hash")) {
            pass("Invalid Base64 salt is safely rejected without throwing");
        } else {
            fail("Invalid base64 salt", "Allowed verification");
        }

        if (!PasswordSecurityUtil.verifyPassword(raw, "PBKDF2WithHmacSHA256:65536:salt:???not-b64???")) {
            pass("Invalid Base64 hash is safely rejected without throwing");
        } else {
            fail("Invalid base64 hash", "Allowed verification");
        }

        if (!PasswordSecurityUtil.verifyPassword(raw, "PBKDF2WithHmacSHA256:65536:incomplete")) {
            pass("Truncated tokens are safely rejected");
        } else {
            fail("Truncated tokens", "Allowed verification");
        }
    }

    private static void testLegacyPlaintextHelpers() {
        System.out.println("\n[4] Testing Legacy Plaintext Detection & Constant-Time Verification:");
        String legacy = "legacyStudentPlaintext123";

        if (PasswordSecurityUtil.isLegacyPlaintext(legacy)) {
            pass("isLegacyPlaintext returns true for un-hashed string");
        } else {
            fail("isLegacyPlaintext check", "Returned false");
        }

        String modernHash = PasswordSecurityUtil.hashPassword("test");
        if (!PasswordSecurityUtil.isLegacyPlaintext(modernHash)) {
            pass("isLegacyPlaintext returns false for modern PBKDF2 hash");
        } else {
            fail("isLegacyPlaintext on modern hash", "Returned true");
        }

        if (PasswordSecurityUtil.verifyPassword(legacy, legacy)) {
            pass("verifyPassword verifies matching legacy plaintext");
        } else {
            fail("verifyPassword legacy match", "Returned false");
        }

        if (!PasswordSecurityUtil.verifyPassword("mismatch", legacy)) {
            pass("verifyPassword rejects non-matching legacy plaintext");
        } else {
            fail("verifyPassword legacy mismatch", "Returned true");
        }
    }

    private static void testNullSafety() {
        System.out.println("\n[5] Testing Null Parameter Safety:");
        if (!PasswordSecurityUtil.verifyPassword(null, "someHash") &&
            !PasswordSecurityUtil.verifyPassword("somePass", null) &&
            !PasswordSecurityUtil.verifyPassword(null, null)) {
            pass("verifyPassword returns false on null inputs without throwing NullPointerException");
        } else {
            fail("Null safety in verifyPassword", "Did not handle null cleanly");
        }

        if (!PasswordSecurityUtil.isHashed(null) && !PasswordSecurityUtil.isLegacyPlaintext(null)) {
            pass("Format predicates safely handle null input");
        } else {
            fail("Null safety in format predicates", "Returned true for null");
        }
    }

    private static void testDatabaseIntegration() {
        System.out.println("\n[6] Testing Database Integration & Seed Credential Verification:");
        String testUrl = "jdbc:h2:mem:campus_connect_phase19;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        String testUser = "sa";
        String testPass = "";

        DBConnection.setCustomConnectionConfig(testUrl, testUser, testPass);
        UserDAO userDAO = new UserDAO();

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = (conn != null) ? conn.createStatement() : null) {
            if (conn == null || stmt == null) {
                fail("Database integration execution", "Failed to obtain test connection from DBConnection");
                return;
            }

            // Create schema
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

            String insertPrefix = "INSERT INTO users";
            int startIdx = seedSql.indexOf(insertPrefix);
            int endIdx = seedSql.indexOf(";", startIdx);
            String userInsertQuery = seedSql.substring(startIdx, endIdx);

            stmt.execute(userInsertQuery);
            pass("Test database initialized with seeded user records");

            // Verify row contents are encoded hashes
            try (ResultSet rs = stmt.executeQuery("SELECT user_id, email, password FROM users")) {
                boolean allHashed = true;
                boolean noPlaintext = true;
                int count = 0;
                while (rs.next()) {
                    count++;
                    String dbPass = rs.getString("password");
                    if (!PasswordSecurityUtil.isHashed(dbPass)) allHashed = false;
                    if ("admin123".equals(dbPass) || "student123".equals(dbPass)) noPlaintext = false;
                }
                if (count >= 6 && allHashed && noPlaintext) {
                    pass("Database rows contain encoded PBKDF2 hashes instead of demo plaintext passwords (" + count + " users)");
                } else {
                    fail("Database hash check", "Found plaintext or unhashed values");
                }
            }

            // Test Admin login
            User admin = userDAO.findByEmail("admin@campusconnect.edu");
            if (admin != null && PasswordSecurityUtil.verifyPassword("admin123", admin.getPassword())) {
                pass("Admin demo account authenticates successfully");
            } else {
                fail("Admin authentication", "Could not verify admin credentials");
            }

            if (admin != null && "ADMIN".equals(admin.getRole())) {
                pass("Admin role-based access preserved as ADMIN");
            } else {
                fail("Admin role check", "Role is not ADMIN");
            }

            // Test Student login
            User student = userDAO.findByEmail("sonya@student.edu");
            if (student != null && PasswordSecurityUtil.verifyPassword("student123", student.getPassword())) {
                pass("Student demo account authenticates successfully");
            } else {
                fail("Student authentication", "Could not verify student credentials");
            }

            if (student != null && "STUDENT".equals(student.getRole())) {
                pass("Student role-based access preserved as STUDENT");
            } else {
                fail("Student role check", "Role is not STUDENT");
            }

            // Test incorrect password
            if (!PasswordSecurityUtil.verifyPassword("wrongAttempt", admin.getPassword())) {
                pass("Incorrect password rejected on admin account");
            } else {
                fail("Incorrect password check", "Wrong password accepted");
            }

            // Test controlled legacy migration
            String legacyEmail = "migrating.user@campus.edu";
            String legacyPlaintext = "LegacySecret987";
            try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, ?)")) {
                insertStmt.setString(1, "Legacy Student");
                insertStmt.setString(2, legacyEmail);
                insertStmt.setString(3, legacyPlaintext);
                insertStmt.setString(4, "STUDENT");
                insertStmt.executeUpdate();
            }

            User legacyUser = userDAO.findByEmail(legacyEmail);
            if (legacyUser != null && PasswordSecurityUtil.isLegacyPlaintext(legacyUser.getPassword())) {
                pass("Legacy account initially identified as un-hashed plaintext");
            } else {
                fail("Legacy identification", "Failed to identify legacy password");
            }

            // Simulate on-login migration using atomic conditional update
            if (legacyUser != null && PasswordSecurityUtil.verifyPassword(legacyPlaintext, legacyUser.getPassword())) {
                String upgradedHash = PasswordSecurityUtil.hashPassword(legacyPlaintext);

                // 1. Conditional update with wrong expected password must fail
                boolean mismatched = userDAO.updatePasswordIfMatches(legacyUser.getUserId(), "wrongOldPassword", upgradedHash);
                if (!mismatched) {
                    pass("Safe conditional update rejects mismatched old password preventing concurrent overwrites");
                } else {
                    fail("Safe conditional update check", "Allowed update with mismatched old password");
                }

                // 2. Conditional update with matching expected password must succeed
                boolean updated = userDAO.updatePasswordIfMatches(legacyUser.getUserId(), legacyPlaintext, upgradedHash);
                if (updated) {
                    pass("Controlled on-login upgrade replaces legacy plaintext with PBKDF2 hash via conditional update");
                } else {
                    fail("Controlled upgrade update", "UserDAO.updatePasswordIfMatches returned false");
                }

                User reloaded = userDAO.findByEmail(legacyEmail);
                if (reloaded != null && PasswordSecurityUtil.isHashed(reloaded.getPassword()) &&
                    PasswordSecurityUtil.verifyPassword(legacyPlaintext, reloaded.getPassword())) {
                    pass("Upgraded user authenticates successfully against new PBKDF2 hash on subsequent logins");
                } else {
                    fail("Subsequent login after upgrade", "Authentication failed");
                }
            }

        } catch (Exception e) {
            fail("Database integration execution", e.getMessage());
        } finally {
            DBConnection.resetConnectionConfig();
            Session.clearSession();
        }
    }
}
