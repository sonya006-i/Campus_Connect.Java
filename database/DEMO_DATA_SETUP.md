# Campus Connect — Demo Data Setup & Safety Guide ◈

**Target Platform:** Supabase PostgreSQL
**Phase:** 23H — Final Seed and Cleanup Integrity Pass
**Scope:** Development & University Viva Demonstration Only

---

## 1. Live-Schema Verification Queries (Read-Only)

Before provisioning demo data, operators can execute these read-only inspection queries in the **Supabase SQL Editor** to verify that the seven core tables and their constraints match expectations. These queries do not assume `_demo_setup_tracking` exists.

### Query 1A: Inspect Columns, Types, Nullability & Defaults
```sql
SELECT
    table_name,
    ordinal_position,
    column_name,
    data_type,
    character_maximum_length,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name IN ('users', 'clubs', 'events', 'registrations', 'attendance', 'feedback', 'certificates')
ORDER BY table_name, ordinal_position;
```

### Query 1B: Inspect Primary Key & Unique Constraints
```sql
SELECT
    tc.table_name,
    tc.constraint_name,
    tc.constraint_type,
    kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
 AND tc.table_schema = kcu.table_schema
WHERE tc.table_schema = current_schema()
  AND tc.table_name IN ('users', 'clubs', 'events', 'registrations', 'attendance', 'feedback', 'certificates')
  AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
ORDER BY tc.table_name, tc.constraint_type, kcu.ordinal_position;
```

### Query 1C: Inspect Foreign Key Constraints & Referential Rules (ON DELETE RESTRICT)
```sql
SELECT
    tc.table_name AS referencing_table,
    kcu.column_name AS foreign_key_column,
    ccu.table_name AS referenced_table,
    ccu.column_name AS referenced_column,
    rc.update_rule,
    rc.delete_rule,
    tc.constraint_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
 AND tc.table_schema = kcu.table_schema
JOIN information_schema.referential_constraints rc
  ON tc.constraint_name = rc.constraint_name
 AND tc.table_schema = rc.constraint_schema
JOIN information_schema.constraint_column_usage ccu
  ON rc.unique_constraint_name = ccu.constraint_name
 AND rc.unique_constraint_schema = ccu.constraint_schema
WHERE tc.table_schema = current_schema()
  AND tc.table_name IN ('users', 'clubs', 'events', 'registrations', 'attendance', 'feedback', 'certificates')
ORDER BY tc.table_name, kcu.column_name;
```

### Query 1D: Inspect Check Constraints
```sql
SELECT
    tc.table_name,
    tc.constraint_name,
    cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON tc.constraint_name = cc.constraint_name
 AND tc.table_schema = cc.constraint_schema
WHERE tc.table_schema = current_schema()
  AND tc.table_name IN ('users', 'clubs', 'events', 'registrations', 'attendance', 'feedback', 'certificates')
ORDER BY tc.table_name, tc.constraint_name;
```

---

## 2. Transaction & Concurrency Safety Model

