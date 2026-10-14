# Changelog ◈
## Campus Connect — Campus Event & Club Management System

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), adhering to academic project phase milestones.

---

## [1.0.0] — 2026-10-14 (Phase 31: Final Submission & Release Readiness)

### Added
- **Private Repository Security & Governance:** Created [`SECURITY.md`](SECURITY.md) documenting credential isolation, `.env` policies, PBKDF2 cryptography, and private vulnerability reporting channels.
- **Comprehensive Academic Documentation:** Completed [`docs/PROJECT_REPORT.md`](docs/PROJECT_REPORT.md), [`docs/DATABASE_DESIGN.md`](docs/DATABASE_DESIGN.md), [`docs/VIVA_PREPARATION.md`](docs/VIVA_PREPARATION.md), [`docs/DEMO_GUIDE.md`](docs/DEMO_GUIDE.md), and [`docs/TESTING_AND_SUPABASE_GUIDE.md`](docs/TESTING_AND_SUPABASE_GUIDE.md).
- **14 High-Resolution Screenshots:** Visual inspection captures at 1440×900 resolution added under [`docs/screenshots/`](docs/screenshots/) and integrated into [`README.md`](README.md).
- **Multi-Tier Automated Testing:** 87 tests discovered across 11 test classes with 74 passing tests, 13 opt-in skipped tests, and zero failures/errors (`mvn clean test`).

### Changed
- **README Modernization:** Restructured [`README.md`](README.md) with complete project overview, private repository local setup, 14-screen visual gallery, dual Supabase/MySQL specs, and 5–7 minute evaluation script.
- **Repository Hygiene:** Enhanced [`.gitignore`](.gitignore) with NetBeans, JVM crash dumps, compiler artifacts, and editor temporary files.

---

## [0.9.0] — 2026-10-14 (Phase 29–30: GUI Workflow Testing & Visual Capture)

### Added
- **UI Screen Capture Engine:** Automated visual capture utility [`test.Phase30UiCaptureUtil`](src/test/java/test/Phase30UiCaptureUtil.java) capturing all 14 application screens directly from live database state.
- **Headless GUI Integration Tests:** Added [`test.Phase29GuiWorkflowTest`](src/test/java/test/Phase29GuiWorkflowTest.java) and [`test.Phase29WorkflowVerificationTest`](src/test/java/test/Phase29WorkflowVerificationTest.java) validating JavaFX view controllers, navigation callbacks, and form bindings.

---

## [0.8.0] — 2026-10-13 (Phase 20–26: Cloud Migration & Supabase PostgreSQL Integration)

### Added
- **Cloud Database Support:** Direct connectivity to **Supabase PostgreSQL** via Session Pooler over SSL (`sslmode=require`).
- **PostgreSQL DDL Schema:** Created [`database/schema_postgres.sql`](database/schema_postgres.sql) with `SERIAL`, `TIMESTAMPTZ`, constraints, and cascade drop semantics.
- **Safe Demo Fixture:** Created [`database/seed_minimal_safe.sql`](database/seed_minimal_safe.sql) with idempotent tracking table `_demo_setup_tracking`.
- **Live Schema Inspection Test:** Added [`test.SupabaseSchemaInspectionTest`](src/test/java/test/SupabaseSchemaInspectionTest.java) validating all 7 core tables and constraints.

### Changed
- **Dialect Migration:** Updated [`src/dao/AttendanceDAO.java`](src/dao/AttendanceDAO.java) with `ON CONFLICT DO UPDATE` and [`src/dao/AnalyticsDAO.java`](src/dao/AnalyticsDAO.java) with PostgreSQL `TO_CHAR` date formatters.
- **Dual-Dialect Connection Pool:** Updated [`src/util/DBConnection.java`](src/util/DBConnection.java) to dynamically detect PostgreSQL and MySQL JDBC URLs.

---

## [0.6.0] — 2026-10-12 (Phase 19: Cryptographic Password Hardening)

### Added
- **PBKDF2 Cryptography Engine:** Built [`src/util/PasswordSecurityUtil.java`](src/util/PasswordSecurityUtil.java) using `PBKDF2WithHmacSHA256`, 65,536 iterations, 16-byte random salts, and constant-time verification.
- **Automated Password Migration:** On-the-fly upgrade of legacy plaintext passwords to salted PBKDF2 hashes upon successful authentication.
- **Password Hardening Test Suite:** Added [`util.Phase19PasswordHardeningTest`](src/util/Phase19PasswordHardeningTest.java) with 30 assertions.

---

## [0.5.0] — 2026-10-11 (Phase 14–17: Analytics, Campus Pulse & E2E Verification)

### Added
- **Campus Pulse Gamification:** Achievement badge milestone system awarding 6 badges (*Campus Pioneer*, *Active Explorer*, *Campus Veteran*, *Certified Achiever*, *Campus Voice*, *Club Enthusiast*).
- **Admin Analytics:** KPI overview cards, category distribution charts, turnout rate calculations, and monthly registration trends.
- **Phase 17 Comprehensive Verification Suite:** Added [`util.Phase17ComprehensiveVerificationTest`](src/util/Phase17ComprehensiveVerificationTest.java) with 182 assertions covering cross-module boundary conditions.

---

## [0.3.0] — 2026-10-10 (Phase 8–13: Core Administrative & Student Workflows)

### Added
- **Event Lifecycle Management:** Admin CRUD operations with deletion safeguards enforcing `ON DELETE RESTRICT`.
- **Registration Moderation:** Approval and rejection workflows with venue capacity quota enforcement.
- **Attendance Roster:** Student attendance tracking with `PRESENT` / `ABSENT` toggles and correction resets.
- **Certificate Issuance:** Dual-prerequisite certificate generation gated on `APPROVED` registration + `PRESENT` attendance.
- **Feedback Module:** 1-to-5 star rating and comment submission gated on attendance verification.

---

## [0.1.0] — 2026-10-09 (Phase 0–3: Foundation, UI Shell & Authentication)

### Added
- **Project Foundation:** Maven configuration with OpenJDK 21 LTS and OpenJFX 21.0.6.
- **Dark SaaS Design System:** JavaFX CSS token system (`resources/css/style.css`) using dark navy (`#0B0A1A`, `#14132A`) and neon indigo/purple (`#6366F1`, `#8B5CF6`).
- **Relational Schema (MySQL):** Created `database/schema.sql` with 7 normalized 3NF tables.
- **Role-Based Authentication:** Sign In screen with role-based routing to Student and Admin portals.
