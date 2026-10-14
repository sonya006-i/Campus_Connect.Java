# Security Policy & Credential Hygiene ◈
## Campus Connect — Campus Event & Club Management System

**Repository Classification:** Private Academic & Portfolio Repository
**Target Environment:** Java 21 LTS | JavaFX 21.0.6 | Pure JDBC | Supabase PostgreSQL 15+ / MySQL 8.0+

---

## 1. Security Overview

**Campus Connect** is an Advanced Java desktop application engineered with defense-in-depth principles. Because this repository is maintained as a **private repository** for academic evaluation, university submission, and personal portfolio demonstration, strict credential hygiene and security standards are enforced across all development and demonstration workflows.

---

## 2. Credential Hygiene & Environment Isolation

### 2.1 The Zero-Secret Rule
- **No plain credentials or database connection strings containing passwords may ever be committed, staged, or pushed to version control.**
- All live credentials must reside exclusively in a local, untracked `.env` file or in environment variables on the host workstation.

### 2.2 `.env` Configuration & Permissions
- The repository includes a placeholder template: [`.env.example`](.env.example).
- When configuring local environments, developers copy [`.env.example`](.env.example) to `.env` and assign strict POSIX file permissions:
  ```bash
  cp .env.example .env
  chmod 600 .env
  ```
- **Git Protection:** `.gitignore` explicitly excludes `.env`, `.env.local`, `.env.*`, and `*.env` (while preserving `!.env.example`).
- **Runtime Loading:** [`src/util/DBConnection.java`](src/util/DBConnection.java) loads `.env` lazily in-memory using pure standard Java (`java.io` / `java.nio`), with zero third-party library dependencies.

### 2.3 Cloud Database Credentials (Supabase PostgreSQL)
- Connections to Supabase PostgreSQL use session pooler endpoints requiring encrypted transport.
- The JDBC connection string must enforce `sslmode=require`:
  ```
  jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require
  ```
- Database passwords and pooler reference tokens must never be embedded in source code, logs, or documentation.

---

## 3. Implemented Security Controls

### 3.1 Cryptographic Password Hardening (PBKDF2-HMAC-SHA256)
All user passwords stored in the database are hashed using the **PBKDF2** algorithm:
- **Algorithm:** `PBKDF2WithHmacSHA256` from standard Java Cryptography Extension (`javax.crypto.SecretKeyFactory`).
- **Iteration Count:** 65,536 iterations to resist high-speed GPU and ASIC dictionary/brute-force attacks.
- **Salt Generation:** 16-byte cryptographically random salt per user generated via `java.security.SecureRandom`.
- **Derived Key Length:** 256 bits (32 bytes).
- **Self-Describing Storage Format:**
  ```
  PBKDF2WithHmacSHA256:<iterations>:<base64Salt>:<base64Hash>
  ```
- **Timing Side-Channel Defense:** Hash verification uses `java.security.MessageDigest.isEqual` to ensure constant-time comparison, preventing timing analysis attacks.
- **Controlled Legacy Upgrade:** If an existing user signs in with a legacy password, the system validates the credential, immediately computes a fresh PBKDF2 salted hash, and securely upgrades the database row in-place via [`src/dao/UserDAO.java`](src/dao/UserDAO.java).

### 3.2 SQL Injection Prevention (Strict Parameterization)
- All database queries across all Data Access Objects (`src/dao/`) exclusively utilize `java.sql.PreparedStatement` with parameterized placeholders (`?`).
- **Rule:** Dynamic SQL construction via string concatenation (`+ userInput +`) is strictly prohibited throughout the entire codebase.

### 3.3 Role-Based Access Control (RBAC) & Boundary Isolation
- **Role Discrimination:** The system enforces two distinct roles: `STUDENT` and `ADMIN`, validated at the database level via `chk_user_role CHECK (role IN ('STUDENT', 'ADMIN'))`.
- **UI Boundary Isolation:** The navigation sidebar in [`src/controller/DashboardController.java`](src/controller/DashboardController.java) dynamically renders menus based on `Session.getCurrentUser().getRole()`. Administrative controls are never constructed or rendered for student accounts.
- **Service-Layer Enforcement:** All administrative mutations (approving registrations, marking attendance, issuing certificates, updating events) perform explicit role checks before querying the database layer.

### 3.4 Referential Integrity & Cascade Deletion Protection
- All relational foreign keys in `database/schema_postgres.sql` and `database/schema.sql` enforce `ON UPDATE CASCADE ON DELETE RESTRICT`.
- Events cannot be deleted if active registrations, attendance records, or issued certificates reference them. This safeguards against accidental or unauthorized data loss.
- Composite unique constraints (`uq_registration_event_student`, `uq_attendance_event_student`, `uq_feedback_event_student`, `uq_cert_event_student`) guarantee duplicate records cannot be inserted even under concurrent operations.

### 3.5 Error Handling & Information Leakage Prevention
- Database connection failures and SQL exceptions are caught gracefully and logged with actionable hints to server logs without leaking database passwords or internal connection strings to the UI.
- Authentication failures display generic messages (*"Invalid email or password. Please try again."*) to prevent username enumeration.

---

## 4. Responsible Vulnerability Reporting

Because this project is maintained in a **private repository** for academic evaluation:

- **Do NOT open public issues or public forum discussions regarding security findings.**
- If you are an authorized academic evaluator, reviewer, or collaborator who identifies a potential vulnerability, please report it directly and privately to the project maintainer:
  - **Maintainer:** Sonya Blade
  - **Contact:** Repository owner via academic student email or private GitHub communication.
- Please include:
  1. A clear description of the potential vulnerability.
  2. Steps to reproduce the issue locally.
  3. Any suggested mitigation or architectural fix.

All valid reports will be addressed promptly with the smallest safe code modification to maintain project stability.
