package dao;

import model.Event;
import model.PopularEventDTO;
import util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CampusPulseDAO — Read-only database access layer for campus activity feeds,
 * popular event aggregations, and student milestone statistics.
 *
 * Implements Phase 15: Campus Pulse & Lightweight Gamification per AGENTS.md:
 * - Uses JDBC PreparedStatement exclusively (no string concatenation).
 * - Avoids N+1 queries by pre-aggregating registration counts and distinct clubs.
 * - Non-destructive: strictly read-only queries with zero mutations.
 * - Safe offline fallback on database connectivity loss.
 */
public class CampusPulseDAO {

    /**
     * Retrieves campus-wide count of verified attendee records (present = true).
     */
    public int getCampusTotalVerifiedAttendees() {
        String sql = "SELECT COUNT(*) FROM attendance WHERE present = TRUE";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = (conn != null) ? conn.prepareStatement(sql) : null) {

            if (pstmt == null) return 0;
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[CampusPulseDAO Error] getCampusTotalVerifiedAttendees failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Retrieves top campus events ranked by registration count.
     * Prevents N+1 queries by grouping event and registration counts together.
     *
     * @param limit maximum number of popular events to return
     * @return list of PopularEventDTO objects
     */
    public List<PopularEventDTO> getPopularEvents(int limit) {
        String sql = "SELECT e.event_id, e.event_name, c.club_name, e.category, e.event_date, e.max_participants, "
                + "COUNT(r.registration_id) AS reg_count "
                + "FROM events e "
                + "JOIN clubs c ON e.club_id = c.club_id "
                + "LEFT JOIN registrations r ON e.event_id = r.event_id "
                + "GROUP BY e.event_id, e.event_name, c.club_name, e.category, e.event_date, e.max_participants "
                + "ORDER BY reg_count DESC, e.event_date DESC "
                + "LIMIT ?";

        List<PopularEventDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = (conn != null) ? conn.prepareStatement(sql) : null) {

            if (pstmt == null) return list;
            pstmt.setInt(1, Math.max(1, limit));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("event_date");
                    LocalDate eventDate = (d != null) ? d.toLocalDate() : null;

                    list.add(new PopularEventDTO(
                            rs.getInt("event_id"),
                            rs.getString("event_name"),
                            rs.getString("club_name"),
                            rs.getString("category"),
                            eventDate,
                            rs.getInt("reg_count"),
                            rs.getInt("max_participants")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[CampusPulseDAO Error] getPopularEvents failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves events with an event_date strictly in the past (event_date < CURRENT_DATE).
     *
     * @param limit maximum number of completed events to return
     * @return list of recently completed Event objects
     */
    public List<Event> getRecentlyCompletedEvents(int limit) {
        String sql = "SELECT e.event_id, e.club_id, e.event_name, e.description, e.category, "
                + "e.event_date, e.event_time, e.venue, e.max_participants, e.registration_deadline, "
                + "c.club_name "
                + "FROM events e "
                + "JOIN clubs c ON e.club_id = c.club_id "
                + "WHERE e.event_date < CURRENT_DATE "
                + "ORDER BY e.event_date DESC, e.event_id DESC "
                + "LIMIT ?";

        List<Event> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = (conn != null) ? conn.prepareStatement(sql) : null) {

            if (pstmt == null) return list;
            pstmt.setInt(1, Math.max(1, limit));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Event ev = new Event();
                    ev.setEventId(rs.getInt("event_id"));
                    ev.setClubId(rs.getInt("club_id"));
                    ev.setEventName(rs.getString("event_name"));
                    ev.setDescription(rs.getString("description"));
                    ev.setCategory(rs.getString("category"));

                    Date d = rs.getDate("event_date");
                    if (d != null) ev.setEventDate(d.toLocalDate());

                    Time t = rs.getTime("event_time");
                    if (t != null) ev.setEventTime(t.toLocalTime());

                    ev.setVenue(rs.getString("venue"));
                    ev.setMaxParticipants(rs.getInt("max_participants"));

                    Date dl = rs.getDate("registration_deadline");
                    if (dl != null) ev.setRegistrationDeadline(dl.toLocalDate());

                    ev.setClubName(rs.getString("club_name"));
                    list.add(ev);
                }
            }
        } catch (SQLException e) {
            System.err.println("[CampusPulseDAO Error] getRecentlyCompletedEvents failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Calculates the number of distinct clubs where a specific student attended an event.
     */
    public int getStudentDistinctClubsAttended(int studentId) {
        if (studentId <= 0) return 0;
        String sql = "SELECT COUNT(DISTINCT e.club_id) "
                + "FROM attendance a "
                + "JOIN events e ON a.event_id = e.event_id "
                + "WHERE a.student_id = ? AND a.present = TRUE";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = (conn != null) ? conn.prepareStatement(sql) : null) {

            if (pstmt == null) return 0;
            pstmt.setInt(1, studentId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[CampusPulseDAO Error] getStudentDistinctClubsAttended failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Retrieves the number of reviews submitted by a student.
     */
    public int getStudentFeedbackCount(int studentId) {
        if (studentId <= 0) return 0;
        String sql = "SELECT COUNT(*) FROM feedback WHERE student_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = (conn != null) ? conn.prepareStatement(sql) : null) {

            if (pstmt == null) return 0;
            pstmt.setInt(1, studentId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[CampusPulseDAO Error] getStudentFeedbackCount failed: " + e.getMessage());
        }
        return 0;
    }
}
