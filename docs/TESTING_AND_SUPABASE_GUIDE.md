# Automated Testing & Supabase PostgreSQL Integration Guide

Campus Connect — University Advanced Java Desktop Application
Target Database: **Supabase PostgreSQL 15+**
Test Engine: **JUnit 5.10.2 + Maven Surefire 3.2.5**

---

## 1. Migration Status & Audit Findings

During the Phase 19A audit, the codebase and database configurations were evaluated for PostgreSQL/Supabase compatibility:

1. **Driver & Connectivity:**
   - PostgreSQL JDBC Driver (`org.postgresql:postgresql:42.7.3`) is registered in `pom.xml`.
   - `DBConnection.java` detects PostgreSQL URLs (`jdbc:postgresql://...`) dynamically and loads `org.postgresql.Driver`.
2. **Schema Compatibility:**
   - Original `database/schema.sql` was written with MySQL DDL (`AUTO_INCREMENT`, `ENGINE=InnoDB`).
   - A dedicated PostgreSQL/Supabase DDL schema was authored at `database/schema_postgres.sql` utilizing `SERIAL`, `TIMESTAMPTZ`, constraints, and cascade drop semantics.
3. **DAO Dialect Audit:**
   - Production DAOs use ANSI SQL and PostgreSQL compliant parameterized queries via `PreparedStatement`.
   - In Phase 20A, PostgreSQL dialect migrations were completed:
     - `src/dao/AttendanceDAO.java`: Migrated to `ON CONFLICT (event_id, student_id) DO UPDATE SET present = ?, recorded_at = CURRENT_TIMESTAMP`.
     - `src/dao/AnalyticsDAO.java`: Migrated to `TO_CHAR(registration_date, 'YYYY-MM')` and `TO_CHAR(registration_date, 'Mon YYYY')`.
     - `database/seed.sql`: Removed MySQL-specific `USE campus_connect;` command for Supabase compatibility.

---

## 2. Test Suites Overview

The testing architecture provides comprehensive multi-tier automated validation without requiring Docker containers:

### Tier 1: Deterministic Unit, Cryptography & In-Memory Tests (Automatic)
Located in `src/test/java/test/`:
- `DeterministicLogicUnitTest.java` (11 tests): Pure business logic, input validation, status transitions, capacity limits, gamification thresholds, and DTO edge cases.
- `PasswordSecurityUtilTest.java` (6 tests): PBKDF2 cryptography, salt uniqueness, iteration validation, legacy plaintext fallback, and tamper resistance.
- `AuthenticationIntegrationTest.java` (9 tests): User lookup, PBKDF2 hash verification, demo account roles, concurrency-safe conditional upgrades, and account enumeration resistance against an in-memory SQL database.
- `DBConnectionConfigurationTest.java` (4 tests): Supabase URL parsing, user defaults, SSL mode validation, and missing password handling.
- `ConnectionReliabilityTest.java` (5 tests): Connection timeout handling, offline graceful degradation, retry safety, and thread isolation.

### Tier 2: Live Supabase PostgreSQL Verification & UI Workflow Tests
Located in `src/test/java/test/`:
- `SupabaseSchemaInspectionTest.java` (1 test): Live read-only inspection verifying that all 7 core tables, columns, data types, and foreign key / check / unique constraints exist in Supabase PostgreSQL (PostgreSQL 17.11).
- `Phase26LiveVerificationTest.java` (17 tests): Live DAO verification against Supabase verifying non-destructive queries, role checks, event fetching, and constraint compliance.
- `Phase29GuiWorkflowTest.java` (9 tests): Headless JavaFX thread execution verifying student and admin UI controller initialization, view binding, and tab navigation.
- `Phase29WorkflowVerificationTest.java` (12 tests): End-to-end integration workflows validating registration eligibility, capacity ceilings, attendance roster gating, and certificate issuance logic.
- `Phase30UiCaptureUtil.java` (1 test): Automated visual inspection engine that renders 1440x900 JavaFX views with CSS applied and saves the 14 UI screenshots to `docs/screenshots/`.

### Tier 3: Opt-In Supabase Integration Tests (Requiring Opt-In Flag)
Located in `src/test/java/test/SupabasePostgresIntegrationTest.java` (13 tests):
- Deep transaction-rollback integration tests targeting live Supabase PostgreSQL.
- Requires explicit opt-in via `export CAMPUS_CONNECT_INTEGRATION=true`.
- Safely skipped when the opt-in flag is unset, reporting as **SKIPPED** (0 failures, 0 errors).

---

## 3. How to Run Ordinary Unit Tests

To run the complete automated test suite:

```bash
mvn clean test
```

Expected output:
- **Discovered Tests:** 87
- **Passed:** 74 (All deterministic unit tests, live schema inspection, GUI controller tests, and workflow tests)
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 13 (`SupabasePostgresIntegrationTest` suites cleanly skipped because `CAMPUS_CONNECT_INTEGRATION` is not enabled)
- **Build:** `BUILD SUCCESS` (Execution time: ~65 seconds)

