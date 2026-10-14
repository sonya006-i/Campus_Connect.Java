package test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import util.DBConnection;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DBConnectionConfigurationTest — Automated unit verification for DBConnection configuration,
 * fallback resolution, SSL enforcement, and credential security.
 */
public class DBConnectionConfigurationTest {

    @BeforeEach
    @AfterEach
    public void resetConfig() {
        DBConnection.resetConnectionConfig();
    }

    @Test
    @DisplayName("Verify default DB URL targets Supabase session pooler with SSL required")
    public void testDefaultDbUrlTarget() {
        String url = DBConnection.getDbUrl();
        assertNotNull(url, "URL must not be null");
        assertTrue(url.startsWith("jdbc:postgresql://"), "URL must use PostgreSQL JDBC protocol");
        assertTrue(url.contains("aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres"),
                "URL must target Supabase session pooler host on port 5432");
        assertTrue(url.contains("sslmode=require"), "URL must require SSL for Supabase");
    }

    @Test
    @DisplayName("Verify default DB User matches configured Supabase pooler user")
    public void testDefaultDbUserTarget() {
        String user = DBConnection.getDbUser();
        assertEquals("postgres.ijcsjfikzkwwhcsfjawh", user,
                "Default username must match Supabase pooler tenant username");
    }

    @Test
    @DisplayName("Verify custom connection overrides apply and reset cleanly")
    public void testCustomConnectionOverride() {
        String testUrl = "jdbc:h2:mem:custom_test;DB_CLOSE_DELAY=-1";
        String testUser = "custom_sa";
        String testPass = "custom_pass";

        DBConnection.setCustomConnectionConfig(testUrl, testUser, testPass);
        assertEquals(testUrl, DBConnection.getDbUrl());
        assertEquals(testUser, DBConnection.getDbUser());
        assertEquals(testPass, DBConnection.getDbPassword());

        DBConnection.resetConnectionConfig();
        assertEquals(DBConnection.DEFAULT_DB_URL, DBConnection.getDbUrl());
        assertEquals(DBConnection.DEFAULT_DB_USER, DBConnection.getDbUser());
    }

    @Test
    @DisplayName("Verify getConnection returns null gracefully when DB_PASSWORD is missing")
    public void testMissingPasswordFailsGracefully() {
        // When not in custom mode and DB_PASSWORD is unset or empty
        DBConnection.resetConnectionConfig();
        String currentPass = DBConnection.getDbPassword();
        if (currentPass == null || currentPass.trim().isEmpty()) {
            Connection conn = DBConnection.getConnection();
            assertNull(conn, "getConnection must return null when password is not configured");
            assertFalse(DBConnection.testConnection(), "testConnection must return false when password is missing");
        }
    }
}
