# Project Report: Campus Connect ◈
### An Advanced Java Desktop Application for Campus Event & Club Management

**Project Title:** Campus Connect
**Academic Course:** Advanced Java Programming
**Technology Stack:** Java 21 (LTS), JavaFX 21.0.6, JDBC, Supabase PostgreSQL 15+ / MySQL 8.0+, Apache Maven 3.9+
**Architecture:** Model-View-Controller (MVC) with Data Access Object (DAO) and Service Layers

---

## 1. Abstract

**Campus Connect** is a desktop-based event and club management system engineered with Java 21 and JavaFX to streamline collegiate extra-curricular operations. Traditional university club activities frequently suffer from fragmented communication channels, manual pen-and-paper or spreadsheet registration tracking, lack of capacity enforcement, lost participation records, and cumbersome post-event certificate distribution.

Campus Connect addresses these challenges by providing a centralized desktop solution with dedicated role-based portals for students and club administrators. Students can discover upcoming events, search and filter by category, submit registrations, view registration statuses in real time, collect verified digital certificates, and submit structured feedback. Club coordinators and administrators can oversee event lifecycles, moderate registrations, enforce seat capacities, mark verified attendance rosters, issue fraud-resistant digital certificates, and review engagement analytics and feedback sentiment.

Built using an MVC-style architecture layered with pure JDBC and Data Access Objects (DAOs), the system demonstrates core Advanced Java competencies: object-oriented domain modeling, parameterized SQL persistence, thread-safe session management, robust exception resilience, and modern desktop UI design utilizing pure JavaFX CSS without external web or heavyweight enterprise frameworks. The persistence layer connects directly to cloud **Supabase PostgreSQL** via Session Pooler over SSL while maintaining full dialect compatibility with local **MySQL 8.0+**.

---

## 2. Introduction

Extra-curricular clubs and societies form an indispensable cornerstone of collegiate student life, nurturing leadership, technical proficiency, cultural expression, and social engagement. However, the administrative processes underpinning student organizations are often ad-hoc and distributed across unconnected platforms.

Event announcements are frequently broadcast through transient social media channels; registrations are gathered via uncoordinated online forms; attendance is marked on printed paper rosters susceptible to loss; and certificates are generated manually, creating substantial administrative overhead and vulnerability to falsification.

Campus Connect was conceived as an integrated, robust, and accessible desktop software system that brings order, transparency, and accountability to campus event administration. By deploying a native JavaFX graphical user interface coupled directly to a relational MySQL backend through JDBC, the application delivers responsiveness, clean user workflows, and dependable data persistence.

---

## 3. Problem Statement

Prior to the implementation of Campus Connect, campus club administration faced several critical operational deficiencies:
1. **Information Silos:** Students had no single source of truth for upcoming campus events, leading to poor turnout for educational workshops and academic symposiums.
2. **Registration Chaos & Capacity Violations:** Manual registration tracking lacked real-time seat ceilings, leading to venue overbooking and logistical hazards on event days.
3. **Absence of Duplicate Prevention:** Students could inadvertently register multiple times for the same event, corrupting attendance forecasts.
4. **Unverified Certification:** Without an integrated attendance-verification pipeline, certificates of participation were frequently issued to students who registered but failed to attend.
5. **Lack of Feedback & Analytics:** Club coordinators had no structured mechanism to collect student feedback or measure turnout trends, preventing data-driven improvements for subsequent event editions.

---

## 4. Objectives

The primary objectives of the Campus Connect project are:
1. **Develop a Reliable Desktop GUI:** Create an intuitive, high-performance desktop application using JavaFX 21, adhering to modern dark SaaS design standards.
2. **Enforce Role-Based Access Control:** Differentiate interfaces and functional permissions between standard `STUDENT` users and authorized club `ADMIN` coordinators.
3. **Implement Robust Relational Persistence:** Design a normalized MySQL schema utilizing primary keys, foreign keys with referential actions, unique constraints, and check constraints.
4. **Demonstrate Advanced Java Concepts:** Utilize JDBC `PreparedStatement` interfaces, DAO abstraction patterns, Service-layer business validations, Java Streams, and thread-safe session tracking.
5. **Automate Core Event Lifecycles:** Provide seamless workflows for event creation, registration moderation, capacity enforcement, attendance tracking, and certificate generation.
6. **Incorporate Basic Engagement Analytics:** Aggregate participation metrics, compute turnout percentages, analyze feedback ratings, and reward student engagement via the Campus Pulse milestone system.

