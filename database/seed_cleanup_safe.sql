-- =============================================================================
-- CAMPUS CONNECT — Hardened Demo Cleanup Script (Phase 23H)
-- Target Platform: Supabase PostgreSQL
-- =============================================================================
--
-- RIGOROUS SAFETY, OWNERSHIP & CONCURRENCY GUARANTEES:
-- 1. Advisory Concurrency Lock: Obtains an exclusive transaction advisory lock
--    (pg_advisory_xact_lock) on the demo setup identifier to serialize concurrent
--    seed or cleanup script executions.
-- 2. Full Transaction Atomicity: Entire cleanup executes inside an atomic DO $$ ... END $$
--    block. If any check, dependency, or deletion fails, PostgreSQL rolls back all
--    changes automatically. Zero partial deletions persist.
-- 3. Strict Tracking Table Validation: Deeply validates '_demo_setup_tracking' schema,
--    verifying exactly 5 columns, types, required nullability, exact composite UNIQUE
--    constraint on (entity_type, entity_id), and explicit CHECK constraint on allowed
--    entity_type values.
-- 4. Rigorous Tracking & Complete Identity Verification: Confirms exactly 5 tracked records
--    exist, and verifies each tracked ID matches all expected attributes of the fixture,
--    including both users' emails, names, roles, and password hashes; club name & description;
--    all seeded event attributes; and registration status & registration date.
--    Never selects an arbitrary registration by entity_type alone.
-- 5. Dependent Data Pre-Inspection: Audits all foreign key dependencies across the
--    seven core tables (attendance, feedback, certificates, extra registrations, extra events).
--    Aborts immediately if ANY dependent rows reference demo records. Untracked data is preserved.
-- 6. Strict Topological Deletion Order:
--    Leaf (registrations) -> Intermediate (events) -> Root (clubs, users).
-- 7. Safe Tracking Table Lifecycle: '_demo_setup_tracking' is dropped ONLY after all
--    5 tracked records have been successfully deleted and verified.
-- =============================================================================

