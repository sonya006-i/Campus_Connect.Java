# Viva Voce Examination Guide: Campus Connect
### Comprehensive Questions & Answers for Academic Evaluation

This document contains **38 in-depth, project-specific viva questions and answers** organized into 12 thematic categories. The answers are tailored directly to the architecture, code, and implementation decisions of **Campus Connect**.

---

## Category 1: Project Motivation & Problem Statement

### Q1. What was the motivation behind developing Campus Connect?
> **Answer:** In our university, student clubs operate through fragmented channels—events are announced on WhatsApp or Instagram, registrations are collected via Google Forms, attendance is scribbled on loose paper sheets, and certificates are manually created. This leads to missed deadlines, overbooked venues, unverified certificate claims, and a total lack of engagement analytics. Campus Connect was built to centralize the entire event lifecycle into a single, reliable desktop portal.

### Q2. Who are the primary users of the system and what value does it bring to each?
> **Answer:** The system serves two distinct user roles:
> 1. **Students:** Enables them to discover campus events in one place, filter by category, register within real-time capacity limits, track their participation history under "My Events", access verified digital certificates, and voice feedback.
> 2. **Club Leads / Administrators:** Gives them a centralized console to schedule events, enforce venue quotas, moderate registrations, mark attendance rosters, generate attendance-backed certificates, and review turnout analytics and student sentiment.

### Q3. Why did you build Campus Connect as a desktop application instead of a web application?
> **Answer:** This project is for our Advanced Java curriculum, which emphasizes core desktop software engineering, multithreaded GUI architectures (JavaFX), low-level database connectivity (JDBC), and clean object-oriented design patterns. A desktop application provides predictable native execution, high responsiveness, direct OS integration, and does not require complex web hosting, cloud infrastructure, or multi-framework overhead for evaluation.

---

## Category 2: Java 21 & Object-Oriented Programming

### Q4. How does the project take advantage of Java 21 features?
> **Answer:** We compile with `release 21` targeting modern Java LTS. We leverage enhanced `switch` statements, pattern matching capabilities, standard functional interfaces (`Consumer<User>`, `Supplier`), Java Streams for in-memory filtering and metric calculation, and the robust Date-Time API (`java.time.LocalDate`, `LocalTime`).

### Q5. Where and how are the four core OOP pillars applied in Campus Connect?
> **Answer:**
> - **Encapsulation:** All models (e.g., `Event`, `User`, `Registration`) maintain private fields accessible only through validated getters and setters. Data cannot be mutated arbitrarily.
> - **Abstraction:** The UI controllers interact with `Service` classes without needing to know whether the data is computed in memory or fetched via SQL queries.
> - **Inheritance:** `Main` extends `javafx.application.Application`; dialogs and custom panels inherit from JavaFX layout containers like `VBox` and `StackPane`.
> - **Polymorphism:** Method overriding in UI components, functional callbacks (`onLoginSuccess.accept(user)`), and interface implementations across collections.

### Q6. What is the difference between Domain Models and Data Transfer Objects (DTOs) in your codebase?
> **Answer:**
> - **Domain Models (`src/model/`):** Directly represent our database tables (e.g., `User`, `Event`, `Attendance`, `Certificate`). They mirror relational rows.
> - **DTOs (e.g., `AdminDashboardDTO`, `StudentAchievementDTO`, `PopularEventDTO`):** Are specialized composite objects crafted specifically for UI views. For instance, `AdminRegistrationDTO` combines fields from `registrations`, `events`, and `users` (such as student name, email, event title, capacity, and status) into a single object so controllers don't perform complex multi-table joins.

---

## Category 3: JavaFX & UI Architecture

### Q7. How is the user interface constructed in Campus Connect? Are you using FXML or pure programmatic JavaFX?
> **Answer:** We construct the UI programmatically in pure Java code styled with a single centralized stylesheet (`resources/css/style.css`). This eliminates the fragility of loose FXML XML files, provides compile-time type safety for all UI components, avoids reflective binding issues, and makes dynamic UI generation (such as event card grids and interactive badge meters) much cleaner and faster.

