# AGENTS.md — Permanent Development Rulebook
## Campus Connect — Campus Event & Club Management System

**Project:** Campus Connect
**Type:** University Advanced Java Desktop Application
**Purpose:** Managing university clubs, events, student registrations, attendance, certificates, feedback, and basic analytics.

---

## 1. PROJECT TECHNOLOGY RULES

The project must strictly use:
- **Java** as the primary programming language
- **JavaFX** for the desktop GUI
- **JDBC** for database connectivity
- **MySQL** for persistent storage
- **Maven** for dependency and build management

### Prohibited Technologies
Do **NOT** introduce:
- Python
- JavaScript / TypeScript
- React
- Node.js
- Spring Boot / Spring Framework
- REST APIs
- Microservices
- Docker
- Cloud services
- Firebase
- MongoDB
- Unnecessary external frameworks

*Rule:* Only add a dependency when it is genuinely required for an approved feature.

---

## 2. PROJECT PHILOSOPHY

This is a university Advanced Java project.

### Priority Order:
1. **Reliability**
2. **Simplicity**
3. **Maintainability**
4. **Clean UI**
5. **Demonstration of Advanced Java concepts**
6. **Extra features**

- Do **NOT** over-engineer the application.
- A simple working solution is always preferred over a complex solution.
- Do not add complexity merely to make the project appear more advanced.

---

## 3. DEVELOPMENT STRATEGY

The project must be developed incrementally.
- Implement **ONE** phase or feature at a time.
- Before starting a new phase:
  - Inspect the existing project.
  - Understand the current architecture.
  - Preserve existing working functionality.
  - Identify files that actually need modification.
- Never rebuild the entire application for a small feature.
- Never rewrite working code without a clear reason.
- Do not make unrelated changes.

---

## 4. STRICT SCOPE CONTROL

### Approved Core Functionality

#### STUDENT:
- Login
- Browse events
- Search/filter events
- View event details
- Register for events
- View registered events
- View participation history
- View certificates
- Submit feedback

#### CLUB / ADMIN:
- Login
- Dashboard
- Create events
- Edit events
- Delete events
- Manage registrations
- Approve/reject registrations
- Mark attendance
- Generate certificates
- View feedback
- View basic analytics

### Optional Features
Optional features may only be added after the complete core application is working:
- Campus Pulse
- Achievement badges
- Campus Passport
- Notification panel
- Event Health indicators
- Small UI animations

### Prohibited Out-of-Scope Systems
Do **NOT** add large new systems such as:
- Chat
- Social networking
- AI recommendation systems
- Real-time messaging
- Maps / external API integrations
- Push notification infrastructure
- Complex QR systems
- Payment systems

*(unless explicitly requested later).*

---

## 5. UI / UX DESIGN RULES

The application must have a polished, modern dark SaaS dashboard aesthetic.

### Visual Reference:
A dark navy/purple modern analytics interface.

### Design Direction:
- Dark navy/purple background
- Subtle blue/purple gradients
- Rounded cards
- Subtle translucent surfaces
- Thin low-contrast borders
- Soft shadows/glows
- Clean typography
- Compact left sidebar
- Modern dashboard layout
- Clean charts
- Generous spacing
- Minimal visual clutter
- Professional SaaS appearance

The application should feel modern and premium.

### What to Avoid:
- Default JavaFX styling
- Default gray controls
- Excessive gradients
- Excessive glow
- Overly bright colors
- Giant buttons
- Cluttered screens
- Unnecessary animations
- Childish UI
- Old-fashioned desktop software appearance

### Animation Guidelines:
Use subtle animations only where they improve the experience:
- Hover states
- Slight card elevation
- Subtle fade transitions
- Selected navigation state
- Chart appearance animation

*Animations must never interfere with usability.*

---

## 6. COLOR DIRECTION

Use a dark visual system.

### Suggested Base Palette:
- **Background:** `#0B0A1A`
- **Panel / Surface:** `#14132A`
- **Primary Accent:** `#6366F1` (Indigo)
- **Secondary Accent:** `#8B5CF6` (Purple)
- **Blue Accent:** `#3B82F6`
- **Primary Text:** `#F5F5F7`
- **Secondary Text:** `#9695A8`

*Note:* These values serve as design guidance; nearby values that improve visual cohesion are permitted. Maintain visual consistency throughout the entire application.

---

## 7. APPLICATION ARCHITECTURE

Use a clean MVC-style architecture with DAO and service layers:

```
src/
├── model/           # Data & domain entity objects
├── dao/             # Database access and SQL queries
├── service/         # Business logic layer
├── controller/      # JavaFX UI event handlers & controllers
├── util/            # Shared utilities (DBConnection, session, etc.)
└── Main.java        # Application entry point

resources/
├── css/             # JavaFX styles (style.css)
└── images/          # Static icons & UI graphics
```

- **MODEL:** Data/domain objects.
- **DAO:** Database access and SQL operations.
- **SERVICE:** Business logic.
- **CONTROLLER:** JavaFX UI interaction.
- **UTIL:** Shared utilities such as database connection and session management.

