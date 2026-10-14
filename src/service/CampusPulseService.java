package service;

import dao.AttendanceDAO;
import dao.CampusPulseDAO;
import dao.CertificateDAO;
import dao.EventDAO;
import dao.RegistrationDAO;
import model.CampusPulseActivityDTO;
import model.CampusPulseDTO;
import model.Event;
import model.PopularEventDTO;
import model.StudentAchievementDTO;
import model.User;
import util.DBConnection;
import util.Session;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CampusPulseService — Business logic layer for Campus Pulse activity feeds
 * and lightweight derived student achievements.
 *
 * Implements Phase 15: Campus Pulse & Lightweight Gamification per AGENTS.md:
 * - Purely derived achievements from verified attendance, certificates, and feedback.
 * - Enforces student identity isolation (students only access their own achievements).
 * - Read-only and non-destructive: zero database modifications or points economy.
 * - Graceful offline fallback when MySQL is unreachable.
 */
public class CampusPulseService {

    private final CampusPulseDAO campusPulseDAO;
    private final AttendanceDAO attendanceDAO;
    private final CertificateDAO certificateDAO;
    private final RegistrationDAO registrationDAO;
    private final EventDAO eventDAO;

    public CampusPulseService() {
        this(new CampusPulseDAO(), new AttendanceDAO(), new CertificateDAO(), new RegistrationDAO(), new EventDAO());
    }

    public CampusPulseService(CampusPulseDAO campusPulseDAO, AttendanceDAO attendanceDAO,
                              CertificateDAO certificateDAO, RegistrationDAO registrationDAO,
                              EventDAO eventDAO) {
        this.campusPulseDAO = (campusPulseDAO != null) ? campusPulseDAO : new CampusPulseDAO();
        this.attendanceDAO = (attendanceDAO != null) ? attendanceDAO : new AttendanceDAO();
        this.certificateDAO = (certificateDAO != null) ? certificateDAO : new CertificateDAO();
        this.registrationDAO = (registrationDAO != null) ? registrationDAO : new RegistrationDAO();
        this.eventDAO = (eventDAO != null) ? eventDAO : new EventDAO();
    }

    /**
     * Checks if current user is authorized to view achievements for studentId.
     * Enforces strict student identity isolation.
     */
    public boolean isAuthorizedForStudent(int studentId) {
        User currentUser = Session.getCurrentUser();
        if (currentUser == null) return false;
        if ("ADMIN".equalsIgnoreCase(currentUser.getRole())) return true;
        return currentUser.getUserId() == studentId;
    }

    /**
     * Calculates derived achievement badges for a specific student based on real records.
     * Rejects unauthorized requests and returns safe empty list on offline database.
     *
     * @param studentId ID of the student
     * @return list of StudentAchievementDTO items with verified unlock status
     */
    public List<StudentAchievementDTO> getStudentAchievements(int studentId) {
        if (!isAuthorizedForStudent(studentId)) {
            System.err.println("[CampusPulseService] Unauthorized: Student " + studentId
                    + " achievements requested by unauthenticated or mismatched session.");
            return Collections.emptyList();
        }

        if (!DBConnection.testConnection()) {
            return getOfflineAchievementsTemplate();
        }

        try {
            int attendedCount = attendanceDAO.countPresentByStudentId(studentId);
            int certsCount = certificateDAO.countByStudentId(studentId);
            int feedbackCount = campusPulseDAO.getStudentFeedbackCount(studentId);
            int distinctClubs = campusPulseDAO.getStudentDistinctClubsAttended(studentId);

            return calculateBadges(attendedCount, certsCount, feedbackCount, distinctClubs);
        } catch (Exception e) {
            System.err.println("[CampusPulseService Error] Failed to calculate student achievements: " + e.getMessage());
            return getOfflineAchievementsTemplate();
        }
    }