### Q8. How do you implement the dark SaaS aesthetic in JavaFX?
> **Answer:** We defined a comprehensive CSS design token system in `style.css` using HSL-tailored dark tones: `#0B0A1A` for root backgrounds, `#14132A` for elevated cards, and `#6366F1` / `#8B5CF6` for vibrant neon purple/indigo accents. We use CSS pseudo-classes (`:hover`, `:pressed`), rounded borders (`-fx-background-radius`), and translucent overlays (`rgba(...)`) to achieve a modern, cohesive dashboard feel.

### Q9. How does the application handle responsiveness across different screen sizes?
> **Answer:** In `Main.java`, the primary stage is configured with a default resolution of 1440×900 and strict minimum bounds of 1100×700 (`setMinWidth`, `setMinHeight`). Inside controllers, layouts use layout managers like `VBox`, `HBox`, and `StackPane` with `Priority.ALWAYS`, `HBox.setHgrow`, `ScrollPane` wrappers, and percentage-based table columns so elements dynamically stretch or wrap without clipping.

---

## Category 4: MVC-Style Separation of Responsibilities

### Q10. Explain the architectural layers of your application.
> **Answer:** We follow a layered MVC + DAO architecture:
> 1. **View / Controller (`src/controller/`):** JavaFX event handlers that capture user interactions, validate form fields, and render visual components.
> 2. **Service Layer (`src/service/`):** Contains pure business logic, eligibility checks, and transaction rules (e.g., ensuring capacity is available before approving a registration).
> 3. **DAO Layer (`src/dao/`):** Contains all SQL queries and JDBC database operations.
> 4. **Model Layer (`src/model/`):** Plain Java objects representing entities and DTOs.
> 5. **Database:** The relational MySQL database.

### Q11. Why shouldn't SQL queries be placed directly inside JavaFX controllers?
> **Answer:** Mixing SQL into UI controllers violates the Single Responsibility Principle (SRP). If controllers execute SQL, database schema changes would break UI code, code cannot be unit tested without launching the JavaFX runtime, and queries cannot be reused across different views. Separating queries into DAOs keeps controllers lightweight and testable.

### Q12. What role does `DashboardController` play in your application?
> **Answer:** `DashboardController` acts as the primary shell coordinator. It manages the persistent navigation sidebar, the breadcrumbs top bar, the active user profile indicator, and handles switching between different view controllers (`EventsController`, `MyEventsController`, `AdminRegistrationsController`, etc.) within the main content `StackPane`.

---

## Category 5: JDBC & SQL

### Q13. Why did you use pure JDBC instead of Hibernate or JPA?
> **Answer:** For an Advanced Java course, pure JDBC demonstrates a foundational understanding of relational database interaction, connection lifecycles, and SQL query tuning. ORM frameworks like Hibernate introduce heavy configuration, caching complexities, and hide raw SQL execution. JDBC gives us explicit control over every query, parameterized statement, and transaction. Furthermore, our custom `DBConnection` manager dynamically detects the JDBC URL dialect and supports both cloud **Supabase PostgreSQL** (using PostgreSQL Session Pooler over SSL) and local **MySQL 8.0+** seamlessly.

### Q14. How does the application prevent SQL Injection attacks?
> **Answer:** We exclusively use `java.sql.PreparedStatement` with parameterized placeholders (`?`) for all queries involving user input. We never use string concatenation to build SQL statements. The JDBC driver ensures that inputs are treated strictly as data literals rather than executable SQL syntax.

### Q15. How are database resources managed to prevent connection leaks?
> **Answer:** All database statements and result sets use Java's `try-with-resources` statement:
> ```java
> try (Connection conn = DBConnection.getConnection();
>      PreparedStatement stmt = conn.prepareStatement(sql)) {
>     ...
> }
> ```
> This guarantees that `Connection`, `PreparedStatement`, and `ResultSet` objects are automatically closed when execution leaves the block, even if an unexpected `SQLException` occurs.

