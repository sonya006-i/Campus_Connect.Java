-- =============================================================================
-- CAMPUS CONNECT — Safe Idempotent Demo Seed Data for Supabase PostgreSQL
-- Target Platform: Supabase PostgreSQL (Phase 23B Reviewed Script)
-- =============================================================================
--
-- SAFETY & DESIGN PRINCIPLES:
-- 1. Sequence-Agnostic: Zero hardcoded primary/foreign key integer IDs.
--    All relational foreign keys are resolved dynamically using natural unique
--    business keys (email for users, club_name for clubs, event_name for events).
-- 2. Fully Idempotent: Can be safely executed multiple times without errors,
--    without duplicate rows, and without crashing on unique constraints.
-- 3. Non-Destructive: Contains ZERO DROP, TRUNCATE, or unconstrained DELETE statements.
-- 4. PBKDF2 Password Security: Uses pre-hashed salted PBKDF2WithHmacSHA256 hashes
--    compatible with PasswordSecurityUtil (65,536 iterations, 16-byte random salts).
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. Users (1 Admin, 5 Students)
-- Uses ON CONFLICT (email) DO NOTHING to prevent unique key violations.
-- -----------------------------------------------------------------------------
INSERT INTO users (name, email, password, role) VALUES
('Dr. Alan Vance', 'admin@campusconnect.edu', 'PBKDF2WithHmacSHA256:65536:3RI9ViNSrqZ9eC3ehkOKLg==:BhWcuWUdSPBM9LWZkGI8LcYo/Q02XrpFGyeWtIPCFpU=', 'ADMIN'),
('Sonya Blade', 'sonya@student.edu', 'PBKDF2WithHmacSHA256:65536:0g4RychnnRlzfdDYThZ72Q==:SVM4bXqgxjuYf8IN+5fGbk9l52YqevZiByQBTSSOIHw=', 'STUDENT'),
('Alex Chen', 'alex@student.edu', 'PBKDF2WithHmacSHA256:65536:pgQ1qZ7UFFAjfxQGOW7ucQ==:cCF5sUvdxDGtXxqdcC4/jBJZ8dIC6RnC4uH8GpPf1O0=', 'STUDENT'),
('Priya Sharma', 'priya@student.edu', 'PBKDF2WithHmacSHA256:65536:MOKwLrPyCA5IdutKuxXU7w==:mhTSoUVPms28+BiaOxCYiN8sYIOSRTq7c9BY4PrhpjM=', 'STUDENT'),
('Marcus Rivera', 'marcus@student.edu', 'PBKDF2WithHmacSHA256:65536:I5qo8woCQUX5R0RMbrlcvg==:8lrtUzZEBIYq3H0EPFqiz68WuF8zfDoytBpc8jchkRs=', 'STUDENT'),
('Elena Rostova', 'elena@student.edu', 'PBKDF2WithHmacSHA256:65536:DWghakAifad3qLMSkmZKvA==:X+0Fwu6Mp5Muz4DBvUASrzfyhK3AdvxK+86XSKKEY38=', 'STUDENT')
ON CONFLICT (email) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. Clubs (3 Clubs)
-- Inserts conditionally if club_name does not already exist.
-- -----------------------------------------------------------------------------
INSERT INTO clubs (club_name, description)
SELECT 'Tech & Coding Society', 'Fostering innovation in software engineering, competitive programming, and emerging technology stacks.'
WHERE NOT EXISTS (SELECT 1 FROM clubs WHERE club_name = 'Tech & Coding Society');

INSERT INTO clubs (club_name, description)
SELECT 'Robotics & AI Guild', 'Building autonomous systems, robotics hardware, machine learning pipelines, and smart drones.'
WHERE NOT EXISTS (SELECT 1 FROM clubs WHERE club_name = 'Robotics & AI Guild');

INSERT INTO clubs (club_name, description)
SELECT 'Creative Arts & Media Club', 'A collaborative hub for digital design, media production, stage styling, and campus photography.'
WHERE NOT EXISTS (SELECT 1 FROM clubs WHERE club_name = 'Creative Arts & Media Club');