DO $$
DECLARE
    -- Advisory lock key for Campus Connect Demo Setup
    c_lock_key       CONSTANT BIGINT := hashtext('campus_connect_demo_setup');

    -- Expected Target Demo Identities & Attributes
    c_admin_email    CONSTANT VARCHAR(150) := 'admin@campusconnect.edu';
    c_admin_name     CONSTANT VARCHAR(100) := 'Dr. Alan Vance';
    c_admin_role     CONSTANT VARCHAR(20)  := 'ADMIN';
    c_admin_hash     CONSTANT VARCHAR(255) := 'PBKDF2WithHmacSHA256:65536:3RI9ViNSrqZ9eC3ehkOKLg==:BhWcuWUdSPBM9LWZkGI8LcYo/Q02XrpFGyeWtIPCFpU=';

    c_student_email  CONSTANT VARCHAR(150) := 'sonya@student.edu';
    c_student_name   CONSTANT VARCHAR(100) := 'Sonya Blade';
    c_student_role   CONSTANT VARCHAR(20)  := 'STUDENT';
    c_student_hash   CONSTANT VARCHAR(255) := 'PBKDF2WithHmacSHA256:65536:0g4RychnnRlzfdDYThZ72Q==:SVM4bXqgxjuYf8IN+5fGbk9l52YqevZiByQBTSSOIHw=';

    c_club_name      CONSTANT VARCHAR(100) := 'Tech & Coding Society';
    c_club_desc      CONSTANT TEXT         := 'Fostering innovation in software engineering, competitive programming, and emerging technology stacks.';

    c_event_name     CONSTANT VARCHAR(150) := 'Tech Hackathon 2026';
    c_event_desc     CONSTANT TEXT         := 'A 24-hour campus hackathon solving university and environmental sustainability challenges with high cash prizes.';
    c_event_cat      CONSTANT VARCHAR(50)  := 'Technology';
    c_event_date     CONSTANT DATE         := '2026-10-20'::DATE;
    c_event_time     CONSTANT TIME         := '09:00:00'::TIME;
    c_event_venue    CONSTANT VARCHAR(150) := 'Main Auditorium & Innovation Hall';
    c_event_max      CONSTANT INT          := 200;
    c_event_deadline CONSTANT DATE         := '2026-10-18'::DATE;

    c_reg_status     CONSTANT VARCHAR(20)  := 'APPROVED';
    c_reg_key        CONSTANT VARCHAR(150) := 'sonya@student.edu -> Tech Hackathon 2026';

    -- Verification counts & state
    v_tracking_exists        BOOLEAN := FALSE;
    v_tracking_total_cols    INT := 0;
    v_tracking_col_count     INT := 0;
    v_tracking_has_exact_uq  BOOLEAN := FALSE;
    v_tracking_has_check     BOOLEAN := FALSE;
    v_tracking_count         INT := 0;

    -- Specific tracked entity counts
    v_trk_total              INT := 0;
    v_trk_admin_cnt          INT := 0;
    v_trk_student_cnt        INT := 0;
    v_trk_club_cnt           INT := 0;
    v_trk_event_cnt          INT := 0;
    v_trk_reg_cnt            INT := 0;
    v_trk_reg_created_at     TIMESTAMPTZ;

    -- Specific tracked entity IDs
    v_admin_id       INT;
    v_student_id     INT;
    v_club_id        INT;
    v_event_id       INT;
    v_reg_id         INT;

    -- Complete identity verification counts
    v_vfy_admin_cnt  INT := 0;
    v_vfy_student_cnt INT := 0;
    v_vfy_club_cnt   INT := 0;
    v_vfy_event_cnt  INT := 0;
    v_vfy_reg_cnt    INT := 0;

    -- Dependent record check counts
    v_dep_attendance   INT := 0;
    v_dep_feedback     INT := 0;
    v_dep_certificates INT := 0;
    v_dep_extra_regs   INT := 0;
    v_dep_extra_events INT := 0;

    -- Deletion counters
    v_deleted_regs   INT := 0;
    v_deleted_events INT := 0;
    v_deleted_clubs  INT := 0;
    v_deleted_users  INT := 0;
