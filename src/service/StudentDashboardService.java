package service;

import dao.AttendanceDAO;
import dao.CertificateDAO;
import dao.ClubDAO;
import dao.EventDAO;
import dao.RegistrationDAO;
import model.Club;
import model.Event;
import model.StudentDashboardDTO;
import util.DBConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * StudentDashboardService — Lightweight service layer providing aggregated metrics and
 * upcoming event feeds for the Student Dashboard.
 *
 * Keeps controllers clean of direct database access per AGENTS.md.
 */
public class StudentDashboardService {

    private final EventDAO eventDAO;
    private final RegistrationDAO registrationDAO;
    private final AttendanceDAO attendanceDAO;
    private final CertificateDAO certificateDAO;
    private final ClubDAO clubDAO;
    private final CampusPulseService campusPulseService;

    public StudentDashboardService() {
        this(new EventDAO(), new RegistrationDAO(), new AttendanceDAO(), new CertificateDAO(), new ClubDAO(), new CampusPulseService());
    }

    public StudentDashboardService(EventDAO eventDAO, RegistrationDAO registrationDAO,
                                   AttendanceDAO attendanceDAO, CertificateDAO certificateDAO,
                                   ClubDAO clubDAO, CampusPulseService campusPulseService) {
        this.eventDAO = eventDAO;
        this.registrationDAO = registrationDAO;
        this.attendanceDAO = attendanceDAO;
        this.certificateDAO = certificateDAO;
        this.clubDAO = clubDAO;
        this.campusPulseService = (campusPulseService != null) ? campusPulseService : new CampusPulseService();
    }

    /**
     * Loads student dashboard metrics, upcoming event listings, campus pulse, and achievements.
     * Gracefully falls back to offline state if MySQL connection is unavailable.
     *
     * @param studentId the user ID of the authenticated student
     * @return StudentDashboardDTO populated with live data or offline indicators
     */
    public StudentDashboardDTO getDashboardData(int studentId) {
        if (!DBConnection.testConnection()) {
            StudentDashboardDTO offline = StudentDashboardDTO.offline();
            offline.setAchievements(campusPulseService.calculateBadges(0, 0, 0, 0));
            return offline;
        }

        try {
            StudentDashboardDTO dto = new StudentDashboardDTO();
            dto.setDbOnline(true);

            // 1. Core KPIs
            dto.setTotalAvailableEvents(eventDAO.countAll());
            dto.setRegisteredCount(registrationDAO.countByStudentId(studentId));
            dto.setUpcomingCount(eventDAO.countUpcoming());
            dto.setAttendedCount(attendanceDAO.countPresentByStudentId(studentId));
            dto.setCertificatesCount(certificateDAO.countByStudentId(studentId));

            // 2. Upcoming events (top 3)
            List<Event> upcomingEventsRaw = eventDAO.getUpcomingEvents(3);
            List<StudentDashboardDTO.EventItemDTO> eventItems = new ArrayList<>();

            for (Event ev : upcomingEventsRaw) {
                Club club = clubDAO.findById(ev.getClubId());
                String clubName = (club != null) ? club.getClubName() : "Campus Society";
                String regStatus = registrationDAO.getRegistrationStatus(ev.getEventId(), studentId);

                eventItems.add(new StudentDashboardDTO.EventItemDTO(ev, clubName, regStatus));
            }
            dto.setUpcomingEvents(eventItems);

            // 3. Campus Pulse & Achievements (Phase 15)
            model.CampusPulseDTO pulseData = campusPulseService.getCampusPulseData(studentId);
            dto.setAchievements(campusPulseService.getStudentAchievements(studentId));
            dto.setPulseFeed(pulseData.getPulseFeed());
            dto.setPopularEvents(pulseData.getPopularEvents());

            return dto;

        } catch (Exception e) {
            System.err.println("[StudentDashboardService Error] Failed to aggregate metrics: " + e.getMessage());
            StudentDashboardDTO fallback = StudentDashboardDTO.offline();
            fallback.setAchievements(campusPulseService.calculateBadges(0, 0, 0, 0));
            return fallback;
        }
    }
}

