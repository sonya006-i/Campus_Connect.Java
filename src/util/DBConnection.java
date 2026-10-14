package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DBConnection - Database Connection Utility for Campus Connect.
 *
 * Manages JDBC connectivity targeting Supabase-hosted PostgreSQL database.
 * Supports configuration via:
 *   1. System properties (-DDB_URL, -DDB_USER, -DDB_PASSWORD)
 *   2. Shell environment variables (DB_URL, DB_USER, DB_PASSWORD)
 *   3. Local untracked .env file (for local development)
 *
 * Follows AGENTS.md security guidelines:
 *   - Strictly no hardcoded credentials in Java source code.
 *   - Fails with clear actionable error messages when required configuration is missing.
 *   - Never logs passwords, secrets, or full connection strings.
 *   - Requires SSL (sslmode=require) for Supabase PostgreSQL connections.
 *   - Preserves full backward compatibility with DAO classes and existing test suites.
 */
public class DBConnection {

    // =========================================================================
    // Supabase PostgreSQL Starting Configuration (Session Pooler)
    // Host: aws-0-ap-northeast-2.pooler.supabase.com
    // Port: 5432
    // Database: postgres
    // =========================================================================
    public static final String DEFAULT_DB_URL = "jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require";
    public static final String DEFAULT_DB_USER = "postgres.ijcsjfikzkwwhcsfjawh";

    // Dynamic test override variables (used by isolated unit & integration tests)
    private static volatile String customDbUrl = null;
    private static volatile String customDbUser = null;
    private static volatile String customDbPassword = null;

    // Cache for local .env file variables
    private static Map<String, String> dotEnvProperties = null;
    private static boolean dotEnvLoaded = false;

    // Driver class names
    private static final String POSTGRES_DRIVER = "org.postgresql.Driver";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String H2_DRIVER = "org.h2.Driver";

    // Private constructor to prevent instantiation
    private DBConnection() {
    }

