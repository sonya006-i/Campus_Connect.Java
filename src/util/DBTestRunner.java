package util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * DBTestRunner — Standalone verification utility for PostgreSQL & JDBC layer.
 *
 * Verifies:
 * 1. JDBC connection to Supabase PostgreSQL database
 * 2. Presence of core tables ('users', 'clubs', 'events', etc.)
 *
 * Does not modify or inject test controls into the JavaFX UI.
 */
public class DBTestRunner {

    private static final String[] REQUIRED_TABLES = {"users", "clubs", "events", "registrations", "attendance", "feedback", "certificates"};

    public static boolean runVerification() {
        System.out.println("==================================================");
        System.out.println("CAMPUS CONNECT — Database Verification Utility");
        System.out.println("Target URL: " + DBConnection.getDbUrl());
        System.out.println("Target User: " + DBConnection.getDbUser());
        System.out.println("==================================================");

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) {
                System.err.println("[FAIL] Could not establish connection to database server.");
                System.err.println("       Hint: Verify database is reachable and credentials are configured.");
                System.err.println("       Ensure DB_PASSWORD is set via environment variable or local .env file.");
                return false;
            }

            System.out.println("[PASS] Database connection established successfully.");

            DatabaseMetaData metaData = conn.getMetaData();
            Set<String> tablesFound = new HashSet<>();

            try (ResultSet rs = metaData.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tablesFound.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }

            System.out.println("\nChecking core tables:");
            boolean allPresent = true;
            for (String table : REQUIRED_TABLES) {
                if (tablesFound.contains(table.toLowerCase())) {
                    System.out.println("  ✔ Table '" + table + "' exists.");
                } else {
                    System.out.println("  ✘ Table '" + table + "' NOT found.");
                    allPresent = false;
                }
            }

            if (allPresent) {
                System.out.println("\n[SUCCESS] All required Campus Connect tables verified!");
            } else {
                System.out.println("\n[NOTICE] Some tables are missing. Please execute database/schema_postgres.sql in Supabase SQL Editor.");
            }
            return allPresent;
        } catch (SQLException e) {
            System.err.println("[FAIL] SQL Exception during verification: " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        boolean success = runVerification();
        System.exit(success ? 0 : 1);
    }
}
