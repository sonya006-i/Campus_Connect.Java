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
import dao.EventDAO;
import dao.UserDAO;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import model.Event;
import model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import util.DBConnection;
import util.Session;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase30UiCaptureUtil — Automated Visual Inspection & High-Fidelity UI Capture Engine.
 *
 * Renders the full Campus Connect application screens at 1440x900 resolution with the
 * official stylesheet applied, allows asynchronous database queries to populate UI widgets,
 * and saves crisp PNG screenshots to docs/screenshots/ for visual inspection.
 */
public class Phase30UiCaptureUtil {

    private static final String SCREENSHOT_DIR = "docs/screenshots";
    private static final int SCENE_WIDTH = 1440;
    private static final int SCENE_HEIGHT = 900;

    private static final String STUDENT_EMAIL = "sonya@student.edu";
    private static final String ADMIN_EMAIL = "admin@campusconnect.edu";

    private static boolean fxInitialized = false;

    @BeforeAll
    public static void setUp() throws Exception {
        assertTrue(DBConnection.testConnection(), "Live database must be reachable");

        File dir = new File(SCREENSHOT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            fxInitialized = latch.await(5, TimeUnit.SECONDS);
        } catch (IllegalStateException e) {
            fxInitialized = true;
        }

        assertTrue(fxInitialized, "JavaFX Toolkit must be active");
    }