*Rule:* Do not create unnecessary abstraction layers.

---

## 8. DATABASE RULES

- **Database:** MySQL
- **Approved Core Tables:**
  - `users`
  - `clubs`
  - `events`
  - `registrations`
  - `attendance`
  - `feedback`
  - `certificates`
- Do not create unnecessary tables unless required by an approved feature.
- **Security:** Always use JDBC `PreparedStatement` for database queries.
- **No string concatenation:** Never concatenate user input directly into SQL queries.
- **Credentials:** Database credentials must never be committed directly into source code.

---

## 9. SECURITY RULES

Implement sensible security practices:
- Validate all user input.
- Use `PreparedStatement` exclusively.
- Do not expose database credentials.
- Avoid logging passwords.
- Do not display sensitive credentials in error messages.
- Use appropriate password handling.
- Handle authentication errors gracefully.
- Do not introduce complex authentication frameworks.

---

## 10. ERROR HANDLING

- Errors must be handled gracefully.
- Never allow a database exception or unexpected error to crash the entire application.
- User-facing messages should be clean and understandable:
  - **Good:** *"Unable to load events. Please check your database connection."*
  - **Bad:** *"SQLException: Communications link failure at line 284..."*
- Detailed technical stack traces may be logged for debugging, but must not overwhelm the user.

---

## 11. CODE QUALITY

Follow standard Java conventions:
- Meaningful class and method names
- Meaningful variable names
- Small, focused methods
- Appropriate encapsulation
- Reusable components where helpful
- Clear separation of responsibilities

Avoid:
- Monolithic controller classes
- Duplicated SQL queries
- Duplicated business logic
- Hardcoded UI data
- Unnecessary global state
- Magic numbers
- Dead code
- Unused dependencies

*Comments:* Add comments only when they explain non-obvious logic. Do not fill the codebase with trivial boilerplate comments.

---

## 12. DATABASE ACCESS

- All database access must pass through DAO classes.
- Controllers must **not** contain SQL queries.
- Business rules belong in service classes, not directly inside UI controllers.
- Keep database operations simple, clear, and understandable.

---

## 13. TESTING RULE

After implementing any feature:
1. Compile the project.
2. Run the application.
3. Test the feature.
4. Check for regressions in existing functionality.
5. Fix errors before moving to the next phase.

*Do not build new features on top of known compilation or runtime errors.*

---

## 14. CHANGE MANAGEMENT

Before modifying existing code:
- Inspect the relevant files.
- Understand their current purpose.
- Make the smallest reasonable change.

### DO:
- Modify only relevant files.
- Preserve existing functionality.
- Reuse existing utilities and components.
- Follow the established architecture.

### DO NOT:
- Rewrite unrelated files.
- Change the database schema unnecessarily.
- Replace JavaFX with another UI technology.
- Introduce a new framework without approval.
- Reorganize the entire project unnecessarily.

---

## 15. PHASE DISCIPLINE

The project will be developed strictly in phases. Do not implement future phases unless explicitly instructed.

- **PHASE 0:** Environment and project foundation
- **PHASE 1:** UI design system and application shell
- **PHASE 2:** MySQL database and JDBC layer
- **PHASE 3:** Authentication
- **PHASE 4:** Student dashboard
- **PHASE 5:** Event browsing and search
- **PHASE 6:** Event registration
- **PHASE 7:** My Events
- **PHASE 8:** Admin dashboard
- **PHASE 9:** Event management
- **PHASE 10:** Registration management
- **PHASE 11:** Attendance
- **PHASE 12:** Certificates
- **PHASE 13:** Feedback
- **PHASE 14:** Analytics
- **PHASE 15:** Optional fun features
- **PHASE 16:** Final UI polish
- **PHASE 17:** Testing and bug fixing
- **PHASE 18:** Documentation and viva preparation

*Rule:* Only implement the phase explicitly requested by the user.

---

## 16. ANTIGRAVITY BEHAVIOR

When receiving a development instruction:
1. Inspect the current project first.
2. Read this `AGENTS.md` file.
3. Identify the current project state.
4. Determine the minimum required changes.
5. Implement only the requested feature.
6. Compile and test the application.
7. Report what was changed.
8. Report any errors that remain.
9. Do not silently introduce unrelated changes.

*If a requested feature conflicts with these rules, prefer simplicity and ask for clarification rather than making a large architectural change.*

---

## 17. "DONE" DEFINITION

A feature is **NOT** considered complete merely because code was written.

A feature is complete **only** when:
- Code compiles without errors.
- Application launches successfully.
- Feature works as intended.
- Existing functionality remains operational (no regressions).
- No obvious runtime exceptions or crashes remain.
- UI adheres to the established design system.

---

## 18. MOST IMPORTANT RULE

> **DO NOT OVERENGINEER CAMPUS CONNECT.**

This project must be:
- **Simple internally**
- **Polished externally**
- **Reliable**
- **Easy to understand**
- **Easy to debug**
- **Realistic enough to demonstrate during a university viva**

*When in doubt, choose the simpler implementation.*