-- -----------------------------------------------------------------------------
-- 3. Events (5 Realistic Campus Events)
-- Dynamic club_id resolution via club_name; conditionally inserted.
-- -----------------------------------------------------------------------------
INSERT INTO events (club_id, event_name, description, category, event_date, event_time, venue, max_participants, registration_deadline)
SELECT c.club_id, 'Tech Hackathon 2026', 'A 24-hour campus hackathon solving university and environmental sustainability challenges with high cash prizes.', 'Technology', '2026-10-20', '09:00:00', 'Main Auditorium & Innovation Hall', 200, '2026-10-18'
FROM clubs c WHERE c.club_name = 'Tech & Coding Society'
AND NOT EXISTS (SELECT 1 FROM events WHERE event_name = 'Tech Hackathon 2026');

INSERT INTO events (club_id, event_name, description, category, event_date, event_time, venue, max_participants, registration_deadline)
SELECT c.club_id, 'AI & Robotics Workshop', 'Hands-on hardware and micro-ROS lab session covering computer vision with OpenCV and embedded edge devices.', 'Robotics', '2026-10-25', '14:00:00', 'Lab Complex B-3', 100, '2026-10-23'
FROM clubs c WHERE c.club_name = 'Robotics & AI Guild'
AND NOT EXISTS (SELECT 1 FROM events WHERE event_name = 'AI & Robotics Workshop');

INSERT INTO events (club_id, event_name, description, category, event_date, event_time, venue, max_participants, registration_deadline)
SELECT c.club_id, 'Campus Fashion Night', 'Annual university runway celebrating student textile designs, sustainable fashion concepts, and live styling.', 'Arts', '2026-11-05', '18:30:00', 'Open Air Amphitheatre', 300, '2026-11-01'
FROM clubs c WHERE c.club_name = 'Creative Arts & Media Club'
AND NOT EXISTS (SELECT 1 FROM events WHERE event_name = 'Campus Fashion Night');

INSERT INTO events (club_id, event_name, description, category, event_date, event_time, venue, max_participants, registration_deadline)
SELECT c.club_id, 'Entrepreneurship Summit', 'Interactive panel discussion and pitch competition with startup founders, venture capitalists, and alumni tech leaders.', 'Business', '2026-11-12', '10:00:00', 'Executive Seminar Hall A', 150, '2026-11-10'
FROM clubs c WHERE c.club_name = 'Tech & Coding Society'
AND NOT EXISTS (SELECT 1 FROM events WHERE event_name = 'Entrepreneurship Summit');

INSERT INTO events (club_id, event_name, description, category, event_date, event_time, venue, max_participants, registration_deadline)
SELECT c.club_id, 'Photography Walk', 'Golden hour photo excursion across campus grounds exploring framing, architectural angles, and light capture techniques.', 'Arts', '2026-11-18', '16:00:00', 'Campus Botanical Gardens', 50, '2026-11-16'
FROM clubs c WHERE c.club_name = 'Creative Arts & Media Club'
AND NOT EXISTS (SELECT 1 FROM events WHERE event_name = 'Photography Walk');

