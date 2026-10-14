package dao;

import model.Attendance;
import model.EventAttendanceDTO;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * AttendanceDAO — Data Access Object for 'attendance' table.
 *
 * Implements Phase 11: Attendance Management per AGENTS.md:
 * - Query attendance rosters joined with student users and event registrations
 * - Record, update, and remove attendance entries safely with parameterized queries
 * - Prevent duplicate records and respect unique constraint (event_id, student_id)
 * - Safe offline handling and error reporting
 */
public class AttendanceDAO {

    /**
     * Finds attendance for a specific student and event.
     */
    public Attendance findByEventAndStudent(int eventId, int studentId) {
        String sql = "SELECT attendance_id, event_id, student_id, present " +
                "FROM attendance WHERE event_id = ? AND student_id = ?";
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
            System.err.println("[AttendanceDAO Error] findByEventAndStudent failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves all attendance records for an event.
     */
    public List<Attendance> findByEventId(int eventId) {
        List<Attendance> list = new ArrayList<>();
        String sql = "SELECT attendance_id, event_id, student_id, present " +
                "FROM attendance WHERE event_id = ?";
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
            System.err.println("[AttendanceDAO Error] findByEventId failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Counts attended events for a specific student (where present = TRUE).
     */
    public int countPresentByStudentId(int studentId) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE student_id = ? AND present = TRUE";
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
            System.err.println("[AttendanceDAO Error] countPresentByStudentId failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts confirmed attendees marked present for a specific event.
     */
    public int countPresentForEvent(int eventId) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE event_id = ? AND present = TRUE";
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
            System.err.println("[AttendanceDAO Error] countPresentForEvent failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts participants marked absent for a specific event.
     */
    public int countAbsentForEvent(int eventId) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE event_id = ? AND present = FALSE";
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
            System.err.println("[AttendanceDAO Error] countAbsentForEvent failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts total attendance records recorded for a specific event.
     */
    public int countTotalForEvent(int eventId) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE event_id = ?";
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
            System.err.println("[AttendanceDAO Error] countTotalForEvent failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Checks if a student has an APPROVED registration for the event.
     */
    public boolean isStudentApproved(int eventId, int studentId) {
        String sql = "SELECT 1 FROM registrations WHERE event_id = ? AND student_id = ? AND status = 'APPROVED'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return false;
            stmt.setInt(1, eventId);
            stmt.setInt(2, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[AttendanceDAO Error] isStudentApproved failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Checks if an event exists.
     */
    public boolean isEventValid(int eventId) {
        String sql = "SELECT 1 FROM events WHERE event_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return false;
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[AttendanceDAO Error] isEventValid failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Retrieves the complete attendance and registration roster for an event.
     * Joins registrations, student users, events, and attendance records.
     */
    public List<EventAttendanceDTO> getEventAttendanceRoster(int eventId) {
        List<EventAttendanceDTO> list = new ArrayList<>();
        String sql = "SELECT " +
                "r.registration_id, r.event_id, r.student_id, r.status AS registration_status, " +
                "u.name AS student_name, u.email AS student_email, " +
                "e.event_name, " +
                "a.attendance_id, a.present " +
                "FROM registrations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN events e ON r.event_id = e.event_id " +
                "LEFT JOIN attendance a ON r.event_id = a.event_id AND r.student_id = a.student_id " +
                "WHERE r.event_id = ? " +
                "ORDER BY " +
                "  CASE WHEN r.status = 'APPROVED' THEN 0 ELSE 1 END, " +
                "  u.name ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return list;
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int regId = rs.getInt("registration_id");
                    int evId = rs.getInt("event_id");
                    int sId = rs.getInt("student_id");
                    String regStatus = rs.getString("registration_status");
                    String sName = rs.getString("student_name");
                    String sEmail = rs.getString("student_email");
                    String evName = rs.getString("event_name");

                    int attIdVal = rs.getInt("attendance_id");
                    boolean wasNullAtt = rs.wasNull();
                    Integer attId = wasNullAtt ? null : attIdVal;
                    boolean present = !wasNullAtt && rs.getBoolean("present");
                    boolean attendanceRecorded = !wasNullAtt;

                    list.add(new EventAttendanceDTO(
                            regId, evId, evName, sId, sName, sEmail,
                            regStatus, attId, attendanceRecorded, present
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AttendanceDAO Error] getEventAttendanceRoster failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Safely records or updates an attendance mark for a student at an event.
     * Uses PostgreSQL ON CONFLICT (event_id, student_id) to prevent duplicate rows while respecting unique constraint.
     */
    public boolean recordAttendance(int eventId, int studentId, boolean present) {
        String sql = "INSERT INTO attendance (event_id, student_id, present) VALUES (?, ?, ?) " +
                "ON CONFLICT (event_id, student_id) DO UPDATE SET present = ?, recorded_at = CURRENT_TIMESTAMP";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return false;
            stmt.setInt(1, eventId);
            stmt.setInt(2, studentId);
            stmt.setBoolean(3, present);
            stmt.setBoolean(4, present);

            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("[AttendanceDAO Error] recordAttendance failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Safely removes an attendance entry to reset the participant state to 'Unmarked'.
     * Used to correct accidental marks as permitted by Phase 11 requirements.
     */
    public boolean removeAttendance(int eventId, int studentId) {
        String sql = "DELETE FROM attendance WHERE event_id = ? AND student_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return false;
            stmt.setInt(1, eventId);
            stmt.setInt(2, studentId);

            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("[AttendanceDAO Error] removeAttendance failed: " + e.getMessage());
            return false;
        }
    }

    private Attendance mapRow(ResultSet rs) throws SQLException {
        return new Attendance(
                rs.getInt("attendance_id"),
                rs.getInt("event_id"),
                rs.getInt("student_id"),
                rs.getBoolean("present")
        );
    }
}
