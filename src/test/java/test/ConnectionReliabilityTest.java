package test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import util.DBConnection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConnectionReliabilityTest — Automated verification for Phase 28 Database Connection Reliability.
 *
 * Verifies:
 * 1. Logical close enforcement: isClosed() returns true, use-after-close throws SQLException.
 * 2. Transaction safety: uncommitted state is rolled back upon logical close, autoCommit reset to true.
 * 3. Aborted transaction recovery: failed queries do not leak poisoned transaction state.
 * 4. Thread safety: concurrent multi-threaded tasks safely lease distinct physical connections without race conditions.
 * 5. Bounded pool capacity: pool size strictly adheres to MAX_POOL_SIZE.
 * 6. Resource cleanup: resetConnectionConfig() cleanly drains and closes all underlying sockets.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConnectionReliabilityTest {

    private static final String TEST_URL = "jdbc:h2:mem:reliability_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String TEST_USER = "sa";
    private static final String TEST_PASS = "";

    @BeforeAll
    public static void initDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(TEST_URL, TEST_USER, TEST_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS reliability_tx_test (" +
                    "id INT PRIMARY KEY, " +
                    "val VARCHAR(100)" +
                    ")");
        }
    }

    @BeforeEach
    public void setupConfig() {
        DBConnection.setCustomConnectionConfig(TEST_URL, TEST_USER, TEST_PASS);
    }

    @AfterEach
    public void cleanup() throws SQLException {
        try (Connection conn = DriverManager.getConnection(TEST_URL, TEST_USER, TEST_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM reliability_tx_test");
        }
        DBConnection.resetConnectionConfig();
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.resetConnectionConfig();
    }

    @Test
    @Order(1)
    @DisplayName("Verify logical close makes isClosed() true and prevents subsequent statement execution")
    public void testLogicalCloseEnforcesClosedState() throws SQLException {
        Connection conn = DBConnection.getConnection();
        assertNotNull(conn, "Connection must not be null");
        assertFalse(conn.isClosed(), "Active connection must report isClosed() == false");

        // Execute query on open connection
        try (PreparedStatement stmt = conn.prepareStatement("SELECT 1");
             ResultSet rs = stmt.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }

        // Close logical connection
        conn.close();
        assertTrue(conn.isClosed(), "Closed connection must report isClosed() == true");

        // Idempotent close
        assertDoesNotThrow(conn::close, "Calling close() multiple times must be safe and idempotent");

        // Use-after-close must throw SQLException
        SQLException ex = assertThrows(SQLException.class, () -> conn.prepareStatement("SELECT 1"),
                "Attempting to prepare a statement on a closed logical connection must throw SQLException");
        assertTrue(ex.getMessage().toLowerCase().contains("closed"),
                "Exception message must mention that connection is closed");
    }

    @Test
    @Order(2)
    @DisplayName("Verify uncommitted transactions are rolled back on logical close and autoCommit is restored")
    public void testTransactionRollbackAndAutoCommitRestoredOnClose() throws SQLException {
        // 1. Borrower 1 starts transaction and inserts uncommitted row
        Connection conn1 = DBConnection.getConnection();
        conn1.setAutoCommit(false);
        assertFalse(conn1.getAutoCommit(), "autoCommit must be false");

        try (PreparedStatement stmt = conn1.prepareStatement("INSERT INTO reliability_tx_test VALUES (1, 'uncommitted_data')")) {
            int rows = stmt.executeUpdate();
            assertEquals(1, rows);
        }

        // Logical close WITHOUT commit
        conn1.close();
        assertTrue(conn1.isClosed());

        // 2. Borrower 2 acquires connection from pool
        Connection conn2 = DBConnection.getConnection();
        assertNotNull(conn2);
        assertTrue(conn2.getAutoCommit(), "autoCommit must be restored to true on connection return");

        // Verify uncommitted row was rolled back automatically
        try (PreparedStatement stmt = conn2.prepareStatement("SELECT count(*) FROM reliability_tx_test WHERE id = 1");
             ResultSet rs = stmt.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "Uncommitted transaction must have been rolled back upon logical close");
        }
        conn2.close();
    }

    @Test
    @Order(3)
    @DisplayName("Verify poisoned/aborted transaction state is cleansed on logical close")
    public void testAbortedTransactionCleanedOnClose() throws SQLException {
        Connection conn1 = DBConnection.getConnection();
        conn1.setAutoCommit(false);

        // Execute intentionally invalid SQL syntax
        assertThrows(SQLException.class, () -> {
            try (Statement stmt = conn1.createStatement()) {
                stmt.execute("SELECT * FROM non_existent_table_xyz_123");
            }
        });

        // Close without calling rollback()
        conn1.close();

        // Borrower 2 must receive a healthy connection with cleared state
        Connection conn2 = DBConnection.getConnection();
        assertTrue(conn2.getAutoCommit());
        try (PreparedStatement stmt = conn2.prepareStatement("SELECT 1");
             ResultSet rs = stmt.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
        conn2.close();
    }

    @Test
    @Order(4)
    @DisplayName("Verify concurrent multi-threaded borrowing safety and bounded capacity")
    public void testConcurrentMultiThreadedBorrowing() throws InterruptedException {
        int threadCount = 6;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger totalOperations = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to start simultaneously
                    for (int op = 0; op < operationsPerThread; op++) {
                        try (Connection conn = DBConnection.getConnection()) {
                            assertNotNull(conn, "Connection leased to thread must not be null");
                            assertFalse(conn.isClosed(), "Leased connection must not be closed");

                            int testId = (threadId * 100) + op;
                            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO reliability_tx_test VALUES (?, ?)")) {
                                stmt.setInt(1, testId);
                                stmt.setString(2, "thread_" + threadId + "_op_" + op);
                                assertEquals(1, stmt.executeUpdate());
                            }

                            try (PreparedStatement queryStmt = conn.prepareStatement("SELECT val FROM reliability_tx_test WHERE id = ?")) {
                                queryStmt.setInt(1, testId);
                                try (ResultSet rs = queryStmt.executeQuery()) {
                                    assertTrue(rs.next());
                                    assertEquals("thread_" + threadId + "_op_" + op, rs.getString("val"));
                                }
                            }

                            totalOperations.incrementAndGet();
                        }
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Launch all threads concurrently
        boolean finishedInTime = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finishedInTime, "All concurrent threads must complete within timeout");
        assertTrue(errors.isEmpty(), "Concurrent execution must have zero errors. Found: " + errors);
        assertEquals(threadCount * operationsPerThread, totalOperations.get(), "All operations must complete successfully");
        assertTrue(DBConnection.getActiveConnectionsCount() <= DBConnection.MAX_POOL_SIZE,
                "Total active physical connections must never exceed MAX_POOL_SIZE");
    }

    @Test
    @Order(5)
    @DisplayName("Verify pool reset drains and closes all underlying connections")
    public void testPoolResetCleansAllConnections() throws SQLException {
        // Borrow and return a connection to ensure pool has an idle physical connection
        try (Connection conn = DBConnection.getConnection()) {
            assertNotNull(conn);
        }

        assertTrue(DBConnection.getActiveConnectionsCount() > 0, "Pool must track active physical connections");

        // Reset
        DBConnection.resetConnectionConfig();

        assertEquals(0, DBConnection.getActiveConnectionsCount(), "Reset must close and clear all active connections");
        assertEquals(0, DBConnection.getAvailableConnectionsCount(), "Reset must empty the available queue");
    }
}
