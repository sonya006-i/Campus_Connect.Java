package dao;

import model.AdminFeedbackDTO;
import model.Feedback;
import model.FeedbackSummaryDTO;
import model.StudentFeedbackDTO;
import util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * FeedbackDAO — Data Access Object for 'feedback' table.
 *
 * Implements Phase 13: Feedback Management per AGENTS.md:
 * - Query feedback entries per event or student using PreparedStatement
 * - Retrieve student feedback eligible events (APPROVED registration + PRESENT attendance)
 * - Insert new student feedback records safely with unique constraint handling
 * - Retrieve admin feedback overview joined with event, club, and student details
 * - Calculate campus-wide and event-specific summary metrics
 * - Full offline resilience and defensive null checks
 */
public class FeedbackDAO {

    /**
     * Retrieves all feedback entries for a specific event.
     */
    public List<Feedback> findByEventId(int eventId) {
        List<Feedback> list = new ArrayList<>();
        String sql = "SELECT feedback_id, event_id, student_id, rating, comment, submitted_at " +
                "FROM feedback WHERE event_id = ? ORDER BY feedback_id DESC";
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
            System.err.println("[FeedbackDAO Error] findByEventId failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Finds feedback submitted by a specific student for a specific event.
     */
    public Feedback findByEventAndStudent(int eventId, int studentId) {
        String sql = "SELECT feedback_id, event_id, student_id, rating, comment, submitted_at " +
                "FROM feedback WHERE event_id = ? AND student_id = ?";
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
            System.err.println("[FeedbackDAO Error] findByEventAndStudent failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Checks if a student has already submitted feedback for an event.
     */
    public boolean hasStudentSubmitted(int eventId, int studentId) {
        String sql = "SELECT 1 FROM feedback WHERE event_id = ? AND student_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return false;
            stmt.setInt(1, eventId);
            stmt.setInt(2, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[FeedbackDAO Error] hasStudentSubmitted failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Retrieves all feedback entries submitted by a specific student.
     */
    public List<Feedback> findByStudentId(int studentId) {
        List<Feedback> list = new ArrayList<>();
        String sql = "SELECT feedback_id, event_id, student_id, rating, comment, submitted_at " +
                "FROM feedback WHERE student_id = ? ORDER BY submitted_at DESC, feedback_id DESC";
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
            System.err.println("[FeedbackDAO Error] findByStudentId failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Calculates the average star rating for a specific event.
     */
    public double getAverageRatingForEvent(int eventId) {
        String sql = "SELECT AVG(rating) FROM feedback WHERE event_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return 0.0;
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[FeedbackDAO Error] getAverageRatingForEvent failed: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Inserts a new feedback entry into the database.
     * Uses PreparedStatement with RETURN_GENERATED_KEYS.
     *
     * @return the generated feedback_id
     * @throws SQLException if a database error or unique constraint violation occurs
     */
    public int insertFeedback(Feedback feedback) throws SQLException {
        String sql = "INSERT INTO feedback (event_id, student_id, rating, comment) VALUES (?, ?, ?, ?)";
        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            throw new SQLException("Database connection unavailable.");
        }

        try (conn;
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, feedback.getEventId());
            stmt.setInt(2, feedback.getStudentId());
            stmt.setInt(3, feedback.getRating());
            stmt.setString(4, feedback.getComment());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Feedback insertion failed, zero rows affected.");
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int generatedId = keys.getInt(1);
                    feedback.setFeedbackId(generatedId);
                    return generatedId;
                } else {
                    throw new SQLException("Feedback inserted successfully but no generated ID was returned.");
                }
            }
        }
    }

    /**
     * Retrieves all events eligible for student feedback (requires APPROVED registration
     * and PRESENT attendance), along with feedback submission state and existing review details.
     */
    public List<StudentFeedbackDTO> getStudentFeedbackEvents(int studentId) {
        List<StudentFeedbackDTO> list = new ArrayList<>();
        String sql = "SELECT e.event_id, e.event_name, e.category, e.event_date, e.event_time, e.venue, " +
                "c.club_name, " +
                "a.present, " +
                "f.feedback_id, f.rating, f.comment, f.submitted_at " +
                "FROM registrations r " +
                "JOIN events e ON r.event_id = e.event_id " +
                "LEFT JOIN clubs c ON e.club_id = c.club_id " +
                "JOIN attendance a ON a.event_id = e.event_id AND a.student_id = r.student_id " +
                "LEFT JOIN feedback f ON f.event_id = e.event_id AND f.student_id = r.student_id " +
                "WHERE r.student_id = ? AND r.status = 'APPROVED' AND a.present = TRUE " +
                "ORDER BY e.event_date DESC, e.event_id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return list;
            stmt.setInt(1, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int eventId = rs.getInt("event_id");
                    String eventName = rs.getString("event_name");
                    String category = rs.getString("category");
                    Date evDate = rs.getDate("event_date");
                    Time evTime = rs.getTime("event_time");
                    String venue = rs.getString("venue");
                    String clubName = rs.getString("club_name");
                    boolean attended = rs.getBoolean("present");

                    int feedbackId = rs.getInt("feedback_id");
                    boolean feedbackSubmitted = !rs.wasNull() && feedbackId > 0;
                    int rating = rs.getInt("rating");
                    String comment = rs.getString("comment");
                    Timestamp subTs = rs.getTimestamp("submitted_at");

                    LocalDate date = (evDate != null) ? evDate.toLocalDate() : null;
                    LocalTime time = (evTime != null) ? evTime.toLocalTime() : null;
                    LocalDateTime submittedAt = (subTs != null) ? subTs.toLocalDateTime() : null;

                    StudentFeedbackDTO dto = new StudentFeedbackDTO(
                            eventId, eventName, clubName, category, date, time, venue,
                            attended, feedbackSubmitted, feedbackId, rating, comment, submittedAt
                    );
                    list.add(dto);
                }
            }
        } catch (SQLException e) {
            System.err.println("[FeedbackDAO Error] getStudentFeedbackEvents failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves all feedback entries across campus with event and student details for administrators.
     */
    public List<AdminFeedbackDTO> getAllFeedbackWithDetails() {
        List<AdminFeedbackDTO> list = new ArrayList<>();
        String sql = "SELECT f.feedback_id, f.event_id, f.student_id, f.rating, f.comment, f.submitted_at, " +
                "e.event_name, e.category, e.event_date, " +
                "c.club_name, " +
                "u.name AS student_name, u.email AS student_email " +
                "FROM feedback f " +
                "JOIN events e ON f.event_id = e.event_id " +
                "LEFT JOIN clubs c ON e.club_id = c.club_id " +
                "JOIN users u ON f.student_id = u.user_id " +
                "ORDER BY f.submitted_at DESC, f.feedback_id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return list;

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int feedbackId = rs.getInt("feedback_id");
                    int eventId = rs.getInt("event_id");
                    int studentId = rs.getInt("student_id");
                    int rating = rs.getInt("rating");
                    String comment = rs.getString("comment");
                    Timestamp subTs = rs.getTimestamp("submitted_at");

                    String eventName = rs.getString("event_name");
                    String category = rs.getString("category");
                    Date evDate = rs.getDate("event_date");
                    String clubName = rs.getString("club_name");
                    String studentName = rs.getString("student_name");
                    String studentEmail = rs.getString("student_email");

                    LocalDate date = (evDate != null) ? evDate.toLocalDate() : null;
                    LocalDateTime submittedAt = (subTs != null) ? subTs.toLocalDateTime() : null;

                    AdminFeedbackDTO dto = new AdminFeedbackDTO(
                            feedbackId, eventId, eventName, clubName, category, date,
                            studentId, studentName, studentEmail, rating, comment, submittedAt
                    );
                    list.add(dto);
                }
            }
        } catch (SQLException e) {
            System.err.println("[FeedbackDAO Error] getAllFeedbackWithDetails failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves campus-wide summary metrics for submitted feedback.
     */
    public FeedbackSummaryDTO getFeedbackSummary() {
        String sql = "SELECT " +
                "COUNT(*) AS total_feedback, " +
                "COALESCE(AVG(rating), 0.0) AS avg_rating, " +
                "COUNT(DISTINCT event_id) AS events_reviewed, " +
                "SUM(CASE WHEN rating = 5 THEN 1 ELSE 0 END) AS five_stars, " +
                "SUM(CASE WHEN rating = 4 THEN 1 ELSE 0 END) AS four_stars, " +
                "SUM(CASE WHEN rating = 3 THEN 1 ELSE 0 END) AS three_stars, " +
                "SUM(CASE WHEN rating = 2 THEN 1 ELSE 0 END) AS two_stars, " +
                "SUM(CASE WHEN rating = 1 THEN 1 ELSE 0 END) AS one_stars " +
                "FROM feedback";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return FeedbackSummaryDTO.offline();

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total_feedback");
                    double avg = rs.getDouble("avg_rating");
                    int eventsReviewed = rs.getInt("events_reviewed");
                    int five = rs.getInt("five_stars");
                    int four = rs.getInt("four_stars");
                    int three = rs.getInt("three_stars");
                    int two = rs.getInt("two_stars");
                    int one = rs.getInt("one_stars");

                    return new FeedbackSummaryDTO(total, avg, five, four, three, two, one, eventsReviewed, true);
                }
            }
        } catch (SQLException e) {
            System.err.println("[FeedbackDAO Error] getFeedbackSummary failed: " + e.getMessage());
        }
        return FeedbackSummaryDTO.offline();
    }

    /**
     * Retrieves summary metrics for feedback on a specific event.
     */
    public FeedbackSummaryDTO getFeedbackSummaryForEvent(int eventId) {
        String sql = "SELECT " +
                "COUNT(*) AS total_feedback, " +
                "COALESCE(AVG(rating), 0.0) AS avg_rating, " +
                "COUNT(DISTINCT event_id) AS events_reviewed, " +
                "SUM(CASE WHEN rating = 5 THEN 1 ELSE 0 END) AS five_stars, " +
                "SUM(CASE WHEN rating = 4 THEN 1 ELSE 0 END) AS four_stars, " +
                "SUM(CASE WHEN rating = 3 THEN 1 ELSE 0 END) AS three_stars, " +
                "SUM(CASE WHEN rating = 2 THEN 1 ELSE 0 END) AS two_stars, " +
                "SUM(CASE WHEN rating = 1 THEN 1 ELSE 0 END) AS one_stars " +
                "FROM feedback WHERE event_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return FeedbackSummaryDTO.offline();
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total_feedback");
                    double avg = rs.getDouble("avg_rating");
                    int eventsReviewed = rs.getInt("events_reviewed");
                    int five = rs.getInt("five_stars");
                    int four = rs.getInt("four_stars");
                    int three = rs.getInt("three_stars");
                    int two = rs.getInt("two_stars");
                    int one = rs.getInt("one_stars");

                    return new FeedbackSummaryDTO(total, avg, five, four, three, two, one, eventsReviewed, true);
                }
            }
        } catch (SQLException e) {
            System.err.println("[FeedbackDAO Error] getFeedbackSummaryForEvent failed: " + e.getMessage());
        }
        return FeedbackSummaryDTO.offline();
    }

    private Feedback mapRow(ResultSet rs) throws SQLException {
        Timestamp subTs = null;
        try {
            subTs = rs.getTimestamp("submitted_at");
        } catch (SQLException ignored) {
            // column might not be requested in legacy queries
        }

        return new Feedback(
                rs.getInt("feedback_id"),
                rs.getInt("event_id"),
                rs.getInt("student_id"),
                rs.getInt("rating"),
                rs.getString("comment"),
                (subTs != null) ? subTs.toLocalDateTime() : null
        );
    }
}
