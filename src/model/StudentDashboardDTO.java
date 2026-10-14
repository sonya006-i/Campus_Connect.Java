package model;

import java.util.ArrayList;
import java.util.List;

/**
 * StudentDashboardDTO — Data transfer bundle containing aggregated student metrics and upcoming events.
 */
public class StudentDashboardDTO {

    private boolean dbOnline;
    private int totalAvailableEvents;
    private int registeredCount;
    private int upcomingCount;
    private int attendedCount;
    private int certificatesCount;
    private List<EventItemDTO> upcomingEvents = new ArrayList<>();
    private List<StudentAchievementDTO> achievements = new ArrayList<>();
    private List<CampusPulseActivityDTO> pulseFeed = new ArrayList<>();
    private List<PopularEventDTO> popularEvents = new ArrayList<>();

    public StudentDashboardDTO() {
    }

    public static StudentDashboardDTO offline() {
        StudentDashboardDTO dto = new StudentDashboardDTO();
        dto.setDbOnline(false);
        return dto;
    }

    public boolean isDbOnline() {
        return dbOnline;
    }

    public void setDbOnline(boolean dbOnline) {
        this.dbOnline = dbOnline;
    }

    public int getTotalAvailableEvents() {
        return totalAvailableEvents;
    }

    public void setTotalAvailableEvents(int totalAvailableEvents) {
        this.totalAvailableEvents = totalAvailableEvents;
    }

    public int getRegisteredCount() {
        return registeredCount;
    }

    public void setRegisteredCount(int registeredCount) {
        this.registeredCount = registeredCount;
    }

    public int getUpcomingCount() {
        return upcomingCount;
    }

    public void setUpcomingCount(int upcomingCount) {
        this.upcomingCount = upcomingCount;
    }

    public int getAttendedCount() {
        return attendedCount;
    }

    public void setAttendedCount(int attendedCount) {
        this.attendedCount = attendedCount;
    }

    public int getCertificatesCount() {
        return certificatesCount;
    }

    public void setCertificatesCount(int certificatesCount) {
        this.certificatesCount = certificatesCount;
    }

    public List<EventItemDTO> getUpcomingEvents() {
        return upcomingEvents;
    }

    public void setUpcomingEvents(List<EventItemDTO> upcomingEvents) {
        this.upcomingEvents = (upcomingEvents != null) ? upcomingEvents : new ArrayList<>();
    }

    public List<StudentAchievementDTO> getAchievements() {
        return achievements;
    }

    public void setAchievements(List<StudentAchievementDTO> achievements) {
        this.achievements = (achievements != null) ? achievements : new ArrayList<>();
    }

    public List<CampusPulseActivityDTO> getPulseFeed() {
        return pulseFeed;
    }

    public void setPulseFeed(List<CampusPulseActivityDTO> pulseFeed) {
        this.pulseFeed = (pulseFeed != null) ? pulseFeed : new ArrayList<>();
    }

    public List<PopularEventDTO> getPopularEvents() {
        return popularEvents;
    }

    public void setPopularEvents(List<PopularEventDTO> popularEvents) {
        this.popularEvents = (popularEvents != null) ? popularEvents : new ArrayList<>();
    }

    /**
     * Nested view model for each event card shown on the dashboard.
     */
    public static class EventItemDTO {
        private final Event event;
        private final String clubName;
        private final String registrationStatus; // "APPROVED", "PENDING", or null

        public EventItemDTO(Event event, String clubName, String registrationStatus) {
            this.event = event;
            this.clubName = clubName;
            this.registrationStatus = registrationStatus;
        }

        public Event getEvent() {
            return event;
        }

        public String getClubName() {
            return clubName;
        }

        public String getRegistrationStatus() {
            return registrationStatus;
        }

        public boolean isRegistered() {
            return registrationStatus != null;
        }
    }
}