---

## 5. Scope & Limitations

### In-Scope Functionality
- **Authentication & Sessions:** User sign-in with email and password verification; role-differentiated routing to Student or Admin dashboards; thread-safe in-memory session tracking; clean logout.
- **Student Event Catalog:** Real-time event browsing, category filtering, keyword searching, and modal event inspection.
- **Registration Management:** Student registration submission with automated checks for duplicates, registration deadlines, and seat capacity limits.
- **My Events & Cancellations:** Personal registration dashboard displaying statuses (`PENDING`, `APPROVED`, `REJECTED`), with cancellation capabilities for unapproved entries.
- **Attendance Roster:** Admin interface to review registered attendees and record `PRESENT` or `ABSENT` status.
- **Certificate Issuance & Viewing:** Attendance-gated certificate issuance with unique verification hashes; dedicated student certificate gallery.
- **Feedback System:** 1-to-5 star rating and comment submission gated on attendance verification; administrative feedback review console.
- **Analytics & Campus Pulse:** Administrative overview cards (Total Events, Total Registrations, Turnout Rate, Average Rating), distribution charts, and student achievement badges.

### Limitations & Out-of-Scope Items
- **Desktop Only:** Designed strictly as a client desktop application; does not provide a web browser interface or RESTful web services.
- **Local Relational Storage:** Operates against a dedicated MySQL database via standard JDBC.
- **In-Memory Session Lifecycle:** Active authentication tokens reside in memory and do not persist across desktop process restarts.
- **Simulated PDF Export:** Certificate issuance records the digital verification path and unique hash; automated PDF byte compilation is left as a future integration point.

---

## 6. Functional Requirements

### 6.1 Authentication Module (FR-01 to FR-03)
- **FR-01 (Sign In):** The system shall authenticate users against the `users` table via unique email and password.
- **FR-02 (Role Routing):** The system shall automatically route authenticated users to either the Student Dashboard or Admin Dashboard based on their assigned role.
- **FR-03 (Session & Logout):** The system shall maintain the active user identity in a thread-safe `Session` object and cleanly clear session data upon logout.

### 6.2 Student Portal (FR-04 to FR-09)
- **FR-04 (Event Discovery):** Students shall be able to browse all scheduled campus events.
- **FR-05 (Search & Filter):** Students shall be able to filter events by club category and search by title or description keywords.
- **FR-06 (Event Registration):** Students shall be able to register for open events if the deadline has not passed, capacity is not exhausted, and no duplicate registration exists.
- **FR-07 (Registration Tracking):** Students shall view the status of their registrations in the "My Events" view.
- **FR-08 (Certificate Gallery):** Students shall be able to view and inspect all certificates awarded to their account.
- **FR-09 (Feedback Submission):** Students who attended an event shall be able to submit a rating (1–5) and comment.

### 6.3 Admin Portal (FR-10 to FR-16)
- **FR-10 (Event CRUD):** Administrators shall create, edit, and delete events, subject to deletion protection when dependent records exist.
- **FR-11 (Registration Moderation):** Administrators shall review pending registrations and approve or reject applications.
- **FR-12 (Capacity Enforcement):** Approval of registrations shall be prohibited once an event's approved count reaches `max_participants`.
- **FR-13 (Attendance Management):** Administrators shall mark and correct attendance for approved event participants.
- **FR-14 (Certificate Generation):** Administrators shall generate certificates only for students who have an `APPROVED` registration and are marked `PRESENT`.
- **FR-15 (Feedback Inspection):** Administrators shall view qualitative feedback and calculate average star ratings per event.
- **FR-16 (Analytics Dashboard):** Administrators shall view system-wide turnout rates, category distributions, and monthly trends.

---

## 7. Non-Functional Requirements

- **NFR-01 (Performance & Responsiveness):** The desktop UI must remain responsive during data loading, completing in-memory view updates within 100 milliseconds.
- **NFR-02 (Data Integrity & ACID Compliance):** All relational modifications must maintain ACID guarantees in MySQL, strictly enforcing foreign key relationships and composite unique constraints.
- **NFR-03 (Fault Tolerance & Offline Resilience):** In the event of a database connectivity interruption, the application must not crash; it must display clean, non-intrusive error notifications and return safe fallback data transfer objects.
- **NFR-04 (Usability & Design Aesthetics):** The interface must follow modern design standards using a cohesive dark SaaS theme, clear typography, intuitive card layouts, and subtle micro-interactions.
- **NFR-05 (Maintainability & Clean Architecture):** Code must be structured into distinct packages (`controller`, `service`, `dao`, `model`, `util`) without business logic in controllers or SQL queries outside DAO classes.