---

## Category 6: Database Schema & Relational Design

### Q16. How many tables are in your database and how are they normalized?
> **Answer:** The schema contains **7 normalized tables**: `users`, `clubs`, `events`, `registrations`, `attendance`, `feedback`, and `certificates`. All tables are normalized to **Third Normal Form (3NF)**: every non-key column depends strictly on the primary key, with zero transitive dependencies. The schema is supported on both Supabase PostgreSQL (`database/schema_postgres.sql`) and MySQL (`database/schema.sql`). All 7 tables and constraints have been verified in our live cloud Supabase PostgreSQL environment via automated schema inspection.

### Q17. Why do your foreign keys use `ON DELETE RESTRICT` instead of `CASCADE`?
> **Answer:** In academic and financial systems, audit trails and historical records must not be deleted accidentally. If an event has 50 registrations and issued certificates, deleting the event via `CASCADE` would permanently wipe out the students' certificates and attendance records. `RESTRICT` prevents deletion while dependent records exist, forcing coordinators to resolve registrations before an event can be removed.

### Q18. What indexes did you create and why?
> **Answer:** We indexed foreign keys and frequently filtered columns:
> - `idx_events_date` on `events(event_date)` for fast chronological filtering.
> - `idx_reg_student` on `registrations(student_id)` for rapid "My Events" loading.
> - `idx_reg_event` on `registrations(event_id)` for quick capacity counting.
> - Indexes on `attendance`, `feedback`, and `certificates` to accelerate eligibility checks.

---

## Category 7: Authentication, Roles & Session Handling

### Q19. How does authentication work in Campus Connect?
> **Answer:** The user enters their email and password on the Login Screen. `LoginController` validates that fields are non-empty, then calls `UserDAO.findByEmail(email)`. If the user exists, authentication delegates to `util.PasswordSecurityUtil.verifyPassword(candidate, storedValue)`.
>
> Password verification uses **PBKDF2WithHmacSHA256** with:
> 1. **Cryptographically random 16-byte salt per user** (`SecureRandom`).
> 2. **65,536 iterations** to resist GPU/ASIC brute-force attacks.
> 3. **Self-describing format:** `PBKDF2WithHmacSHA256:<iterations>:<base64Salt>:<base64Hash>`.
> 4. **Constant-time comparison:** `MessageDigest.isEqual` prevents timing side-channel attacks.
> 5. **Controlled legacy upgrade:** If an existing record contains a legacy plaintext password, it is verified via constant-time comparison, and immediately upon successful login, the system derives a fresh PBKDF2 salted hash and updates the database row in-place via `UserDAO.updatePassword()`.
>
> If authentication succeeds, the `User` object is stored in `util.Session` and the view switches to the appropriate dashboard. If authentication fails, a generic message (*"Invalid email or password. Please try again."*) is displayed to prevent username enumeration.

### Q20. How is the user session managed? Is it thread-safe?
> **Answer:** We manage sessions using a dedicated utility class `util.Session`. It holds a static reference to the currently authenticated `User` object with `synchronized` accessors (`setCurrentUser`, `getCurrentUser`, `clearSession`, `isLoggedIn`). This provides thread-safe access across JavaFX application threads and background workers.

### Q21. How do you prevent a student from accessing administrator views?
> **Answer:** We enforce access control at two layers:
> 1. **UI Layer:** The sidebar navigation menu conditionally renders items based on `Session.getCurrentUser().getRole()`. Admin navigation buttons are never rendered for students.
> 2. **Controller/Service Layer:** Admin controllers explicitly verify `user.getRole().equals("ADMIN")` prior to executing sensitive mutations.

---

## Category 8: Registration & Capacity Rules

### Q22. How does the system prevent duplicate registrations?
> **Answer:** We use dual-layer protection:
> 1. **Database Constraint:** `registrations` defines a composite unique constraint `CONSTRAINT uq_registration_event_student UNIQUE (event_id, student_id)`.
> 2. **Application Layer:** `RegistrationService.registerForEvent()` queries `RegistrationDAO.hasStudentRegistered(eventId, studentId)` before attempting insertion and alerts the student if they have already registered.

