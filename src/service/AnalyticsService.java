package service;

import dao.AnalyticsDAO;
import model.AnalyticsOverviewDTO;
import model.CategoryDistributionDTO;
import model.ClubEngagementDTO;
import model.EventPerformanceDTO;
import model.MonthlyTrendDTO;
import model.User;
import util.DBConnection;
import util.Session;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AnalyticsService — Business logic and administrator authorization gateway for analytics.
 *
 * Implements Phase 14: Analytics per AGENTS.md:
 * - Strict role-based security: All queries require an authenticated ADMIN session.
 * - Enforces offline resilience: Database unavailability results in safe fallback objects without exceptions.
 * - Decoupled data access via AnalyticsDAO with constructor injection for unit testing.
 */
public class AnalyticsService {

    private final AnalyticsDAO analyticsDAO;

    public AnalyticsService() {
        this(new AnalyticsDAO());
    }

    public AnalyticsService(AnalyticsDAO analyticsDAO) {
        this.analyticsDAO = (analyticsDAO != null) ? analyticsDAO : new AnalyticsDAO();
    }

    /**
     * Checks if the currently authenticated user in Session has ADMIN privileges.
     */
    public boolean isAuthorized() {
        User user = Session.getCurrentUser();
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    /**
     * Retrieves campus-wide telemetry and KPI ratios.
     * Returns offline DTO if database is unreachable or user is unauthorized.
     */
    public AnalyticsOverviewDTO getOverviewMetrics() {
        if (!isAuthorized()) {
            System.err.println("[AnalyticsService] Access denied: ADMIN role required for analytics overview.");
            return AnalyticsOverviewDTO.offline();
        }

        if (!DBConnection.testConnection()) {
            return AnalyticsOverviewDTO.offline();
        }

        try {
            return analyticsDAO.getOverviewMetrics();
        } catch (Exception e) {
            System.err.println("[AnalyticsService Error] Failed to load overview metrics: " + e.getMessage());
            return AnalyticsOverviewDTO.offline();
        }
    }

    /**
     * Retrieves club engagement statistics (events, registrations, turnout, rating).
     */
    public List<ClubEngagementDTO> getClubEngagementMetrics() {
        if (!isAuthorized()) {
            System.err.println("[AnalyticsService] Access denied: ADMIN role required for club metrics.");
            return Collections.emptyList();
        }

        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }

        try {
            return analyticsDAO.getClubEngagementMetrics();
        } catch (Exception e) {
            System.err.println("[AnalyticsService Error] Failed to load club metrics: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves individual event performance metrics.
     */
    public List<EventPerformanceDTO> getEventPerformanceMetrics() {
        if (!isAuthorized()) {
            System.err.println("[AnalyticsService] Access denied: ADMIN role required for event performance.");
            return Collections.emptyList();
        }

        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }

        try {
            return analyticsDAO.getEventPerformanceMetrics();
        } catch (Exception e) {
            System.err.println("[AnalyticsService Error] Failed to load event performance: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves event and registration distributions by category.
     */
    public List<CategoryDistributionDTO> getCategoryDistribution() {
        if (!isAuthorized()) {
            System.err.println("[AnalyticsService] Access denied: ADMIN role required for category distribution.");
            return Collections.emptyList();
        }

        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }

        try {
            return analyticsDAO.getCategoryDistribution();
        } catch (Exception e) {
            System.err.println("[AnalyticsService Error] Failed to load category distribution: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves chronological monthly trends for registrations and attendance.
     */
    public List<MonthlyTrendDTO> getMonthlyParticipationTrends() {
        if (!isAuthorized()) {
            System.err.println("[AnalyticsService] Access denied: ADMIN role required for monthly trends.");
            return Collections.emptyList();
        }

        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }

        try {
            return analyticsDAO.getMonthlyParticipationTrends();
        } catch (Exception e) {
            System.err.println("[AnalyticsService Error] Failed to load monthly trends: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves count of registrations by status (APPROVED, PENDING, REJECTED).
     */
    public Map<String, Integer> getRegistrationStatusCounts() {
        if (!isAuthorized()) {
            System.err.println("[AnalyticsService] Access denied: ADMIN role required for registration breakdown.");
            Map<String, Integer> empty = new LinkedHashMap<>();
            empty.put("APPROVED", 0);
            empty.put("PENDING", 0);
            empty.put("REJECTED", 0);
            return empty;
        }

        if (!DBConnection.testConnection()) {
            Map<String, Integer> offline = new LinkedHashMap<>();
            offline.put("APPROVED", 0);
            offline.put("PENDING", 0);
            offline.put("REJECTED", 0);
            return offline;
        }

        try {
            return analyticsDAO.getRegistrationStatusCounts();
        } catch (Exception e) {
            System.err.println("[AnalyticsService Error] Failed to load status counts: " + e.getMessage());
            Map<String, Integer> errMap = new LinkedHashMap<>();
            errMap.put("APPROVED", 0);
            errMap.put("PENDING", 0);
            errMap.put("REJECTED", 0);
            return errMap;
        }
    }
}
