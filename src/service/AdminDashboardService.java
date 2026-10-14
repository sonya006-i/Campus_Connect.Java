package service;

import dao.ClubDAO;
import dao.EventDAO;
import dao.RegistrationDAO;
import model.AdminDashboardDTO;
import model.Club;
import model.Event;
import util.DBConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * AdminDashboardService — Coordinates campus-wide metrics, event overviews,
 * and registration distributions for the Admin Dashboard.
 *
 * Implements Phase 8: Admin Dashboard per AGENTS.md.
 */
public class AdminDashboardService {

    private final EventDAO eventDAO;
    private final RegistrationDAO registrationDAO;
    private final ClubDAO clubDAO;

    public AdminDashboardService() {
        this.eventDAO = new EventDAO();
        this.registrationDAO = new RegistrationDAO();
        this.clubDAO = new ClubDAO();
    }

    public AdminDashboardService(EventDAO eventDAO, RegistrationDAO registrationDAO, ClubDAO clubDAO) {
        this.eventDAO = eventDAO;
        this.registrationDAO = registrationDAO;
        this.clubDAO = clubDAO;
    }

    /**
     * Aggregates live campus-wide metrics and event rosters for the Admin Dashboard.
     * Falls back safely to offline indicators if the database connection is unavailable.
     */
    public AdminDashboardDTO getDashboardData() {
        if (!DBConnection.testConnection()) {
            return AdminDashboardDTO.offline();
        }

        try {
            AdminDashboardDTO dto = new AdminDashboardDTO();
            dto.setDbOnline(true);

            // 1. Campus-wide KPI metrics
            dto.setTotalEvents(eventDAO.countAll());
            dto.setUpcomingEvents(eventDAO.countUpcoming());
            dto.setTotalRegistrations(registrationDAO.countAll());
            dto.setPendingRegistrations(registrationDAO.countByStatus("PENDING"));
            dto.setApprovedRegistrations(registrationDAO.countByStatus("APPROVED"));
            dto.setRejectedRegistrations(registrationDAO.countByStatus("REJECTED"));

            // 2. Event Overview (top upcoming or recent events)
            List<Event> events = eventDAO.getUpcomingEvents(5);
            if (events == null || events.isEmpty()) {
                events = eventDAO.getAll();
                if (events != null && events.size() > 5) {
                    events = events.subList(0, 5);
                }
            }

            List<AdminDashboardDTO.AdminEventItemDTO> items = new ArrayList<>();
            if (events != null) {
                for (Event ev : events) {
                    String clubName = ev.getClubName();
                    if (clubName == null || clubName.isEmpty()) {
                        Club club = clubDAO.findById(ev.getClubId());
                        clubName = (club != null) ? club.getClubName() : "Campus Society";
                    }

                    int regCount = registrationDAO.countTotalForEvent(ev.getEventId());
                    items.add(new AdminDashboardDTO.AdminEventItemDTO(ev, clubName, regCount));
                }
            }

            dto.setRecentEvents(items);
            return dto;

        } catch (Exception e) {
            System.err.println("[AdminDashboardService Error] Failed to aggregate admin metrics: " + e.getMessage());
            return AdminDashboardDTO.offline();
        }
    }
}