    /**
     * Pure calculation logic mapping recorded counts into achievement badges.
     * Independent of database connectivity for deterministic unit testing.
     */
    public List<StudentAchievementDTO> calculateBadges(int attendedCount, int certsCount,
                                                      int feedbackCount, int distinctClubs) {
        List<StudentAchievementDTO> badges = new ArrayList<>();

        // 1. First Step: Attended >= 1 event
        badges.add(new StudentAchievementDTO(
                "FIRST_STEP",
                "Campus Pioneer",
                "Attended your first verified campus event",
                "🚀",
                attendedCount >= 1,
                attendedCount,
                1,
                "ATTENDANCE"
        ));

        // 2. Active Explorer: Attended >= 3 events
        badges.add(new StudentAchievementDTO(
                "ACTIVE_EXPLORER",
                "Active Explorer",
                "Attended 3 or more campus events",
                "🌟",
                attendedCount >= 3,
                attendedCount,
                3,
                "ATTENDANCE"
        ));

        // 3. Campus Veteran: Attended >= 5 events
        badges.add(new StudentAchievementDTO(
                "CAMPUS_VETERAN",
                "Campus Veteran",
                "Consistently engaged with 5 or more campus events",
                "🏆",
                attendedCount >= 5,
                attendedCount,
                5,
                "ATTENDANCE"
        ));

        // 4. Certified Achiever: Certificates >= 1
        badges.add(new StudentAchievementDTO(
                "CERTIFIED_ACHIEVER",
                "Certified Achiever",
                "Earned your first verified event completion certificate",
                "📜",
                certsCount >= 1,
                certsCount,
                1,
                "CERTIFICATE"
        ));

        // 5. Campus Voice: Feedback submitted >= 1
        badges.add(new StudentAchievementDTO(
                "CAMPUS_VOICE",
                "Campus Voice",
                "Shared valuable feedback for an attended event",
                "💬",
                feedbackCount >= 1,
                feedbackCount,
                1,
                "FEEDBACK"
        ));

        // 6. Club Enthusiast: Participated in events across >= 2 distinct clubs
        badges.add(new StudentAchievementDTO(
                "MULTI_CLUB",
                "Club Enthusiast",
                "Participated across 2 or more distinct campus clubs",
                "🏛️",
                distinctClubs >= 2,
                distinctClubs,
                2,
                "DIVERSITY"
        ));

        return badges;
    }

    /**
     * Gathers live campus pulse telemetry and aggregates feed items.
     *
     * @param studentId optional student ID to bundle personalized achievements
     * @return populated CampusPulseDTO
     */
    public CampusPulseDTO getCampusPulseData(int studentId) {
        if (!DBConnection.testConnection()) {
            CampusPulseDTO offline = CampusPulseDTO.offline();
            offline.setStudentAchievements(getOfflineAchievementsTemplate());
            return offline;
        }

        try {
            CampusPulseDTO dto = new CampusPulseDTO();
            dto.setDbOnline(true);

            // 1. Campus summary metrics
            dto.setTotalCampusRegistrations(registrationDAO.countAll());
            dto.setTotalCampusVerifiedAttendees(campusPulseDAO.getCampusTotalVerifiedAttendees());
            dto.setTotalEventsHosted(eventDAO.countAll());

            // 2. Popular & Recently Completed Events
            List<PopularEventDTO> popular = campusPulseDAO.getPopularEvents(3);
            List<Event> completed = campusPulseDAO.getRecentlyCompletedEvents(3);

            dto.setPopularEvents(popular);
            dto.setRecentlyCompletedEvents(completed);

            // 3. Synthesize Pulse Activity Feed
            List<CampusPulseActivityDTO> feed = new ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");

            // Milestone item
            if (dto.getTotalCampusVerifiedAttendees() > 0) {
                feed.add(new CampusPulseActivityDTO(
                        CampusPulseActivityDTO.ActivityType.CAMPUS_MILESTONE,
                        "Campus Attendance Milestone Reached",
                        dto.getTotalCampusVerifiedAttendees() + " verified participants across university societies",
                        "Milestone",
                        "⚡"
                ));
            }

            // Popular events items
            for (PopularEventDTO pop : popular) {
                if (pop.getRegistrationCount() > 0) {
                    feed.add(new CampusPulseActivityDTO(
                            CampusPulseActivityDTO.ActivityType.UPCOMING_EVENT,
                            pop.getEventName() + " is trending",
                            pop.getClubName() + " • " + pop.getRegistrationCount() + " students registered",
                            (pop.getEventDate() != null ? pop.getEventDate().format(formatter) : "Upcoming"),
                            "🔥"
                    ));
                }
            }

            // Recently completed items
            for (Event ev : completed) {
                feed.add(new CampusPulseActivityDTO(
                        CampusPulseActivityDTO.ActivityType.RECENTLY_COMPLETED,
                        ev.getEventName() + " concluded successfully",
                        ev.getClubName() + " (" + ev.getCategory() + ")",
                        (ev.getEventDate() != null ? ev.getEventDate().format(formatter) : "Concluded"),
                        "✓"
                ));
            }

            dto.setPulseFeed(feed);

            // 4. Attach personalized achievements if requested
            if (studentId > 0 && isAuthorizedForStudent(studentId)) {
                dto.setStudentAchievements(getStudentAchievements(studentId));
            }

            return dto;

        } catch (Exception e) {
            System.err.println("[CampusPulseService Error] Failed to gather campus pulse data: " + e.getMessage());
            CampusPulseDTO fallback = CampusPulseDTO.offline();
            fallback.setStudentAchievements(getOfflineAchievementsTemplate());
            return fallback;
        }
    }

    private List<StudentAchievementDTO> getOfflineAchievementsTemplate() {
        return calculateBadges(0, 0, 0, 0);
    }
}
