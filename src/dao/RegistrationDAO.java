package dao;

import model.AdminRegistrationDTO;
import model.Event;
import model.Registration;
import model.StudentRegistrationDTO;
import util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

/**
 * RegistrationDAO — Data Access Object for 'registrations' table.
 */
public class RegistrationDAO {

    /**
     * Retrieves all registrations for a specific student.
     */
    public List<Registration> findByStudentId(int studentId) {
        List<Registration> list = new ArrayList<>();
        String sql = "SELECT registration_id, event_id, student_id, status, registration_date " +
                "FROM registrations WHERE student_id = ? ORDER BY registration_date DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return list;
            stmt.setInt(1, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] findByStudentId failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves all registrations for a specific event.
     */
    public List<Registration> findByEventId(int eventId) {
        List<Registration> list = new ArrayList<>();
        String sql = "SELECT registration_id, event_id, student_id, status, registration_date " +
                "FROM registrations WHERE event_id = ? ORDER BY registration_date ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return list;
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] findByEventId failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Counts approved registrations for a specific event.
     */
    public int countApprovedForEvent(int eventId) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE event_id = ? AND status = 'APPROVED'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return 0;
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] countApprovedForEvent failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts total registrations for a specific student.
     */
    public int countByStudentId(int studentId) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE student_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return 0;
            stmt.setInt(1, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] countByStudentId failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts all registrations across campus.
     */
    public int countAll() {
        String sql = "SELECT COUNT(*) FROM registrations";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return 0;

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] countAll failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts registrations by a specific status across campus (e.g. 'PENDING', 'APPROVED', 'REJECTED').
     */
    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE status = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return 0;
            stmt.setString(1, status);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] countByStatus failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts total registrations recorded for a specific event.
     */
    public int countTotalForEvent(int eventId) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE event_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return 0;
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] countTotalForEvent failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Inserts a new student registration with initial 'PENDING' status.
     * Uses RETURN_GENERATED_KEYS to obtain the database-generated registration_id.
     */
    public int registerStudent(int eventId, int studentId) throws SQLException {
        String sql = "INSERT INTO registrations (event_id, student_id, status, registration_date) VALUES (?, ?, 'PENDING', ?)";
        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            throw new SQLException("Database connection unavailable.");
        }

        try (conn;
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, eventId);
            stmt.setInt(2, studentId);
            stmt.setDate(3, Date.valueOf(java.time.LocalDate.now()));

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Registration insert failed, zero rows affected.");
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                } else {
                    throw new SQLException("Registration succeeded but no generated ID was returned.");
                }
            }
        }
    }

    /**
     * Retrieves a student's registration for a specific event.
     */
    public Registration findByEventAndStudent(int eventId, int studentId) {
        String sql = "SELECT registration_id, event_id, student_id, status, registration_date " +
                "FROM registrations WHERE event_id = ? AND student_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return null;
            stmt.setInt(1, eventId);
            stmt.setInt(2, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] findByEventAndStudent failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Checks if a student is already registered for an event.
     */
    public boolean isRegistered(int eventId, int studentId) {
        return findByEventAndStudent(eventId, studentId) != null;
    }

    /**
     * Checks registration status for a student on a specific event.
     * Returns "APPROVED", "PENDING", "REJECTED", or null if not registered.
     */
    public String getRegistrationStatus(int eventId, int studentId) {
        Registration reg = findByEventAndStudent(eventId, studentId);
        return reg != null ? reg.getStatus() : null;
    }

    /**
     * Counts active registrations occupying capacity (PENDING + APPROVED).
     * Rejected registrations do not occupy slots.
     */
    public int countActiveForEvent(int eventId) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE event_id = ? AND status IN ('PENDING', 'APPROVED')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return 0;
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] countActiveForEvent failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Retrieves all registrations for a specific student with full event and club details
     * via a single optimized SQL JOIN query (no N+1 database roundtrips).
     */
    public List<StudentRegistrationDTO> getStudentRegistrationsWithEvents(int studentId) {
        List<StudentRegistrationDTO> list = new ArrayList<>();
        String sql = "SELECT " +
                "r.registration_id, r.event_id, r.student_id, r.status, r.registration_date, " +
                "e.club_id, e.event_name, e.description, e.category, e.event_date, e.event_time, e.venue, e.max_participants, e.registration_deadline, " +
                "c.club_name " +
                "FROM registrations r " +
                "JOIN events e ON r.event_id = e.event_id " +
                "LEFT JOIN clubs c ON e.club_id = c.club_id " +
                "WHERE r.student_id = ? " +
                "ORDER BY r.registration_date DESC, r.registration_id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return list;
            stmt.setInt(1, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date regDate = rs.getDate("registration_date");
                    Registration reg = new Registration(
                            rs.getInt("registration_id"),
                            rs.getInt("event_id"),
                            rs.getInt("student_id"),
                            rs.getString("status"),
                            regDate != null ? regDate.toLocalDate() : null
                    );

                    Date evDate = rs.getDate("event_date");
                    Time evTime = rs.getTime("event_time");
                    Date deadline = rs.getDate("registration_deadline");
                    Event event = new Event(
                            rs.getInt("event_id"),
                            rs.getInt("club_id"),
                            rs.getString("event_name"),
                            rs.getString("description"),
                            rs.getString("category"),
                            evDate != null ? evDate.toLocalDate() : null,
                            evTime != null ? evTime.toLocalTime() : null,
                            rs.getString("venue"),
                            rs.getInt("max_participants"),
                            deadline != null ? deadline.toLocalDate() : null
                    );
                    event.setClubName(rs.getString("club_name"));

                    list.add(new StudentRegistrationDTO(reg, event));
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] getStudentRegistrationsWithEvents failed: " + e.getMessage());
        }
        return list;
    }

    private Registration mapRow(ResultSet rs) throws SQLException {
        Date regDate = rs.getDate("registration_date");
        return new Registration(
                rs.getInt("registration_id"),
                rs.getInt("event_id"),
                rs.getInt("student_id"),
                rs.getString("status"),
                regDate != null ? regDate.toLocalDate() : null
        );
    }

    /**
     * Finds a registration by its ID.
     */
    public Registration findById(int registrationId) {
        String sql = "SELECT registration_id, event_id, student_id, status, registration_date " +
                "FROM registrations WHERE registration_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return null;
            stmt.setInt(1, registrationId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] findById failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves an AdminRegistrationDTO by registration ID with student and event details.
     */
    public AdminRegistrationDTO getAdminRegistrationById(int registrationId) {
        String sql = "SELECT r.registration_id, r.event_id, r.student_id, r.status, r.registration_date, " +
                "u.name AS student_name, u.email AS student_email, " +
                "e.event_name, e.category, e.event_date, e.event_time, e.venue, e.max_participants, " +
                "c.club_name " +
                "FROM registrations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN events e ON r.event_id = e.event_id " +
                "LEFT JOIN clubs c ON e.club_id = c.club_id " +
                "WHERE r.registration_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return null;
            stmt.setInt(1, registrationId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapAdminRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] getAdminRegistrationById failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves all campus registrations for administrator review.
     */
    public List<AdminRegistrationDTO> getAllAdminRegistrations() {
        return searchAdminRegistrations(null, null, null);
    }

    /**
     * Searches and filters campus registrations by keyword, status, and event ID.
     */
    public List<AdminRegistrationDTO> searchAdminRegistrations(String keyword, String status, Integer eventId) {
        List<AdminRegistrationDTO> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT r.registration_id, r.event_id, r.student_id, r.status, r.registration_date, " +
                "u.name AS student_name, u.email AS student_email, " +
                "e.event_name, e.category, e.event_date, e.event_time, e.venue, e.max_participants, " +
                "c.club_name " +
                "FROM registrations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN events e ON r.event_id = e.event_id " +
                "LEFT JOIN clubs c ON e.club_id = c.club_id " +
                "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            sql.append(" AND r.status = ? ");
            params.add(status.trim().toUpperCase());
        }

        if (eventId != null && eventId > 0) {
            sql.append(" AND r.event_id = ? ");
            params.add(eventId);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (LOWER(u.name) LIKE ? OR LOWER(u.email) LIKE ? OR LOWER(e.event_name) LIKE ? OR LOWER(c.club_name) LIKE ?) ");
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(pattern);
            }
        }

        sql.append(" ORDER BY r.registration_date DESC, r.registration_id DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql.toString()) : null) {

            if (stmt == null) return list;

            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) {
                    stmt.setInt(i + 1, (Integer) p);
                } else {
                    stmt.setString(i + 1, (String) p);
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapAdminRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[RegistrationDAO Error] searchAdminRegistrations failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Atomically approves a pending registration with event capacity validation.
     * Uses a database transaction with conditional update to prevent race conditions and stale overwrites.
     */
    public ApprovalResult approveRegistrationWithCapacityCheck(int registrationId) {
        if (registrationId <= 0) {
            return ApprovalResult.notFound("Invalid registration ID.");
        }

        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            return ApprovalResult.dbError("Database connection is unavailable.");
        }

        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // 1. Inspect current registration and event capacity
            int eventId = -1;
            String currentStatus = null;
            int maxParticipants = 0;
            String eventName = "";

            String selectSql = "SELECT r.registration_id, r.event_id, r.status, " +
                    "e.max_participants, e.event_name " +
                    "FROM registrations r " +
                    "JOIN events e ON r.event_id = e.event_id " +
                    "WHERE r.registration_id = ? FOR UPDATE";

            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setInt(1, registrationId);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        eventId = rs.getInt("event_id");
                        currentStatus = rs.getString("status");
                        maxParticipants = rs.getInt("max_participants");
                        eventName = rs.getString("event_name");
                    } else {
                        conn.rollback();
                        return ApprovalResult.notFound("Registration record or associated event does not exist.");
                    }
                }
            }

            // Verify status is PENDING
            if (!"PENDING".equalsIgnoreCase(currentStatus)) {
                conn.rollback();
                return ApprovalResult.staleStatus("Registration is currently '" + currentStatus + "' (only PENDING registrations can be approved).");
            }

            // 2. Capacity Check: count how many registrations for this event are already APPROVED
            int approvedCount = 0;
            String countSql = "SELECT COUNT(*) FROM registrations WHERE event_id = ? AND status = 'APPROVED'";
            try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                countStmt.setInt(1, eventId);
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        approvedCount = rs.getInt(1);
                    }
                }
            }

            if (maxParticipants > 0 && approvedCount >= maxParticipants) {
                // Capacity reached: leave registration as PENDING and explain
                conn.rollback();
                return ApprovalResult.capacityReached(
                        "Cannot approve registration: Event '" + eventName + "' has reached its maximum capacity of "
                                + maxParticipants + " participants (" + approvedCount + " already confirmed)."
                );
            }

            // 3. Conditional Update: execute update strictly on PENDING
            String updateSql = "UPDATE registrations SET status = 'APPROVED' WHERE registration_id = ? AND status = 'PENDING'";
            int affected;
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setInt(1, registrationId);
                affected = updateStmt.executeUpdate();
            }

            if (affected == 1) {
                conn.commit();
                return ApprovalResult.success("Registration #" + registrationId + " for '" + eventName + "' was successfully approved.");
            } else {
                conn.rollback();
                return ApprovalResult.staleStatus("Registration status was updated concurrently by another administrator.");
            }

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("[RegistrationDAO Error] rollback failed: " + rollbackEx.getMessage());
            }
            System.err.println("[RegistrationDAO Error] approveRegistrationWithCapacityCheck failed: " + e.getMessage());
            return ApprovalResult.dbError("Database error during registration approval: " + e.getMessage());
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
                conn.close();
            } catch (SQLException e) {
                System.err.println("[RegistrationDAO Error] Failed to restore connection: " + e.getMessage());
            }
        }
    }

    /**
     * Atomically rejects a pending registration using conditional status update.
     */
    public RejectionResult rejectRegistration(int registrationId) {
        if (registrationId <= 0) {
            return RejectionResult.notFound("Invalid registration ID.");
        }

        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            return RejectionResult.dbError("Database connection is unavailable.");
        }

        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Check current status
            String currentStatus = null;
            String eventName = "";
            String selectSql = "SELECT r.status, e.event_name FROM registrations r " +
                    "JOIN events e ON r.event_id = e.event_id WHERE r.registration_id = ? FOR UPDATE";
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setInt(1, registrationId);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        currentStatus = rs.getString("status");
                        eventName = rs.getString("event_name");
                    } else {
                        conn.rollback();
                        return RejectionResult.notFound("Registration record does not exist.");
                    }
                }
            }

            if (!"PENDING".equalsIgnoreCase(currentStatus)) {
                conn.rollback();
                return RejectionResult.staleStatus("Registration is currently '" + currentStatus + "' (only PENDING registrations can be rejected).");
            }

            // Conditional update
            String updateSql = "UPDATE registrations SET status = 'REJECTED' WHERE registration_id = ? AND status = 'PENDING'";
            int affected;
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setInt(1, registrationId);
                affected = updateStmt.executeUpdate();
            }

            if (affected == 1) {
                conn.commit();
                return RejectionResult.success("Registration #" + registrationId + " for '" + eventName + "' was rejected.");
            } else {
                conn.rollback();
                return RejectionResult.staleStatus("Registration status was updated concurrently by another administrator.");
            }

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("[RegistrationDAO Error] rollback failed: " + rollbackEx.getMessage());
            }
            System.err.println("[RegistrationDAO Error] rejectRegistration failed: " + e.getMessage());
            return RejectionResult.dbError("Database error during registration rejection: " + e.getMessage());
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
                conn.close();
            } catch (SQLException e) {
                System.err.println("[RegistrationDAO Error] Failed to restore connection: " + e.getMessage());
            }
        }
    }

    private AdminRegistrationDTO mapAdminRow(ResultSet rs) throws SQLException {
        Date regDate = rs.getDate("registration_date");
        Date evDate = rs.getDate("event_date");
        Time evTime = rs.getTime("event_time");

        return new AdminRegistrationDTO(
                rs.getInt("registration_id"),
                rs.getInt("event_id"),
                rs.getInt("student_id"),
                rs.getString("status"),
                regDate != null ? regDate.toLocalDate() : null,
                rs.getString("student_name"),
                rs.getString("student_email"),
                rs.getString("event_name"),
                rs.getString("category"),
                rs.getString("club_name"),
                evDate != null ? evDate.toLocalDate() : null,
                evTime != null ? evTime.toLocalTime() : null,
                rs.getString("venue"),
                rs.getInt("max_participants")
        );
    }

    /**
     * Result of an approval operation.
     */
    public static class ApprovalResult {
        public enum Status {
            SUCCESS,
            CAPACITY_REACHED,
            STALE_STATUS,
            NOT_FOUND,
            DATABASE_ERROR
        }

        private final boolean success;
        private final Status status;
        private final String message;

        public ApprovalResult(boolean success, Status status, String message) {
            this.success = success;
            this.status = status;
            this.message = message;
        }

        public static ApprovalResult success(String message) {
            return new ApprovalResult(true, Status.SUCCESS, message);
        }

        public static ApprovalResult capacityReached(String message) {
            return new ApprovalResult(false, Status.CAPACITY_REACHED, message);
        }

        public static ApprovalResult staleStatus(String message) {
            return new ApprovalResult(false, Status.STALE_STATUS, message);
        }

        public static ApprovalResult notFound(String message) {
            return new ApprovalResult(false, Status.NOT_FOUND, message);
        }

        public static ApprovalResult dbError(String message) {
            return new ApprovalResult(false, Status.DATABASE_ERROR, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public Status getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Result of a rejection operation.
     */
    public static class RejectionResult {
        public enum Status {
            SUCCESS,
            STALE_STATUS,
            NOT_FOUND,
            DATABASE_ERROR
        }

        private final boolean success;
        private final Status status;
        private final String message;

        public RejectionResult(boolean success, Status status, String message) {
            this.success = success;
            this.status = status;
            this.message = message;
        }

        public static RejectionResult success(String message) {
            return new RejectionResult(true, Status.SUCCESS, message);
        }

        public static RejectionResult staleStatus(String message) {
            return new RejectionResult(false, Status.STALE_STATUS, message);
        }

        public static RejectionResult notFound(String message) {
            return new RejectionResult(false, Status.NOT_FOUND, message);
        }

        public static RejectionResult dbError(String message) {
            return new RejectionResult(false, Status.DATABASE_ERROR, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public Status getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}
