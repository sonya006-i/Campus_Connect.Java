package controller;

import dao.UserDAO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.User;
import util.PasswordSecurityUtil;
import util.Session;

import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * LoginController — Controls the Campus Connect login interface and authentication logic.
 *
 * Adheres strictly to AGENTS.md:
 * - Simple, robust validation before database interaction
 * - Zero external authentication dependencies
 * - Graceful error handling for missing credentials, bad passwords, and database connection failures
 */
public class LoginController {

    private final UserDAO userDAO;
    private final Consumer<User> onLoginSuccess;

    private StackPane rootNode;
    private TextField emailField;
    private PasswordField passwordField;
    private HBox errorBanner;
    private Label errorText;

    public LoginController(Consumer<User> onLoginSuccess) {
        this(new UserDAO(), onLoginSuccess);
    }

    public LoginController(UserDAO userDAO, Consumer<User> onLoginSuccess) {
        this.userDAO = userDAO != null ? userDAO : new UserDAO();
        this.onLoginSuccess = onLoginSuccess;
        buildView();
    }

    private void buildView() {
        rootNode = new StackPane();
        rootNode.getStyleClass().addAll("root", "login-root");
        rootNode.setAlignment(Pos.CENTER);
        rootNode.setPadding(new Insets(20));

        VBox card = new VBox(18);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        // 1. Branding Header
        HBox brandHeader = new HBox(8);
        brandHeader.setAlignment(Pos.CENTER_LEFT);

        Label brandTitle = new Label("CAMPUS");
        brandTitle.getStyleClass().add("login-brand-title");

        Label brandSubtitle = new Label("CONNECT");
        brandSubtitle.getStyleClass().add("login-brand-subtitle");

        Region brandSpacer = new Region();
        HBox.setHgrow(brandSpacer, Priority.ALWAYS);

        Label brandPill = new Label("v1.0");
        brandPill.getStyleClass().add("brand-pill");

        brandHeader.getChildren().addAll(brandTitle, brandSubtitle, brandSpacer, brandPill);

        // 2. Welcome Title
        VBox titleBox = new VBox(4);
        Label welcomeTitle = new Label("Sign In");
        welcomeTitle.getStyleClass().add("login-welcome-title");

        Label welcomeSubtitle = new Label("Enter your campus credentials to access the portal.");
        welcomeSubtitle.getStyleClass().add("login-welcome-subtitle");
        titleBox.getChildren().addAll(welcomeTitle, welcomeSubtitle);

        // 3. Error Banner (Hidden by default)
        errorBanner = new HBox(8);
        errorBanner.setAlignment(Pos.CENTER_LEFT);
        errorBanner.getStyleClass().add("error-banner");
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);

        Label errorIcon = new Label("⚠");
        errorIcon.setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");

        errorText = new Label();
        errorText.getStyleClass().add("error-text");
        errorText.setWrapText(true);

        errorBanner.getChildren().addAll(errorIcon, errorText);

        // 4. Input Fields
        VBox emailBox = new VBox(6);
        Label emailLabel = new Label("Email Address");
        emailLabel.getStyleClass().add("form-label");

        emailField = new TextField();
        emailField.setPromptText("e.g. sonya@student.edu");
        emailField.getStyleClass().add("text-input");
        emailField.setOnAction(e -> handleLogin());
        emailBox.getChildren().addAll(emailLabel, emailField);

        VBox passwordBox = new VBox(6);
        Label passwordLabel = new Label("Password");
        passwordLabel.getStyleClass().add("form-label");

        passwordField = new PasswordField();
        passwordField.setPromptText("••••••••");
        passwordField.getStyleClass().add("text-input");
        passwordField.setOnAction(e -> handleLogin());
        passwordBox.getChildren().addAll(passwordLabel, passwordField);

        // 5. Submit Button
        Button loginBtn = new Button("Sign In →");
        loginBtn.getStyleClass().add("login-btn");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setOnAction(e -> handleLogin());

        // 6. Demo Credentials Quick Fill Box (Viva & Demo friendly)
        VBox demoBox = new VBox(8);
        demoBox.getStyleClass().add("demo-credentials-box");

