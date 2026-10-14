package dao;

import model.User;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * UserDAO — Data Access Object for 'users' table.
 */
public class UserDAO {

    /**
     * Retrieves a user by their unique primary key ID.
     */
    public User findById(int userId) {
        String sql = "SELECT user_id, name, email, password, role FROM users WHERE user_id = ?";
        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            return null;
        }

        try (conn;
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO Error] findById failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves a user by their unique email address for authentication.
     * Performs case-insensitive matching to ensure reliable login across client casing differences.
     * Throws SQLException when database connectivity or query execution fails.
     */
    public User findByEmail(String email) throws SQLException {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        String sql = "SELECT user_id, name, email, password, role FROM users WHERE LOWER(email) = LOWER(?)";
        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            throw new SQLException("Unable to establish database connection.");
        }

        try (conn;
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all users currently registered in the system.
     */
    public List<User> getAll() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT user_id, name, email, password, role FROM users ORDER BY user_id ASC";
        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            return list;
        }

        try (conn;
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO Error] getAll failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Updates the password hash for a specific user using parameterized PreparedStatement.
     * Supports controlled upgrade of legacy plaintext credentials upon authenticated login.
     *
     * @param userId primary key of the user
     * @param newHashedPassword self-describing encoded PBKDF2 hash
     * @return true if updated successfully, false otherwise
     */
    public boolean updatePassword(int userId, String newHashedPassword) {
        if (userId <= 0 || newHashedPassword == null || newHashedPassword.trim().isEmpty()) {
            return false;
        }

        String sql = "UPDATE users SET password = ? WHERE user_id = ?";
        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            return false;
        }

        try (conn;
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newHashedPassword);
            stmt.setInt(2, userId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO Error] updatePassword failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Atomically and conditionally updates the password hash only if the current stored
     * password in the database still matches expectedOldPassword.
     * Prevents race conditions and overwriting concurrently changed passwords during legacy migration.
     *
     * @param userId primary key of the user
     * @param expectedOldPassword the password value expected to currently exist in the database
     * @param newHashedPassword self-describing encoded PBKDF2 hash
     * @return true if updated successfully (1 row affected), false otherwise
     */
    public boolean updatePasswordIfMatches(int userId, String expectedOldPassword, String newHashedPassword) {
        if (userId <= 0 || expectedOldPassword == null || newHashedPassword == null || newHashedPassword.trim().isEmpty()) {
            return false;
        }

        String sql = "UPDATE users SET password = ? WHERE user_id = ? AND password = ?";
        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            return false;
        }

        try (conn;
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newHashedPassword);
            stmt.setInt(2, userId);
            stmt.setString(3, expectedOldPassword);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO Error] updatePasswordIfMatches failed: " + e.getMessage());
            return false;
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("user_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("password"),
                rs.getString("role")
        );
    }
}
