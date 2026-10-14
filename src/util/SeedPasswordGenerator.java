package util;

/**
 * SeedPasswordGenerator — Maintainable Java utility to generate compatible PBKDF2
 * password hashes for seed data and administrative user provisioning.
 *
 * Adheres strictly to AGENTS.md and Phase 19B:
 * - Uses production PasswordSecurityUtil (PBKDF2WithHmacSHA256, 65,536 iterations, 16-byte random salt).
 * - Generates cryptographically secure, self-describing hashes for seed.sql without hardcoding.
 * - Allows local administrators or developers to generate safe hash strings for any raw password.
 */
public class SeedPasswordGenerator {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("CAMPUS CONNECT — PBKDF2 Seed Password Generator");
        System.out.println("==================================================");

        if (args != null && args.length > 0) {
            for (String arg : args) {
                String hash = PasswordSecurityUtil.hashPassword(arg);
                System.out.println("Password: \"" + arg + "\"");
                System.out.println("Generated Hash: " + hash);
                System.out.println("Verification: " + PasswordSecurityUtil.verifyPassword(arg, hash));
                System.out.println("--------------------------------------------------");
            }
            return;
        }

        // Default: Generate fresh hashes for standard documented demo accounts
        System.out.println("Generating fresh PBKDF2 hashes for documented demo accounts:\n");

        String adminPass = "admin123";
        String adminHash = PasswordSecurityUtil.hashPassword(adminPass);
        System.out.println("Role: ADMIN (admin@campusconnect.edu)");
        System.out.println("Raw Demo Password: \"" + adminPass + "\"");
        System.out.println("PBKDF2 Hash: " + adminHash);
        System.out.println("Verified: " + PasswordSecurityUtil.verifyPassword(adminPass, adminHash));
        System.out.println();

        String studentPass = "student123";
        String[] students = {
                "sonya@student.edu (Sonya Blade)",
                "alex@student.edu (Alex Chen)",
                "priya@student.edu (Priya Sharma)",
                "marcus@student.edu (Marcus Rivera)",
                "elena@student.edu (Elena Rostova)"
        };

        for (String student : students) {
            String studentHash = PasswordSecurityUtil.hashPassword(studentPass);
            System.out.println("Student: " + student);
            System.out.println("Raw Demo Password: \"" + studentPass + "\"");
            System.out.println("PBKDF2 Hash: " + studentHash);
            System.out.println("Verified: " + PasswordSecurityUtil.verifyPassword(studentPass, studentHash));
            System.out.println();
        }

        System.out.println("==================================================");
        System.out.println("Done. Copy the generated hashes into database/seed.sql if updating seed records.");
    }
}
