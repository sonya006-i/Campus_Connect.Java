-- =============================================================================
-- CAMPUS CONNECT — PostgreSQL / Supabase Schema (Phase 21B)
-- Target Platform: Supabase PostgreSQL
-- =============================================================================
--
-- SAFETY & IDEMPOTENCY POLICY:
-- 1. Strictly NON-DESTRUCTIVE: This script contains ZERO DROP TABLE, TRUNCATE,
--    or DELETE statements. Existing database objects and records are never deleted.
-- 2. Idempotent Creation: Uses 'CREATE TABLE IF NOT EXISTS' and 'CREATE INDEX IF NOT EXISTS'
--    to allow safe repeat execution against target databases.
-- 3. Pre-Deployment Requirement: 'IF NOT EXISTS' skips creation when a relation
--    already exists, but does NOT inspect, alter, or repair preexisting tables or
--    constraints that have incompatible definitions. Operators must inspect existing
--    tables prior to deployment (see docs/SUPABASE_SETUP_CHECKLIST.md).
-- 4. Creation Order: Strictly follows relational dependencies:
--    users & clubs (roots) -> events -> registrations, attendance, feedback, certificates (leaves).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. users Table
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    -- password: Stores self-describing PBKDF2WithHmacSHA256 hash (PBKDF2WithHmacSHA256:<iter>:<salt>:<hash>)
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_role CHECK (role IN ('STUDENT', 'ADMIN'))
);

-- -----------------------------------------------------------------------------
-- 2. clubs Table
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS clubs (
    club_id SERIAL PRIMARY KEY,
    club_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 3. events Table
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS events (
    event_id SERIAL PRIMARY KEY,
    club_id INT NOT NULL,
    event_name VARCHAR(150) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    event_date DATE NOT NULL,
    event_time TIME,
    venue VARCHAR(150),
    max_participants INT NOT NULL,
    registration_deadline DATE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_events_club FOREIGN KEY (club_id)
        REFERENCES clubs(club_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_events_date ON events(event_date);
CREATE INDEX IF NOT EXISTS idx_events_club ON events(club_id);

-- -----------------------------------------------------------------------------
-- 4. registrations Table
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS registrations (
    registration_id SERIAL PRIMARY KEY,
    event_id INT NOT NULL,
    student_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    registration_date DATE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
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
);

CREATE INDEX IF NOT EXISTS idx_reg_student ON registrations(student_id);
CREATE INDEX IF NOT EXISTS idx_reg_event ON registrations(event_id);

-- -----------------------------------------------------------------------------
-- 5. attendance Table
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id SERIAL PRIMARY KEY,
    event_id INT NOT NULL,
    student_id INT NOT NULL,
    present BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attendance_event_student UNIQUE (event_id, student_id),
    CONSTRAINT fk_att_event FOREIGN KEY (event_id)
        REFERENCES events(event_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_att_student FOREIGN KEY (student_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_att_student ON attendance(student_id);
CREATE INDEX IF NOT EXISTS idx_att_event ON attendance(event_id);

-- -----------------------------------------------------------------------------
-- 6. feedback Table
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS feedback (
    feedback_id SERIAL PRIMARY KEY,
    event_id INT NOT NULL,
    student_id INT NOT NULL,
    rating INT NOT NULL,
    comment TEXT,
    submitted_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
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
);

CREATE INDEX IF NOT EXISTS idx_fb_event ON feedback(event_id);
CREATE INDEX IF NOT EXISTS idx_fb_student ON feedback(student_id);

-- -----------------------------------------------------------------------------
-- 7. certificates Table
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS certificates (
    certificate_id SERIAL PRIMARY KEY,
    event_id INT NOT NULL,
    student_id INT NOT NULL,
    certificate_path VARCHAR(500),
    generated_date DATE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cert_event_student UNIQUE (event_id, student_id),
    CONSTRAINT fk_cert_event FOREIGN KEY (event_id)
        REFERENCES events(event_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_cert_student FOREIGN KEY (student_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_cert_student ON certificates(student_id);
CREATE INDEX IF NOT EXISTS idx_cert_event ON certificates(event_id);