---

## 8. User Roles & Permissions

The system enforces strict role-based access control (RBAC) at both the controller and service layers:

| Action / Capability | STUDENT Role | ADMIN Role |
| :--- | :---: | :---: |
| Browse & Search Events | Yes | Yes |
| View Event Details Modal | Yes | Yes |
| Register for Events | Yes | No |
| Cancel Pending Registration | Yes | No |
| View "My Events" & Registration Status | Yes | No (Accesses Admin Console) |
| View Earned Certificates | Yes (Own Only) | Yes (Can view & issue all) |
| Submit Event Feedback | Yes (Attended Only) | No (Reviews feedback) |
| View Campus Pulse Badges | Yes (Personal) | No (Reviews aggregate metrics) |
| Create / Edit Events | No | Yes |
| Delete Events (Subject to dependency checks) | No | Yes |
| Approve / Reject Registrations | No | Yes |
| Mark Student Attendance | No | Yes |
| Issue Certificates | No | Yes |
| View System Analytics & KPI Metrics | No | Yes |

---

## 9. System Architecture

Campus Connect implements a classic multi-tier desktop architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                 PRESENTATION LAYER (JavaFX)                 │
│  - Main.java (Application Coordinator & Scene Navigator)    │
│  - 12 Specialized Controllers (UI Event Handlers)           │
│  - Pure JavaFX CSS (resources/css/style.css)                │
└──────────────────────────────┬──────────────────────────────┘
                               │ UI Invocations / DTOs
┌──────────────────────────────▼──────────────────────────────┐
│                    BUSINESS SERVICE LAYER                   │
│  - 10 Business Services (Rules, Validations, Security)      │
│  - Enforces capacity limits, deadlines, duplicate checks    │
└──────────────────────────────┬──────────────────────────────┘
                               │ Entity Operations / Queries
┌──────────────────────────────▼──────────────────────────────┐
│                   DATA ACCESS OBJECT (DAO) LAYER            │
│  - 9 DAOs encapsulating pure JDBC SQL logic                 │
│  - PreparedStatement parametrization                        │
│  - Safe ResultSet mapping into Domain Models & DTOs         │
└──────────────────────────────┬──────────────────────────────┘
                               │ JDBC Protocol
