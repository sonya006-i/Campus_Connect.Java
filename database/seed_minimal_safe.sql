DO $$
DECLARE
    -- =============================================================================
    -- CAMPUS CONNECT — Hardened Minimal Demo Seed Data (Phase 23H)
    -- Target Platform: Supabase PostgreSQL
    -- =============================================================================
    --
    -- RIGOROUS SAFETY, OWNERSHIP & CONCURRENCY PROTOCOL:
    -- 1. Advisory Concurrency Lock: Obtains an exclusive transaction advisory lock
    --    (pg_advisory_xact_lock) on the demo setup identifier to serialize concurrent
    --    seed or cleanup script executions.
    -- 2. Full Transaction Atomicity: Runs inside a single atomic DO $$ ... END $$ block.
    --    Any constraint violation or raised exception immediately rolls back all
    --    table creations, inserts, and tracking writes. Zero exceptions are suppressed.
    -- 3. Strict Tracking Table Validation: Deeply inspects '_demo_setup_tracking' schema,
    --    verifying exactly 5 columns, types, required NOT NULL flags, the exact composite
    --    UNIQUE constraint on (entity_type, entity_id), and the explicit CHECK constraint
    --    on allowed entity_type values. Incompatible pre-existing tables cause an abort
    --    without alteration.
    -- 4. Ambiguity Detection: Prohibits 'LIMIT 1' on non-unique entity queries. If duplicate
    --    matching clubs or events exist, execution halts on ambiguity.
    -- 5. Deterministic Registration-Date Policy: Registration date is inserted as CURRENT_DATE,
    --    and rerun verification requires registration_date to match the date recorded in
    --    the tracking metadata when the fixture was created (created_at::DATE).
    -- 6. Complete Rerun Validation: Rerun is permitted as a no-op ONLY if the tracking
    --    table contains exactly 5 records (1 admin, 1 student, 1 club, 1 event, 1 registration),
    --    with zero extra rows, and every entity strictly matches all expected demo identities,
    --    descriptions, dates, times, venues, capacities, deadlines, hashes, and relationships.
    -- 7. Dynamic Key Resolution: Serial IDs are captured dynamically via RETURNING INTO;
    --    zero hardcoded primary or foreign key integers.
    -- =============================================================================

    -- Advisory lock key for Campus Connect Demo Setup
    c_lock_key       CONSTANT BIGINT := hashtext('campus_connect_demo_setup');

    -- Expected Target Demo User Identities
    c_admin_email    CONSTANT VARCHAR(150) := 'admin@campusconnect.edu';
    c_admin_name     CONSTANT VARCHAR(100) := 'Dr. Alan Vance';
    c_admin_role     CONSTANT VARCHAR(20)  := 'ADMIN';
    c_admin_hash     CONSTANT VARCHAR(255) := 'PBKDF2WithHmacSHA256:65536:3RI9ViNSrqZ9eC3ehkOKLg==:BhWcuWUdSPBM9LWZkGI8LcYo/Q02XrpFGyeWtIPCFpU=';

    c_student_email  CONSTANT VARCHAR(150) := 'sonya@student.edu';
    c_student_name   CONSTANT VARCHAR(100) := 'Sonya Blade';
    c_student_role   CONSTANT VARCHAR(20)  := 'STUDENT';
    c_student_hash   CONSTANT VARCHAR(255) := 'PBKDF2WithHmacSHA256:65536:0g4RychnnRlzfdDYThZ72Q==:SVM4bXqgxjuYf8IN+5fGbk9l52YqevZiByQBTSSOIHw=';

    -- Expected Target Demo Club Identity & Description
    c_club_name      CONSTANT VARCHAR(100) := 'Tech & Coding Society';
    c_club_desc      CONSTANT TEXT         := 'Fostering innovation in software engineering, competitive programming, and emerging technology stacks.';

    -- Expected Target Demo Event Attributes
    c_event_name     CONSTANT VARCHAR(150) := 'Tech Hackathon 2026';
    c_event_desc     CONSTANT TEXT         := 'A 24-hour campus hackathon solving university and environmental sustainability challenges with high cash prizes.';
    c_event_cat      CONSTANT VARCHAR(50)  := 'Technology';
    c_event_date     CONSTANT DATE         := '2026-10-20'::DATE;
    c_event_time     CONSTANT TIME         := '09:00:00'::TIME;
    c_event_venue    CONSTANT VARCHAR(150) := 'Main Auditorium & Innovation Hall';
    c_event_max      CONSTANT INT          := 200;
    c_event_deadline CONSTANT DATE         := '2026-10-18'::DATE;

    -- Expected Registration Attributes
    c_reg_status     CONSTANT VARCHAR(20)  := 'APPROVED';
    c_reg_key        CONSTANT VARCHAR(150) := 'sonya@student.edu -> Tech Hackathon 2026';

    -- Variables for Pre-existence & Ambiguity Checks
    v_user_admin_cnt        INT := 0;
    v_user_student_cnt      INT := 0;
    v_club_match_cnt        INT := 0;
    v_event_match_cnt       INT := 0;
    v_reg_match_cnt         INT := 0;

    v_existing_admin_id     INT;
    v_existing_admin_name   VARCHAR(100);
    v_existing_admin_role   VARCHAR(20);
    v_existing_admin_pass   VARCHAR(255);

    v_existing_student_id   INT;
    v_existing_student_name VARCHAR(100);
    v_existing_student_role VARCHAR(20);
    v_existing_student_pass VARCHAR(255);

    v_existing_club_id      INT;
    v_existing_club_desc    TEXT;

    v_existing_event_id       INT;
    v_existing_event_club     INT;
    v_existing_event_desc     TEXT;
    v_existing_event_cat      VARCHAR(50);
    v_existing_event_date     DATE;
    v_existing_event_time     TIME;
    v_existing_event_venue    VARCHAR(150);
    v_existing_event_max      INT;
    v_existing_event_deadline DATE;

    v_existing_reg_id       INT;
    v_existing_reg_event    INT;
    v_existing_reg_student  INT;
    v_existing_reg_status   VARCHAR(20);
    v_existing_reg_date     DATE;

    -- Tracking Table Inspection Variables
    v_tracking_exists        BOOLEAN := FALSE;
    v_tracking_total_cols    INT := 0;
    v_tracking_col_count     INT := 0;
    v_tracking_has_exact_uq  BOOLEAN := FALSE;
    v_tracking_has_check     BOOLEAN := FALSE;
    v_tracking_row_count     INT := 0;

    -- Specific tracking entity counts for rerun verification
    v_trk_total              INT := 0;
    v_trk_admin_cnt          INT := 0;
    v_trk_student_cnt        INT := 0;
    v_trk_club_cnt           INT := 0;
    v_trk_event_cnt          INT := 0;
    v_trk_reg_cnt            INT := 0;
    v_trk_reg_created_at     TIMESTAMPTZ;

    -- Variables for Newly Inserted IDs
    v_admin_id       INT;
    v_student_id     INT;
    v_club_id        INT;
    v_event_id       INT;
    v_reg_id         INT;

    v_collision_details TEXT := '';
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

    IF v_tracking_exists THEN
        -- Verify total column count is exactly 5
        SELECT COUNT(*) INTO v_tracking_total_cols
        FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = '_demo_setup_tracking';

        IF v_tracking_total_cols <> 5 THEN
            RAISE EXCEPTION 'PRECONDITION ERROR: Table ''_demo_setup_tracking'' exists with % columns (expected exactly 5). Aborting without altering table.', v_tracking_total_cols;
        END IF;

        -- Verify expected column names, compatible data types, and required NOT NULL constraints
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
            RAISE EXCEPTION 'PRECONDITION ERROR: Table ''_demo_setup_tracking'' has incompatible column definitions or nullability (%/5 valid). Aborting without altering table.', v_tracking_col_count;
        END IF;

        -- Verify that a UNIQUE constraint specifically covers (entity_type, entity_id) in that order
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
            RAISE EXCEPTION 'PRECONDITION ERROR: Table ''_demo_setup_tracking'' exists but lacks the exact composite UNIQUE constraint on (entity_type, entity_id). Aborting without altering table.';
        END IF;

        -- Verify that an explicit CHECK constraint exists on entity_type restricting allowed values
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
            RAISE EXCEPTION 'PRECONDITION ERROR: Table ''_demo_setup_tracking'' exists but lacks the required CHECK constraint on entity_type. Aborting without altering table.';
        END IF;

        SELECT COUNT(*) INTO v_tracking_row_count FROM _demo_setup_tracking;
    END IF;

    -- -------------------------------------------------------------------------
    -- Step 3: Inspect Pre-existing Records in Target Database with Ambiguity Checks
    -- -------------------------------------------------------------------------
    -- 3a. Admin user inspection
    SELECT COUNT(*) INTO v_user_admin_cnt FROM users WHERE email = c_admin_email;
    IF v_user_admin_cnt = 1 THEN
        SELECT user_id, name, role, password
        INTO v_existing_admin_id, v_existing_admin_name, v_existing_admin_role, v_existing_admin_pass
        FROM users WHERE email = c_admin_email;
    END IF;

    -- 3b. Student user inspection
    SELECT COUNT(*) INTO v_user_student_cnt FROM users WHERE email = c_student_email;
    IF v_user_student_cnt = 1 THEN
        SELECT user_id, name, role, password
        INTO v_existing_student_id, v_existing_student_name, v_existing_student_role, v_existing_student_pass
        FROM users WHERE email = c_student_email;
    END IF;

    -- 3c. Club inspection with ambiguity detection (NO LIMIT 1)
    SELECT COUNT(*) INTO v_club_match_cnt FROM clubs WHERE club_name = c_club_name;
    IF v_club_match_cnt > 1 THEN
        RAISE EXCEPTION 'AMBIGUOUS PRE-EXISTING RECORDS: Found % clubs sharing the name ''%''. Aborting to avoid non-deterministic binding.',
            v_club_match_cnt, c_club_name;
    ELSIF v_club_match_cnt = 1 THEN
        SELECT club_id, description
        INTO v_existing_club_id, v_existing_club_desc
        FROM clubs WHERE club_name = c_club_name;
    END IF;

    -- 3d. Event inspection with ambiguity detection (NO LIMIT 1)
    SELECT COUNT(*) INTO v_event_match_cnt FROM events WHERE event_name = c_event_name;
    IF v_event_match_cnt > 1 THEN
        RAISE EXCEPTION 'AMBIGUOUS PRE-EXISTING RECORDS: Found % events sharing the name ''%''. Aborting to avoid non-deterministic binding.',
            v_event_match_cnt, c_event_name;
    ELSIF v_event_match_cnt = 1 THEN
        SELECT event_id, club_id, description, category, event_date, event_time, venue, max_participants, registration_deadline
        INTO v_existing_event_id, v_existing_event_club, v_existing_event_desc, v_existing_event_cat, v_existing_event_date,
             v_existing_event_time, v_existing_event_venue, v_existing_event_max, v_existing_event_deadline
        FROM events WHERE event_name = c_event_name;
    END IF;

    -- 3e. Registration inspection
    IF v_existing_event_id IS NOT NULL AND v_existing_student_id IS NOT NULL THEN
        SELECT COUNT(*) INTO v_reg_match_cnt
        FROM registrations
        WHERE event_id = v_existing_event_id AND student_id = v_existing_student_id;

        IF v_reg_match_cnt = 1 THEN
            SELECT registration_id, event_id, student_id, status, registration_date
            INTO v_existing_reg_id, v_existing_reg_event, v_existing_reg_student, v_existing_reg_status, v_existing_reg_date
            FROM registrations
            WHERE event_id = v_existing_event_id AND student_id = v_existing_student_id;
        END IF;
    END IF;

    -- -------------------------------------------------------------------------
    -- Step 4: Complete Rerun Validation vs. Collision Detection
    -- -------------------------------------------------------------------------
    -- Evaluate clean rerun ONLY if all entities exist and tracking table exists with exactly 5 rows
    IF v_existing_admin_id IS NOT NULL AND v_existing_student_id IS NOT NULL
       AND v_existing_club_id IS NOT NULL AND v_existing_event_id IS NOT NULL
       AND v_existing_reg_id IS NOT NULL AND v_tracking_exists AND v_tracking_row_count = 5 THEN

        -- Verify tracking breakdown has exactly 1 of each expected identity and 0 unexpected rows
        SELECT
            COUNT(*),
            COUNT(*) FILTER (WHERE entity_type = 'user' AND entity_id = v_existing_admin_id AND entity_key = c_admin_email),
            COUNT(*) FILTER (WHERE entity_type = 'user' AND entity_id = v_existing_student_id AND entity_key = c_student_email),
            COUNT(*) FILTER (WHERE entity_type = 'club' AND entity_id = v_existing_club_id AND entity_key = c_club_name),
            COUNT(*) FILTER (WHERE entity_type = 'event' AND entity_id = v_existing_event_id AND entity_key = c_event_name),
            COUNT(*) FILTER (WHERE entity_type = 'registration' AND entity_id = v_existing_reg_id AND entity_key = c_reg_key)
        INTO
            v_trk_total,
            v_trk_admin_cnt,
            v_trk_student_cnt,
            v_trk_club_cnt,
            v_trk_event_cnt,
            v_trk_reg_cnt
        FROM _demo_setup_tracking;

        -- Extract the tracking creation timestamp for deterministic registration-date policy
        SELECT created_at INTO v_trk_reg_created_at
        FROM _demo_setup_tracking
        WHERE entity_type = 'registration' AND entity_id = v_existing_reg_id AND entity_key = c_reg_key;

        -- Verify EVERY relevant field, credential, registration-date, and relationship inserted by the seed
        IF v_trk_total = 5 AND v_trk_admin_cnt = 1 AND v_trk_student_cnt = 1
           AND v_trk_club_cnt = 1 AND v_trk_event_cnt = 1 AND v_trk_reg_cnt = 1
           AND v_existing_admin_role = c_admin_role
           AND v_existing_admin_name = c_admin_name
           AND v_existing_admin_pass = c_admin_hash
           AND v_existing_student_role = c_student_role
           AND v_existing_student_name = c_student_name
           AND v_existing_student_pass = c_student_hash
           AND v_existing_club_desc = c_club_desc
           AND v_existing_event_club = v_existing_club_id
           AND v_existing_event_desc = c_event_desc
           AND v_existing_event_cat = c_event_cat
           AND v_existing_event_date = c_event_date
           AND v_existing_event_time = c_event_time
           AND v_existing_event_venue = c_event_venue
           AND v_existing_event_max = c_event_max
           AND v_existing_event_deadline = c_event_deadline
           AND v_existing_reg_event = v_existing_event_id
           AND v_existing_reg_student = v_existing_student_id
           AND v_existing_reg_status = c_reg_status
           AND v_existing_reg_date = (v_trk_reg_created_at::DATE) THEN

            RAISE NOTICE '[DEMO SEED] Verified clean rerun: All 5 minimal demo entities and their corresponding _demo_setup_tracking records already exist with identical signatures and relationships. Zero mutations performed.';
            RETURN;
        END IF;
    END IF;

    -- If not an exact verified rerun, ANY existing entity is a collision risk
    IF v_existing_admin_id IS NOT NULL THEN
        v_collision_details := v_collision_details || ' Admin email already exists (user_id=' || v_existing_admin_id || ').';
    END IF;
    IF v_existing_student_id IS NOT NULL THEN
        v_collision_details := v_collision_details || ' Student email already exists (user_id=' || v_existing_student_id || ').';
    END IF;
    IF v_club_match_cnt > 0 THEN
        v_collision_details := v_collision_details || ' Club name already exists (' || v_club_match_cnt || ' matches).';
    END IF;
    IF v_event_match_cnt > 0 THEN
        v_collision_details := v_collision_details || ' Event name already exists (' || v_event_match_cnt || ' matches).';
    END IF;
    IF v_tracking_exists AND v_tracking_row_count > 0 THEN
        v_collision_details := v_collision_details || ' Tracking table ''_demo_setup_tracking'' contains ' || v_tracking_row_count || ' pre-existing rows.';
    END IF;

    IF v_collision_details <> '' THEN
        RAISE EXCEPTION 'PRECONDITION COLLISION DETECTED: Target demo records already exist in a partial, ambiguous, or mismatched state:% Setup halted; transaction rolled back. Manual review required.', v_collision_details;
    END IF;

    -- -------------------------------------------------------------------------
    -- Step 5: Ensure Tracking Table Exists with Exact Schema & Constraints
    -- -------------------------------------------------------------------------
    IF NOT v_tracking_exists THEN
        CREATE TABLE _demo_setup_tracking (
            tracking_id SERIAL PRIMARY KEY,
            entity_type VARCHAR(50) NOT NULL,
            entity_id   INT NOT NULL,
            entity_key  VARCHAR(150) NOT NULL,
            created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT uq_demo_tracking_type_id UNIQUE (entity_type, entity_id),
            CONSTRAINT chk_demo_tracking_type CHECK (entity_type IN ('user', 'club', 'event', 'registration'))
        );
    END IF;

    -- -------------------------------------------------------------------------
    -- Step 6: Execute Safe Inserts and Resolve Generated IDs Dynamically
    -- -------------------------------------------------------------------------
    -- 6a. Admin User
    INSERT INTO users (name, email, password, role)
    VALUES (c_admin_name, c_admin_email, c_admin_hash, c_admin_role)
    RETURNING user_id INTO v_admin_id;

    INSERT INTO _demo_setup_tracking (entity_type, entity_id, entity_key)
    VALUES ('user', v_admin_id, c_admin_email);

    -- 6b. Student User
    INSERT INTO users (name, email, password, role)
    VALUES (c_student_name, c_student_email, c_student_hash, c_student_role)
    RETURNING user_id INTO v_student_id;

    INSERT INTO _demo_setup_tracking (entity_type, entity_id, entity_key)
    VALUES ('user', v_student_id, c_student_email);

    -- 6c. Club
    INSERT INTO clubs (club_name, description)
    VALUES (c_club_name, c_club_desc)
    RETURNING club_id INTO v_club_id;

    INSERT INTO _demo_setup_tracking (entity_type, entity_id, entity_key)
    VALUES ('club', v_club_id, c_club_name);

    -- 6d. Event (Referencing dynamically acquired v_club_id)
    INSERT INTO events (
        club_id, event_name, description, category,
        event_date, event_time, venue, max_participants, registration_deadline
    )
    VALUES (
        v_club_id,
        c_event_name,
        c_event_desc,
        c_event_cat,
        c_event_date,
        c_event_time,
        c_event_venue,
        c_event_max,
        c_event_deadline
    )
    RETURNING event_id INTO v_event_id;

    INSERT INTO _demo_setup_tracking (entity_type, entity_id, entity_key)
    VALUES ('event', v_event_id, c_event_name);

    -- 6e. Registration (Referencing dynamically acquired v_event_id and v_student_id)
    -- Registration date is set to CURRENT_DATE, which matches the created_at timestamp in the tracking table
    INSERT INTO registrations (event_id, student_id, status, registration_date)
    VALUES (v_event_id, v_student_id, c_reg_status, CURRENT_DATE)
    RETURNING registration_id INTO v_reg_id;

    INSERT INTO _demo_setup_tracking (entity_type, entity_id, entity_key)
    VALUES ('registration', v_reg_id, c_reg_key);

    -- -------------------------------------------------------------------------
    -- Step 7: Post-Insert Tracking Verification
    -- -------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_tracking_row_count FROM _demo_setup_tracking;
    IF v_tracking_row_count <> 5 THEN
        RAISE EXCEPTION 'POST-INSERT INTEGRITY FAILURE: Expected exactly 5 tracking records, but found %. Transaction rolled back.', v_tracking_row_count;
    END IF;

    RAISE NOTICE '[DEMO SEED] Successfully provisioned minimal demo dataset (Admin ID %, Student ID %, Club ID %, Event ID %, Registration ID %). Exactly 5 tracking records confirmed.',
        v_admin_id, v_student_id, v_club_id, v_event_id, v_reg_id;
END $$;
