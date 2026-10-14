package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * StudentFeedbackDTO — Data Transfer Object representing an attended event
 * and its feedback submission status from the student's perspective.
 */
public class StudentFeedbackDTO {

    private int eventId;
    private String eventName;
    private String clubName;
    private String category;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private String venue;
    private boolean attended;
    private boolean feedbackSubmitted;
    private int feedbackId;
    private int rating; // 1 to 5, or 0 if not submitted
    private String comment;
    private LocalDateTime submittedAt;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public StudentFeedbackDTO() {
    }

    public StudentFeedbackDTO(int eventId, String eventName, String clubName, String category,
                              LocalDate eventDate, LocalTime eventTime, String venue,
                              boolean attended, boolean feedbackSubmitted, int feedbackId,
                              int rating, String comment, LocalDateTime submittedAt) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.clubName = clubName;
        this.category = category;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.venue = venue;
        this.attended = attended;
        this.feedbackSubmitted = feedbackSubmitted;
        this.feedbackId = feedbackId;
        this.rating = rating;
        this.comment = comment;
        this.submittedAt = submittedAt;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName != null ? eventName : "Untitled Event";
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getClubName() {
        return clubName != null ? clubName : "Campus Society";
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
    }

    public String getCategory() {
        return category != null ? category : "Campus Event";
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getVenue() {
        return venue != null ? venue : "Campus Venue";
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public boolean isAttended() {
        return attended;
    }

    public void setAttended(boolean attended) {
        this.attended = attended;
    }

    public boolean isFeedbackSubmitted() {
        return feedbackSubmitted;
    }

    public void setFeedbackSubmitted(boolean feedbackSubmitted) {
        this.feedbackSubmitted = feedbackSubmitted;
    }

    public int getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(int feedbackId) {
        this.feedbackId = feedbackId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment != null ? comment : "";
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getFormattedEventDate() {
        return eventDate != null ? eventDate.format(DATE_FORMATTER) : "TBD";
    }

    public String getFormattedSubmittedAt() {
        return submittedAt != null ? submittedAt.format(TIMESTAMP_FORMATTER) : "—";
    }

    public String getStarString() {
        if (!feedbackSubmitted || rating < 1) {
            return "☆☆☆☆☆";
        }
        int clamped = Math.max(1, Math.min(5, rating));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i < clamped) {
                sb.append("★");
            } else {
                sb.append("☆");
            }
        }
        return sb.toString();
    }
}
