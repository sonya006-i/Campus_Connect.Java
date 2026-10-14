package model;

import java.util.Locale;

/**
 * AnalyticsOverviewDTO — Campus-wide high-level telemetry and KPI calculations
 * for Phase 14: Analytics.
 */
public class AnalyticsOverviewDTO {

    private boolean dbOnline;
    private int totalEvents;
    private int totalRegistrations;
    private int pendingRegistrations;
    private int approvedRegistrations;
    private int rejectedRegistrations;
    private int totalAttended;
    private int totalCertificatesIssued;
    private int totalFeedbackCount;
    private double averageFeedbackRating;
    private int totalClubs;
    private int totalStudents;

    public AnalyticsOverviewDTO() {
        this.dbOnline = true;
    }

    public static AnalyticsOverviewDTO offline() {
        AnalyticsOverviewDTO dto = new AnalyticsOverviewDTO();
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

    public int getTotalAttended() {
        return totalAttended;
    }

    public void setTotalAttended(int totalAttended) {
        this.totalAttended = totalAttended;
    }

    public int getTotalCertificatesIssued() {
        return totalCertificatesIssued;
    }

    public void setTotalCertificatesIssued(int totalCertificatesIssued) {
        this.totalCertificatesIssued = totalCertificatesIssued;
    }

    public int getTotalFeedbackCount() {
        return totalFeedbackCount;
    }

    public void setTotalFeedbackCount(int totalFeedbackCount) {
        this.totalFeedbackCount = totalFeedbackCount;
    }

    public double getAverageFeedbackRating() {
        return averageFeedbackRating;
    }

    public void setAverageFeedbackRating(double averageFeedbackRating) {
        this.averageFeedbackRating = averageFeedbackRating;
    }

    public int getTotalClubs() {
        return totalClubs;
    }

    public void setTotalClubs(int totalClubs) {
        this.totalClubs = totalClubs;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
    }

    // --- Computed Analytics Ratios ---

    /**
     * Calculates percentage of registrations approved out of total registrations.
     */
    public double getApprovalRate() {
        if (totalRegistrations <= 0) return 0.0;
        return (approvedRegistrations * 100.0) / totalRegistrations;
    }

    /**
     * Calculates turnout rate: attended participants out of approved registrations.
     */
    public double getTurnoutRate() {
        if (approvedRegistrations <= 0) return 0.0;
        return Math.min(100.0, (totalAttended * 100.0) / approvedRegistrations);
    }

    /**
     * Calculates certificate issuance rate: certificates issued out of verified attendees.
     */
    public double getCertificateIssuanceRate() {
        if (totalAttended <= 0) return 0.0;
        return Math.min(100.0, (totalCertificatesIssued * 100.0) / totalAttended);
    }

    /**
     * Calculates feedback response rate: feedback reviews submitted out of verified attendees.
     */
    public double getFeedbackResponseRate() {
        if (totalAttended <= 0) return 0.0;
        return Math.min(100.0, (totalFeedbackCount * 100.0) / totalAttended);
    }

    /**
     * Formats average rating to 1 decimal place or N/A if no feedback.
     */
    public String getFormattedAverageRating() {
        if (totalFeedbackCount <= 0 || averageFeedbackRating <= 0.0) {
            return "N/A";
        }
        return String.format(Locale.US, "%.1f / 5.0", averageFeedbackRating);
    }
}
