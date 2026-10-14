package model;

/**
 * Feedback — Model representing student feedback and ratings for an event.
 */
public class Feedback {

    private int feedbackId;
    private int eventId;
    private int studentId;
    private int rating; // 1 to 5
    private String comment;
    private java.time.LocalDateTime submittedAt;

    public Feedback() {
    }

    public Feedback(int feedbackId, int eventId, int studentId, int rating, String comment) {
        this.feedbackId = feedbackId;
        this.eventId = eventId;
        this.studentId = studentId;
        this.rating = rating;
        this.comment = comment;
    }

    public Feedback(int feedbackId, int eventId, int studentId, int rating, String comment, java.time.LocalDateTime submittedAt) {
        this.feedbackId = feedbackId;
        this.eventId = eventId;
        this.studentId = studentId;
        this.rating = rating;
        this.comment = comment;
        this.submittedAt = submittedAt;
    }

    public Feedback(int eventId, int studentId, int rating, String comment) {
        this.eventId = eventId;
        this.studentId = studentId;
        this.rating = rating;
        this.comment = comment;
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

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public java.time.LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(java.time.LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    @Override
    public String toString() {
        return "Feedback{" +
                "feedbackId=" + feedbackId +
                ", eventId=" + eventId +
                ", studentId=" + studentId +
                ", rating=" + rating +
                ", submittedAt=" + submittedAt +
                '}';
    }
}