### Precise Transaction Behavior:
Both [`seed_minimal_safe.sql`](file:///home/sonya/Java_mini_ClubManagementSystem/database/seed_minimal_safe.sql) and [`seed_cleanup_safe.sql`](file:///home/sonya/Java_mini_ClubManagementSystem/database/seed_cleanup_safe.sql) execute inside single anonymous PL/pgSQL `DO $$ ... END $$;` blocks without `EXCEPTION WHEN` handlers:
- **Automatic Abort on Failure:** If any precondition check fails or a database exception is thrown, PostgreSQL places the current transaction into an aborted error state.
- **Rollback Requirement:** In interactive psql sessions or multi-statement clients, the operator must execute `ROLLBACK;`. An operator must **never** issue `COMMIT;` on an aborted transaction (PostgreSQL will reject it with a warning). GUI clients like Supabase SQL Editor roll back aborted transactions automatically.
- **Non-Transactional Sequences:** In PostgreSQL, `SERIAL` sequences (`nextval`) are monotonic and non-transactional. If an insert transaction rolls back, generated sequence values are discarded and never reused. Sequences will advance regardless of whether inserts commit. This reinforces why dynamic sequence resolution (`RETURNING`) is mandatory.

### Registration-Date Policy:
When the demo fixture is created, the student's registration is timestamped with `registration_date = CURRENT_DATE`, and the tracking table records the creation timestamp (`created_at TIMESTAMPTZ`).
- On a rerun, the script verifies that `registrations.registration_date = (created_at::DATE)`.
- During cleanup, identity verification also requires `registrations.registration_date = (created_at::DATE)`.
- If an external process or manual edit modifies the registration date, both rerun and cleanup fail closed and abort immediately.

### Concurrency Protection & Scope Limitations:
- **Advisory Transaction Lock:** Both scripts call `PERFORM pg_advisory_xact_lock(hashtext('campus_connect_demo_setup'));` at line 1. This serializes execution between concurrent invocations of the seed and cleanup scripts.
- **Scope Limits:** Advisory locks do **not** coordinate with the JavaFX desktop client or arbitrary SQL clients writing to the database. They do not lock tables against external `SELECT` queries.
- **Relational Defense Against Concurrent Writes:** If an external application concurrently inserts dependent records (e.g. marking attendance or submitting feedback) while cleanup runs, PostgreSQL's foreign key constraints (`ON DELETE RESTRICT`) will immediately reject deletion of the parent event or user, causing the entire cleanup transaction to roll back safely.

---

## 3. Minimal Demo Dataset Specification

| Entity | Primary Key / Natural Key | Role / Category | Expected Attributes |
|---|---|---|---|
| **Admin User** | `admin@campusconnect.edu` | `ADMIN` | `Dr. Alan Vance` (PBKDF2 hash; raw: `admin123`) |
| **Student User** | `sonya@student.edu` | `STUDENT` | `Sonya Blade` (PBKDF2 hash; raw: `student123`) |
| **Club** | `Tech & Coding Society` | Club Entity | Campus technology organization |
| **Event** | `Tech Hackathon 2026` | `Technology` | Assigned to `Tech & Coding Society`; date: `2026-10-20`; max: `200` |
| **Registration** | Sonya Blade -> Hackathon | `APPROVED` | Foreign keys to dynamic event and student; date: `CURRENT_DATE` |
| **Ownership Tracker** | `_demo_setup_tracking` | Metadata Table | Exactly 5 records with exact composite UNIQUE on `(entity_type, entity_id)` and CHECK on `entity_type` |

---

## 4. Operator Runbook

### Phase 1: Pre-Flight Verification
Run the Live-Schema queries (Section 1) in the Supabase SQL Editor. Confirm that:
1. Core tables exist (`users`, `clubs`, `events`, `registrations`, `attendance`, `feedback`, `certificates`).
2. Foreign keys on `attendance`, `feedback`, and `certificates` reference `events` and `users` with `ON DELETE RESTRICT`.

To inspect pre-existing records prior to seeding without assuming `_demo_setup_tracking` exists:
```sql
SELECT 'Pre-seed Admin Check' AS check_name, COUNT(*) AS matches FROM users WHERE email = 'admin@campusconnect.edu'
UNION ALL
SELECT 'Pre-seed Student Check', COUNT(*) FROM users WHERE email = 'sonya@student.edu'
UNION ALL
SELECT 'Pre-seed Club Check', COUNT(*) FROM clubs WHERE club_name = 'Tech & Coding Society'
UNION ALL
SELECT 'Pre-seed Event Check', COUNT(*) FROM events WHERE event_name = 'Tech Hackathon 2026';
```

### Phase 2: Execute Minimal Seed Script
1. Open the Supabase SQL Editor.
2. Paste the contents of [`database/seed_minimal_safe.sql`](file:///home/sonya/Java_mini_ClubManagementSystem/database/seed_minimal_safe.sql) and click **Run**.
3. Verify expected output notice:
   ```text
   NOTICE: [DEMO SEED] Successfully provisioned minimal demo dataset (Admin ID ..., Student ID ..., Club ID ..., Event ID ..., Registration ID ...). Exactly 5 tracking records confirmed.
   ```
4. **Rerun Behavior:** If re-executed against an intact fixture, the script outputs:
   ```text
   NOTICE: [DEMO SEED] Verified clean rerun: All 5 minimal demo entities and their corresponding _demo_setup_tracking records already exist with identical signatures and relationships. Zero mutations performed.
   ```
   If any field, hash, date, or relationship has been modified, it aborts with `PRECONDITION COLLISION DETECTED`.

### Phase 3: Post-Seed Read-Only Verification
*(Run these queries ONLY after `database/seed_minimal_safe.sql` has successfully executed)*

#### Verification Query 3A: Independent Entity Counts
*Validates that every single entity exists independently without relying on joins that could conceal missing records:*
```sql
SELECT
    '1. Admin User' AS entity,
    COUNT(*) AS record_count,
    CASE WHEN COUNT(*) = 1 THEN 'VALID' ELSE 'MISSING / DUPLICATE' END AS status
FROM users WHERE email = 'admin@campusconnect.edu' AND role = 'ADMIN'
UNION ALL
SELECT
    '2. Student User',
    COUNT(*),
    CASE WHEN COUNT(*) = 1 THEN 'VALID' ELSE 'MISSING / DUPLICATE' END
FROM users WHERE email = 'sonya@student.edu' AND role = 'STUDENT'
UNION ALL
SELECT
    '3. Club',
    COUNT(*),
    CASE WHEN COUNT(*) = 1 THEN 'VALID' ELSE 'MISSING / DUPLICATE' END
FROM clubs WHERE club_name = 'Tech & Coding Society'
UNION ALL
SELECT
    '4. Event',
    COUNT(*),
    CASE WHEN COUNT(*) = 1 THEN 'VALID' ELSE 'MISSING / DUPLICATE' END
FROM events WHERE event_name = 'Tech Hackathon 2026'
UNION ALL
SELECT
    '5. Registration',
    COUNT(*),
    CASE WHEN COUNT(*) = 1 THEN 'VALID' ELSE 'MISSING / DUPLICATE' END
FROM registrations r
JOIN events e ON r.event_id = e.event_id
JOIN users u ON r.student_id = u.user_id
WHERE e.event_name = 'Tech Hackathon 2026' AND u.email = 'sonya@student.edu'
UNION ALL
SELECT
    '6. Tracking Entries',
    COUNT(*),
    CASE WHEN COUNT(*) = 5 THEN 'VALID' ELSE 'MISCONFIGURED' END
FROM _demo_setup_tracking;
```

#### Verification Query 3B: Full Relational Graph Inspection
*Inspects tracked primary keys and verifies each relational link:*
```sql
SELECT
    t.tracking_id,
    t.entity_type,
    t.entity_id,
    t.entity_key,
    CASE
        WHEN t.entity_type = 'user' THEN u.name || ' (' || u.email || ')'
        WHEN t.entity_type = 'club' THEN c.club_name
        WHEN t.entity_type = 'event' THEN e.event_name
        WHEN t.entity_type = 'registration' THEN 'Status: ' || r.status
    END AS entity_details,
    CASE
        WHEN t.entity_type = 'event' THEN 'Linked to Club ID: ' || e.club_id
        WHEN t.entity_type = 'registration' THEN 'Event ID: ' || r.event_id || ', Student ID: ' || r.student_id
        ELSE 'Root Entity'
    END AS relational_link
FROM _demo_setup_tracking t
LEFT JOIN users u ON t.entity_type = 'user' AND t.entity_id = u.user_id
LEFT JOIN clubs c ON t.entity_type = 'club' AND t.entity_id = c.club_id
LEFT JOIN events e ON t.entity_type = 'event' AND t.entity_id = e.event_id
LEFT JOIN registrations r ON t.entity_type = 'registration' AND t.entity_id = r.registration_id
ORDER BY t.tracking_id ASC;
```

### Phase 4: Application Testing
Launch the desktop client:
```bash
/usr/lib/netbeans/java/maven/bin/mvn javafx:run
```
- Click the **Student Demo Pill** (`sonya@student.edu` / `student123`). Confirm dashboard loads showing 1 approved registration.
- Click the **Admin Demo Pill** (`admin@campusconnect.edu` / `admin123`). Confirm admin moderation tools load.

### Phase 5: Safe Cleanup Procedure
1. Open the Supabase SQL Editor.
2. Paste the contents of [`database/seed_cleanup_safe.sql`](file:///home/sonya/Java_mini_ClubManagementSystem/database/seed_cleanup_safe.sql) and click **Run**.
3. **Safety Protection:** If you generated certificates, marked attendance, or submitted feedback in JavaFX, the cleanup script **aborts immediately** with `CLEANUP SAFETY ABORT`, reporting the exact count of dependent records.
4. If zero dependent records exist, output will confirm:
   ```text
   NOTICE: [DEMO CLEANUP] Safely purged demo fixture: 1 registration, 1 event, 1 club, 2 users. Tracking table removed.
   ```
5. Confirm complete removal:
   ```sql
   SELECT COUNT(*) FROM users WHERE email IN ('admin@campusconnect.edu', 'sonya@student.edu');
   ```
   Must return `0`.

---

## 5. Remaining Limitations & Assumptions

1. **Non-Transactional Sequence Increments:** If the seed script aborts due to a collision or validation failure after generating an ID, the consumed sequence values in PostgreSQL cannot be reclaimed. This is standard database engine behavior.
2. **Interactive UI Testing Blocks:** Any row inserted during JavaFX manual testing into `attendance`, `feedback`, or `certificates` will block cleanup under `ON DELETE RESTRICT`. Those test rows must be reviewed and removed before cleanup can proceed.
3. **Tracking Table Dependency:** If an operator manually drops `_demo_setup_tracking` out-of-band while demo records remain in the core tables, the cleanup script will refuse to delete any records, requiring manual investigation.