---

## 4. How to Configure Supabase PostgreSQL & Enable Integration Tests

Campus Connect supports configuration via:
1. Shell environment variables (`export DB_URL=...`, etc.)
2. Java system properties (`-DDB_URL=...`, etc.)
3. Local untracked `.env` file (loaded automatically by `DBConnection` with zero external dependencies)

### Option A: Local `.env` File (Recommended for Local Development)
Copy the template and add your database password:
```bash
cp .env.example .env
chmod 600 .env
```

Edit `.env` (it is strictly ignored by `.gitignore`):
```env
DB_URL=jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require
DB_USER=postgres.ijcsjfikzkwwhcsfjawh
DB_PASSWORD=your_actual_supabase_password_here
```

### Option B: Shell Environment Variables
```bash
# Supabase Session Pooler configuration
export DB_URL="jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require"
export DB_USER="postgres.ijcsjfikzkwwhcsfjawh"
export DB_PASSWORD="your_actual_supabase_password_here"
```

### Running Live Supabase Integration Tests
To execute live integration tests against your Supabase database:
```bash
# Enable the opt-in flag
export CAMPUS_CONNECT_INTEGRATION=true

# Execute Maven test runner
mvn test -Dtest=SupabasePostgresIntegrationTest
```

> [!IMPORTANT]
> - Never hardcode or commit database credentials in source code.
> - Ensure `sslmode=require` is specified in the JDBC URL as mandated by Supabase security policies.

---

## 5. How to Safely Prepare a Test Schema

When deploying to Supabase PostgreSQL:
1. In the Supabase SQL Editor, execute `database/schema_postgres.sql` to initialize the 7 core tables:
   - `users`
   - `clubs`
   - `events`
   - `registrations`
   - `attendance`
   - `feedback`
   - `certificates`
2. For isolated test runs, use an isolated PostgreSQL schema (e.g., `CREATE SCHEMA test_campus_connect; SET search_path TO test_campus_connect;`) or ensure integration tests run under their self-reverting transaction rollback mode.
3. Never execute destructive DDL (`DROP DATABASE` or `TRUNCATE`) against production schemas.

---

## 6. Execution Status & Audit Log

| Test Suite Class | Tests Discovered | Passed | Failed | Errors | Skipped | Status / Scope |
|---|---|---|---|---|---|---|
| `AuthenticationIntegrationTest` | 9 | 9 | 0 | 0 | 0 | PASSED (In-memory SQL authentication & concurrency safety) |
| `ConnectionReliabilityTest` | 5 | 5 | 0 | 0 | 0 | PASSED (Timeout handling, offline degradation, thread isolation) |
| `DBConnectionConfigurationTest` | 4 | 4 | 0 | 0 | 0 | PASSED (Supabase URL, user defaults, SSL & password validation) |
| `DeterministicLogicUnitTest` | 11 | 11 | 0 | 0 | 0 | PASSED (Pure business logic, status transitions, input validation) |
| `PasswordSecurityUtilTest` | 6 | 6 | 0 | 0 | 0 | PASSED (PBKDF2 cryptography, salt uniqueness, tamper resistance) |
| `Phase26LiveVerificationTest` | 17 | 17 | 0 | 0 | 0 | PASSED (Live Supabase queries, role checks, event fetching) |
| `Phase29GuiWorkflowTest` | 9 | 9 | 0 | 0 | 0 | PASSED (Headless JavaFX controller lifecycle & view binding) |
| `Phase29WorkflowVerificationTest` | 12 | 12 | 0 | 0 | 0 | PASSED (End-to-end registration, capacity, and gating logic) |
| `Phase30UiCaptureUtil` | 1 | 1 | 0 | 0 | 0 | PASSED (Visual inspection engine & 14 UI screenshots) |
| `SupabaseSchemaInspectionTest` | 1 | 1 | 0 | 0 | 0 | PASSED (Live read-only schema inspection on PostgreSQL 17.11) |
| `SupabasePostgresIntegrationTest` | 13 | 0 | 0 | 0 | 13 | **SKIPPED** (`CAMPUS_CONNECT_INTEGRATION` unset) |
| **Total Maven Discovered** | **87** | **74** | **0** | **0** | **13** | **BUILD SUCCESS** |

### Standalone Verification Suites:
In addition to the 87 automated Maven Surefire tests, the repository contains standalone test runners in `src/util/`:
- `Phase17ComprehensiveVerificationTest`: 182/182 assertions passed (`mvn exec:java -Dexec.mainClass="util.Phase17ComprehensiveVerificationTest"`)
- `Phase19PasswordHardeningTest`: 30/30 assertions passed (`mvn exec:java -Dexec.mainClass="util.Phase19PasswordHardeningTest"`)
- `SeedPasswordGenerator`: Standalone utility to generate production-grade PBKDF2 hashes for seed records (`mvn exec:java -Dexec.mainClass="util.SeedPasswordGenerator"`)