BEGIN
    -- -------------------------------------------------------------------------
    -- Step 1: Acquire Transaction Advisory Lock for Concurrency Safety
    -- -------------------------------------------------------------------------
    PERFORM pg_advisory_xact_lock(c_lock_key);

    -- -------------------------------------------------------------------------
    -- Step 2: Validate Tracking Table Infrastructure (Structure & Constraints)
    -- -------------------------------------------------------------------------
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = '_demo_setup_tracking'
    ) INTO v_tracking_exists;

    IF NOT v_tracking_exists THEN
        RAISE EXCEPTION 'SAFETY CHECK FAILED: Table ''_demo_setup_tracking'' does not exist. Cannot safely identify demo records without verified ownership tracking. Cleanup aborted to protect database integrity.';
    END IF;

    -- Verify total column count is exactly 5
    SELECT COUNT(*) INTO v_tracking_total_cols
    FROM information_schema.columns
    WHERE table_schema = current_schema() AND table_name = '_demo_setup_tracking';

    IF v_tracking_total_cols <> 5 THEN
        RAISE EXCEPTION 'SAFETY CHECK FAILED: Table ''_demo_setup_tracking'' has % columns (expected exactly 5). Refusing to execute cleanup with corrupted tracking table.', v_tracking_total_cols;
    END IF;

    -- Validate expected column definitions and required nullability
    SELECT COUNT(*) INTO v_tracking_col_count
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = '_demo_setup_tracking'
      AND (
          (column_name = 'tracking_id' AND data_type IN ('integer', 'bigint', 'smallint') AND is_nullable = 'NO') OR
          (column_name = 'entity_type' AND data_type IN ('character varying', 'text') AND is_nullable = 'NO') OR
          (column_name = 'entity_id' AND data_type IN ('integer', 'bigint') AND is_nullable = 'NO') OR
          (column_name = 'entity_key' AND data_type IN ('character varying', 'text') AND is_nullable = 'NO') OR
          (column_name = 'created_at' AND data_type LIKE 'timestamp%')
      );

    IF v_tracking_col_count <> 5 THEN
        RAISE EXCEPTION 'SAFETY CHECK FAILED: Table ''_demo_setup_tracking'' has unexpected column schema (%/5 valid). Refusing to execute cleanup with corrupted tracking infrastructure.', v_tracking_col_count;
    END IF;

    -- Validate that a UNIQUE constraint specifically covers (entity_type, entity_id) in that order
    SELECT EXISTS (
        SELECT 1
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = current_schema()
          AND rel.relname = '_demo_setup_tracking'
          AND con.contype = 'u'
          AND con.conkey = ARRAY[
              (SELECT attnum FROM pg_attribute WHERE attrelid = rel.oid AND attname = 'entity_type'),
              (SELECT attnum FROM pg_attribute WHERE attrelid = rel.oid AND attname = 'entity_id')
          ]::smallint[]
    ) INTO v_tracking_has_exact_uq;

    IF NOT v_tracking_has_exact_uq THEN
        RAISE EXCEPTION 'SAFETY CHECK FAILED: Table ''_demo_setup_tracking'' lacks the exact composite UNIQUE constraint on (entity_type, entity_id). Aborting.';
    END IF;

    -- Validate that an explicit CHECK constraint exists on entity_type
    SELECT EXISTS (
        SELECT 1
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = current_schema()
          AND rel.relname = '_demo_setup_tracking'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%entity_type%'
          AND pg_get_constraintdef(con.oid) ILIKE '%user%'
          AND pg_get_constraintdef(con.oid) ILIKE '%club%'
          AND pg_get_constraintdef(con.oid) ILIKE '%event%'
          AND pg_get_constraintdef(con.oid) ILIKE '%registration%'
    ) INTO v_tracking_has_check;

    IF NOT v_tracking_has_check THEN
        RAISE EXCEPTION 'SAFETY CHECK FAILED: Table ''_demo_setup_tracking'' lacks the required CHECK constraint on entity_type. Aborting.';
    END IF;

    SELECT COUNT(*) INTO v_tracking_count FROM _demo_setup_tracking;
    IF v_tracking_count = 0 THEN
        RAISE NOTICE '[DEMO CLEANUP] Table ''_demo_setup_tracking'' is empty. No demo records to remove.';
        RETURN;
    END IF;

    -- -------------------------------------------------------------------------
    -- Step 3: Validate the Exact Five Tracking Records
    -- -------------------------------------------------------------------------
    SELECT
        COUNT(*),
        COUNT(*) FILTER (WHERE entity_type = 'user' AND entity_key = c_admin_email),
        COUNT(*) FILTER (WHERE entity_type = 'user' AND entity_key = c_student_email),
        COUNT(*) FILTER (WHERE entity_type = 'club' AND entity_key = c_club_name),
        COUNT(*) FILTER (WHERE entity_type = 'event' AND entity_key = c_event_name),
        COUNT(*) FILTER (WHERE entity_type = 'registration' AND entity_key = c_reg_key)
    INTO
        v_trk_total,
        v_trk_admin_cnt,
        v_trk_student_cnt,
        v_trk_club_cnt,
        v_trk_event_cnt,
        v_trk_reg_cnt
    FROM _demo_setup_tracking;

    IF v_trk_total <> 5 OR v_trk_admin_cnt <> 1 OR v_trk_student_cnt <> 1
       OR v_trk_club_cnt <> 1 OR v_trk_event_cnt <> 1 OR v_trk_reg_cnt <> 1 THEN
        RAISE EXCEPTION 'SAFETY CHECK FAILED: Expected exactly 5 tracked entities (1 admin, 1 student, 1 club, 1 event, 1 registration), but found (total=%, admin=%, student=%, club=%, event=%, reg=%). Refusing to proceed with unexpected tracking state.',
            v_trk_total, v_trk_admin_cnt, v_trk_student_cnt, v_trk_club_cnt, v_trk_event_cnt, v_trk_reg_cnt;
    END IF;

    -- Extract verified primary keys
    SELECT entity_id INTO v_admin_id
    FROM _demo_setup_tracking
    WHERE entity_type = 'user' AND entity_key = c_admin_email;

    SELECT entity_id INTO v_student_id
    FROM _demo_setup_tracking
    WHERE entity_type = 'user' AND entity_key = c_student_email;

    SELECT entity_id INTO v_club_id
    FROM _demo_setup_tracking
    WHERE entity_type = 'club' AND entity_key = c_club_name;

    SELECT entity_id INTO v_event_id
    FROM _demo_setup_tracking
    WHERE entity_type = 'event' AND entity_key = c_event_name;

    -- Strictly extract registration matching c_reg_key and capture its creation timestamp
    SELECT entity_id, created_at INTO v_reg_id, v_trk_reg_created_at
    FROM _demo_setup_tracking
    WHERE entity_type = 'registration' AND entity_key = c_reg_key;

    IF v_admin_id IS NULL OR v_student_id IS NULL OR v_club_id IS NULL
       OR v_event_id IS NULL OR v_reg_id IS NULL THEN
        RAISE EXCEPTION 'SAFETY CHECK FAILED: Could not resolve all 5 tracked primary keys. Cleanup aborted.';
    END IF;

    -- -------------------------------------------------------------------------
    -- Step 4: Complete Identity & Relational Verification Against Intended Fixture
    -- -------------------------------------------------------------------------
    -- Verify Admin user identity, credentials & role in 'users' table
    SELECT COUNT(*) INTO v_vfy_admin_cnt
    FROM users
    WHERE user_id = v_admin_id AND email = c_admin_email AND name = c_admin_name
      AND role = c_admin_role AND password = c_admin_hash;

    -- Verify Student user identity, credentials & role in 'users' table
    SELECT COUNT(*) INTO v_vfy_student_cnt
    FROM users
    WHERE user_id = v_student_id AND email = c_student_email AND name = c_student_name
      AND role = c_student_role AND password = c_student_hash;

    -- Verify Club identity and description in 'clubs' table
    SELECT COUNT(*) INTO v_vfy_club_cnt
    FROM clubs
    WHERE club_id = v_club_id AND club_name = c_club_name AND description = c_club_desc;

    -- Verify Event identity, club link, and all seeded attributes in 'events' table
    SELECT COUNT(*) INTO v_vfy_event_cnt
    FROM events
    WHERE event_id = v_event_id AND club_id = v_club_id AND event_name = c_event_name
      AND description = c_event_desc AND category = c_event_cat AND event_date = c_event_date
      AND event_time = c_event_time AND venue = c_event_venue AND max_participants = c_event_max
      AND registration_deadline = c_event_deadline;

    -- Verify Registration identity, event & student link, status, and registration date
    -- Registration date must match the tracking creation timestamp date (created_at::DATE)
    SELECT COUNT(*) INTO v_vfy_reg_cnt
    FROM registrations
    WHERE registration_id = v_reg_id AND event_id = v_event_id AND student_id = v_student_id
      AND status = c_reg_status AND registration_date = (v_trk_reg_created_at::DATE);

    IF v_vfy_admin_cnt <> 1 OR v_vfy_student_cnt <> 1 OR v_vfy_club_cnt <> 1
       OR v_vfy_event_cnt <> 1 OR v_vfy_reg_cnt <> 1 THEN
        RAISE EXCEPTION 'IDENTITY VERIFICATION FAILED: Tracked IDs do not match expected demo entity identities, credentials, or attributes (Admin: %/1, Student: %/1, Club: %/1, Event: %/1, Reg: %/1). Cleanup aborted to prevent deleting modified records. Tracking table preserved.',
            v_vfy_admin_cnt, v_vfy_student_cnt, v_vfy_club_cnt, v_vfy_event_cnt, v_vfy_reg_cnt;
    END IF;

    -- -------------------------------------------------------------------------
    -- Step 5: Pre-Inspection for Dependent Records (Foreign Key Protection)
    -- -------------------------------------------------------------------------
    -- Check attendance table
    SELECT COUNT(*) INTO v_dep_attendance
    FROM attendance
    WHERE event_id = v_event_id OR student_id IN (v_student_id, v_admin_id);

    -- Check feedback table
    SELECT COUNT(*) INTO v_dep_feedback
    FROM feedback
    WHERE event_id = v_event_id OR student_id IN (v_student_id, v_admin_id);

    -- Check certificates table
    SELECT COUNT(*) INTO v_dep_certificates
    FROM certificates
    WHERE event_id = v_event_id OR student_id IN (v_student_id, v_admin_id);

    -- Check additional untracked registrations
    SELECT COUNT(*) INTO v_dep_extra_regs
    FROM registrations
    WHERE (event_id = v_event_id OR student_id IN (v_student_id, v_admin_id))
      AND registration_id <> v_reg_id;

    -- Check additional untracked events for the tracked club
    SELECT COUNT(*) INTO v_dep_extra_events
    FROM events
    WHERE club_id = v_club_id AND event_id <> v_event_id;

    IF v_dep_attendance > 0 OR v_dep_feedback > 0 OR v_dep_certificates > 0
       OR v_dep_extra_regs > 0 OR v_dep_extra_events > 0 THEN
        RAISE EXCEPTION 'CLEANUP SAFETY ABORT: Found unexpected dependent records created during testing (attendance: %, feedback: %, certificates: %, additional registrations: %, additional events: %). Schema constraints (ON DELETE RESTRICT) prevent deletion. Cleanup aborted to protect database integrity. Zero records deleted; tracking table preserved.',
            v_dep_attendance, v_dep_feedback, v_dep_certificates, v_dep_extra_regs, v_dep_extra_events;
    END IF;

    -- -------------------------------------------------------------------------
    -- Step 6: Execute Deletions in Strict Foreign-Key-Safe Topological Order
    -- -------------------------------------------------------------------------
    -- 6a. Delete Leaf Registration
    DELETE FROM registrations WHERE registration_id = v_reg_id;
    GET DIAGNOSTICS v_deleted_regs = ROW_COUNT;

    -- 6b. Delete Intermediate Event
    DELETE FROM events WHERE event_id = v_event_id;
    GET DIAGNOSTICS v_deleted_events = ROW_COUNT;

    -- 6c. Delete Root Club
    DELETE FROM clubs WHERE club_id = v_club_id;
    GET DIAGNOSTICS v_deleted_clubs = ROW_COUNT;

    -- 6d. Delete Root Users (Admin & Student)
    DELETE FROM users WHERE user_id IN (v_admin_id, v_student_id);
    GET DIAGNOSTICS v_deleted_users = ROW_COUNT;

    -- -------------------------------------------------------------------------
    -- Step 7: Post-Deletion Verification
    -- -------------------------------------------------------------------------
    IF v_deleted_regs <> 1 OR v_deleted_events <> 1 OR v_deleted_clubs <> 1 OR v_deleted_users <> 2 THEN
        RAISE EXCEPTION 'POST-DELETION VERIFICATION FAILURE: Expected 1 registration, 1 event, 1 club, 2 users deleted; got regs=%, events=%, clubs=%, users=%. Transaction rolled back.',
            v_deleted_regs, v_deleted_events, v_deleted_clubs, v_deleted_users;
    END IF;

    -- -------------------------------------------------------------------------
    -- Step 8: Drop Tracking Table ONLY After Successful Verified Deletion
    -- -------------------------------------------------------------------------
    DROP TABLE _demo_setup_tracking;

    RAISE NOTICE '[DEMO CLEANUP] Safely purged demo fixture: % registration, % event, % club, % users. Tracking table removed.',
        v_deleted_regs, v_deleted_events, v_deleted_clubs, v_deleted_users;
END $$;