┌──────────────────────────────▼──────────────────────────────┐
│                     DATABASE PERSISTENCE                    │
│  - MySQL 8.0+ / MariaDB Engine (InnoDB, utf8mb4)            │
│  - 7 Normalized Tables, Foreign Keys, Unique & Check Rules  │
└─────────────────────────────────────────────────────────────┘
```

### Architectural Separation of Responsibilities:
1. **Controller Layer (`src/controller/`):** Listens for JavaFX user input events (clicks, key presses, search typing), coordinates modal dialogs, and updates observable UI nodes. No SQL queries reside in controllers.
2. **Service Layer (`src/service/`):** Houses business rules and validation logic. Verifies whether an event is full before allowing approval, checks registration deadlines, and enforces attendance prerequisites before certificate generation.
3. **DAO Layer (`src/dao/`):** Contains raw SQL queries executed exclusively through `java.sql.PreparedStatement`. Translates `ResultSet` rows into strongly typed Java model objects or DTOs.
4. **Model Layer (`src/model/`):** Contains pure domain entities (`User`, `Event`, `Club`, `Registration`, `Attendance`, `Certificate`, `Feedback`) and specialized read-only Data Transfer Objects (DTOs) tailored for UI views.
5. **Utility Layer (`src/util/`):** Provides static connection factory methods (`DBConnection`), thread-safe user session state (`Session`), and comprehensive regression test harnesses.

---

## 10. Module Descriptions

### 10.1 Authentication & Shell Navigation (`LoginController`, `DashboardController`, `Main`)
Coordinates application launch, credential verification, and user routing. The `DashboardController` maintains a persistent dark SaaS shell featuring a collapsible sidebar, breadcrumbs, user profile badge, and role-specific navigation menus.

### 10.2 Event Discovery & Registration (`EventsController`, `RegistrationService`, `EventDAO`)
Presents an event catalog styled as responsive cards displaying title, organizing club, date, time, venue, category badge, and seat capacity meters. Clicking a card opens a modal dialog allowing students to register with instant validation against deadlines and seat limits.

### 10.3 My Events & Lifecycle Tracking (`MyEventsController`, `RegistrationDAO`)
Enables students to inspect their registration history. Registrations are grouped or filtered by status (`PENDING`, `APPROVED`, `REJECTED`). Students can cancel unapproved registrations with a single click, instantly freeing capacity.

### 10.4 Admin Event Management (`AdminEventsController`, `EventManagementService`)
Provides complete CRUD capabilities for club events. Form validations enforce minimum title lengths, positive seat quotas, valid date sequences (registration deadline must precede or match event date), and deletion safeguards preventing accidental removal of events with historical participation records.

### 10.5 Registration Moderation (`AdminRegistrationsController`, `RegistrationManagementService`)
Enables administrators to filter registrations by event and status. Includes one-click **Approve** and **Reject** buttons with dynamic capacity checks that refuse approval if maximum capacity has been reached.

### 10.6 Attendance Tracking (`AdminAttendanceController`, `AttendanceManagementService`, `AttendanceDAO`)
Displays participant rosters for confirmed events. Administrators toggle student attendance between `PRESENT` and `ABSENT`, with full correction capabilities (unmarking attendance).

### 10.7 Certificate Issuance & Viewer (`AdminCertificatesController`, `MyCertificatesController`, `CertificateDAO`)
Implements verified credential issuance. Certificates can only be issued for students who have an `APPROVED` registration and are marked `PRESENT`. Each certificate generates a unique digital path and verification token. Students view their earned credentials in an elegant certificate gallery.

### 10.8 Feedback & Sentiment Analysis (`StudentFeedbackController`, `AdminFeedbackController`, `FeedbackDAO`)
Students who attended an event can rate their experience on a 1-to-5 star scale and provide comments. Administrators can review all submitted feedback, calculate average ratings, and evaluate student sentiment.

### 10.9 Analytics & Campus Pulse (`AdminAnalyticsController`, `CampusPulseService`, `AnalyticsDAO`)
Computes aggregated metrics: total registrations, average event attendance, turnout percentage, category breakdown, and monthly registration trends. Simultaneously calculates gamified student badges (*Campus Pioneer*, *Active Explorer*, *Campus Veteran*, *Certified Achiever*, *Campus Voice*, *Club Enthusiast*) with exact unlock thresholds.

---

## 11. Database Design & Table Relationships

The database schema (`campus_connect`) comprises **7 normalized relational tables** utilizing InnoDB engine with utf8mb4 encoding:

1. **`users`:** `user_id` (PK, AI), `name`, `email` (UQ), `password`, `role` (CHECK: 'STUDENT', 'ADMIN'), `created_at`.
2. **`clubs`:** `club_id` (PK, AI), `club_name`, `description`, `created_at`.
3. **`events`:** `event_id` (PK, AI), `club_id` (FK -> `clubs`), `event_name`, `description`, `category`, `event_date`, `event_time`, `venue`, `max_participants`, `registration_deadline`, `created_at`.
4. **`registrations`:** `registration_id` (PK, AI), `event_id` (FK -> `events`), `student_id` (FK -> `users`), `status` (CHECK: 'PENDING', 'APPROVED', 'REJECTED'), `registration_date`, `created_at`, UQ(`event_id`, `student_id`).
5. **`attendance`:** `attendance_id` (PK, AI), `event_id` (FK -> `events`), `student_id` (FK -> `users`), `present` (BOOLEAN), `recorded_at`, UQ(`event_id`, `student_id`).
6. **`feedback`:** `feedback_id` (PK, AI), `event_id` (FK -> `events`), `student_id` (FK -> `users`), `rating` (CHECK: 1–5), `comment`, `submitted_at`, UQ(`event_id`, `student_id`).
7. **`certificates`:** `certificate_id` (PK, AI), `event_id` (FK -> `events`), `student_id` (FK -> `users`), `certificate_path`, `generated_date`, `created_at`, UQ(`event_id`, `student_id`).

*Refer to [docs/DATABASE_DESIGN.md](file:///home/sonya/Java_mini_ClubManagementSystem/docs/DATABASE_DESIGN.md) for detailed field specifications, indexes, and the complete Mermaid ER diagram.*

---

## 12. Important Business Rules

1. **Duplicate Registration Prevention:** A student may register for a given event at most once (`uq_registration_event_student`).
2. **Registration Deadline Enforcement:** Registrations cannot be submitted after the specified `registration_deadline`.
3. **Capacity Ceiling:** When the number of approved registrations reaches `max_participants`, pending registrations cannot be approved.
4. **Attendance Prerequisites:** Only students with an `APPROVED` registration may have their attendance recorded.
5. **Certificate Eligibility Rule:** A certificate can be generated **only** if the student's registration is `APPROVED` **and** attendance is recorded as `PRESENT`. Duplicate certificates are strictly prevented (`uq_cert_event_student`).
6. **Feedback Eligibility Rule:** A student can submit feedback **only** for events where they were `APPROVED` and marked `PRESENT`. Only one review per student per event is permitted (`uq_feedback_event_student`).
7. **Event Deletion Safeguard:** An event cannot be deleted if it has associated registrations, attendance records, feedback entries, or certificates. The administrator must resolve dependent records first.

---

## 13. Validation & Error Handling

- **Client-Side Form Validation:** All forms perform upfront sanity checks before executing service calls. Empty fields, invalid date sequences, and out-of-range ratings are intercepted with user-friendly alerts.
- **SQL Exception Encapsulation:** All database calls catch `SQLException` within the DAO layer or rethrow them as meaningful business exceptions. Raw stack traces are never exposed to the user interface.
- **Offline Resilience:** If the MySQL database is unreachable or connection drops, `DBConnection.getConnection()` returns `null`. DAOs catch this condition gracefully and return empty lists or zero-initialized DTOs. Controllers display high-contrast, informative offline banners without crashing.
- **Zero-Division Protection:** Turnout rate, approval percentage, and average rating calculations guard against empty collections by verifying counts prior to division.

---

## 14. Security Considerations

- **Parameterized Queries:** Every dynamic SQL interaction uses JDBC `PreparedStatement` with placeholder binding (`?`). Dynamic string concatenation into SQL statements is prohibited, eliminating SQL Injection vulnerabilities.
- **Salted PBKDF2 Password Hashing (Phase 19B):** Passwords are encrypted using standard Java `PBKDF2WithHmacSHA256` with 65,536 iterations and a 16-byte cryptographically random salt per user. Hash verification employs constant-time byte comparison (`MessageDigest.isEqual`) to prevent timing side-channel attacks. Legacy plaintext records undergo safe on-the-fly upgrades upon successful login.
- **Role Isolation:** Controller navigation verifies `Session.getCurrentUser().getRole()` prior to rendering administrative screens. Student sessions cannot load or execute admin management views.
- **Credential Protection:** Passwords and database credentials are not committed to source repositories. Connection parameters default to configurable environment variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
- **Data Isolation:** Student endpoints restrict query parameters (`student_id = ?`) to the authenticated session's ID, preventing students from accessing other students' registration, certificate, or feedback records.

---

## 15. Testing Methodology & Results

Testing followed a rigorous multi-phase strategy combining automated Maven Surefire test suites and standalone Phase verification runners:

### Automated Maven Surefire Test Suite (`mvn clean test`)
The automated test suite in `src/test/java/test/` discovers **87 tests across 11 test classes**:
- **74 Passed** (Deterministic unit tests, in-memory SQL authentication, live schema inspection, JavaFX UI controller workflows)
- **13 Skipped** (Opt-in live integration tests in `SupabasePostgresIntegrationTest`, cleanly skipped when `CAMPUS_CONNECT_INTEGRATION` is not enabled)
- **0 Failures, 0 Errors**
- **Build Status:** `BUILD SUCCESS` (Execution time: ~65 seconds without requiring Docker)

| Test Suite Class | Discovered | Passed | Skipped | Focus Area |
| :--- | :---: | :---: | :---: | :--- |
| `AuthenticationIntegrationTest` | 9 | 9 | 0 | In-memory SQL authentication & concurrency safety |
| `ConnectionReliabilityTest` | 5 | 5 | 0 | Timeout handling, offline degradation, thread isolation |
| `DBConnectionConfigurationTest` | 4 | 4 | 0 | Supabase URL, user defaults, SSL & password validation |
| `DeterministicLogicUnitTest` | 11 | 11 | 0 | Business logic, status transitions, input validation |
| `PasswordSecurityUtilTest` | 6 | 6 | 0 | PBKDF2 cryptography, salt uniqueness, tamper resistance |
| `Phase26LiveVerificationTest` | 17 | 17 | 0 | Live Supabase queries, role checks, event fetching |
| `Phase29GuiWorkflowTest` | 9 | 9 | 0 | Headless JavaFX controller lifecycle & view binding |
| `Phase29WorkflowVerificationTest` | 12 | 12 | 0 | End-to-end registration, capacity, and gating logic |
| `Phase30UiCaptureUtil` | 1 | 1 | 0 | Visual inspection engine generating 14 UI screenshots |
| `SupabaseSchemaInspectionTest` | 1 | 1 | 0 | Live read-only schema inspection on PostgreSQL 17.11 |
| `SupabasePostgresIntegrationTest` | 13 | 0 | 13 | Transaction-rollback integration tests (opt-in) |
| **Total Maven Discovered** | **87** | **74** | **13** | **Zero Failures, Zero Errors (BUILD SUCCESS)** |

### Standalone Regression Test Runners (`src/util/`)
In addition, the repository includes 10 standalone verification runners covering 895+ assertions:
- `Phase17ComprehensiveVerificationTest` (182 assertions)
- `Phase19PasswordHardeningTest` (30 assertions)
- Phase-specific runners (`Phase8` through `Phase15`) validating individual milestone services.

### Compilation, UI Launch & Visual Inspection Verification
- Maven compilation (`mvn clean compile test-compile`) compiles 73 main source files and 11 test source files with zero errors (`BUILD SUCCESS`).
- Application launch (`mvn javafx:run`) initializes cleanly at 1440×900 resolution with the custom dark SaaS theme.
- **14 High-Resolution Screenshots** (`docs/screenshots/01_login_screen.png` through `14_admin_analytics.png`) were captured directly from the live application and cataloged for evaluation review.

---

## 16. Known Limitations

1. **Desktop Client Architecture:** Built as an Advanced Java desktop application; does not provide a web browser interface or RESTful web services.
2. **In-Memory Session Storage:** User session state is maintained in-memory via `util.Session` and is cleanly reset when the desktop process terminates.
3. **Digital Hash Certificate Representation:** Certificate issuance records the digital verification hash, issuance date, and event metadata; automated binary PDF file rendering is architected for future expansion.
4. **Network Dependency for Cloud Persistence:** Live Supabase PostgreSQL access requires network connectivity to the session pooler host; when offline, the application seamlessly activates its offline resilience mode.

---

## 17. Conclusion

Campus Connect successfully demonstrates the design and implementation of a professional, full-featured desktop software application using Advanced Java technologies. By combining **Java 21**, **JavaFX**, **JDBC**, and **MySQL**, the system satisfies all academic requirements while delivering a polished, modern dark SaaS aesthetic.

The application proves that classic enterprise Java desktop architecture—when decoupled into clean MVC, Service, and DAO tiers—can achieve exceptional performance, rigorous data integrity, and a premium user experience without unnecessary architectural bloat or heavyweight web frameworks.

---

## 18. Future Scope

1. **Multi-Factor Authentication (MFA):** Extending the implemented PBKDF2-HMAC-SHA256 password security layer with time-based one-time password (TOTP) verification.
2. **PDF Certificate Generation:** Integrating an open-source library such as Apache PDFBox to generate printable PDF certificates with embedded QR codes.
3. **Automated Email Notifications:** Implementing JavaMail API to send automatic confirmation emails upon registration approval or certificate issuance.
4. **Multi-Role Sub-Committees:** Allowing club executives to manage only their specific club's events rather than system-wide events.

---

## 19. References

1. Oracle Corporation. *Java Platform, Standard Edition 21 Documentation (JDK 21)*. https://docs.oracle.com/en/java/javase/21/
2. OpenJFX Project. *JavaFX 21 Documentation & API Reference*. https://openjfx.io/
3. Oracle Corporation. *Java Database Connectivity (JDBC) Specification*. https://docs.oracle.com/javase/8/docs/technotes/guides/jdbc/
4. MySQL AB / Oracle Corporation. *MySQL 8.0 Reference Manual: InnoDB Storage Engine & SQL Syntax*. https://dev.mysql.com/doc/refman/8.0/en/
5. Apache Software Foundation. *Apache Maven Project Documentation*. https://maven.apache.org/
