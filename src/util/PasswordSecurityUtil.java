package util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PasswordSecurityUtil — Cryptographic utility for salted, slow PBKDF2 password hashing.
 *
 * Implements Phase 19B Password Hardening:
 * - PBKDF2WithHmacSHA256 from standard Java cryptography (zero external dependencies).
 * - Cryptographically random 16-byte salt per password using SecureRandom.
 * - 65,536 iterations for defense against brute-force attacks.
 * - Self-describing format: "PBKDF2WithHmacSHA256:<iterations>:<base64Salt>:<base64Hash>".
 * - Constant-time hash verification via MessageDigest.isEqual to eliminate timing attacks.
 * - Controlled backward compatibility: detects legacy plaintext passwords and permits
 *   on-the-fly upgrade during authenticated login.
 */
public final class PasswordSecurityUtil {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int DEFAULT_ITERATIONS = 65536;
    public static final int SALT_BYTES = 16;
    public static final int DERIVED_KEY_BITS = 256;
    public static final String FORMAT_PREFIX = "PBKDF2WithHmacSHA256";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordSecurityUtil() {
        // Utility class: prevent instantiation
    }

    /**
     * Hashes a plaintext password using PBKDF2WithHmacSHA256 with a fresh cryptographically
     * random salt and standard 65,536 iterations.
     *
     * @param plaintextPassword the raw password to hash
     * @return self-describing encoded string: "PBKDF2WithHmacSHA256:65536:<salt>:<hash>"
     */
    public static String hashPassword(String plaintextPassword) {
        if (plaintextPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return hashPassword(plaintextPassword, salt, DEFAULT_ITERATIONS);
    }

    /**
     * Hashes a plaintext password with a specified salt and iteration count.
     *
     * @param plaintextPassword the raw password
     * @param salt byte array salt
     * @param iterations iteration count
     * @return self-describing encoded string
     */
    public static String hashPassword(String plaintextPassword, byte[] salt, int iterations) {
        if (plaintextPassword == null || salt == null || iterations <= 0) {
            throw new IllegalArgumentException("Invalid password, salt, or iterations");
        }

        char[] passwordChars = plaintextPassword.toCharArray();
        try {
            PBEKeySpec spec = new PBEKeySpec(passwordChars, salt, iterations, DERIVED_KEY_BITS);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();

            String encodedSalt = Base64.getEncoder().encodeToString(salt);
            String encodedHash = Base64.getEncoder().encodeToString(hash);

            return FORMAT_PREFIX + ":" + iterations + ":" + encodedSalt + ":" + encodedHash;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Cryptographic provider failure: " + e.getMessage(), e);
        } finally {
            // Clean sensitive character array from memory
            java.util.Arrays.fill(passwordChars, '\0');
        }
    }

    /**
     * Checks whether a stored value is formatted as a valid self-describing PBKDF2 hash.
     *
     * @param candidate string to evaluate
     * @return true if string conforms to "PBKDF2WithHmacSHA256:<iterations>:<salt>:<hash>"
     */
    public static boolean isHashed(String candidate) {
        if (candidate == null || !candidate.startsWith(FORMAT_PREFIX + ":")) {
            return false;
        }

        String[] parts = candidate.split(":", -1);
        if (parts.length != 4) {
            return false;
        }

        if (!FORMAT_PREFIX.equals(parts[0])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] hash = Base64.getDecoder().decode(parts[3]);
            return salt.length > 0 && hash.length > 0;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Checks if a candidate password string is an un-hashed legacy plaintext record.
     *
     * @param candidate string to evaluate
     * @return true if non-null and not formatted as an encoded PBKDF2 hash
     */
    public static boolean isLegacyPlaintext(String candidate) {
        return candidate != null && !isHashed(candidate);
    }

    /**
     * Verifies a candidate raw password against a stored password value.
     *
     * Supports both:
     * 1. Modern PBKDF2 self-describing hashed strings (constant-time verification).
     * 2. Legacy plaintext credentials (constant-time byte comparison) for controlled upgrade.
     *
     * @param candidatePassword raw password provided by user during login
     * @param storedValue password string stored in database
     * @return true if password matches, false otherwise
     */
    public static boolean verifyPassword(String candidatePassword, String storedValue) {
        if (candidatePassword == null || storedValue == null) {
            return false;
        }

        if (isHashed(storedValue)) {
            return verifyHashedPassword(candidatePassword, storedValue);
        } else {
            // Legacy plaintext fallback verification
            return verifyLegacyPlaintext(candidatePassword, storedValue);
        }
    }

    private static boolean verifyHashedPassword(String candidatePassword, String storedHash) {
        String[] parts = storedHash.split(":", -1);
        if (parts.length != 4) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);

            char[] passwordChars = candidatePassword.toCharArray();
            try {
                PBEKeySpec spec = new PBEKeySpec(passwordChars, salt, iterations, expectedHash.length * 8);
                SecretKeyFactory skf = SecretKeyFactory.getInstance(parts[0]);
                byte[] actualHash = skf.generateSecret(spec).getEncoded();
                spec.clearPassword();

                // Constant-time byte array comparison prevents timing side-channel attacks
                return MessageDigest.isEqual(actualHash, expectedHash);
            } finally {
                java.util.Arrays.fill(passwordChars, '\0');
            }
        } catch (Exception ex) {
            // Safe failure on malformed format, corrupted base64, or unknown algorithm
            return false;
        }
    }

    private static boolean verifyLegacyPlaintext(String candidatePassword, String storedPlaintext) {
        byte[] candidateBytes = candidatePassword.getBytes(StandardCharsets.UTF_8);
        byte[] storedBytes = storedPlaintext.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(candidateBytes, storedBytes);
    }
}
