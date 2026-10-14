package dao;

import model.Event;
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
 * EventDAO — Data Access Object for 'events' table.
 */
public class EventDAO {

    /**
     * Finds an event by its ID with club name included.
     */
    public Event findById(int eventId) {
        String sql = "SELECT e.event_id, e.club_id, e.event_name, e.description, e.category, " +
                "e.event_date, e.event_time, e.venue, e.max_participants, e.registration_deadline, " +
                "c.club_name " +
                "FROM events e " +
                "LEFT JOIN clubs c ON e.club_id = c.club_id " +
                "WHERE e.event_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return null;
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[EventDAO Error] findById failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves all events ordered by date ascending with club names.
     */
    public List<Event> getAll() {
        return searchAndFilter(null, null);
    }

    /**
     * Retrieves all distinct categories present in the events database.
     */
    public List<String> getDistinctCategories() {
        List<String> categories = new ArrayList<>();
        String sql = "SELECT DISTINCT category FROM events WHERE category IS NOT NULL AND category != '' ORDER BY category ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return categories;

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    categories.add(rs.getString("category"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[EventDAO Error] getDistinctCategories failed: " + e.getMessage());
        }
        return categories;
    }

    /**
     * Searches and filters events by keyword and category with a single clean JOIN query.
     * Searches case-insensitively against event name, category, venue, and club name.
     */
    public List<Event> searchAndFilter(String keyword, String category) {
        return searchAndFilter(keyword, category, null);
    }

    /**
     * Searches and filters events by keyword, category, and optionally club ID.
     */
    public List<Event> searchAndFilter(String keyword, String category, Integer clubId) {
        List<Event> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT e.event_id, e.club_id, e.event_name, e.description, e.category, " +
                "e.event_date, e.event_time, e.venue, e.max_participants, e.registration_deadline, " +
                "c.club_name " +
                "FROM events e " +
                "LEFT JOIN clubs c ON e.club_id = c.club_id " +
                "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();

        if (clubId != null && clubId > 0) {
            sql.append(" AND e.club_id = ? ");
            params.add(clubId);
        }

        // Category filter
        boolean hasCategory = category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("All Categories");
        if (hasCategory) {
            sql.append(" AND e.category = ? ");
            params.add(category.trim());
        }

        // Keyword search filter
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        if (hasKeyword) {
            sql.append(" AND (LOWER(e.event_name) LIKE ? OR LOWER(e.category) LIKE ? OR LOWER(e.venue) LIKE ? OR LOWER(c.club_name) LIKE ? OR LOWER(e.description) LIKE ?) ");
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(pattern);
            }
        }

        sql.append(" ORDER BY e.event_date ASC, e.event_time ASC");

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
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[EventDAO Error] searchAndFilter failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Inserts a new event into the 'events' table.
     * Sets the database-generated event_id on the passed Event object.
     *
     * @param event Event entity containing event details
     * @return generated event_id if successful, -1 otherwise
     */
    public int insert(Event event) {
        if (event == null) return -1;
        String sql = "INSERT INTO events (club_id, event_name, description, category, " +
                "event_date, event_time, venue, max_participants, registration_deadline) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS) : null) {

            if (stmt == null) return -1;

            stmt.setInt(1, event.getClubId());
            stmt.setString(2, event.getEventName());
            stmt.setString(3, event.getDescription());
            stmt.setString(4, event.getCategory());
            stmt.setDate(5, event.getEventDate() != null ? Date.valueOf(event.getEventDate()) : null);
            stmt.setTime(6, event.getEventTime() != null ? Time.valueOf(event.getEventTime()) : null);
            stmt.setString(7, event.getVenue());
            stmt.setInt(8, event.getMaxParticipants());
            stmt.setDate(9, event.getRegistrationDeadline() != null ? Date.valueOf(event.getRegistrationDeadline()) : null);

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int generatedId = rs.getInt(1);
                        event.setEventId(generatedId);
                        return generatedId;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[EventDAO Error] insert failed: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Updates an existing event in the 'events' table.
     *
     * @param event Event entity containing updated details and valid event_id
     * @return true if updated successfully, false otherwise
     */
    public boolean update(Event event) {
        if (event == null || event.getEventId() <= 0) return false;
        String sql = "UPDATE events SET club_id = ?, event_name = ?, description = ?, category = ?, " +
                "event_date = ?, event_time = ?, venue = ?, max_participants = ?, registration_deadline = ? " +
                "WHERE event_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return false;

            stmt.setInt(1, event.getClubId());
            stmt.setString(2, event.getEventName());
            stmt.setString(3, event.getDescription());
            stmt.setString(4, event.getCategory());
            stmt.setDate(5, event.getEventDate() != null ? Date.valueOf(event.getEventDate()) : null);
            stmt.setTime(6, event.getEventTime() != null ? Time.valueOf(event.getEventTime()) : null);
            stmt.setString(7, event.getVenue());
            stmt.setInt(8, event.getMaxParticipants());
            stmt.setDate(9, event.getRegistrationDeadline() != null ? Date.valueOf(event.getRegistrationDeadline()) : null);
            stmt.setInt(10, event.getEventId());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[EventDAO Error] update failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Summary of dependent records for a specific event.
     */
    public static class DependentSummary {
        private final int registrationCount;
        private final int attendanceCount;
        private final int feedbackCount;
        private final int certificateCount;

        public DependentSummary(int registrationCount, int attendanceCount, int feedbackCount, int certificateCount) {
            this.registrationCount = registrationCount;
            this.attendanceCount = attendanceCount;
            this.feedbackCount = feedbackCount;
            this.certificateCount = certificateCount;
        }

        public static DependentSummary empty() {
            return new DependentSummary(0, 0, 0, 0);
        }

        public int getRegistrationCount() {
            return registrationCount;
        }

        public int getAttendanceCount() {
            return attendanceCount;
        }

        public int getFeedbackCount() {
            return feedbackCount;
        }

        public int getCertificateCount() {
            return certificateCount;
        }

        public int getTotalCount() {
            return registrationCount + attendanceCount + feedbackCount + certificateCount;
        }

        public boolean hasDependencies() {
            return getTotalCount() > 0;
        }

        public String toDescription() {
            List<String> parts = new ArrayList<>();
            if (registrationCount > 0) parts.add(registrationCount + " student registration(s)");
            if (attendanceCount > 0) parts.add(attendanceCount + " attendance record(s)");
            if (feedbackCount > 0) parts.add(feedbackCount + " feedback review(s)");
            if (certificateCount > 0) parts.add(certificateCount + " certificate(s)");
            if (parts.isEmpty()) return "0 dependent records";
            return String.join(", ", parts);
        }
    }

    /**
     * Outcome of an event deletion attempt.
     */
    public static class DeletionOutcome {
        public enum Status {
            SUCCESS,
            HAS_DEPENDENT_RECORDS,
            FOREIGN_KEY_VIOLATION,
            NOT_FOUND,
            DATABASE_ERROR
        }

        private final Status status;
        private final String message;
        private final DependentSummary dependentSummary;

        public DeletionOutcome(Status status, String message, DependentSummary dependentSummary) {
            this.status = status;
            this.message = message;
            this.dependentSummary = dependentSummary;
        }

        public static DeletionOutcome success() {
            return new DeletionOutcome(Status.SUCCESS, "Event successfully deleted.", null);
        }

        public static DeletionOutcome hasDependentRecords(DependentSummary summary) {
            return new DeletionOutcome(
                    Status.HAS_DEPENDENT_RECORDS,
                    "Cannot delete event: " + (summary != null ? summary.toDescription() : "dependent records")
                            + " exist. Deleting events with existing participant records is prohibited to preserve data integrity.",
                    summary
            );
        }

        public static DeletionOutcome foreignKeyViolation(String detail) {
            return new DeletionOutcome(
                    Status.FOREIGN_KEY_VIOLATION,
                    "Cannot delete event: A related record prevents deletion (foreign-key constraint violation).",
                    null
            );
        }

        public static DeletionOutcome notFound() {
            return new DeletionOutcome(Status.NOT_FOUND, "Event not found or already deleted.", null);
        }

        public static DeletionOutcome databaseError(String message) {
            return new DeletionOutcome(Status.DATABASE_ERROR, message, null);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public boolean hasDependencies() {
            return status == Status.HAS_DEPENDENT_RECORDS;
        }

        public boolean isForeignKeyViolation() {
            return status == Status.FOREIGN_KEY_VIOLATION;
        }

        public boolean isNotFound() {
            return status == Status.NOT_FOUND;
        }

        public Status getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public DependentSummary getDependentSummary() {
            return dependentSummary;
        }
    }

    /**
     * Checks whether related records (registrations, attendance, feedback, certificates)
     * exist for an event that prevent its safe deletion.
     *
     * @param eventId ID of event to inspect
     * @return DependentSummary containing counts of all related records
     */
    public DependentSummary getDependentSummary(int eventId) {
        if (eventId <= 0) return DependentSummary.empty();

        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM registrations WHERE event_id = ?) AS reg_count, " +
                "(SELECT COUNT(*) FROM attendance WHERE event_id = ?) AS att_count, " +
                "(SELECT COUNT(*) FROM feedback WHERE event_id = ?) AS fb_count, " +
                "(SELECT COUNT(*) FROM certificates WHERE event_id = ?) AS cert_count";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return DependentSummary.empty();

            stmt.setInt(1, eventId);
            stmt.setInt(2, eventId);
            stmt.setInt(3, eventId);
            stmt.setInt(4, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new DependentSummary(
                            rs.getInt("reg_count"),
                            rs.getInt("att_count"),
                            rs.getInt("fb_count"),
                            rs.getInt("cert_count")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[EventDAO Error] getDependentSummary failed: " + e.getMessage());
        }
        return DependentSummary.empty();
    }

    /**
     * Checks if an event has any dependent records preventing deletion.
     */
    public boolean hasDependentRecords(int eventId) {
        return getDependentSummary(eventId).hasDependencies();
    }

    /**
     * Safely deletes an event by its ID if and only if NO dependent records exist.
     *
     * Strict Rules:
     * 1. NEVER deletes registrations, attendance, feedback, or certificates.
     * 2. Checks if related records exist; if so, refuses deletion immediately.
     * 3. Executes DELETE only against the 'events' table using a parameterized SQL statement.
     * 4. Uses a transaction with commit on success and rollback on failure.
     * 5. Handles foreign-key constraint violations gracefully without crashing.
     *
     * @param eventId ID of event to delete
     * @return DeletionOutcome detailing the result
     */
    public DeletionOutcome deleteEvent(int eventId) {
        if (eventId <= 0) {
            return DeletionOutcome.notFound();
        }

        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            return DeletionOutcome.databaseError("Database connection is unavailable.");
        }

        // 1. Explicit check: verify whether dependent records prevent safe deletion
        DependentSummary summary = getDependentSummary(eventId);
        if (summary.hasDependencies()) {
            DBConnection.closeConnection(conn);
            System.err.println("[EventDAO] Deletion refused for event_id=" + eventId + ": " + summary.toDescription());
            return DeletionOutcome.hasDependentRecords(summary);
        }

        // 2. No dependent records detected; proceed to delete ONLY the requested event in a transaction
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            int affected;
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM events WHERE event_id = ?")) {
                stmt.setInt(1, eventId);
                affected = stmt.executeUpdate();
            }

            if (affected > 0) {
                conn.commit();
                return DeletionOutcome.success();
            } else {
                conn.rollback();
                return DeletionOutcome.notFound();
            }

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("[EventDAO Error] rollback failed: " + rollbackEx.getMessage());
            }

            // 3. Gracefully handle foreign-key constraint violations (e.g. MySQL 1451 / SQLState 23000)
            boolean isFkViolation = "23000".equals(e.getSQLState()) || e.getErrorCode() == 1451;
            if (isFkViolation) {
                System.err.println("[EventDAO] Foreign key constraint prevented event deletion: " + e.getMessage());
                return DeletionOutcome.foreignKeyViolation(e.getMessage());
            }

            System.err.println("[EventDAO Error] delete failed: " + e.getMessage());
            return DeletionOutcome.databaseError("Database error during event deletion: " + e.getMessage());

        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
                conn.close();
            } catch (SQLException e) {
                System.err.println("[EventDAO Error] Failed to restore connection autoCommit/close: " + e.getMessage());
            }
        }
    }

