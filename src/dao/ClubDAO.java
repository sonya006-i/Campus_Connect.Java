package dao;

import model.Club;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ClubDAO — Data Access Object for 'clubs' table.
 */
public class ClubDAO {

    /**
     * Finds a club by its ID.
     */
    public Club findById(int clubId) {
        String sql = "SELECT club_id, club_name, description FROM clubs WHERE club_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return null;
            stmt.setInt(1, clubId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ClubDAO Error] findById failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves all clubs in the database.
     */
    public List<Club> getAll() {
        List<Club> list = new ArrayList<>();
        String sql = "SELECT club_id, club_name, description FROM clubs ORDER BY club_name ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return list;

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ClubDAO Error] getAll failed: " + e.getMessage());
        }
        return list;
    }

    private Club mapRow(ResultSet rs) throws SQLException {
        return new Club(
                rs.getInt("club_id"),
                rs.getString("club_name"),
                rs.getString("description")
        );
    }
}
