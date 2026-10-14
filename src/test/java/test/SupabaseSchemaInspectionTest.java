package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import util.DBConnection;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SupabaseSchemaInspectionTest — Strictly read-only metadata audit tool for Phase 24B.
 *
 * Safety Guarantees:
 * - Purely read-only: executes zero DDL and zero DML (no insert, update, delete, create, drop, alter).
 * - Never logs or exposes credentials, connection URLs, or secret values.
 * - Inspects PostgreSQL information_schema and DatabaseMetaData to verify schema alignment.
 */
public class SupabaseSchemaInspectionTest {

    private static final String[] EXPECTED_TABLES = {
            "users", "clubs", "events", "registrations", "attendance", "feedback", "certificates"
    };

    @Test
    @DisplayName("Read-only audit of Supabase PostgreSQL schema structure")
    public void auditSupabaseSchema() {
        System.out.println("================================================================================");
        System.out.println("[AUDIT START] Inspecting Supabase PostgreSQL live schema (READ-ONLY)");
        System.out.println("================================================================================");

        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            System.err.println("[AUDIT ERROR] Could not establish connection to the database.");
            System.err.println("  Possible causes: database unreachable, missing DB_PASSWORD, or invalid credentials.");
            fail("Unable to connect to Supabase database. Review connection configuration.");
            return;
        }

        try (conn) {
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            String dbVersion = conn.getMetaData().getDatabaseProductVersion();
            System.out.println("[DB ENGINE] " + dbProduct + " (Version: " + dbVersion + ")");

            DatabaseMetaData meta = conn.getMetaData();

            // 1. Check Tables Present in current schema
            Set<String> tablesFound = new HashSet<>();
            try (ResultSet rs = meta.getTables(conn.getCatalog(), "public", "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tablesFound.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
            // Fallback check if catalog/schema name is null
            if (tablesFound.isEmpty()) {
                try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        tablesFound.add(rs.getString("TABLE_NAME").toLowerCase());
                    }
                }
            }

            System.out.println("\n--- 1. TABLE PRESENCE AUDIT ---");
            boolean allTablesExist = true;
            for (String expected : EXPECTED_TABLES) {
                boolean exists = tablesFound.contains(expected);
                System.out.println("  Table '" + expected + "': " + (exists ? "PRESENT" : "MISSING"));
                if (!exists) {
                    allTablesExist = false;
                }
            }

            boolean trackingExists = tablesFound.contains("_demo_setup_tracking");
            System.out.println("  Table '_demo_setup_tracking': " + (trackingExists ? "EXISTS" : "DOES NOT EXIST (Expected before first seed)"));

            // 2. Row Counts Audit (strictly read-only COUNT(*))
            System.out.println("\n--- 2. TABLE ROW COUNTS ---");
            for (String tbl : tablesFound) {
                if (tbl.startsWith("_") || java.util.Arrays.asList(EXPECTED_TABLES).contains(tbl)) {
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM \"" + tbl + "\"");
                         ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            long count = rs.getLong(1);
                            System.out.println("  Row count for '" + tbl + "': " + count);
                        }
                    } catch (SQLException e) {
                        System.out.println("  Row count for '" + tbl + "': ERROR querying (" + e.getMessage() + ")");
                    }
                }
            }

            // 3. Columns Audit for Core Tables
            System.out.println("\n--- 3. COLUMN DEFINITIONS AUDIT ---");
            for (String expected : EXPECTED_TABLES) {
                if (tablesFound.contains(expected)) {
                    System.out.println("\n  [Columns for table '" + expected + "']");
                    String colSql = "SELECT column_name, data_type, is_nullable, column_default " +
                            "FROM information_schema.columns " +
                            "WHERE table_schema = current_schema() AND table_name = ? " +
                            "ORDER BY ordinal_position";
                    try (PreparedStatement stmt = conn.prepareStatement(colSql)) {
                        stmt.setString(1, expected);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                String colName = rs.getString("column_name");
                                String dType = rs.getString("data_type");
                                String isNull = rs.getString("is_nullable");
                                String colDef = rs.getString("column_default");
                                System.out.println("    - " + colName + " (" + dType + ", Nullable=" + isNull +
                                        (colDef != null ? ", Default=" + colDef : "") + ")");
                            }
                        }
                    }
                }
            }

            // 4. Constraints Audit (Primary Keys, Foreign Keys, Unique, Check)
            System.out.println("\n--- 4. CONSTRAINTS AUDIT ---");
            String conSql = "SELECT conname, contype, pg_get_constraintdef(c.oid) AS def " +
                    "FROM pg_constraint c " +
                    "JOIN pg_class r ON r.oid = c.conrelid " +
                    "JOIN pg_namespace n ON n.oid = r.relnamespace " +
                    "WHERE n.nspname = current_schema() AND r.relname = ? " +
                    "ORDER BY contype, conname";

            for (String expected : EXPECTED_TABLES) {
                if (tablesFound.contains(expected)) {
                    System.out.println("\n  [Constraints for table '" + expected + "']");
                    try (PreparedStatement stmt = conn.prepareStatement(conSql)) {
                        stmt.setString(1, expected);
                        try (ResultSet rs = stmt.executeQuery()) {
                            int conCount = 0;
                            while (rs.next()) {
                                conCount++;
                                String conName = rs.getString("conname");
                                String conType = rs.getString("contype");
                                String typeDesc = switch (conType) {
                                    case "p" -> "PRIMARY KEY";
                                    case "u" -> "UNIQUE";
                                    case "f" -> "FOREIGN KEY";
                                    case "c" -> "CHECK";
                                    default -> conType;
                                };
                                String conDef = rs.getString("def");
                                System.out.println("    - [" + typeDesc + "] " + conName + ": " + conDef);
                            }
                            if (conCount == 0) {
                                System.out.println("    (No explicit constraints found)");
                            }
                        }
                    }
                }
            }

            System.out.println("\n================================================================================");
            System.out.println("[AUDIT SUMMARY] Live schema inspection complete.");
            System.out.println("  Core Tables Status: " + (allTablesExist ? "ALL 7 TABLES FOUND" : "SOME TABLES MISSING"));
            System.out.println("================================================================================");

            assertTrue(allTablesExist, "Expected all 7 core tables to be present in Supabase.");

        } catch (SQLException e) {
            System.err.println("[AUDIT EXCEPTION] " + e.getMessage());
            fail("SQLException during schema inspection: " + e.getMessage());
        }
    }
}