    /**
     * Backward-compatible boolean deletion method.
     * Strictly adheres to safe deletion rules: refuses deletion if dependent records exist.
     *
     * @param eventId ID of event to delete
     * @return true if deleted safely, false if refused or failed
     */
    public boolean delete(int eventId) {
        return deleteEvent(eventId).isSuccess();
    }

    /**
     * Counts the total number of events.
     */
    public int countAll() {
        String sql = "SELECT COUNT(*) FROM events";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return 0;

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[EventDAO Error] countAll failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts upcoming events where event_date is on or after CURRENT_DATE.
     */
    public int countUpcoming() {
        String sql = "SELECT COUNT(*) FROM events WHERE event_date >= CURRENT_DATE";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return 0;

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[EventDAO Error] countUpcoming failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Retrieves upcoming events ordered chronologically, capped at limit.
     */
    public List<Event> getUpcomingEvents(int limit) {
        List<Event> list = new ArrayList<>();
        String sql = "SELECT e.event_id, e.club_id, e.event_name, e.description, e.category, " +
                "e.event_date, e.event_time, e.venue, e.max_participants, e.registration_deadline, " +
                "c.club_name " +
                "FROM events e " +
                "LEFT JOIN clubs c ON e.club_id = c.club_id " +
                "WHERE e.event_date >= CURRENT_DATE ORDER BY e.event_date ASC, e.event_time ASC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return list;
            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[EventDAO Error] getUpcomingEvents failed: " + e.getMessage());
        }
        return list;
    }

    private Event mapRow(ResultSet rs) throws SQLException {
        Date evDate = rs.getDate("event_date");
        Time evTime = rs.getTime("event_time");
        Date regDeadline = rs.getDate("registration_deadline");

        Event ev = new Event(
                rs.getInt("event_id"),
                rs.getInt("club_id"),
                rs.getString("event_name"),
                rs.getString("description"),
                rs.getString("category"),
                evDate != null ? evDate.toLocalDate() : null,
                evTime != null ? evTime.toLocalTime() : null,
                rs.getString("venue"),
                rs.getInt("max_participants"),
                regDeadline != null ? regDeadline.toLocalDate() : null
        );

        try {
            String club = rs.getString("club_name");
            if (club != null) {
                ev.setClubName(club);
            }
        } catch (SQLException ignored) {
            // column club_name might not be in basic queries
        }

        return ev;
    }
}
