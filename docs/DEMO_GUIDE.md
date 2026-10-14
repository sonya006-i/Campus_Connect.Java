# Demonstration Guide: Campus Connect ◈
### Practical 5–7 Minute Evaluation & Viva Presentation Script

**Project:** Campus Connect — Campus Event & Club Management System
**Target Architecture:** Java 21 LTS | JavaFX 21.0.6 | Pure JDBC | Supabase PostgreSQL 15+ (with MySQL 8.0+ compatibility)
**Estimated Time:** 5 to 7 Minutes

---

## 1. Demonstration Setup & Pre-Flight Checklist

Before presenting to evaluators, ensure your workstation environment meets these conditions:
1. **Java Runtime:** OpenJDK 21 or higher installed (`java -version`).
2. **Build System:** Apache Maven 3.9+ installed (`mvn -version`). If installed in `~/.local/bin`, ensure `export PATH="$HOME/.local/bin:$PATH"` is configured.
3. **Database Connectivity:**
   - **Live Cloud Database (Standard):** Supabase PostgreSQL connected via session pooler with SSL (`.env` configured with `DB_URL`, `DB_USER`, `DB_PASSWORD`).
   - **Offline Resilience Mode (Alternative):** If internet connectivity is unavailable, the application gracefully activates its offline fallbacks, maintaining UI responsiveness without crashing.
4. **Automated Visual Verification:** All 14 reference screens are verified and available under `docs/screenshots/`.
5. **Seeded Demo Accounts (Available via 1-Click Login Pills):**
   - **Student:** `sonya@student.edu` / `student123` (Name: Sonya Blade)
   - **Administrator:** `admin@campusconnect.edu` / `admin123` (Name: Dr. Alan Vance)

---

## 2. Launching the Application

In your terminal, navigate to the repository root and launch the JavaFX desktop application:

```bash
# If maven is in your local directory:
export PATH="$HOME/.local/bin:$PATH"

# Build and launch:
mvn javafx:run
```

*Speaking Point:*
> *"Campus Connect is an Advanced Java desktop application built with Java 21 and JavaFX, adhering to a clean MVC + DAO architecture. It connects via pure JDBC to Supabase PostgreSQL over SSL. Notice the instant startup and custom dark SaaS interface rendered at 1440×900 using pure JavaFX CSS."*

---

## 3. The 5–7 Minute Evaluation Sequence

### Step 1: Student Authentication (0:00 – 0:45)
1. On the **Sign In** screen, point out the clean dark styling, email/password fields, and the demo pill buttons.
2. Click the demo pill **"Student: sonya@student.edu"**.
   - Email (`sonya@student.edu`) and password (`student123`) populate automatically.
3. Click **"Sign In →"**.
4. The system navigates to the **Student Portal**.

*Speaking Point:*
> *"Authentication uses cryptographically salted PBKDF2 with HMAC-SHA256 and 65,536 iterations. The application validates the credentials via `UserDAO`, stores the authenticated identity in a thread-safe `Session` object, and automatically routes Sonya to the Student Dashboard."*

---

### Step 2: Event Discovery & Inspection (0:45 – 1:45)
1. Point to the **Student Dashboard KPI cards**: Registered Events, Approved Count, Certificates Earned, and Reviews Submitted.
2. Click **"Events"** on the left navigation sidebar.
3. Show the event catalog:
   - Point out the active event card: **Tech Hackathon 2026** (organized by the *Coding & Algorithms Society*).
   - Point out the category badge (`Technology`), date, venue (`Innovation Lab 3`), and capacity indicator.
4. Demonstrate interactivity:
   - Type `"Hackathon"` in the search field to demonstrate live keyword filtering.
   - Click category filter pills (e.g. *Technology*, *All*).
5. Click the **Tech Hackathon 2026** card to open the **Event Details Modal**.

*Speaking Point:*
> *"The event catalog provides real-time event discovery with category filtering and keyword search. Opening an event displays full venue details, seat quotas, and registration deadlines. Business logic prevents students from registering if the deadline has passed or seat capacity is exhausted."*

---

### Step 3: Registration Status & "My Events" (1:45 – 2:30)
1. In the modal, point out that Sonya already holds an active registration.
2. Close the modal and click **"My Events"** on the left sidebar.
3. Inspect Sonya's registration table:
   - Event: **Tech Hackathon 2026**
   - Category: `Technology`
   - Date & Venue: Innovation Lab 3
   - Status badge: `APPROVED` (highlighted in glowing green)
4. Explain registration lifecycle and cancellation:
   - Point out that students can cancel registrations with status `PENDING` or `REJECTED`, which immediately frees capacity for other students.
   - For `APPROVED` registrations, the seat is confirmed and cancellation is protected.

*Speaking Point:*
> *"Under 'My Events', students have full visibility into their registration lifecycle. A composite unique constraint `(event_id, student_id)` at the database level physically prevents duplicate registrations, while our service layer guarantees seat quota integrity."*

---

### Step 4: Administrator Authentication & Role Switching (2:30 – 3:15)
1. Click **"Log Out"** at the bottom of the left sidebar.
2. Return to the Sign In screen.
3. Click the demo pill **"Admin: admin@campusconnect.edu"**.
   - Fields autofill with `admin@campusconnect.edu` and `admin123`.