-- -----------------------------------------------------------------------------
-- 4. Registrations (Realistic registrations for student users)
-- Dynamic resolution via event_name and student email.
-- -----------------------------------------------------------------------------
INSERT INTO registrations (event_id, student_id, status, registration_date)
SELECT e.event_id, u.user_id, 'APPROVED', '2026-10-02'
FROM events e, users u
WHERE e.event_name = 'Tech Hackathon 2026' AND u.email = 'sonya@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO registrations (event_id, student_id, status, registration_date)
SELECT e.event_id, u.user_id, 'APPROVED', '2026-10-03'
FROM events e, users u
WHERE e.event_name = 'Tech Hackathon 2026' AND u.email = 'alex@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO registrations (event_id, student_id, status, registration_date)
SELECT e.event_id, u.user_id, 'PENDING', '2026-10-04'
FROM events e, users u
WHERE e.event_name = 'Tech Hackathon 2026' AND u.email = 'priya@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO registrations (event_id, student_id, status, registration_date)
SELECT e.event_id, u.user_id, 'APPROVED', '2026-10-04'
FROM events e, users u
WHERE e.event_name = 'AI & Robotics Workshop' AND u.email = 'sonya@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO registrations (event_id, student_id, status, registration_date)
SELECT e.event_id, u.user_id, 'APPROVED', '2026-10-05'
FROM events e, users u
WHERE e.event_name = 'AI & Robotics Workshop' AND u.email = 'marcus@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO registrations (event_id, student_id, status, registration_date)
SELECT e.event_id, u.user_id, 'APPROVED', '2026-10-06'
FROM events e, users u
WHERE e.event_name = 'Campus Fashion Night' AND u.email = 'priya@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO registrations (event_id, student_id, status, registration_date)
SELECT e.event_id, u.user_id, 'PENDING', '2026-10-07'
FROM events e, users u
WHERE e.event_name = 'Campus Fashion Night' AND u.email = 'elena@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO registrations (event_id, student_id, status, registration_date)
SELECT e.event_id, u.user_id, 'APPROVED', '2026-10-05'
FROM events e, users u
WHERE e.event_name = 'Entrepreneurship Summit' AND u.email = 'alex@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO registrations (event_id, student_id, status, registration_date)
SELECT e.event_id, u.user_id, 'APPROVED', '2026-10-06'
FROM events e, users u
WHERE e.event_name = 'Photography Walk' AND u.email = 'elena@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. Attendance (Sample attendance for completed/confirmed activities)
-- -----------------------------------------------------------------------------
INSERT INTO attendance (event_id, student_id, present)
SELECT e.event_id, u.user_id, TRUE
FROM events e, users u
WHERE e.event_name = 'Tech Hackathon 2026' AND u.email = 'sonya@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO attendance (event_id, student_id, present)
SELECT e.event_id, u.user_id, TRUE
FROM events e, users u
WHERE e.event_name = 'Tech Hackathon 2026' AND u.email = 'alex@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO attendance (event_id, student_id, present)
SELECT e.event_id, u.user_id, TRUE
FROM events e, users u
WHERE e.event_name = 'AI & Robotics Workshop' AND u.email = 'sonya@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO attendance (event_id, student_id, present)
SELECT e.event_id, u.user_id, FALSE
FROM events e, users u
WHERE e.event_name = 'AI & Robotics Workshop' AND u.email = 'marcus@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 6. Feedback (Sample feedback entries)
-- -----------------------------------------------------------------------------
INSERT INTO feedback (event_id, student_id, rating, comment)
SELECT e.event_id, u.user_id, 5, 'Incredible organization, inspiring mentors, and smooth logistics! Looking forward to next year.'
FROM events e, users u
WHERE e.event_name = 'Tech Hackathon 2026' AND u.email = 'sonya@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO feedback (event_id, student_id, rating, comment)
SELECT e.event_id, u.user_id, 4, 'Great challenge statements and venue setup. Would love more hardware tracks in the future.'
FROM events e, users u
WHERE e.event_name = 'Tech Hackathon 2026' AND u.email = 'alex@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO feedback (event_id, student_id, rating, comment)
SELECT e.event_id, u.user_id, 5, 'Hands-on edge devices and vision models were clearly taught.'
FROM events e, users u
WHERE e.event_name = 'AI & Robotics Workshop' AND u.email = 'sonya@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 7. Certificates (Sample issued certificates)
-- -----------------------------------------------------------------------------
INSERT INTO certificates (event_id, student_id, certificate_path, generated_date)
SELECT e.event_id, u.user_id, '/certificates/cert_event1_user2.pdf', '2026-10-21'
FROM events e, users u
WHERE e.event_name = 'Tech Hackathon 2026' AND u.email = 'sonya@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

INSERT INTO certificates (event_id, student_id, certificate_path, generated_date)
SELECT e.event_id, u.user_id, '/certificates/cert_event1_user3.pdf', '2026-10-21'
FROM events e, users u
WHERE e.event_name = 'Tech Hackathon 2026' AND u.email = 'alex@student.edu'
ON CONFLICT (event_id, student_id) DO NOTHING;

COMMIT;
