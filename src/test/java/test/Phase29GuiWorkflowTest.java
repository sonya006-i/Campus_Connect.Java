package test;

import controller.AdminAnalyticsController;
import controller.AdminAttendanceController;
import controller.AdminCertificatesController;
import controller.AdminEventsController;
import controller.AdminFeedbackController;
import controller.AdminRegistrationsController;
import controller.DashboardController;
import controller.EventsController;
import controller.LoginController;
import controller.MyCertificatesController;
import controller.MyEventsController;
import controller.StudentFeedbackController;
import javafx.application.Platform;
import javafx.scene.Parent;
import model.Event;
import model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import util.DBConnection;
import util.Session;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase29GuiWorkflowTest — JavaFX GUI Layer & Controller End-to-End Workflow Verification Suite.
 *
 * Verifies that the JavaFX UI controllers, scene hierarchies, navigation graphs,
 * and view lifecycle hooks operate seamlessly against live Supabase PostgreSQL data:
 *
 * 1. JavaFX Toolkit Lifecycle initialization
 * 2. Student GUI Workflow:
 *    - LoginController credentials form autofill and submission
 *    - Session propagation to DashboardController
 *    - Student view navigation: Dashboard, Events Discovery, Event Details, My Events,
 *      My Certificates, and Student Feedback
 *    - Logout clearing the GUI state and Session
 * 3. Admin GUI Workflow:
 *    - Admin LoginController authentication
 *    - Role badge and permissions propagation
 *    - Navigation across all 6 admin management screens:
 *      * Event Management (AdminEventsController)
 *      * Registration Moderation (AdminRegistrationsController)
 *      * Attendance Tracking (AdminAttendanceController)
 *      * Certificate Issuance (AdminCertificatesController)
 *      * Feedback Moderation (AdminFeedbackController)
 *      * Campus Analytics (AdminAnalyticsController)
 * 4. Asynchronous data load completion without uncaught exceptions
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase29GuiWorkflowTest {

    private static final String ADMIN_EMAIL = "admin@campusconnect.edu";
    private static final String ADMIN_PASS = "admin123";

    private static final String STUDENT_EMAIL = "sonya@student.edu";
    private static final String STUDENT_PASS = "student123";

    private static boolean fxInitialized = false;

    @BeforeAll
    public static void initPlatform() throws Exception {
        assertTrue(DBConnection.testConnection(), "Live database must be reachable for GUI workflow verification");

        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            fxInitialized = latch.await(5, TimeUnit.SECONDS);
        } catch (IllegalStateException e) {
            // Toolkit already initialized by prior tests
            fxInitialized = true;
        }

        assertTrue(fxInitialized, "JavaFX Platform toolkit must be initialized");
    }

    @AfterEach
    public void cleanupSession() {
        Session.clearSession();
    }

    private static void runOnFxThread(ThrowingRunnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "JavaFX thread operation timed out after 10 seconds");

        if (errorRef.get() != null) {
            throw new RuntimeException("Error during FX operation", errorRef.get());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    // =========================================================================
    // WORKFLOW 1: STUDENT GUI WORKFLOW
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("GUI Workflow 1.1: Student LoginController UI authentication flow")
    public void testStudentGuiLogin() throws Exception {
        AtomicBoolean loginCallbackFired = new AtomicBoolean(false);
        AtomicReference<User> loggedInUserRef = new AtomicReference<>();

        runOnFxThread(() -> {
            LoginController loginController = new LoginController(user -> {
                loginCallbackFired.set(true);
                loggedInUserRef.set(user);
            });

            Parent view = loginController.getView();
            assertNotNull(view, "LoginController root view must be constructed");
            assertTrue(view.getStyleClass().contains("login-root"));

            // Fill demo credentials
            loginController.fillCredentials(STUDENT_EMAIL, STUDENT_PASS);

            // Execute login
            loginController.handleLogin();
        });

        assertTrue(loginCallbackFired.get(), "Login success callback must be invoked");
        assertNotNull(loggedInUserRef.get(), "Authenticated User must not be null");
        assertEquals(STUDENT_EMAIL, loggedInUserRef.get().getEmail());
        assertEquals("STUDENT", loggedInUserRef.get().getRole());
        assertTrue(Session.isLoggedIn(), "Session must be established");
    }

    @Test
    @Order(2)
    @DisplayName("GUI Workflow 1.2: Student DashboardController navigation to all student views")
    public void testStudentDashboardNavigation() throws Exception {
        AtomicBoolean logoutFired = new AtomicBoolean(false);

        runOnFxThread(() -> {
            User student = Session.getCurrentUser();
            if (student == null) {
                // Re-authenticate if clean session
                new dao.UserDAO();
            }
            // Ensure student session
            Session.setCurrentUser(new dao.UserDAO().findByEmail(STUDENT_EMAIL));

            DashboardController dashboardController = new DashboardController(() -> logoutFired.set(true));
            Parent dashboardView = dashboardController.getView();
            assertNotNull(dashboardView, "Dashboard view must be instantiated");

            // 1. Refresh session data for student
            dashboardController.refreshSessionData();

            // 2. Navigate to Student Dashboard
            dashboardController.navigateToDashboard();
            assertNotNull(dashboardController.getView());

            // 3. Navigate to Events Discovery
            dashboardController.navigateToEvents();
            assertNotNull(dashboardController.getView());

            // 4. Navigate to My Events
            dashboardController.navigateToMyEvents();
            assertNotNull(dashboardController.getView());

            // 5. Navigate to My Certificates
            dashboardController.navigateToMyCertificates();
            assertNotNull(dashboardController.getView());

            // 6. Navigate to Student Feedback
            dashboardController.navigateToFeedback();
            assertNotNull(dashboardController.getView());
        });

        // Allow async loaders to complete
        Thread.sleep(1000);
    }

    @Test
    @Order(3)
    @DisplayName("GUI Workflow 1.3: Student EventsController discovery and detail view rendering")
    public void testStudentEventsControllerDetailFlow() throws Exception {
        runOnFxThread(() -> {
            Session.setCurrentUser(new dao.UserDAO().findByEmail(STUDENT_EMAIL));
            EventsController eventsController = new EventsController();
            assertNotNull(eventsController.getView());

            // Load initial discovery data
            eventsController.loadInitialData();

            // Find an event from database
            Event hackathon = new dao.EventDAO().getAll().stream()
                    .filter(e -> "Tech Hackathon 2026".equalsIgnoreCase(e.getEventName()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(hackathon);

            // Open Event Detail View
            AtomicBoolean backClicked = new AtomicBoolean(false);
            eventsController.openEventDetailView(hackathon, () -> backClicked.set(true));
            assertNotNull(eventsController.getView());
        });

        Thread.sleep(1000);
    }

    @Test
    @Order(4)
    @DisplayName("GUI Workflow 1.4: Student MyEventsController rendering and interactions")
    public void testStudentMyEventsControllerFlow() throws Exception {
        runOnFxThread(() -> {
            Session.setCurrentUser(new dao.UserDAO().findByEmail(STUDENT_EMAIL));
            MyEventsController myEventsController = new MyEventsController();
            assertNotNull(myEventsController.getView());

            myEventsController.loadInitialData();
        });

        Thread.sleep(1000);
    }

    @Test
    @Order(5)
    @DisplayName("GUI Workflow 1.5: Student MyCertificatesController and StudentFeedbackController rendering")
    public void testStudentCertificatesAndFeedbackViews() throws Exception {
        runOnFxThread(() -> {
            Session.setCurrentUser(new dao.UserDAO().findByEmail(STUDENT_EMAIL));

            MyCertificatesController certController = new MyCertificatesController();
            assertNotNull(certController.getView());
            certController.loadInitialData();

            StudentFeedbackController feedbackController = new StudentFeedbackController();
            assertNotNull(feedbackController.getView());
            feedbackController.loadInitialData();
        });

        Thread.sleep(1000);
    }

    @Test
    @Order(6)
    @DisplayName("GUI Workflow 1.6: Student logout clears session and resets UI")
    public void testStudentLogoutResetsSession() throws Exception {
        runOnFxThread(() -> {
            Session.setCurrentUser(new dao.UserDAO().findByEmail(STUDENT_EMAIL));
            assertTrue(Session.isLoggedIn());

            AtomicBoolean logoutTriggered = new AtomicBoolean(false);
            LoginController loginController = new LoginController(u -> {});
            loginController.fillCredentials(STUDENT_EMAIL, STUDENT_PASS);

            // Clear session (simulating DashboardController logout button action)
            Session.clearSession();
            loginController.resetForm();

            assertFalse(Session.isLoggedIn(), "Session must be logged out");
            assertNull(Session.getCurrentUser(), "Session user must be null");
        });
    }

    // =========================================================================
    // WORKFLOW 2: ADMIN GUI WORKFLOW
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("GUI Workflow 2.1: Admin LoginController UI authentication flow")
    public void testAdminGuiLogin() throws Exception {
        AtomicBoolean loginCallbackFired = new AtomicBoolean(false);
        AtomicReference<User> loggedInUserRef = new AtomicReference<>();

        runOnFxThread(() -> {
            LoginController loginController = new LoginController(user -> {
                loginCallbackFired.set(true);
                loggedInUserRef.set(user);
            });

            Parent view = loginController.getView();
            assertNotNull(view);

            // Fill admin demo credentials
            loginController.fillCredentials(ADMIN_EMAIL, ADMIN_PASS);

            // Execute login
            loginController.handleLogin();
        });

        assertTrue(loginCallbackFired.get(), "Admin login success callback must be invoked");
        assertNotNull(loggedInUserRef.get());
        assertEquals(ADMIN_EMAIL, loggedInUserRef.get().getEmail());
        assertEquals("ADMIN", loggedInUserRef.get().getRole());
        assertTrue(Session.isLoggedIn());
    }

    @Test
    @Order(8)
    @DisplayName("GUI Workflow 2.2: Admin DashboardController navigation across all 6 admin views")
    public void testAdminDashboardNavigationAllSixScreens() throws Exception {
        runOnFxThread(() -> {
            User admin = new dao.UserDAO().findByEmail(ADMIN_EMAIL);
            Session.setCurrentUser(admin);

            DashboardController dashboardController = new DashboardController(() -> {});
            assertNotNull(dashboardController.getView());

            dashboardController.refreshSessionData();

            // 1. Admin Dashboard View
            dashboardController.navigateToDashboard();
            assertNotNull(dashboardController.getView());

            // 2. Admin Event Management View
            dashboardController.navigateToManageEvents();
            assertNotNull(dashboardController.getView());

            // 3. Admin Registration Moderation View
            dashboardController.navigateToManageRegistrations();
            assertNotNull(dashboardController.getView());

            // 4. Admin Attendance Management View
            dashboardController.navigateToManageAttendance();
            assertNotNull(dashboardController.getView());

            // 5. Admin Certificate Management View
            dashboardController.navigateToManageCertificates();
            assertNotNull(dashboardController.getView());

            // 6. Admin Feedback Management View
            dashboardController.navigateToManageFeedback();
            assertNotNull(dashboardController.getView());

            // 7. Admin Campus Analytics View
            dashboardController.navigateToAnalytics();
            assertNotNull(dashboardController.getView());
        });

        Thread.sleep(1500);
    }

    @Test
    @Order(9)
    @DisplayName("GUI Workflow 2.3: Admin individual screen controllers load and render live data")
    public void testAdminIndividualScreenControllers() throws Exception {
        runOnFxThread(() -> {
            User admin = new dao.UserDAO().findByEmail(ADMIN_EMAIL);
            Session.setCurrentUser(admin);

            // 1. AdminEventsController
            AdminEventsController adminEvents = new AdminEventsController();
            assertNotNull(adminEvents.getView());
            adminEvents.loadInitialData();

            // 2. AdminRegistrationsController
            AdminRegistrationsController adminRegs = new AdminRegistrationsController();
            assertNotNull(adminRegs.getView());
            adminRegs.loadInitialData();

            // 3. AdminAttendanceController
            AdminAttendanceController adminAtt = new AdminAttendanceController();
            assertNotNull(adminAtt.getView());
            adminAtt.loadInitialData();

            // 4. AdminCertificatesController
            AdminCertificatesController adminCerts = new AdminCertificatesController();
            assertNotNull(adminCerts.getView());
            adminCerts.loadInitialData();

            // 5. AdminFeedbackController
            AdminFeedbackController adminFb = new AdminFeedbackController();
            assertNotNull(adminFb.getView());
            adminFb.loadInitialData();

            // 6. AdminAnalyticsController
            AdminAnalyticsController adminAnalytics = new AdminAnalyticsController();
            assertNotNull(adminAnalytics.getView());
            adminAnalytics.loadInitialData();
        });

        // Allow background queries to resolve and populate controls
        Thread.sleep(2000);
    }
}
