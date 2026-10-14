-- =============================================================================
-- CAMPUS CONNECT — Database Schema
-- University Advanced Java Project
-- Database: campus_connect
-- =============================================================================

CREATE DATABASE IF NOT EXISTS campus_connect
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE campus_connect;

-- Disable foreign key checks during schema re-creation if needed
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS certificates;
DROP TABLE IF EXISTS feedback;
DROP TABLE IF EXISTS attendance;
DROP TABLE IF EXISTS registrations;
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS clubs;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 1. users Table
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    -- password: Stores self-describing PBKDF2WithHmacSHA256 hash (format: PBKDF2WithHmacSHA256:<iter>:<salt>:<hash>)
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_role CHECK (role IN ('STUDENT', 'ADMIN'))
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 2. clubs Table
-- -----------------------------------------------------------------------------
CREATE TABLE clubs (
    club_id INT PRIMARY KEY AUTO_INCREMENT,
    club_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 3. events Table
-- -----------------------------------------------------------------------------
CREATE TABLE events (
    event_id INT PRIMARY KEY AUTO_INCREMENT,
    club_id INT NOT NULL,
    event_name VARCHAR(150) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    event_date DATE NOT NULL,
    event_time TIME,
    venue VARCHAR(150),
    max_participants INT NOT NULL,
    registration_deadline DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_events_club FOREIGN KEY (club_id)
        REFERENCES clubs(club_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE INDEX idx_events_date ON events(event_date);
CREATE INDEX idx_events_club ON events(club_id);

-- -----------------------------------------------------------------------------
-- 4. registrations Table
-- -----------------------------------------------------------------------------
CREATE TABLE registrations (
    registration_id INT PRIMARY KEY AUTO_INCREMENT,
    event_id INT NOT NULL,
    student_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    registration_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_registration_event_student UNIQUE (event_id, student_id),
    CONSTRAINT chk_reg_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_reg_event FOREIGN KEY (event_id)
        REFERENCES events(event_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_reg_student FOREIGN KEY (student_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE INDEX idx_reg_student ON registrations(student_id);
CREATE INDEX idx_reg_event ON registrations(event_id);

-- -----------------------------------------------------------------------------
-- 5. attendance Table
-- -----------------------------------------------------------------------------
CREATE TABLE attendance (
    attendance_id INT PRIMARY KEY AUTO_INCREMENT,
    event_id INT NOT NULL,
    student_id INT NOT NULL,
    present BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attendance_event_student UNIQUE (event_id, student_id),
    CONSTRAINT fk_att_event FOREIGN KEY (event_id)
        REFERENCES events(event_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_att_student FOREIGN KEY (student_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE INDEX idx_att_student ON attendance(student_id);
CREATE INDEX idx_att_event ON attendance(event_id);

-- -----------------------------------------------------------------------------
-- 6. feedback Table
-- -----------------------------------------------------------------------------
CREATE TABLE feedback (
    feedback_id INT PRIMARY KEY AUTO_INCREMENT,
    event_id INT NOT NULL,
    student_id INT NOT NULL,
    rating INT NOT NULL,
    comment TEXT,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_feedback_event_student UNIQUE (event_id, student_id),
    CONSTRAINT chk_feedback_rating CHECK (rating >= 1 AND rating <= 5),
    CONSTRAINT fk_fb_event FOREIGN KEY (event_id)
        REFERENCES events(event_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_fb_student FOREIGN KEY (student_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE INDEX idx_fb_event ON feedback(event_id);
CREATE INDEX idx_fb_student ON feedback(student_id);

-- -----------------------------------------------------------------------------
-- 7. certificates Table
-- -----------------------------------------------------------------------------
CREATE TABLE certificates (
    certificate_id INT PRIMARY KEY AUTO_INCREMENT,
    event_id INT NOT NULL,
    student_id INT NOT NULL,
    certificate_path VARCHAR(500),
    generated_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cert_event_student UNIQUE (event_id, student_id),
    CONSTRAINT fk_cert_event FOREIGN KEY (event_id)
        REFERENCES events(event_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_cert_student FOREIGN KEY (student_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE INDEX idx_cert_student ON certificates(student_id);
CREATE INDEX idx_cert_event ON certificates(event_id);
