package model;

import java.util.ArrayList;
import java.util.List;

/**
 * CampusPulseDTO — Aggregated snapshot of campus life intelligence and personal achievements
 * for Phase 15: Campus Pulse & Lightweight Gamification.
 */
public class CampusPulseDTO {

    private boolean dbOnline;
    private int totalCampusRegistrations;
    private int totalCampusVerifiedAttendees;
    private int totalEventsHosted;
    private List<PopularEventDTO> popularEvents = new ArrayList<>();
    private List<Event> recentlyCompletedEvents = new ArrayList<>();
    private List<CampusPulseActivityDTO> pulseFeed = new ArrayList<>();
    private List<StudentAchievementDTO> studentAchievements = new ArrayList<>();

    public CampusPulseDTO() {
        this.dbOnline = true;
    }

    public static CampusPulseDTO offline() {
        CampusPulseDTO dto = new CampusPulseDTO();
        dto.setDbOnline(false);
        return dto;
    }

    public boolean isDbOnline() {
        return dbOnline;
    }

    public void setDbOnline(boolean dbOnline) {
        this.dbOnline = dbOnline;
    }

    public int getTotalCampusRegistrations() {
        return totalCampusRegistrations;
    }

    public void setTotalCampusRegistrations(int totalCampusRegistrations) {
        this.totalCampusRegistrations = totalCampusRegistrations;
    }

    public int getTotalCampusVerifiedAttendees() {
        return totalCampusVerifiedAttendees;
    }

    public void setTotalCampusVerifiedAttendees(int totalCampusVerifiedAttendees) {
        this.totalCampusVerifiedAttendees = totalCampusVerifiedAttendees;
    }

    public int getTotalEventsHosted() {
        return totalEventsHosted;
    }

    public void setTotalEventsHosted(int totalEventsHosted) {
        this.totalEventsHosted = totalEventsHosted;
    }

    public List<PopularEventDTO> getPopularEvents() {
        return popularEvents;
    }

    public void setPopularEvents(List<PopularEventDTO> popularEvents) {
        this.popularEvents = (popularEvents != null) ? popularEvents : new ArrayList<>();
    }

    public List<Event> getRecentlyCompletedEvents() {
        return recentlyCompletedEvents;
    }

    public void setRecentlyCompletedEvents(List<Event> recentlyCompletedEvents) {
        this.recentlyCompletedEvents = (recentlyCompletedEvents != null) ? recentlyCompletedEvents : new ArrayList<>();
    }

    public List<CampusPulseActivityDTO> getPulseFeed() {
        return pulseFeed;
    }

    public void setPulseFeed(List<CampusPulseActivityDTO> pulseFeed) {
        this.pulseFeed = (pulseFeed != null) ? pulseFeed : new ArrayList<>();
    }

    public List<StudentAchievementDTO> getStudentAchievements() {
        return studentAchievements;
    }

    public void setStudentAchievements(List<StudentAchievementDTO> studentAchievements) {
        this.studentAchievements = (studentAchievements != null) ? studentAchievements : new ArrayList<>();
    }

    public int getUnlockedAchievementCount() {
        int count = 0;
        for (StudentAchievementDTO badge : studentAchievements) {
            if (badge.isUnlocked()) count++;
        }
        return count;
    }
}
