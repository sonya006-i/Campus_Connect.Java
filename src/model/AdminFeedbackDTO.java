package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AdminFeedbackDTO — Data Transfer Object representing submitted student feedback
 * joined with event and student details for administrator review.
 */
public class AdminFeedbackDTO {

    private int feedbackId;
    private int eventId;
    private String eventName;
    private String clubName;
    private String category;
    private LocalDate eventDate;
    private int studentId;
    private String studentName;
    private String studentEmail;
    private int rating; // 1 to 5
    private String comment;
    private LocalDateTime submittedAt;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public AdminFeedbackDTO() {
    }

    public AdminFeedbackDTO(int feedbackId, int eventId, String eventName, String clubName, String category,
                            LocalDate eventDate, int studentId, String studentName, String studentEmail,
                            int rating, String comment, LocalDateTime submittedAt) {
        this.feedbackId = feedbackId;
        this.eventId = eventId;
        this.eventName = eventName;
        this.clubName = clubName;
        this.category = category;
        this.eventDate = eventDate;
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.rating = rating;
        this.comment = comment;
        this.submittedAt = submittedAt;
    }

    public int getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(int feedbackId) {
        this.feedbackId = feedbackId;
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
        return category != null ? category : "General";
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

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName != null ? studentName : "Student Member";
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentEmail() {
        return studentEmail != null ? studentEmail : "—";
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
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
