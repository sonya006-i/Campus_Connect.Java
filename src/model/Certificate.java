package model;

import java.time.LocalDate;

/**
 * Certificate — Model representing an event certificate issued to a student.
 */
public class Certificate {

    private int certificateId;
    private int eventId;
    private int studentId;
    private String certificatePath;
    private LocalDate generatedDate;

    public Certificate() {
    }

    public Certificate(int certificateId, int eventId, int studentId, String certificatePath, LocalDate generatedDate) {
        this.certificateId = certificateId;
        this.eventId = eventId;
        this.studentId = studentId;
        this.certificatePath = certificatePath;
        this.generatedDate = generatedDate;
    }

    public Certificate(int eventId, int studentId, String certificatePath, LocalDate generatedDate) {
        this.eventId = eventId;
        this.studentId = studentId;
        this.certificatePath = certificatePath;
        this.generatedDate = generatedDate;
    }

    public int getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(int certificateId) {
        this.certificateId = certificateId;
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

    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    @Override
    public String toString() {
        return "Certificate{" +
                "certificateId=" + certificateId +
                ", eventId=" + eventId +
                ", studentId=" + studentId +
                ", certificatePath='" + certificatePath + '\'' +
                ", generatedDate=" + generatedDate +
                '}';
    }
}