4. Click **"Sign In →"**.
5. The application dynamically switches context into the **Admin Console**.

*Speaking Point:*
> *"Logging in as Dr. Vance switches the UI context to the Admin Console. Notice that the navigation sidebar dynamically updates with administrative modules: Events Management, Registrations, Attendance, Certificates, Feedback, and Analytics. Student sessions cannot access these views."*

---

### Step 5: Event Management & Registration Moderation (3:15 – 4:30)
1. Point to the **Admin Dashboard**:
   - Highlight system KPI metrics: Total Events, Total Registrations, Turnout Rate, and Student Satisfaction.
2. Click **"Events"** in the sidebar:
   - Show the event roster table with schedule, venue, capacity, and action buttons.
   - Click **"+ Create Event"** to show the event creation dialog: venue assignment, seat quotas, and date pickers.
   - Explain the **Deletion Safeguard**:
     > *"In `EventDAO.deleteEvent()`, foreign keys use `ON DELETE RESTRICT`. If an administrator attempts to delete an event that has active registrations or attendance records, the deletion is safely blocked with a clear warning explaining the dependencies."*
3. Click **"Registrations"** in the sidebar:
   - Filter registrations by status (`PENDING`, `APPROVED`, `REJECTED`) or by event.
   - Select an entry to demonstrate the **Approve** and **Reject** moderation controls.
   - Explain capacity ceilings:
     > *"Before approving a registration, `RegistrationManagementService` counts confirmed seats against `max_participants`. If an event is at capacity, approvals are locked to prevent overbooking."*

---

### Step 6: Attendance, Certificates & Feedback Eligibility (4:30 – 5:45)
1. Click **"Attendance"** in the sidebar:
   - Select **Tech Hackathon 2026**.
   - Point out that only students with `APPROVED` registrations appear in the attendance roster.
   - Explain the `PRESENT` / `ABSENT` toggle and attendance correction capability.
2. Click **"Certificates"** in the sidebar:
   - Explain the strict two-step prerequisite pipeline:
     > *"A student is eligible for a completion certificate if and only if two conditions are met: first, their registration must be `APPROVED`; second, their attendance record must be marked `PRESENT`. Certificates use composite unique constraints `(event_id, student_id)` to prevent duplicate issuance."*
3. Click **"Feedback"** in the sidebar:
   - Explain that post-event feedback (1–5 stars and comments) is also gated on attendance: students who did not attend cannot submit reviews.

---

### Step 7: Analytics Overview & Architectural Summary (5:45 – 7:00)
1. Click **"Analytics"** in the sidebar:
   - Review the aggregate metrics:
     - Total Events, Total Registrations, System Turnout Rate (%), and Average Satisfaction Score.
     - Category Distribution table (breakdown across Technology, Arts, Robotics, Business).
     - Monthly participation trend visualization.
   - Explain zero-division safety:
     > *"The analytics calculations explicitly guard against division-by-zero, ensuring clean metrics even when an event has zero registrants or zero attendees."*
2. Conclude with an **Architectural Summary**:
   - **Clean Layering:** MVC controllers delegate exclusively to Service classes; Services enforce business validation; DAOs execute parameterized JDBC queries; Models encapsulate domain state.
   - **Relational Integrity:** 7 normalized 3NF tables with composite unique constraints and `ON DELETE RESTRICT` foreign keys.
   - **Automated Verification:** 87 Maven Surefire tests (74 passed, 13 skipped opt-in integration tests, 0 failures) plus 10 standalone Phase verification suites.

---

## 4. Data Workflow Scope & Demonstration Notes

| Workflow | Uses Existing Seeded Data? | Requires Interactive Action? | Notes |
| :--- | :---: | :---: | :--- |
| **Student Login** | Yes (`sonya@student.edu`) | 1-Click Pill | Authenticates immediately via PBKDF2 hash |
| **Event Discovery** | Yes (`Tech Hackathon 2026`) | Read-only / Filter | Demonstrates search, categories, modal |
| **My Events View** | Yes (`APPROVED` registration) | Read-only | Shows verified approved status |
| **Admin Login** | Yes (`admin@campusconnect.edu`) | 1-Click Pill | Switches to Admin Dashboard |
| **Event Management** | Yes | Optional Create | Shows event list, validation dialog |
| **Registration Moderation** | Yes | Optional Toggle | Shows status filters and capacity checks |
| **Attendance Roster** | Yes (Approved attendee) | Optional Toggle | Demonstrates attendance prerequisite |
| **Certificate Issuance** | Yes (Eligibility rule) | Optional Generate | Gated on `APPROVED` + `PRESENT` |
| **Analytics Dashboard** | Yes | Read-only | Aggregated charts and metrics |
| **Campus Pulse (Student)** | Yes | Read-only | Displays 6 milestone badges with progress meters |

---

## 5. Summary Closing Statement for Evaluators

> *"In conclusion, Campus Connect demonstrates how Advanced Java principles—object-oriented domain modeling, JavaFX programmatic UI design, parameterized JDBC persistence, and cryptographic security—can be combined to deliver a robust, beautiful, and secure event management platform. We are now ready for your questions."*
