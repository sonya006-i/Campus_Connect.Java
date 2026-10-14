package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import util.PasswordSecurityUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PasswordSecurityUtilTest — Unit verification of PBKDF2WithHmacSHA256 hashing logic.
 *
 * Tests:
 * 1. Correct password verification
 * 2. Incorrect password verification failure
 * 3. Distinct salts generated for the same plaintext password
 * 4. Malformed and corrupted encoded string rejection
 * 5. Legacy plaintext detection and verification
 * 6. Null and empty input resilience
 */
public class PasswordSecurityUtilTest {

    @Test
    @DisplayName("Correct password verifies successfully against generated PBKDF2 hash")
    public void testCorrectPasswordVerification() {
        String rawPassword = "CorrectPassword!2026";
        String hash = PasswordSecurityUtil.hashPassword(rawPassword);

        assertNotNull(hash);
        assertTrue(PasswordSecurityUtil.isHashed(hash));
        assertTrue(PasswordSecurityUtil.verifyPassword(rawPassword, hash));
    }

    @Test
    @DisplayName("Incorrect password fails verification against generated PBKDF2 hash")
    public void testIncorrectPasswordFails() {
        String rawPassword = "CorrectPassword!2026";
        String wrongPassword = "WrongPassword!9999";
        String hash = PasswordSecurityUtil.hashPassword(rawPassword);

        assertFalse(PasswordSecurityUtil.verifyPassword(wrongPassword, hash));
        assertFalse(PasswordSecurityUtil.verifyPassword("", hash));
        assertFalse(PasswordSecurityUtil.verifyPassword("correctpassword!2026", hash)); // case sensitivity check
    }

    @Test
    @DisplayName("Distinct cryptographically random salts generated for the same password")
    public void testDistinctSaltsForSamePassword() {
        String password = "UniversalStudentPassword123";

        String hash1 = PasswordSecurityUtil.hashPassword(password);
        String hash2 = PasswordSecurityUtil.hashPassword(password);
        String hash3 = PasswordSecurityUtil.hashPassword(password);

        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotNull(hash3);

        // Hashes must be unique because of fresh 16-byte random salts
        assertNotEquals(hash1, hash2, "Distinct salts must produce distinct encoded hashes");
        assertNotEquals(hash2, hash3, "Distinct salts must produce distinct encoded hashes");
        assertNotEquals(hash1, hash3, "Distinct salts must produce distinct encoded hashes");

        // All distinct hashes must successfully verify the original password
        assertTrue(PasswordSecurityUtil.verifyPassword(password, hash1));
        assertTrue(PasswordSecurityUtil.verifyPassword(password, hash2));
        assertTrue(PasswordSecurityUtil.verifyPassword(password, hash3));
    }

    @Test
    @DisplayName("Malformed, corrupted, or tampered encoded values fail verification safely")
    public void testMalformedEncodedValuesFail() {
        String testPassword = "PasswordToTestMalformed";

        // Missing prefix
        assertFalse(PasswordSecurityUtil.verifyPassword(testPassword, "65536:c2FsdA==:aGFzaA=="));

        // Unknown algorithm
        assertFalse(PasswordSecurityUtil.verifyPassword(testPassword, "SHA1:65536:c2FsdA==:aGFzaA=="));

        // Invalid iteration count (non-numeric)
        assertFalse(PasswordSecurityUtil.verifyPassword(testPassword, "PBKDF2WithHmacSHA256:invalid:c2FsdA==:aGFzaA=="));

        // Zero or negative iterations
        assertFalse(PasswordSecurityUtil.verifyPassword(testPassword, "PBKDF2WithHmacSHA256:0:c2FsdA==:aGFzaA=="));
        assertFalse(PasswordSecurityUtil.verifyPassword(testPassword, "PBKDF2WithHmacSHA256:-500:c2FsdA==:aGFzaA=="));

        // Invalid Base64 in salt or hash
        assertFalse(PasswordSecurityUtil.verifyPassword(testPassword, "PBKDF2WithHmacSHA256:65536:???not-base64???:aGFzaA=="));
        assertFalse(PasswordSecurityUtil.verifyPassword(testPassword, "PBKDF2WithHmacSHA256:65536:c2FsdA==:???not-base64???"));

        // Truncated tokens
        assertFalse(PasswordSecurityUtil.verifyPassword(testPassword, "PBKDF2WithHmacSHA256:65536:c2FsdA=="));
        assertFalse(PasswordSecurityUtil.verifyPassword(testPassword, "PBKDF2WithHmacSHA256"));
        assertFalse(PasswordSecurityUtil.verifyPassword(testPassword, ""));

        // isHashed helper verification on malformed strings
        assertFalse(PasswordSecurityUtil.isHashed(null));
        assertFalse(PasswordSecurityUtil.isHashed(""));
        assertFalse(PasswordSecurityUtil.isHashed("plainTextPassword"));
        assertFalse(PasswordSecurityUtil.isHashed("PBKDF2WithHmacSHA256:missing:parts"));
    }

    @Test
    @DisplayName("Legacy plaintext detection and constant-time fallback verification")
    public void testLegacyPlaintextVerification() {
        String legacyPlaintext = "oldPlaintextPassword2024";

        // Must be identified as legacy plaintext
        assertTrue(PasswordSecurityUtil.isLegacyPlaintext(legacyPlaintext));
        assertFalse(PasswordSecurityUtil.isHashed(legacyPlaintext));

        // Verifies correctly with the matching password
        assertTrue(PasswordSecurityUtil.verifyPassword(legacyPlaintext, legacyPlaintext));

        // Fails with mismatched password
        assertFalse(PasswordSecurityUtil.verifyPassword("wrongAttempt", legacyPlaintext));
    }

    @Test
    @DisplayName("Null parameters fail verification safely without throwing runtime exceptions")
    public void testNullInputsFailGracefully() {
        assertFalse(PasswordSecurityUtil.verifyPassword(null, "someHash"));
        assertFalse(PasswordSecurityUtil.verifyPassword("somePassword", null));
        assertFalse(PasswordSecurityUtil.verifyPassword(null, null));

        assertFalse(PasswordSecurityUtil.isLegacyPlaintext(null));
        assertFalse(PasswordSecurityUtil.isHashed(null));
    }
}