    /**
     * Lazily loads key-value pairs from an untracked local .env file in the current working directory.
     * Pure standard Java implementation with zero external dependencies.
     */
    private static synchronized void loadDotEnvIfNeeded() {
        if (dotEnvLoaded) {
            return;
        }
        dotEnvLoaded = true;
        dotEnvProperties = new HashMap<>();

        File envFile = new File(".env");
        if (!envFile.exists() || !envFile.isFile()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();
                    // Strip enclosing single or double quotes if present
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        if (value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        }
                    }
                    dotEnvProperties.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("[DBConnection Note] Could not read local .env file: " + e.getMessage());
        }
    }

    /**
     * Resolves a configuration key in prioritized order:
     * 1. Java System Property (e.g. -Dkey=val)
     * 2. Shell Environment Variable (e.g. export key=val)
     * 3. Local untracked .env file
     */
    private static String getEnvOrProperty(String key) {
        String prop = System.getProperty(key);
        if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
        }

        String env = System.getenv(key);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }

        loadDotEnvIfNeeded();
        if (dotEnvProperties != null) {
            String val = dotEnvProperties.get(key);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }

        return null;
    }

    /**
     * Overrides connection configuration for testing suites and resets active pool.
     */
    public static synchronized void setCustomConnectionConfig(String url, String user, String password) {
        customDbUrl = url;
        customDbUser = user;
        customDbPassword = password;
        closeAllPoolConnections();
    }

    // =========================================================================
    // Thread-Safe Bounded Connection Pool & Resource Sanitization
    // =========================================================================
    public static final int MAX_POOL_SIZE = 3;
    public static final long BORROW_TIMEOUT_MS = 10000;

    private static final BlockingQueue<Connection> availablePool = new ArrayBlockingQueue<>(MAX_POOL_SIZE);
    private static final Set<Connection> allPoolConnections = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Object poolLock = new Object();
    private static volatile String activePoolKey = null;

    /**
     * Drains and physically closes all pooled database connections.
     */
    public static void closeAllPoolConnections() {
        synchronized (poolLock) {
            availablePool.clear();
            for (Connection conn : allPoolConnections) {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException ignored) {
                    }
                }
            }
            allPoolConnections.clear();
            activePoolKey = null;
        }
    }

    /**
     * Borrows an exclusive physical connection from the pool, creating a new one if below MAX_POOL_SIZE.
     */
    private static Connection borrowPhysicalConnection(String url, String user, String password)
            throws ClassNotFoundException, SQLException {
        String key = url + "||" + (user != null ? user : "");
        synchronized (poolLock) {
            if (activePoolKey != null && !activePoolKey.equals(key)) {
                closeAllPoolConnections();
            }
            activePoolKey = key;
        }

        long deadline = System.currentTimeMillis() + BORROW_TIMEOUT_MS;

        while (true) {
            // 1. Try polling an existing available connection
            Connection physical = availablePool.poll();
            if (physical != null) {
                if (!physical.isClosed()) {
                    return physical;
                } else {
                    allPoolConnections.remove(physical);
                    continue;
                }
            }

            // 2. No connection immediately available: create one if under capacity
            synchronized (poolLock) {
                if (allPoolConnections.size() < MAX_POOL_SIZE) {
                    if (url.startsWith("jdbc:postgresql:")) {
                        Class.forName(POSTGRES_DRIVER);
                    } else if (url.startsWith("jdbc:h2:")) {
                        Class.forName(H2_DRIVER);
                    } else {
                        Class.forName(MYSQL_DRIVER);
                    }
                    physical = DriverManager.getConnection(url, user, password);
                    allPoolConnections.add(physical);
                    return physical;
                }
            }

            // 3. Pool at capacity: wait for an available connection
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new SQLException("Database connection pool exhausted: timed out waiting for an available connection.");
            }

            try {
                physical = availablePool.poll(remaining, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for connection from pool: " + e.getMessage());
            }

            if (physical != null) {
                if (!physical.isClosed()) {
                    return physical;
                } else {
                    allPoolConnections.remove(physical);
                }
            }
        }
    }

    /**
     * Sanitizes a returned connection (rollbacks uncommitted state, resets autoCommit to true, clears warnings)
     * and returns it to the available pool.
     */
    private static void sanitizeAndReturn(Connection physical) {
        if (physical == null) {
            return;
        }

        try {
            if (physical.isClosed()) {
                allPoolConnections.remove(physical);
                return;
            }

            // 1. Transaction Safety: rollback any pending uncommitted transaction
            if (!physical.getAutoCommit()) {
                try {
                    physical.rollback();
                } catch (SQLException ignored) {
                }
                try {
                    physical.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }

            // 2. Clear accumulated warnings
            try {
                physical.clearWarnings();
            } catch (SQLException ignored) {
            }

            // 3. Offer back to available pool
            if (allPoolConnections.contains(physical)) {
                if (!availablePool.offer(physical)) {
                    allPoolConnections.remove(physical);
                    try {
                        physical.close();
                    } catch (SQLException ignored) {
                    }
                }
            } else {
                try {
                    physical.close();
                } catch (SQLException ignored) {
                }
            }

        } catch (Exception e) {
            allPoolConnections.remove(physical);
            try {
                physical.close();
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Wraps a leased physical connection in a smart logical proxy that:
     * - Enforces isClosed() == true after logical close
     * - Disallows execution on closed logical connections
     * - Sanitizes and returns physical connection upon close()
     */
    private static Connection wrapLogicalConnection(final Connection physical) {
        final AtomicBoolean logicallyClosed = new AtomicBoolean(false);

        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String methodName = method.getName();

                        if ("close".equals(methodName)) {
                            if (logicallyClosed.compareAndSet(false, true)) {
                                sanitizeAndReturn(physical);
                            }
                            return null;
                        }

                        if ("isClosed".equals(methodName)) {
                            return logicallyClosed.get() || physical.isClosed();
                        }

                        if (logicallyClosed.get()) {
                            throw new SQLException("Connection has already been closed.");
                        }

                        try {
                            return method.invoke(physical, args);
                        } catch (java.lang.reflect.InvocationTargetException ite) {
                            throw ite.getCause() != null ? ite.getCause() : ite;
                        }
                    }
                }
        );
    }

    /**
     * Resets any custom connection overrides back to environment/default settings and clears connection pool.
     */
    public static synchronized void resetConnectionConfig() {
        customDbUrl = null;
        customDbUser = null;
        customDbPassword = null;
        closeAllPoolConnections();
    }

    /**
     * Resolves the database URL, checking custom test override first, then env/property/.env, then default.
     */
    public static String getDbUrl() {
        if (customDbUrl != null) {
            return customDbUrl;
        }
        String resolved = getEnvOrProperty("DB_URL");
        return (resolved != null && !resolved.trim().isEmpty()) ? resolved : DEFAULT_DB_URL;
    }

    /**
     * Resolves the database user, checking custom test override first, then env/property/.env, then default.
     */
    public static String getDbUser() {
        if (customDbUser != null) {
            return customDbUser;
        }
        String resolved = getEnvOrProperty("DB_USER");
        return (resolved != null && !resolved.trim().isEmpty()) ? resolved : DEFAULT_DB_USER;
    }

    /**
     * Resolves the database password, checking custom test override first, then env/property/.env.
     * Returns null if unconfigured (never hardcodes passwords).
     */
    public static String getDbPassword() {
        if (customDbPassword != null) {
            return customDbPassword;
        }
        return getEnvOrProperty("DB_PASSWORD");
    }

    /**
     * Obtains a thread-safe connection from the pool.
     * Callers should manage the lifecycle using try-with-resources.
     *
     * Validates required configuration:
     * - Fails with actionable error if DB_PASSWORD is not provided.
     * - Enforces SSL (sslmode=require) for Supabase PostgreSQL.
     * - Leases an exclusive physical connection from a bounded pool (MAX_POOL_SIZE = 3).
     * - Never leaks passwords in logs or error output.
     *
     * @return active java.sql.Connection object, or null if connection fails
     */
    public static Connection getConnection() {
        String url;
        String user;
        String password;

        if (customDbUrl != null) {
            url = customDbUrl;
            user = customDbUser;
            password = customDbPassword;
        } else {
            url = getDbUrl();
            user = getDbUser();
            password = getDbPassword();

            // 1. Validate required environment variables for default mode
            if (url == null || url.trim().isEmpty()) {
                System.err.println("[DBConnection Error] Required configuration 'DB_URL' is missing.");
                System.err.println("  Action: Set DB_URL in your environment or local .env file.");
                return null;
            }

            if (user == null || user.trim().isEmpty()) {
                System.err.println("[DBConnection Error] Required configuration 'DB_USER' is missing.");
                System.err.println("  Action: Set DB_USER in your environment or local .env file.");
                return null;
            }

            if (password == null || password.trim().isEmpty()) {
                System.err.println("[DBConnection Error] Required configuration 'DB_PASSWORD' is missing.");
                System.err.println("  Action: Export DB_PASSWORD in your shell (e.g., export DB_PASSWORD=\"...\") or add DB_PASSWORD to a local .env file.");
                System.err.println("  Security Note: Never commit passwords to version control.");
                return null;
            }

            // 2. Enforce SSL requirement for PostgreSQL / Supabase
            if (url.startsWith("jdbc:postgresql:") && !url.toLowerCase().contains("sslmode=")) {
                url = url.contains("?") ? url + "&sslmode=require" : url + "?sslmode=require";
            }
        }

        try {
            Connection physical = borrowPhysicalConnection(url, user, password);
            return wrapLogicalConnection(physical);
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection Error] JDBC Driver not found on classpath: " + e.getMessage());
            return null;
        } catch (SQLException e) {
            System.err.println("[DBConnection Error] Failed to obtain connection: " + e.getMessage());
            return null;
        }
    }

    /**
     * Safely closes the provided database connection (or returns it to the pool if pooled).
     *
     * @param conn the Connection to close
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Returns the total number of physical connections currently maintained by the pool.
     */
    public static int getActiveConnectionsCount() {
        synchronized (poolLock) {
            return allPoolConnections.size();
        }
    }

    /**
     * Returns the number of idle physical connections currently ready to be borrowed.
     */
    public static int getAvailableConnectionsCount() {
        return availablePool.size();
    }

    /**
     * Tests whether a connection can be established to the database.
     *
     * @return true if connection succeeds, false otherwise
     */
    public static boolean testConnection() {
        try (Connection testConn = getConnection()) {
            return testConn != null && !testConn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Verifies the presence of the core tables in the connected database.
     * Works across PostgreSQL, MySQL, and H2 by querying the active catalog/schema.
     *
     * @return Set of found table names (lowercase)
     */
    public static Set<String> verifyDatabaseStructure() {
        Set<String> tablesFound = new HashSet<>();
        try (Connection conn = getConnection()) {
            if (conn == null) {
                return tablesFound;
            }
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tablesFound.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
        } catch (SQLException e) {
            System.err.println("[DBConnection Error] Failed to inspect metadata: " + e.getMessage());
        }
        return tablesFound;
    }
}
