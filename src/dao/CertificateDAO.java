package dao;

import model.AdminCertificateParticipantDTO;
import model.Certificate;
import model.StudentCertificateDTO;
import util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * CertificateDAO — Data Access Object for 'certificates' table.
 *
 * Implements Phase 12: Certificate Management per AGENTS.md:
 * - Retrieve issued certificates for a student joined with event and club metadata
 * - Retrieve event participant certificate rosters joined with attendance and registrations
 * - Atomic certificate issuance with unique constraint handling
 * - Event-level eligible and issued participant counters
 */
public class CertificateDAO {

    /**
     * Retrieves all certificates issued to a student.
     */
    public List<Certificate> findByStudentId(int studentId) {
        List<Certificate> list = new ArrayList<>();
        String sql = "SELECT certificate_id, event_id, student_id, certificate_path, generated_date " +
                "FROM certificates WHERE student_id = ? ORDER BY generated_date DESC";
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
            System.err.println("[CertificateDAO Error] findByStudentId failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Finds a certificate for a student for a specific event.
     */
    public Certificate findByEventAndStudent(int eventId, int studentId) {
        String sql = "SELECT certificate_id, event_id, student_id, certificate_path, generated_date " +
                "FROM certificates WHERE event_id = ? AND student_id = ?";
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
            System.err.println("[CertificateDAO Error] findByEventAndStudent failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Checks if a certificate has already been issued to a student for an event.
     */
    public boolean isCertificateIssued(int eventId, int studentId) {
        return findByEventAndStudent(eventId, studentId) != null;
    }

    /**
     * Counts certificates issued to a specific student.
     */
    public int countByStudentId(int studentId) {
        String sql = "SELECT COUNT(*) FROM certificates WHERE student_id = ?";
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
            System.err.println("[CertificateDAO Error] countByStudentId failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts total certificates issued for a specific event.
     */
    public int countIssuedForEvent(int eventId) {
        String sql = "SELECT COUNT(*) FROM certificates WHERE event_id = ?";
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
            System.err.println("[CertificateDAO Error] countIssuedForEvent failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts eligible participants for an event (APPROVED registration and attendance present = TRUE).
     */
    public int countEligibleForEvent(int eventId) {
        String sql = "SELECT COUNT(*) " +
                "FROM registrations r " +
                "JOIN attendance a ON r.event_id = a.event_id AND r.student_id = a.student_id " +
                "WHERE r.event_id = ? AND r.status = 'APPROVED' AND a.present = TRUE";
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
            System.err.println("[CertificateDAO Error] countEligibleForEvent failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Retrieves all certificates issued to a student with event, club, and verification details.
     */
    public List<StudentCertificateDTO> getCertificatesForStudent(int studentId) {
        List<StudentCertificateDTO> list = new ArrayList<>();
        String sql = "SELECT " +
                "c.certificate_id, c.event_id, c.student_id, c.certificate_path, c.generated_date, " +
                "e.event_name, e.category, e.event_date, e.venue, " +
                "cl.club_name, " +
                "u.name AS student_name, u.email AS student_email " +
                "FROM certificates c " +
                "JOIN events e ON c.event_id = e.event_id " +
                "LEFT JOIN clubs cl ON e.club_id = cl.club_id " +
                "JOIN users u ON c.student_id = u.user_id " +
                "WHERE c.student_id = ? " +
                "ORDER BY c.generated_date DESC, c.certificate_id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return list;
            stmt.setInt(1, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date genDate = rs.getDate("generated_date");
                    Date evDate = rs.getDate("event_date");

                    list.add(new StudentCertificateDTO(
                            rs.getInt("certificate_id"),
                            rs.getInt("event_id"),
                            rs.getInt("student_id"),
                            rs.getString("certificate_path"),
                            genDate != null ? genDate.toLocalDate() : null,
                            rs.getString("event_name"),
                            rs.getString("category"),
                            evDate != null ? evDate.toLocalDate() : null,
                            rs.getString("venue"),
                            rs.getString("club_name"),
                            rs.getString("student_name"),
                            rs.getString("student_email")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[CertificateDAO Error] getCertificatesForStudent failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves a single certificate by its ID with full metadata for high-fidelity preview.
     */
    public StudentCertificateDTO getCertificateDetailsById(int certificateId) {
        String sql = "SELECT " +
                "c.certificate_id, c.event_id, c.student_id, c.certificate_path, c.generated_date, " +
                "e.event_name, e.category, e.event_date, e.venue, " +
                "cl.club_name, " +
                "u.name AS student_name, u.email AS student_email " +
                "FROM certificates c " +
                "JOIN events e ON c.event_id = e.event_id " +
                "LEFT JOIN clubs cl ON e.club_id = cl.club_id " +
                "JOIN users u ON c.student_id = u.user_id " +
                "WHERE c.certificate_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return null;
            stmt.setInt(1, certificateId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Date genDate = rs.getDate("generated_date");
                    Date evDate = rs.getDate("event_date");

                    return new StudentCertificateDTO(
                            rs.getInt("certificate_id"),
                            rs.getInt("event_id"),
                            rs.getInt("student_id"),
                            rs.getString("certificate_path"),
                            genDate != null ? genDate.toLocalDate() : null,
                            rs.getString("event_name"),
                            rs.getString("category"),
                            evDate != null ? evDate.toLocalDate() : null,
                            rs.getString("venue"),
                            rs.getString("club_name"),
                            rs.getString("student_name"),
                            rs.getString("student_email")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[CertificateDAO Error] getCertificateDetailsById failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves all event participants with registration, attendance, and certificate issuance status.
     * Orders unissued eligible participants first, followed by already issued, then ineligible participants.
     */
    public List<AdminCertificateParticipantDTO> getParticipantCertificateRoster(int eventId) {
        List<AdminCertificateParticipantDTO> list = new ArrayList<>();
        String sql = "SELECT " +
                "r.registration_id, r.event_id, r.student_id, r.status AS registration_status, " +
                "u.name AS student_name, u.email AS student_email, " +
                "e.event_name, " +
                "a.attendance_id, a.present, " +
                "c.certificate_id, c.certificate_path, c.generated_date " +
                "FROM registrations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN events e ON r.event_id = e.event_id " +
                "LEFT JOIN attendance a ON r.event_id = a.event_id AND r.student_id = a.student_id " +
                "LEFT JOIN certificates c ON r.event_id = c.event_id AND r.student_id = c.student_id " +
                "WHERE r.event_id = ? " +
                "ORDER BY " +
                "  CASE WHEN c.certificate_id IS NOT NULL THEN 1 " +
                "       WHEN r.status = 'APPROVED' AND a.present = TRUE THEN 0 " +
                "       ELSE 2 END, " +
                "  u.name ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return list;
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int sId = rs.getInt("student_id");
                    String sName = rs.getString("student_name");
                    String sEmail = rs.getString("student_email");
                    int evId = rs.getInt("event_id");
                    String evName = rs.getString("event_name");
                    int regId = rs.getInt("registration_id");
                    String regStatus = rs.getString("registration_status");

                    int attId = rs.getInt("attendance_id");
                    boolean attendanceRecorded = !rs.wasNull();
                    boolean attended = attendanceRecorded && rs.getBoolean("present");

                    int certIdVal = rs.getInt("certificate_id");
                    boolean certIssued = !rs.wasNull();
                    Integer certId = certIssued ? certIdVal : null;
                    String certPath = rs.getString("certificate_path");
                    Date genDate = rs.getDate("generated_date");
                    LocalDate genLocalDate = genDate != null ? genDate.toLocalDate() : null;

                    list.add(new AdminCertificateParticipantDTO(
                            sId, sName, sEmail, evId, evName, regId, regStatus,
                            attendanceRecorded, attended, certIssued, certId, certPath, genLocalDate
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[CertificateDAO Error] getParticipantCertificateRoster failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Checks if a participant is eligible for certificate issuance:
     * - Registration is APPROVED
     * - Attendance is present = TRUE
     * - Certificate has NOT yet been issued
     */
    public boolean isEligibleForCertificate(int eventId, int studentId) {
        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM registrations WHERE event_id = ? AND student_id = ? AND status = 'APPROVED') AS is_approved, " +
                "(SELECT COUNT(*) FROM attendance WHERE event_id = ? AND student_id = ? AND present = TRUE) AS is_present, " +
                "(SELECT COUNT(*) FROM certificates WHERE event_id = ? AND student_id = ?) AS already_issued";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {

            if (stmt == null) return false;
            stmt.setInt(1, eventId);
            stmt.setInt(2, studentId);
            stmt.setInt(3, eventId);
            stmt.setInt(4, studentId);
            stmt.setInt(5, eventId);
            stmt.setInt(6, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int approved = rs.getInt("is_approved");
                    int present = rs.getInt("is_present");
                    int issued = rs.getInt("already_issued");

                    return (approved > 0) && (present > 0) && (issued == 0);
                }
            }
        } catch (SQLException e) {
            System.err.println("[CertificateDAO Error] isEligibleForCertificate failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Safely inserts a certificate record and returns the generated certificate_id.
     * Respects uq_cert_event_student unique constraint.
     */
    public int issueCertificate(int eventId, int studentId, String certificatePath, LocalDate issueDate) {
        String sql = "INSERT INTO certificates (event_id, student_id, certificate_path, generated_date) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : null) {

            if (stmt == null) return -1;
            stmt.setInt(1, eventId);
            stmt.setInt(2, studentId);
            stmt.setString(3, certificatePath);
            stmt.setDate(4, Date.valueOf(issueDate != null ? issueDate : LocalDate.now()));

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            // Handle duplicate key error gracefully
            if (e.getErrorCode() == 1062 || (e.getSQLState() != null && e.getSQLState().startsWith("23"))) {
                System.err.println("[CertificateDAO] Certificate already exists for event " + eventId + " and student " + studentId);
                return -2; // Signal already exists
            }
            System.err.println("[CertificateDAO Error] issueCertificate failed: " + e.getMessage());
        }
        return -1;
    }

    private Certificate mapRow(ResultSet rs) throws SQLException {
        Date genDate = rs.getDate("generated_date");
        return new Certificate(
                rs.getInt("certificate_id"),
                rs.getInt("event_id"),
                rs.getInt("student_id"),
                rs.getString("certificate_path"),
                genDate != null ? genDate.toLocalDate() : null
        );
    }
}
