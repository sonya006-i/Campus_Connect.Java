package dao;

import model.AnalyticsOverviewDTO;
import model.CategoryDistributionDTO;
import model.ClubEngagementDTO;
import model.EventPerformanceDTO;
import model.MonthlyTrendDTO;
import util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * AnalyticsDAO — Database access layer for aggregated analytics and intelligence queries.
 *
 * Implements Phase 14: Analytics per AGENTS.md:
 * - Uses JDBC PreparedStatement exclusively (no string concatenation).
 * - Implements isolated subquery aggregations to PREVENT inflated counts from multi-table joins.
 * - Non-destructive: read-only analytics operations with zero deletions or cascading updates.
 * - Graceful offline fallback on connection failures.
 */
public class AnalyticsDAO {

    /**
     * Aggregates campus-wide telemetry into an AnalyticsOverviewDTO.
     * Uses independent scalar subqueries to eliminate Cartesian product inflation.
     */
    public AnalyticsOverviewDTO getOverviewMetrics() {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM events) AS total_events, "
                + "(SELECT COUNT(*) FROM registrations) AS total_regs, "
                + "(SELECT COUNT(*) FROM registrations WHERE status = 'PENDING') AS pending_regs, "
                + "(SELECT COUNT(*) FROM registrations WHERE status = 'APPROVED') AS approved_regs, "
                + "(SELECT COUNT(*) FROM registrations WHERE status = 'REJECTED') AS rejected_regs, "
                + "(SELECT COUNT(*) FROM attendance WHERE present = TRUE) AS total_attended, "
                + "(SELECT COUNT(*) FROM certificates) AS total_certs, "
                + "(SELECT COUNT(*) FROM feedback) AS total_feedback, "
                + "(SELECT COALESCE(AVG(rating), 0.0) FROM feedback) AS avg_rating, "
                + "(SELECT COUNT(*) FROM clubs) AS total_clubs, "
                + "(SELECT COUNT(*) FROM users WHERE role = 'STUDENT') AS total_students";

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) {
                return AnalyticsOverviewDTO.offline();
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                if (rs.next()) {
                    AnalyticsOverviewDTO dto = new AnalyticsOverviewDTO();
                    dto.setDbOnline(true);
                    dto.setTotalEvents(rs.getInt("total_events"));
                    dto.setTotalRegistrations(rs.getInt("total_regs"));
                    dto.setPendingRegistrations(rs.getInt("pending_regs"));
                    dto.setApprovedRegistrations(rs.getInt("approved_regs"));
                    dto.setRejectedRegistrations(rs.getInt("rejected_regs"));
                    dto.setTotalAttended(rs.getInt("total_attended"));
                    dto.setTotalCertificatesIssued(rs.getInt("total_certs"));
                    dto.setTotalFeedbackCount(rs.getInt("total_feedback"));
                    dto.setAverageFeedbackRating(rs.getDouble("avg_rating"));
                    dto.setTotalClubs(rs.getInt("total_clubs"));
                    dto.setTotalStudents(rs.getInt("total_students"));
                    return dto;
                }
            }
        } catch (SQLException e) {
            System.err.println("[AnalyticsDAO Error] getOverviewMetrics failed: " + e.getMessage());
        }

        return AnalyticsOverviewDTO.offline();
    }

    /**
     * Aggregates club performance, total registrations, attendance, and feedback ratings.
     * Derived subqueries are pre-aggregated by club_id before joining to ensure exact counts.
     */
    public List<ClubEngagementDTO> getClubEngagementMetrics() {
        String sql = "SELECT c.club_id, c.club_name, "
                + "COALESCE(e.event_count, 0) AS event_count, "
                + "COALESCE(r.total_regs, 0) AS total_regs, "
                + "COALESCE(a.total_attended, 0) AS total_attended, "
                + "COALESCE(f.avg_rating, 0.0) AS avg_rating "
                + "FROM clubs c "
                + "LEFT JOIN ("
                + "    SELECT club_id, COUNT(*) AS event_count FROM events GROUP BY club_id"
                + ") e ON c.club_id = e.club_id "
                + "LEFT JOIN ("
                + "    SELECT ev.club_id, COUNT(reg.registration_id) AS total_regs "
                + "    FROM events ev JOIN registrations reg ON ev.event_id = reg.event_id "
                + "    GROUP BY ev.club_id"
                + ") r ON c.club_id = r.club_id "
                + "LEFT JOIN ("
                + "    SELECT ev.club_id, COUNT(att.attendance_id) AS total_attended "
                + "    FROM events ev JOIN attendance att ON ev.event_id = att.event_id "
                + "    WHERE att.present = TRUE GROUP BY ev.club_id"
                + ") a ON c.club_id = a.club_id "
                + "LEFT JOIN ("
                + "    SELECT ev.club_id, AVG(fb.rating) AS avg_rating "
                + "    FROM events ev JOIN feedback fb ON ev.event_id = fb.event_id "
                + "    GROUP BY ev.club_id"
                + ") f ON c.club_id = f.club_id "
                + "ORDER BY total_regs DESC, c.club_name ASC";

        List<ClubEngagementDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return list;

            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    list.add(new ClubEngagementDTO(
                            rs.getInt("club_id"),
                            rs.getString("club_name"),
                            rs.getInt("event_count"),
                            rs.getInt("total_regs"),
                            rs.getInt("total_attended"),
                            rs.getDouble("avg_rating")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AnalyticsDAO Error] getClubEngagementMetrics failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Aggregates event-by-event performance, registration funnel, and attendance.
     * Derived subqueries are pre-aggregated by event_id before joining.
     */
    public List<EventPerformanceDTO> getEventPerformanceMetrics() {
        String sql = "SELECT ev.event_id, ev.event_name, c.club_name, ev.category, ev.event_date, ev.max_participants, "
                + "COALESCE(r.reg_count, 0) AS total_regs, "
                + "COALESCE(r.approved_count, 0) AS approved_regs, "
                + "COALESCE(a.present_count, 0) AS present_count, "
                + "COALESCE(f.avg_rating, 0.0) AS avg_rating, "
                + "COALESCE(f.feedback_count, 0) AS feedback_count "
                + "FROM events ev "
                + "JOIN clubs c ON ev.club_id = c.club_id "
                + "LEFT JOIN ("
                + "    SELECT event_id, COUNT(*) AS reg_count, "
                + "           SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_count "
                + "    FROM registrations GROUP BY event_id"
                + ") r ON ev.event_id = r.event_id "
                + "LEFT JOIN ("
                + "    SELECT event_id, COUNT(*) AS present_count "
                + "    FROM attendance WHERE present = TRUE GROUP BY event_id"
                + ") a ON ev.event_id = a.event_id "
                + "LEFT JOIN ("
                + "    SELECT event_id, AVG(rating) AS avg_rating, COUNT(*) AS feedback_count "
                + "    FROM feedback GROUP BY event_id"
                + ") f ON ev.event_id = f.event_id "
                + "ORDER BY ev.event_date DESC, ev.event_id DESC";

        List<EventPerformanceDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return list;

            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    Date dateSql = rs.getDate("event_date");
                    LocalDate eventDate = (dateSql != null) ? dateSql.toLocalDate() : null;

                    list.add(new EventPerformanceDTO(
                            rs.getInt("event_id"),
                            rs.getString("event_name"),
                            rs.getString("club_name"),
                            rs.getString("category"),
                            eventDate,
                            rs.getInt("max_participants"),
                            rs.getInt("total_regs"),
                            rs.getInt("approved_regs"),
                            rs.getInt("present_count"),
                            rs.getDouble("avg_rating"),
                            rs.getInt("feedback_count")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AnalyticsDAO Error] getEventPerformanceMetrics failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves event and registration counts grouped by category.
     */
    public List<CategoryDistributionDTO> getCategoryDistribution() {
        String sql = "SELECT COALESCE(ev.category, 'General') AS cat_name, "
                + "COUNT(DISTINCT ev.event_id) AS event_count, "
                + "COUNT(reg.registration_id) AS total_regs "
                + "FROM events ev "
                + "LEFT JOIN registrations reg ON ev.event_id = reg.event_id "
                + "GROUP BY COALESCE(ev.category, 'General') "
                + "ORDER BY total_regs DESC, event_count DESC";

        List<CategoryDistributionDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return list;

            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    list.add(new CategoryDistributionDTO(
                            rs.getString("cat_name"),
                            rs.getInt("event_count"),
                            rs.getInt("total_regs")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AnalyticsDAO Error] getCategoryDistribution failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves monthly registration and attendance trends, merging them cleanly by month.
     * Prevents cross-product inflation by querying each timeline independently and merging in Java.
     */
    public List<MonthlyTrendDTO> getMonthlyParticipationTrends() {
        String regSql = "SELECT TO_CHAR(registration_date, 'YYYY-MM') AS m_key, "
                + "TO_CHAR(registration_date, 'Mon YYYY') AS m_label, "
                + "COUNT(*) AS reg_cnt "
                + "FROM registrations "
                + "GROUP BY TO_CHAR(registration_date, 'YYYY-MM'), TO_CHAR(registration_date, 'Mon YYYY') "
                + "ORDER BY m_key ASC";

        String attSql = "SELECT TO_CHAR(ev.event_date, 'YYYY-MM') AS m_key, "
                + "TO_CHAR(ev.event_date, 'Mon YYYY') AS m_label, "
                + "COUNT(att.attendance_id) AS att_cnt "
                + "FROM events ev "
                + "JOIN attendance att ON ev.event_id = att.event_id "
                + "WHERE att.present = TRUE "
                + "GROUP BY TO_CHAR(ev.event_date, 'YYYY-MM'), TO_CHAR(ev.event_date, 'Mon YYYY') "
                + "ORDER BY m_key ASC";

        Map<String, MonthlyTrendDTO> trendMap = new TreeMap<>();

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return Collections.emptyList();

            // 1. Fetch registrations by month
            try (PreparedStatement pstmt = conn.prepareStatement(regSql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    String mKey = rs.getString("m_key");
                    String mLabel = rs.getString("m_label");
                    int count = rs.getInt("reg_cnt");

                    MonthlyTrendDTO dto = trendMap.computeIfAbsent(mKey, k -> new MonthlyTrendDTO(mKey, mLabel));
                    dto.setRegistrationCount(count);
                }
            }

            // 2. Fetch verified attendance by month
            try (PreparedStatement pstmt = conn.prepareStatement(attSql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    String mKey = rs.getString("m_key");
                    String mLabel = rs.getString("m_label");
                    int count = rs.getInt("att_cnt");

                    MonthlyTrendDTO dto = trendMap.computeIfAbsent(mKey, k -> new MonthlyTrendDTO(mKey, mLabel));
                    dto.setAttendanceCount(count);
                }
            }

        } catch (SQLException e) {
            System.err.println("[AnalyticsDAO Error] getMonthlyParticipationTrends failed: " + e.getMessage());
        }

        return new ArrayList<>(trendMap.values());
    }

    /**
     * Retrieves distribution of registrations across status values (APPROVED, PENDING, REJECTED).
     */
    public Map<String, Integer> getRegistrationStatusCounts() {
        String sql = "SELECT status, COUNT(*) AS cnt FROM registrations GROUP BY status";
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("APPROVED", 0);
        map.put("PENDING", 0);
        map.put("REJECTED", 0);

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return map;

            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    String status = rs.getString("status");
                    int count = rs.getInt("cnt");
                    if (status != null) {
                        map.put(status.toUpperCase(), count);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[AnalyticsDAO Error] getRegistrationStatusCounts failed: " + e.getMessage());
        }

        return map;
    }
}