### Q23. How is event capacity enforced?
> **Answer:** Each event has a `max_participants` quota. Before approving a pending registration in `RegistrationManagementService.approveRegistration()`, the system counts current `APPROVED` registrations for that event. If `approvedCount >= max_participants`, approval is rejected with a descriptive capacity warning.

### Q24. Can a student cancel their registration?
> **Answer:** Yes. In `MyEventsController`, students can cancel registrations with status `PENDING` or `REJECTED`. This invokes `RegistrationService.cancelRegistration()`, updating status or removing the record, immediately freeing capacity for other students.

---

## Category 9: Attendance, Certificates & Feedback Eligibility

### Q25. What is the prerequisite for marking a student's attendance?
> **Answer:** Attendance can only be recorded for students whose registration status is `APPROVED`. Pending or rejected registrants cannot have attendance marked.

### Q26. What are the exact eligibility rules for issuing a certificate?
> **Answer:** In `CertificateManagementService.isEligibleForCertificate()`, a student must meet two strict criteria:
> 1. Their event registration must be `APPROVED`.
> 2. Their attendance record must be marked `present = TRUE`.
> Furthermore, `uq_cert_event_student` guarantees a certificate cannot be issued more than once for the same event and student.

### Q27. What are the rules for submitting event feedback?
> **Answer:** In `FeedbackManagementService.canSubmitFeedback()`, a student can submit feedback only if they have an `APPROVED` registration and were marked `PRESENT`. Ratings must be an integer between 1 and 5 (`chk_feedback_rating`), and each student can submit only one review per event (`uq_feedback_event_student`).

---

## Category 10: Analytics & Campus Pulse

### Q28. How is the attendance turnout rate calculated in Analytics?
> **Answer:** `AnalyticsService` divides the count of students marked `present = TRUE` by the total number of `APPROVED` registrations for that event or system-wide, multiplied by 100. The code explicitly checks for zero approved registrations to prevent division-by-zero exceptions.

### Q29. What is Campus Pulse and how are achievement badges awarded?
> **Answer:** Campus Pulse is a gamification module in `CampusPulseService` that incentivizes student participation. It calculates progress and awards 6 milestone badges based on exact thresholds:
> - *Campus Pioneer:* Attended 1 event.
> - *Active Explorer:* Attended 3 events.
> - *Campus Veteran:* Attended 5 events.
> - *Certified Achiever:* Earned 1 certificate.
> - *Campus Voice:* Submitted 1 feedback review.
> - *Club Enthusiast:* Participated in events across 2 distinct clubs.

---

## Category 11: Exception Handling & Offline Behavior

### Q30. What happens if the database server is unreachable or the network drops?
> **Answer:** `DBConnection.getConnection()` catches `SQLException` and logs an actionable warning without throwing an unhandled runtime exception. Instead of crashing, DAOs detect `null` connections and return safe empty lists or zero-initialized fallback DTOs. The UI catches these states and displays a high-contrast banner informing the user that the system is operating in offline resilience mode.

### Q31. How do you handle non-existent records or unexpected null parameters in services?
> **Answer:** All public service methods perform defensive parameter validation. If an invalid or negative ID, blank title, or null entity is supplied, the method rejects the operation immediately and returns a descriptive error message without contacting the database.

---

## Category 12: Testing, Limitations & Future Scope