    private static void saveSceneSnapshot(Scene scene, String filename) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WritableImage> imgRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                WritableImage snapshot = scene.snapshot(null);
                imgRef.set(snapshot);
            } finally {
                latch.countDown();
            }
        });

        latch.await(10, TimeUnit.SECONDS);
        WritableImage fxImage = imgRef.get();
        assertNotNull(fxImage, "Snapshot must not be null for " + filename);

        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();
        PixelReader reader = fxImage.getPixelReader();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, reader.getArgb(x, y));
            }
        }

        File outFile = new File(SCREENSHOT_DIR, filename);
        ImageIO.write(bufferedImage, "png", outFile);
        System.out.println("[Phase30 Visual Inspection] Captured: " + outFile.getAbsolutePath() + " (" + width + "x" + height + ")");
    }

    private static Scene createStyledScene(Parent root) {
        Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
        try {
            String cssPath = Phase30UiCaptureUtil.class.getResource("/css/style.css") != null
                    ? Phase30UiCaptureUtil.class.getResource("/css/style.css").toExternalForm()
                    : null;
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
        } catch (Exception ex) {
            System.err.println("Could not load CSS: " + ex.getMessage());
        }
        return scene;
    }

    private static void runOnFx(Runnable action) throws Exception {
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

        latch.await(10, TimeUnit.SECONDS);
        if (errorRef.get() != null) {
            throw new RuntimeException("Error during FX operation", errorRef.get());
        }
    }

    @Test
    @DisplayName("Capture All Core Application Screens for Visual Inspection")
    public void captureAllApplicationScreens() throws Exception {
        User student = new UserDAO().findByEmail(STUDENT_EMAIL);
        User admin = new UserDAO().findByEmail(ADMIN_EMAIL);
        assertNotNull(student);
        assertNotNull(admin);

        // -------------------------------------------------------------
        // 1. LOGIN SCREEN
        // -------------------------------------------------------------
        AtomicReference<Scene> loginSceneRef = new AtomicReference<>();
        runOnFx(() -> {
            LoginController loginController = new LoginController(u -> {});
            loginController.fillCredentials(STUDENT_EMAIL, "student123");
            Scene scene = createStyledScene(loginController.getView());
            loginSceneRef.set(scene);
        });
        saveSceneSnapshot(loginSceneRef.get(), "01_login_screen.png");

        // -------------------------------------------------------------
        // 2. STUDENT DASHBOARD
        // -------------------------------------------------------------
        AtomicReference<Scene> studentDashSceneRef = new AtomicReference<>();
        AtomicReference<DashboardController> studentDashControllerRef = new AtomicReference<>();
        runOnFx(() -> {
            Session.setCurrentUser(student);
            DashboardController dash = new DashboardController(() -> {});
            studentDashControllerRef.set(dash);
            dash.refreshSessionData();
            dash.navigateToDashboard();
            Scene scene = createStyledScene(dash.getView());
            studentDashSceneRef.set(scene);
        });
        Thread.sleep(7000);
        saveSceneSnapshot(studentDashSceneRef.get(), "02_student_dashboard.png");

        // -------------------------------------------------------------
        // 3. STUDENT EVENT DISCOVERY
        // -------------------------------------------------------------
        runOnFx(() -> {
            studentDashControllerRef.get().navigateToEvents();
        });
        Thread.sleep(2500);
        saveSceneSnapshot(studentDashSceneRef.get(), "03_student_event_discovery.png");

        // -------------------------------------------------------------
        // 4. STUDENT EVENT DETAIL VIEW
        // -------------------------------------------------------------
        Event hackathon = new EventDAO().getAll().stream()
                .filter(e -> "Tech Hackathon 2026".equalsIgnoreCase(e.getEventName()))
                .findFirst().orElse(null);
        assertNotNull(hackathon);

        runOnFx(() -> {
            studentDashControllerRef.get().navigateToEventDetails(hackathon);
        });
        Thread.sleep(2500);
        saveSceneSnapshot(studentDashSceneRef.get(), "04_student_event_detail.png");

        // -------------------------------------------------------------
        // 5. STUDENT MY EVENTS
        // -------------------------------------------------------------
        runOnFx(() -> {
            studentDashControllerRef.get().navigateToMyEvents();
        });
        Thread.sleep(2500);
        saveSceneSnapshot(studentDashSceneRef.get(), "05_student_my_events.png");

        // -------------------------------------------------------------
        // 6. STUDENT MY CERTIFICATES
        // -------------------------------------------------------------
        runOnFx(() -> {
            studentDashControllerRef.get().navigateToMyCertificates();
        });
        Thread.sleep(2500);
        saveSceneSnapshot(studentDashSceneRef.get(), "06_student_my_certificates.png");

        // -------------------------------------------------------------
        // 7. STUDENT FEEDBACK
        // -------------------------------------------------------------
        runOnFx(() -> {
            studentDashControllerRef.get().navigateToFeedback();
        });
        Thread.sleep(2500);
        saveSceneSnapshot(studentDashSceneRef.get(), "07_student_feedback.png");

        // -------------------------------------------------------------
        // 8. ADMIN DASHBOARD
        // -------------------------------------------------------------
        AtomicReference<Scene> adminDashSceneRef = new AtomicReference<>();
        AtomicReference<DashboardController> adminDashControllerRef = new AtomicReference<>();
        runOnFx(() -> {
            Session.setCurrentUser(admin);
            DashboardController dash = new DashboardController(() -> {});
            adminDashControllerRef.set(dash);
            dash.refreshSessionData();
            dash.navigateToDashboard();
            Scene scene = createStyledScene(dash.getView());
            adminDashSceneRef.set(scene);
        });
        Thread.sleep(2500);
        saveSceneSnapshot(adminDashSceneRef.get(), "08_admin_dashboard.png");

        // -------------------------------------------------------------
        // 9. ADMIN EVENT MANAGEMENT
        // -------------------------------------------------------------
        runOnFx(() -> {
            adminDashControllerRef.get().navigateToManageEvents();
        });
        Thread.sleep(2500);
        saveSceneSnapshot(adminDashSceneRef.get(), "09_admin_events.png");

        // -------------------------------------------------------------
        // 10. ADMIN REGISTRATION MODERATION
        // -------------------------------------------------------------
        runOnFx(() -> {
            adminDashControllerRef.get().navigateToManageRegistrations();
        });
        Thread.sleep(2500);
        saveSceneSnapshot(adminDashSceneRef.get(), "10_admin_registrations.png");

        // -------------------------------------------------------------
        // 11. ADMIN ATTENDANCE MANAGEMENT
        // -------------------------------------------------------------
        runOnFx(() -> {
            adminDashControllerRef.get().navigateToManageAttendance();
        });
        Thread.sleep(2500);
        saveSceneSnapshot(adminDashSceneRef.get(), "11_admin_attendance.png");

        // -------------------------------------------------------------
        // 12. ADMIN CERTIFICATE MANAGEMENT
        // -------------------------------------------------------------
        runOnFx(() -> {
            adminDashControllerRef.get().navigateToManageCertificates();
        });
        Thread.sleep(2500);
        saveSceneSnapshot(adminDashSceneRef.get(), "12_admin_certificates.png");

        // -------------------------------------------------------------
        // 13. ADMIN FEEDBACK MANAGEMENT
        // -------------------------------------------------------------
        runOnFx(() -> {
            adminDashControllerRef.get().navigateToManageFeedback();
        });
        Thread.sleep(2500);
        saveSceneSnapshot(adminDashSceneRef.get(), "13_admin_feedback.png");

        // -------------------------------------------------------------
        // 14. ADMIN CAMPUS ANALYTICS
        // -------------------------------------------------------------
        runOnFx(() -> {
            adminDashControllerRef.get().navigateToAnalytics();
        });
        Thread.sleep(1200);
        saveSceneSnapshot(adminDashSceneRef.get(), "14_admin_analytics.png");

        Session.clearSession();
    }
}
