-- =============================================================================
-- CAMPUS CONNECT — Seed Data
-- Target Platform: PostgreSQL / Supabase (and MySQL compatible)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Users (1 Admin, 5 Students)
-- Passwords stored using salted PBKDF2WithHmacSHA256 self-describing hashes
-- -----------------------------------------------------------------------------
INSERT INTO users (name, email, password, role) VALUES
('Dr. Alan Vance', 'admin@campusconnect.edu', 'PBKDF2WithHmacSHA256:65536:3RI9ViNSrqZ9eC3ehkOKLg==:BhWcuWUdSPBM9LWZkGI8LcYo/Q02XrpFGyeWtIPCFpU=', 'ADMIN'),
('Sonya Blade', 'sonya@student.edu', 'PBKDF2WithHmacSHA256:65536:0g4RychnnRlzfdDYThZ72Q==:SVM4bXqgxjuYf8IN+5fGbk9l52YqevZiByQBTSSOIHw=', 'STUDENT'),
('Alex Chen', 'alex@student.edu', 'PBKDF2WithHmacSHA256:65536:pgQ1qZ7UFFAjfxQGOW7ucQ==:cCF5sUvdxDGtXxqdcC4/jBJZ8dIC6RnC4uH8GpPf1O0=', 'STUDENT'),
('Priya Sharma', 'priya@student.edu', 'PBKDF2WithHmacSHA256:65536:MOKwLrPyCA5IdutKuxXU7w==:mhTSoUVPms28+BiaOxCYiN8sYIOSRTq7c9BY4PrhpjM=', 'STUDENT'),
('Marcus Rivera', 'marcus@student.edu', 'PBKDF2WithHmacSHA256:65536:I5qo8woCQUX5R0RMbrlcvg==:8lrtUzZEBIYq3H0EPFqiz68WuF8zfDoytBpc8jchkRs=', 'STUDENT'),
('Elena Rostova', 'elena@student.edu', 'PBKDF2WithHmacSHA256:65536:DWghakAifad3qLMSkmZKvA==:X+0Fwu6Mp5Muz4DBvUASrzfyhK3AdvxK+86XSKKEY38=', 'STUDENT');

-- -----------------------------------------------------------------------------
-- 2. Clubs (3 Clubs)
-- -----------------------------------------------------------------------------
INSERT INTO clubs (club_name, description) VALUES
('Tech & Coding Society', 'Fostering innovation in software engineering, competitive programming, and emerging technology stacks.'),
('Robotics & AI Guild', 'Building autonomous systems, robotics hardware, machine learning pipelines, and smart drones.'),
('Creative Arts & Media Club', 'A collaborative hub for digital design, media production, stage styling, and campus photography.');

-- -----------------------------------------------------------------------------
-- 3. Events (5 Realistic Campus Events)
-- -----------------------------------------------------------------------------
INSERT INTO events (club_id, event_name, description, category, event_date, event_time, venue, max_participants, registration_deadline) VALUES
(1, 'Tech Hackathon 2026', 'A 24-hour campus hackathon solving university and environmental sustainability challenges with high cash prizes.', 'Technology', '2026-10-20', '09:00:00', 'Main Auditorium & Innovation Hall', 200, '2026-10-18'),
(2, 'AI & Robotics Workshop', 'Hands-on hardware and micro-ROS lab session covering computer vision with OpenCV and embedded edge devices.', 'Robotics', '2026-10-25', '14:00:00', 'Lab Complex B-3', 100, '2026-10-23'),
(3, 'Campus Fashion Night', 'Annual university runway celebrating student textile designs, sustainable fashion concepts, and live styling.', 'Arts', '2026-11-05', '18:30:00', 'Open Air Amphitheatre', 300, '2026-11-01'),
(1, 'Entrepreneurship Summit', 'Interactive panel discussion and pitch competition with startup founders, venture capitalists, and alumni tech leaders.', 'Business', '2026-11-12', '10:00:00', 'Executive Seminar Hall A', 150, '2026-11-10'),
(3, 'Photography Walk', 'Golden hour photo excursion across campus grounds exploring framing, architectural angles, and light capture techniques.', 'Arts', '2026-11-18', '16:00:00', 'Campus Botanical Gardens', 50, '2026-11-16');

-- -----------------------------------------------------------------------------
-- 4. Registrations (Realistic registrations for student users)
-- -----------------------------------------------------------------------------
INSERT INTO registrations (event_id, student_id, status, registration_date) VALUES
(1, 2, 'APPROVED', '2026-10-02'), -- Sonya in Hackathon
(1, 3, 'APPROVED', '2026-10-03'), -- Alex in Hackathon
(1, 4, 'PENDING',  '2026-10-04'), -- Priya in Hackathon
(2, 2, 'APPROVED', '2026-10-04'), -- Sonya in AI Workshop
(2, 5, 'APPROVED', '2026-10-05'), -- Marcus in AI Workshop
(3, 4, 'APPROVED', '2026-10-06'), -- Priya in Fashion Night
(3, 6, 'PENDING',  '2026-10-07'), -- Elena in Fashion Night
(4, 3, 'APPROVED', '2026-10-05'), -- Alex in Summit
(5, 6, 'APPROVED', '2026-10-06'); -- Elena in Photo Walk

-- -----------------------------------------------------------------------------
-- 5. Attendance (Sample attendance for completed/confirmed activities)
-- -----------------------------------------------------------------------------
INSERT INTO attendance (event_id, student_id, present) VALUES
(1, 2, TRUE),
(1, 3, TRUE),
(2, 2, TRUE),
(2, 5, FALSE);

-- -----------------------------------------------------------------------------
-- 6. Feedback (Sample feedback entries)
-- -----------------------------------------------------------------------------
INSERT INTO feedback (event_id, student_id, rating, comment) VALUES
(1, 2, 5, 'Incredible organization, inspiring mentors, and smooth logistics! Looking forward to next year.'),
(1, 3, 4, 'Great challenge statements and venue setup. Would love more hardware tracks in the future.'),
(2, 2, 5, 'Hands-on edge devices and vision models were clearly taught.');

-- -----------------------------------------------------------------------------
-- 7. Certificates (Sample issued certificates)
-- -----------------------------------------------------------------------------
INSERT INTO certificates (event_id, student_id, certificate_path, generated_date) VALUES
(1, 2, '/certificates/cert_event1_user2.pdf', '2026-10-21'),
(1, 3, '/certificates/cert_event1_user3.pdf', '2026-10-21');