### Q32. What testing methodology was used in Campus Connect?
> **Answer:** We implemented a comprehensive multi-tier testing strategy combining automated Maven Surefire test suites (`src/test/java/test/`) and standalone Phase verification runners (`src/util/`). Our automated suite includes:
> - Deterministic unit tests for business validation and capacity limits (`DeterministicLogicUnitTest`).
> - Cryptographic invariant tests for PBKDF2 password hashing and salt verification (`PasswordSecurityUtilTest`).
> - Concurrency-safe in-memory SQL authentication tests (`AuthenticationIntegrationTest`).
> - Connection reliability and timeout tests (`ConnectionReliabilityTest`, `DBConnectionConfigurationTest`).
> - Live schema inspection and DAO verification against Supabase PostgreSQL (`SupabaseSchemaInspectionTest`, `Phase26LiveVerificationTest`).
> - Headless JavaFX UI controller lifecycle and workflow tests (`Phase29GuiWorkflowTest`, `Phase29WorkflowVerificationTest`).
> - Visual UI capture testing producing 1440x900 screenshots (`Phase30UiCaptureUtil`).

### Q33. What is the difference between your unit tests and integration tests?
> **Answer:**
> - **Unit & In-Memory Tests:** Validate business logic, threshold calculations, input validation, and DTO consistency entirely in-memory (or with in-memory H2 databases) without network access.
> - **Live Integration Tests:** Connect directly to a live Supabase PostgreSQL instance via SSL to execute physical queries, inspect tables, and verify referential constraints. When deep mutations are tested, transactions use rollbacks to prevent altering seeded records.

### Q34. Have the live database queries been verified in this environment?
> **Answer:** Yes. The application connects directly to a live cloud **Supabase PostgreSQL 17** database over SSL via Session Pooler. Live database verification has been confirmed through:
> 1. `SupabaseSchemaInspectionTest`: Confirms all 7 core tables (`users`, `clubs`, `events`, `registrations`, `attendance`, `feedback`, `certificates`) and their primary, unique, check, and foreign key constraints exist on PostgreSQL 17.11.
> 2. `Phase26LiveVerificationTest` (17 tests): Executes live DAO queries for user lookup, event listing, registration status, and attendance queries.
> 3. `Phase30UiCaptureUtil`: Renders all 14 screens populated with live data from Supabase, saving high-resolution PNG captures to `docs/screenshots/`.
> 4. For offline development, the application's fallback handlers and offline unit tests ensure 100% test coverage without requiring internet connectivity.

### Q35. What is a specific bug you found and resolved during testing?
> **Answer:** During Phase 17 boundary testing of `StudentAchievementDTO`, we noticed that passing negative progress counts resulted in negative percentage values (e.g. `-500.0%`). We corrected this by clamping `currentProgress` to `Math.max(0, Math.min(progress, targetGoal))` and clamping `getProgressPercentage()` strictly to `[0.0, 100.0]%`.

### Q36. How are tests organized and executed in Campus Connect?
> **Answer:** Campus Connect supports dual test execution:
> 1. **Automated Maven Surefire Suite:** Executed via `mvn clean test`. Discovers 87 tests across 11 test classes: 74 passed, 13 skipped (opt-in Supabase integration tests), 0 failures, 0 errors.
> 2. **Standalone Test Runners:** Standalone executables with `main` methods in `src/util/` (e.g. `Phase17ComprehensiveVerificationTest` with 182 assertions, `Phase19PasswordHardeningTest` with 30 assertions) executed via `mvn exec:java -Dexec.mainClass="util.<TestClass>"`.

### Q37. What are the key limitations of the current implementation?
> **Answer:**
> 1. Desktop Architecture: Designed strictly as an Advanced Java desktop client; does not provide a web interface or REST API.
> 2. In-Memory Session Storage: User session state is maintained in-memory via `util.Session` and resets when the application is closed.
> 3. Certificate Generation: Produces verified digital credentials with unique verification codes and paths; automated binary PDF file rendering is left for future expansion.
> 4. Network Dependency for Live Cloud Data: Live PostgreSQL persistence requires internet connectivity to reach Supabase; offline environments run in simulation/fallback mode.

### Q38. What would you improve if given additional time?
> **Answer:** I would implement Multi-Factor Authentication (TOTP), introduce Apache PDFBox for physical PDF certificate export with QR code verification, implement JavaMail for email notifications, and allow club coordinators to have scoped admin privileges restricted only to their specific club.