        Label demoTitle = new Label("Demo Accounts (Click to Autofill):");
        demoTitle.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11px; -fx-font-weight: bold;");

        VBox demoPills = new VBox(6);
        demoPills.setAlignment(Pos.CENTER_LEFT);

        Button studentPill = new Button("Student: sonya@student.edu");
        studentPill.setMaxWidth(Double.MAX_VALUE);
        studentPill.setAlignment(Pos.CENTER_LEFT);
        studentPill.getStyleClass().add("demo-pill");
        studentPill.setOnAction(e -> fillCredentials("sonya@student.edu", "student123"));

        Button adminPill = new Button("Admin: admin@campusconnect.edu");
        adminPill.setMaxWidth(Double.MAX_VALUE);
        adminPill.setAlignment(Pos.CENTER_LEFT);
        adminPill.getStyleClass().add("demo-pill");
        adminPill.setOnAction(e -> fillCredentials("admin@campusconnect.edu", "admin123"));

        demoPills.getChildren().addAll(studentPill, adminPill);
        demoBox.getChildren().addAll(demoTitle, demoPills);

        card.getChildren().addAll(
                brandHeader,
                titleBox,
                errorBanner,
                emailBox,
                passwordBox,
                loginBtn,
                demoBox
        );

        rootNode.getChildren().add(card);
    }

    /**
     * Executes the authentication flow.
     */
    public void handleLogin() {
        String email = emailField.getText() != null ? emailField.getText().trim() : "";
        String password = passwordField.getText() != null ? passwordField.getText().trim() : "";

        // 1. Validation: Disallow empty credentials without hitting the database
        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter both email and password.");
            return;
        }

        // 2. Query database and verify credentials using constant-time PBKDF2 comparison
        try {
            User user = userDAO.findByEmail(email);

            if (user == null || !PasswordSecurityUtil.verifyPassword(password, user.getPassword())) {
                showError("Invalid email or password. Please try again.");
                return;
            }

            // Controlled migration: If stored password was legacy plaintext, upgrade immediately to salted PBKDF2 hash
            if (PasswordSecurityUtil.isLegacyPlaintext(user.getPassword())) {
                try {
                    String oldStoredPassword = user.getPassword();
                    String upgradedHash = PasswordSecurityUtil.hashPassword(password);
                    boolean upgraded = userDAO.updatePasswordIfMatches(user.getUserId(), oldStoredPassword, upgradedHash);
                    if (upgraded) {
                        user.setPassword(upgradedHash);
                        System.out.println("[Security] Upgraded legacy credential for user ID " + user.getUserId() + " to PBKDF2.");
                    }
                } catch (Exception upgradeEx) {
                    // Non-fatal: do not block authenticated login, but log warning without sensitive data
                    System.err.println("[LoginController] Warning: Could not upgrade legacy credential for user: " + upgradeEx.getMessage());
                }
            }

            // 3. Authentication successful
            hideError();
            Session.setCurrentUser(user);

            if (onLoginSuccess != null) {
                onLoginSuccess.accept(user);
            }

        } catch (SQLException e) {
            System.err.println("[LoginController Error] Database failure during login: " + e.getMessage());
            showError("Unable to connect to the database. Please check your database connection.");
        } catch (Exception e) {
            System.err.println("[LoginController Error] Unexpected error during login: " + e.getMessage());
            showError("An unexpected error occurred. Please try again.");
        }
    }

    /**
     * Fills demo credentials for rapid testing during university vivas and automated verification.
     */
    public void fillCredentials(String email, String password) {
        emailField.setText(email);
        passwordField.setText(password);
        hideError();
    }

    /**
     * Displays a clean, non-crashing error notification to the user.
     */
    private void showError(String message) {
        errorText.setText(message);
        errorBanner.setVisible(true);
        errorBanner.setManaged(true);
    }

    /**
     * Hides the error banner.
     */
    private void hideError() {
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);
        errorText.setText("");
    }

    /**
     * Resets input fields and error messages upon logout.
     */
    public void resetForm() {
        if (passwordField != null) {
            passwordField.clear();
        }
        hideError();
    }

    public Parent getView() {
        return rootNode;
    }
}
