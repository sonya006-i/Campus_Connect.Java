import controller.DashboardController;
import controller.LoginController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import model.User;

/**
 * Main — Entry point for the Campus Connect Desktop Application.
 *
 * Implements Phase 3 Authentication navigation flow:
 * Application Launch -> Login Screen -> Role-based Dashboard -> Logout -> Login Screen.
 */
public class Main extends Application {

    private static final String APP_TITLE = "Campus Connect — Campus Event & Club Management System";
    private static final int DEFAULT_WIDTH = 1440;
    private static final int DEFAULT_HEIGHT = 900;
    private static final int MIN_WIDTH = 1100;
    private static final int MIN_HEIGHT = 700;

    private Stage primaryStage;
    private Scene scene;

    private LoginController loginController;
    private DashboardController dashboardController;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Initialize controllers with lifecycle navigation callbacks
        this.loginController = new LoginController(this::showDashboardView);
        this.dashboardController = new DashboardController(this::showLoginView);

        // Placeholder root until initial view loads
        StackPane baseRoot = new StackPane();
        baseRoot.getStyleClass().add("root");

        this.scene = new Scene(baseRoot, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // Load custom dark SaaS stylesheet
        try {
            String cssPath = getClass().getResource("/css/style.css") != null
                    ? getClass().getResource("/css/style.css").toExternalForm()
                    : null;
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
        } catch (Exception ex) {
            System.err.println("[Main] Warning: Could not load stylesheet: " + ex.getMessage());
        }

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);

        // Application launches directly into the Login Screen
        showLoginView();

        primaryStage.show();
        System.out.println("[Main] Campus Connect launched successfully on Login Screen.");
    }

    /**
     * Navigates to the Login Screen.
     */
    public void showLoginView() {
        loginController.resetForm();
        scene.setRoot(loginController.getView());
        primaryStage.setTitle("Campus Connect — Sign In");
    }

    /**
     * Navigates to the Role-based Dashboard for the authenticated user.
     */
    public void showDashboardView(User user) {
        dashboardController.refreshSessionData();
        scene.setRoot(dashboardController.getView());

        String roleTitle = (user != null && "ADMIN".equalsIgnoreCase(user.getRole()))
                ? "Admin Dashboard"
                : "Student Dashboard";
        primaryStage.setTitle("Campus Connect — " + roleTitle);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
