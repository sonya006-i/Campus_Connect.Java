package model;

import java.util.ArrayList;
import java.util.List;

/**
 * AdminDashboardDTO — Data bundle holding campus-wide aggregated metrics,
 * registration breakdown, and event overview for Phase 8: Admin Dashboard.
 */
public class AdminDashboardDTO {

    private boolean dbOnline;
    private int totalEvents;
    private int upcomingEvents;
    private int totalRegistrations;
    private int pendingRegistrations;
    private int approvedRegistrations;
    private int rejectedRegistrations;
    private List<AdminEventItemDTO> recentEvents = new ArrayList<>();

    public AdminDashboardDTO() {
    }

    public static AdminDashboardDTO offline() {
        AdminDashboardDTO dto = new AdminDashboardDTO();
        dto.setDbOnline(false);
        return dto;
    }

    public boolean isDbOnline() {
        return dbOnline;
    }

    public void setDbOnline(boolean dbOnline) {
        this.dbOnline = dbOnline;
    }

    public int getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(int totalEvents) {
        this.totalEvents = totalEvents;
    }

    public int getUpcomingEvents() {
        return upcomingEvents;
    }

    public void setUpcomingEvents(int upcomingEvents) {
        this.upcomingEvents = upcomingEvents;
    }

    public int getTotalRegistrations() {
        return totalRegistrations;
    }

    public void setTotalRegistrations(int totalRegistrations) {
        this.totalRegistrations = totalRegistrations;
    }

    public int getPendingRegistrations() {
        return pendingRegistrations;
    }

    public void setPendingRegistrations(int pendingRegistrations) {
        this.pendingRegistrations = pendingRegistrations;
    }

    public int getApprovedRegistrations() {
        return approvedRegistrations;
    }

    public void setApprovedRegistrations(int approvedRegistrations) {
        this.approvedRegistrations = approvedRegistrations;
    }

    public int getRejectedRegistrations() {
        return rejectedRegistrations;
    }

    public void setRejectedRegistrations(int rejectedRegistrations) {
        this.rejectedRegistrations = rejectedRegistrations;
    }

    public List<AdminEventItemDTO> getRecentEvents() {
        return recentEvents;
    }

    public void setRecentEvents(List<AdminEventItemDTO> recentEvents) {
        this.recentEvents = recentEvents;
    }

    /**
     * View model for each event shown in the Admin Event Overview.
     */
    public static class AdminEventItemDTO {
        private final Event event;
        private final String clubName;
        private final int registrationCount;

        public AdminEventItemDTO(Event event, String clubName, int registrationCount) {
            this.event = event;
            this.clubName = clubName;
            this.registrationCount = registrationCount;
        }

        public Event getEvent() {
            return event;
        }

        public String getClubName() {
            return clubName;
        }

        public int getRegistrationCount() {
            return registrationCount;
        }
    }
}
