# Supabase Setup & Schema Deployment Checklist ◈
## Campus Connect — Campus Event & Club Management System

**Target Database:** Supabase PostgreSQL (PostgreSQL 15 / 16)
**Schema Definition:** [`database/schema_postgres.sql`](file:///home/sonya/Java_mini_ClubManagementSystem/database/schema_postgres.sql) (Non-destructive, idempotent DDL)
**Optional Seed Data:** [`database/seed.sql`](file:///home/sonya/Java_mini_ClubManagementSystem/database/seed.sql) (Manual, optional demo data only)
**Security Rule:** Zero passwords in version control, logs, scripts, or screenshots.

---

## 1. Target Verification & Pre-Deployment Inspection

Before executing any script in the Supabase Dashboard, perform these mandatory verification steps:

### Step 1.1: Verify the Target Project
1. Log in to the [Supabase Dashboard](https://supabase.com/dashboard).
2. Confirm the active project matches your Campus Connect deployment:
   - **Project Name:** Campus Connect (or your designated project name).
   - **Project Reference ID:** Verify the project reference matches your session pooler host (e.g., `ijcsjfikzkwwhcsfjawh`).
   - **Region:** Verify region matches your connection pooler (e.g., `ap-northeast-2` Tokyo/Seoul).

> [!WARNING]
> **Target Project Verification:**
> The schema script is intended only for a verified, appropriate target database. Never run schema scripts against an unverified project, a shared workspace, or an unrelated production database.

### Step 1.2: Inspect Existing Database Tables
Open the **SQL Editor** (`>_` terminal icon on the left sidebar) and click **+ New query**. Run the inspection query below:

```sql
-- Inspect runtime environment
SELECT current_database(), current_schema(), version();

-- Inspect existing tables in the public schema
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
```

- **If public has no user tables:** The database is fresh and ready for schema deployment.
- **If public already contains tables:** Review the schema idempotency caveats in Section 3.1 before proceeding.

---

## 2. Locating the SQL Editor in Supabase

1. Navigate to your project on [https://supabase.com/dashboard](https://supabase.com/dashboard).
2. In the navigation sidebar on the left, locate the **SQL Editor** (icon of a terminal `>_`).
3. Click the **"+ New query"** button to open an empty query buffer.
4. Name the query tab: `CampusConnect_Schema_Deployment`.

---

## 3. Schema Review & Deployment Procedure

### Step 3.1: Non-Destructive Schema Policy & Idempotency Caveats
[`database/schema_postgres.sql`](file:///home/sonya/Java_mini_ClubManagementSystem/database/schema_postgres.sql) is designed with strict safety policies:
1. **Zero Destructive Operations:** The script contains **NO** `DROP TABLE`, `TRUNCATE`, or `DELETE` statements. It will never drop existing tables or delete existing rows.
2. **Safe Rerunnability:** All DDL statements use `CREATE TABLE IF NOT EXISTS` and `CREATE INDEX IF NOT EXISTS`.

> [!IMPORTANT]
> **Limitations of `IF NOT EXISTS` on Existing Tables:**
> A successful rerun of `schema_postgres.sql` **does NOT guarantee** that existing table definitions match the intended Campus Connect schema.
> - If a table named `users` already exists from an earlier migration or another project, `CREATE TABLE IF NOT EXISTS users` will simply **skip** creation.
> - It does **NOT** alter columns, repair missing columns, add missing constraints, or validate column data types on existing tables.
> - Therefore, existing tables must always be inspected using the queries in Section 4 to ensure structural conformity.

### Step 3.2: Table Creation Order
The tables are ordered strictly according to foreign key dependencies:
1. `users` (independent parent)
2. `clubs` (independent parent)
3. `events` (references `clubs`)
4. `registrations` (references `events` and `users`)
5. `attendance` (references `events` and `users`)
6. `feedback` (references `events` and `users`)
7. `certificates` (references `events` and `users`)

### Step 3.3: Manual Execution in SQL Editor
1. Copy the full contents of [`database/schema_postgres.sql`](file:///home/sonya/Java_mini_ClubManagementSystem/database/schema_postgres.sql).
2. Paste into the SQL Editor query window.
3. Click the **Run** button (or press `Ctrl+Enter` / `Cmd+Enter`).
4. Verify the output displays: `Success. No rows returned`.

---

## 4. Post-Deployment Verification Queries

Run these verification queries in a new SQL Editor tab to confirm that all 7 tables and constraints match the required application specification:

### 4.1: Verify All 7 Tables (Identifies Any Missing Table)
```sql
SELECT
    expected.table_name,
    CASE
        WHEN actual.table_name IS NOT NULL THEN 'PRESENT'
        ELSE 'MISSING'
    END AS status
FROM (
    VALUES
        ('users'),
        ('clubs'),
        ('events'),
        ('registrations'),
        ('attendance'),
        ('feedback'),
        ('certificates')
) AS expected(table_name)
LEFT JOIN (
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = 'public'
) AS actual ON expected.table_name = actual.table_name
ORDER BY expected.table_name;
```
*Expected Result:* All 7 rows report status `PRESENT`. If any table was skipped or failed, it will explicitly report `MISSING`.

### 4.2: Verify Primary Keys
```sql
SELECT tc.table_name, kcu.column_name AS primary_key_column
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
 AND tc.table_schema = kcu.table_schema
WHERE tc.constraint_type = 'PRIMARY KEY'
  AND tc.table_schema = 'public'
ORDER BY tc.table_name;
```
*Expected Result:* 7 rows (`attendance_id`, `certificate_id`, `club_id`, `event_id`, `feedback_id`, `registration_id`, `user_id`).

### 4.3: Verify Foreign Key Relationships (Identifies Any Missing FK)
```sql
SELECT
    expected.constraint_name,
    expected.child_table,
    expected.parent_table,
    CASE
        WHEN actual.constraint_name IS NOT NULL THEN 'PRESENT'
        ELSE 'MISSING'
    END AS status
FROM (
    VALUES
        ('fk_events_club', 'events', 'clubs'),
        ('fk_reg_event', 'registrations', 'events'),
        ('fk_reg_student', 'registrations', 'users'),
        ('fk_att_event', 'attendance', 'events'),
        ('fk_att_student', 'attendance', 'users'),
        ('fk_fb_event', 'feedback', 'events'),
        ('fk_fb_student', 'feedback', 'users'),
        ('fk_cert_event', 'certificates', 'events'),
        ('fk_cert_student', 'certificates', 'users')
) AS expected(constraint_name, child_table, parent_table)
LEFT JOIN (
    SELECT tc.constraint_name, tc.table_name AS child_table, ccu.table_name AS parent_table
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_name = tc.constraint_name
     AND ccu.table_schema = tc.table_schema
    WHERE tc.constraint_type = 'FOREIGN KEY'
      AND tc.table_schema = 'public'
) AS actual
  ON expected.constraint_name = actual.constraint_name
 AND expected.child_table = actual.child_table
ORDER BY expected.child_table, expected.constraint_name;
```
*Expected Result:* All 9 foreign key constraints report status `PRESENT`.

### 4.4: Verify Unique Constraints & Check Constraints (Identifies Missing Constraints)
```sql
SELECT
    expected.constraint_name,
    expected.expected_table,
    expected.expected_type,
    CASE
        WHEN actual.constraint_name IS NOT NULL THEN 'PRESENT'
        ELSE 'MISSING'
    END AS status
FROM (
    VALUES
        ('uq_registration_event_student', 'registrations', 'UNIQUE'),
        ('uq_attendance_event_student', 'attendance', 'UNIQUE'),
        ('uq_feedback_event_student', 'feedback', 'UNIQUE'),
        ('uq_cert_event_student', 'certificates', 'UNIQUE'),
        ('chk_user_role', 'users', 'CHECK'),
        ('chk_reg_status', 'registrations', 'CHECK'),
        ('chk_feedback_rating', 'feedback', 'CHECK')
) AS expected(constraint_name, expected_table, expected_type)
LEFT JOIN (
    SELECT tc.constraint_name, tc.table_name, tc.constraint_type
    FROM information_schema.table_constraints tc
    WHERE tc.table_schema = 'public'
) AS actual
  ON expected.constraint_name = actual.constraint_name
 AND expected.expected_table = actual.table_name
ORDER BY expected.expected_table, expected.constraint_name;
```
*Expected Result:* All 7 key business constraints report status `PRESENT`.

---

## 5. Optional Seed Data Deployment

> [!CAUTION]
> **Seed Data Status: STRICTLY OPTIONAL & NEVER AUTOMATED**
> `database/seed.sql` must **never** be run automatically as part of schema setup. It is strictly optional for local demonstrations or testing.

### 5.1: Critical Seed Data Constraints
1. **Sequence Dependency:** `seed.sql` assumes auto-generated `SERIAL` sequences start at `1` (`users` 1–6, `clubs` 1–3, `events` 1–5). If data or test rows already exist, IDs will not match foreign key mappings.
2. **Non-Idempotent:** Running `seed.sql` more than once will throw unique constraint violations on `users.email` and `uq_registration_event_student`.
3. **Password Security:** All demo passwords in `seed.sql` are securely stored as salted PBKDF2 hashes (`PBKDF2WithHmacSHA256:65536:<salt>:<hash>`). Plaintext passwords are never inserted.

### 5.2: Applying Seed Data (Optional Step Only)
1. Open a new query tab in the Supabase SQL Editor.
2. Paste the contents of [`database/seed.sql`](file:///home/sonya/Java_mini_ClubManagementSystem/database/seed.sql).
3. Click **Run**.
4. Confirm user count:
   ```sql
   SELECT role, COUNT(*) FROM users GROUP BY role;
   ```
   *Expected:* 1 ADMIN (`admin@campusconnect.edu`), 5 STUDENT (`sonya@student.edu`, etc.).

---

## 6. Backups & Disaster Recovery Verification

> [!WARNING]
> **Backup & Recovery Protocol:**
> Before modifying any database containing valuable or production data, operators must establish a verified backup and recovery plan.

1. **No Undo in SQL Editor:** Executed DDL changes cannot be reverted with an undo command.
2. **Backups Prior to Modification:** Use the Supabase Dashboard **Database -> Backups** panel or execute `pg_dump` locally before altering schema structures on active databases:
   ```bash
   # Example logical backup via pg_dump (run with SSL required)
   pg_dump "postgres://<user>:<password>@<pooler-host>:5432/postgres?sslmode=require" > campus_connect_backup_$(date +%Y%m%d).sql
   ```
3. **Data Loss Prevention:** Never introduce `DROP TABLE` or `TRUNCATE` into standard migration or deployment pipelines.

---

## 7. Credential Hygiene & Security Directives

1. **Keep Secrets in Untracked Files:**
   - Store real database passwords strictly in the local untracked `.env` file (`chmod 600`).
   - Confirm git ignore status: `git check-ignore -v .env`.
   - Maintain `.env.example` as a template with placeholders only (`your_supabase_password_here`).
2. **No Credentials in Scripts or Queries:**
   - Do not paste connection strings with plaintext passwords into SQL Editor tabs or saved snippets.
   - Do not include database passwords in commit messages, documentation, or issue trackers.
3. **Screenshots & Viva Presentations:**
   - Blur or crop project reference IDs, database usernames, and connection pooler credentials in UI screenshots or university presentation slides.
