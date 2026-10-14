package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import model.AdminDashboardDTO;
import model.CampusPulseActivityDTO;
import model.Event;
import model.PopularEventDTO;
import model.StudentAchievementDTO;
import model.StudentDashboardDTO;
import model.User;
import service.AdminDashboardService;
import service.StudentDashboardService;
import util.Session;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DashboardController — Renders and controls the main Campus Connect dashboard shell.
 *
 * Implements Phase 4, Phase 5, and Phase 8:
 * - Dynamic time-based greeting and role badges for Students & Admins
 * - Database-backed KPIs for both Student personal records and Admin campus-wide metrics
 * - Live upcoming events list with registration counts and status tags
 * - Integrated Events Discovery controller and view switching
 * - Graceful empty-state and database-offline handling
 */
public class DashboardController {

    private final Runnable onLogout;
    private final StudentDashboardService dashboardService;
    private final AdminDashboardService adminDashboardService;
    private final List<Button> navButtons = new ArrayList<>();

    private BorderPane rootLayout;
    private ScrollPane mainScrollPane;
    private EventsController eventsController;
    private MyEventsController myEventsController;
    private AdminEventsController adminEventsController;
    private AdminRegistrationsController adminRegistrationsController;
    private AdminAttendanceController adminAttendanceController;
    private MyCertificatesController myCertificatesController;
    private AdminCertificatesController adminCertificatesController;
    private StudentFeedbackController studentFeedbackController;
    private AdminFeedbackController adminFeedbackController;
    private AdminAnalyticsController adminAnalyticsController;

    private Button navDashboardBtn;
    private Button navEventsBtn;
    private Button navMyEventsBtn;
    private Button navManageEventsBtn;
    private Button navManageRegistrationsBtn;
    private Button navManageAttendanceBtn;
    private Button navManageCertificatesBtn;
    private Button navManageFeedbackBtn;
    private Button navAnalyticsBtn;
    private Button navCertificatesBtn;
    private Button navFeedbackBtn;

    private Label headerSubtitleLabel;
    private Label userNameLabel;
    private Label userRoleLabel;
    private Label portalBadgeLabel;
    private Circle portalStatusDot;
    private Circle userAvatarCircle;

    private StackPane rootStack;
    private StackPane modalOverlay;
    private Label navEventsHeading;
    private Label navCampusHeading;
    private Button navClubs;
    private Button navSettings;

    // 4 KPI Cards UI Elements (dynamically populated for Student or Admin)
    private Label card1TitleLabel;
    private Label card1ValueLabel;
    private Label card1SubtextLabel;
    private Label card1BadgeLabel;

    private VBox card2Box;
    private Label card2TitleLabel;
    private Label card2ValueLabel;
    private Label card2SubtextLabel;
    private Label card2BadgeLabel;

    private Label card3TitleLabel;
    private Label card3ValueLabel;
    private Label card3SubtextLabel;
    private Label card3BadgeLabel;

    private VBox card4Box;
    private Label card4TitleLabel;
    private Label card4ValueLabel;
    private Label card4SubtextLabel;
    private Label card4BadgeLabel;

    // Middle Chart Card
    private VBox chartCard;

    // Dynamic containers & titles for bottom sections
    private Label upcomingCardTitle;
    private Label upcomingCardSubtitle;
    private Button upcomingCardActionBtn;
    private Button upcomingCardCreateBtn;
    private VBox upcomingEventsListContainer;

    private Label activityCardTitle;
    private Label activityCardSubtitle;
    private VBox activitySummaryContainer;

    public DashboardController(Runnable onLogout) {
        this.onLogout = onLogout;
        this.dashboardService = new StudentDashboardService();
        this.adminDashboardService = new AdminDashboardService();
        this.eventsController = new EventsController();
        this.myEventsController = new MyEventsController();
        this.adminEventsController = new AdminEventsController();
        this.adminRegistrationsController = new AdminRegistrationsController();

        // Wire EventsController callbacks
        this.eventsController.setOnNavigateMyEvents(this::navigateToMyEvents);

        // Wire MyEvents navigation callbacks
        this.myEventsController.setOnExploreEvents(this::navigateToEvents);
        this.myEventsController.setOnViewEventDetails(this::navigateToEventDetails);
        this.myEventsController.setOnNavigateDashboard(this::navigateToDashboard);

        // Wire AdminEventsController navigation callbacks
        this.adminEventsController.setOnNavigateDashboard(this::navigateToDashboard);
        this.adminEventsController.setOnViewEventDetails(this::navigateToEventDetails);

        // Wire AdminRegistrationsController navigation callbacks
        this.adminRegistrationsController.setOnNavigateDashboard(this::navigateToDashboard);

        // Wire AdminAttendanceController navigation callbacks
        this.adminAttendanceController = new AdminAttendanceController();
        this.adminAttendanceController.setOnNavigateDashboard(this::navigateToDashboard);

        // Wire Certificate Management navigation callbacks (Phase 12)
        this.myCertificatesController = new MyCertificatesController();
        this.myCertificatesController.setOnNavigateDashboard(this::navigateToDashboard);
        this.myCertificatesController.setOnExploreEvents(this::navigateToEvents);

        this.adminCertificatesController = new AdminCertificatesController();
        this.adminCertificatesController.setOnNavigateDashboard(this::navigateToDashboard);

        // Wire Feedback Management navigation callbacks (Phase 13)
        this.studentFeedbackController = new StudentFeedbackController();
        this.studentFeedbackController.setOnNavigateDashboard(this::navigateToDashboard);
        this.studentFeedbackController.setOnExploreEvents(this::navigateToEvents);

        this.adminFeedbackController = new AdminFeedbackController();
        this.adminFeedbackController.setOnNavigateDashboard(this::navigateToDashboard);

        // Wire Analytics navigation callbacks (Phase 14)
        this.adminAnalyticsController = new AdminAnalyticsController();
        this.adminAnalyticsController.setOnNavigateDashboard(this::navigateToDashboard);

        buildView();
    }

    private void buildView() {
        rootLayout = new BorderPane();
        rootLayout.getStyleClass().add("root");

        // 1. Left Navigation Sidebar
        VBox sidebar = createSidebar();
        rootLayout.setLeft(sidebar);

        // 2. Central Scrollable Dashboard Content
        mainScrollPane = createMainContentScrollPane();
        rootLayout.setCenter(mainScrollPane);

        // 3. Modal Overlay for in-dashboard dialogs (Settings, etc.)
        modalOverlay = new StackPane();
        modalOverlay.getStyleClass().add("dialog-overlay");
        modalOverlay.setVisible(false);
        modalOverlay.setPickOnBounds(true);

        rootStack = new StackPane(rootLayout, modalOverlay);
        rootStack.getStyleClass().add("root");
    }

    /**
     * Navigates the center area to the main Dashboard view.
     */
    public void navigateToDashboard() {
        setActiveNavButton(navDashboardBtn);
        rootLayout.setCenter(mainScrollPane);

        User user = Session.getCurrentUser();
        if (user != null && "ADMIN".equalsIgnoreCase(user.getRole())) {
            loadAdminDashboardData();
        } else if (user != null && "STUDENT".equalsIgnoreCase(user.getRole())) {
            loadStudentDashboardData(user.getUserId());
        }
    }

    /**
     * Navigates the center area to the Events Discovery view.
     */
    public void navigateToEvents() {
        setActiveNavButton(navEventsBtn);
        rootLayout.setCenter(eventsController.getView());
        eventsController.loadInitialData();
    }

    /**
     * Navigates the center area to the My Events view.
     */
    public void navigateToMyEvents() {
        setActiveNavButton(navMyEventsBtn);
        rootLayout.setCenter(myEventsController.getView());
        myEventsController.loadInitialData();
    }

    /**
     * Navigates from My Events or Dashboard to the Event Detail view in EventsController.
     */
    public void navigateToEventDetails(Event event) {
        setActiveNavButton(navEventsBtn);
        rootLayout.setCenter(eventsController.getView());
        eventsController.openEventDetailView(event, () -> {
            User user = Session.getCurrentUser();
            if (user != null && "ADMIN".equalsIgnoreCase(user.getRole())) {
                navigateToDashboard();
            } else {
                navigateToMyEvents();
            }
        });
    }

    /**
     * Navigates the center area to the Admin Event Management view.
     */
    public void navigateToManageEvents() {
        setActiveNavButton(navManageEventsBtn);
        rootLayout.setCenter(adminEventsController.getView());
        adminEventsController.loadInitialData();
    }

    /**
     * Navigates the center area to the Admin Registration Management view.
     */
    public void navigateToManageRegistrations() {
        User user = Session.getCurrentUser();
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            navigateToDashboard();
            return;
        }
        setActiveNavButton(navManageRegistrationsBtn);
        rootLayout.setCenter(adminRegistrationsController.getView());
        adminRegistrationsController.loadInitialData();
    }

    /**
     * Navigates the center area to the Admin Attendance Management view.
     */
    public void navigateToManageAttendance() {
        User user = Session.getCurrentUser();
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            navigateToDashboard();
            return;
        }
        setActiveNavButton(navManageAttendanceBtn);
        rootLayout.setCenter(adminAttendanceController.getView());
        adminAttendanceController.loadInitialData();
    }

    /**
     * Navigates the center area to the Student My Certificates view.
     */
    public void navigateToMyCertificates() {
        User user = Session.getCurrentUser();
        if (user == null) {
            navigateToDashboard();
            return;
        }
        setActiveNavButton(navCertificatesBtn);
        rootLayout.setCenter(myCertificatesController.getView());
        myCertificatesController.loadInitialData();
    }

    /**
     * Navigates the center area to the Admin Certificate Management view.
     */
    public void navigateToManageCertificates() {
        User user = Session.getCurrentUser();
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            navigateToDashboard();
            return;
        }
        setActiveNavButton(navManageCertificatesBtn);
        rootLayout.setCenter(adminCertificatesController.getView());
        adminCertificatesController.loadInitialData();
    }

    /**
     * Navigates the center area to the Student Feedback view.
     */
    public void navigateToFeedback() {
        User user = Session.getCurrentUser();
        if (user == null) {
            navigateToDashboard();
            return;
        }
        setActiveNavButton(navFeedbackBtn);
        rootLayout.setCenter(studentFeedbackController.getView());
        studentFeedbackController.loadInitialData();
    }

    /**
     * Navigates the center area to the Admin Feedback Management view.
     */
    public void navigateToManageFeedback() {
        User user = Session.getCurrentUser();
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            navigateToDashboard();
            return;
        }
        setActiveNavButton(navManageFeedbackBtn);
        rootLayout.setCenter(adminFeedbackController.getView());
        adminFeedbackController.loadInitialData();
    }

    /**
     * Navigates the center area to the Admin Campus Analytics view.
     */
    public void navigateToAnalytics() {
        User user = Session.getCurrentUser();
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            navigateToDashboard();
            return;
        }
        setActiveNavButton(navAnalyticsBtn);
        rootLayout.setCenter(adminAnalyticsController.getView());
        adminAnalyticsController.loadInitialData();
    }

    /**
     * Directly navigates to Event Management and opens the Create Event dialog.
     */
    public void openCreateEventFlow() {
        setActiveNavButton(navManageEventsBtn);
        rootLayout.setCenter(adminEventsController.getView());
        adminEventsController.loadInitialData();
        adminEventsController.openCreateEventDialog();
    }

    /**
     * Directly navigates to Event Management and opens the Edit Event dialog.
     */
    public void openEditEventFlow(Event event) {
        setActiveNavButton(navManageEventsBtn);
        rootLayout.setCenter(adminEventsController.getView());
        adminEventsController.loadInitialData();
        adminEventsController.openEditEventDialog(event);
    }

    private void setActiveNavButton(Button activeBtn) {
        if (activeBtn == null) return;
        for (Button btn : navButtons) {
            btn.getStyleClass().remove("nav-item-active");
            if (!btn.getStyleClass().contains("nav-item")) {
                btn.getStyleClass().add("nav-item");
            }
        }
        activeBtn.getStyleClass().remove("nav-item");
        if (!activeBtn.getStyleClass().contains("nav-item-active")) {
            activeBtn.getStyleClass().add("nav-item-active");
        }
    }

    /**
     * Refreshes dynamic session data and triggers asynchronous database loading.
     */
    public void refreshSessionData() {
        User user = Session.getCurrentUser();
        if (user == null) {
            return;
        }

        // Reset view to Dashboard overview by default
        navigateToDashboard();

        String roleName = user.getRole() != null ? user.getRole().toUpperCase() : "STUDENT";
        boolean isAdmin = "ADMIN".equals(roleName);

        // 1. Header & Sidebar User Card
        String greeting = getTimeBasedGreeting();
        if (headerSubtitleLabel != null) {
            headerSubtitleLabel.setText(isAdmin
                    ? greeting + ", " + user.getName() + "  •  Campus Administrator Overview"
                    : greeting + ", " + user.getName() + "  •  Here's what's happening on campus.");
        }

        if (userNameLabel != null) {
            userNameLabel.setText(user.getName());
        }

        if (userRoleLabel != null) {
            userRoleLabel.setText(isAdmin ? "System Administrator" : "Student Member");
        }

        if (userAvatarCircle != null) {
            userAvatarCircle.setStyle(isAdmin
                    ? "-fx-fill: linear-gradient(to bottom right, #3B82F6, #6366F1);"
                    : "-fx-fill: linear-gradient(to bottom right, #6366F1, #8B5CF6);");
        }

        // Sidebar adjustments based on role
        if (isAdmin) {
            if (navEventsHeading != null) navEventsHeading.setText("EVENT MANAGEMENT");
            if (navCampusHeading != null) navCampusHeading.setText("ADMINISTRATION");
            if (navClubs != null) {
                navClubs.setVisible(false);
                navClubs.setManaged(false);
            }
            // Set Admin KPI card titles immediately so no delay or mismatched flicker occurs
            if (card1TitleLabel != null) card1TitleLabel.setText("Total Events");
            if (card1BadgeLabel != null) {
                card1BadgeLabel.setText("Campus");
                card1BadgeLabel.getStyleClass().setAll("badge-indigo");
            }
            if (card2TitleLabel != null) card2TitleLabel.setText("Upcoming Events");
            if (card2BadgeLabel != null) {
                card2BadgeLabel.setText("Upcoming");
                card2BadgeLabel.getStyleClass().setAll("badge-purple");
            }
            if (card2Box != null) {
                card2Box.setOnMouseClicked(null);
                card2Box.setStyle("-fx-cursor: default;");
            }
            if (card3TitleLabel != null) card3TitleLabel.setText("Total Registrations");
            if (card3BadgeLabel != null) {
                card3BadgeLabel.setText("All Clubs");
                card3BadgeLabel.getStyleClass().setAll("badge-indigo");
            }
            if (card4TitleLabel != null) card4TitleLabel.setText("Pending Registrations");
            if (card4BadgeLabel != null) {
                card4BadgeLabel.setText("Pending");
                card4BadgeLabel.getStyleClass().setAll("badge-warning");
            }
            if (card4Box != null) {
                card4Box.setOnMouseClicked(e -> navigateToManageRegistrations());
                card4Box.setStyle("-fx-cursor: hand;");
            }
            if (upcomingCardTitle != null) upcomingCardTitle.setText("Campus Event Overview");
            if (upcomingCardSubtitle != null) upcomingCardSubtitle.setText("Recent & upcoming events across campus clubs");
            if (activityCardTitle != null) activityCardTitle.setText("Registration Overview");
            if (activityCardSubtitle != null) activityCardSubtitle.setText("Breakdown of campus-wide registration statuses");
        } else {
            if (navEventsHeading != null) navEventsHeading.setText("EVENTS");
            if (navCampusHeading != null) navCampusHeading.setText("CAMPUS");
            if (navClubs != null) {
                navClubs.setVisible(true);
                navClubs.setManaged(true);
            }
            // Set Student KPI card titles immediately
            if (card1TitleLabel != null) card1TitleLabel.setText("Total Events");
            if (card1BadgeLabel != null) {
                card1BadgeLabel.setText("Active");
                card1BadgeLabel.getStyleClass().setAll("badge-indigo");
            }
            if (card2TitleLabel != null) card2TitleLabel.setText("My Registrations");
            if (card2BadgeLabel != null) {
                card2BadgeLabel.setText("Registered");
                card2BadgeLabel.getStyleClass().setAll("badge-success");
            }
            if (card2Box != null) {
                card2Box.setOnMouseClicked(e -> navigateToMyEvents());
                card2Box.setStyle("-fx-cursor: hand;");
            }
            if (card3TitleLabel != null) card3TitleLabel.setText("Upcoming Events");
            if (card3BadgeLabel != null) {
                card3BadgeLabel.setText("Upcoming");
                card3BadgeLabel.getStyleClass().setAll("badge-purple");
            }
            if (card4TitleLabel != null) card4TitleLabel.setText("Events Attended");
            if (card4BadgeLabel != null) {
                card4BadgeLabel.setText("Attended");
                card4BadgeLabel.getStyleClass().setAll("badge-indigo");
            }
            if (card4Box != null) {
                card4Box.setOnMouseClicked(null);
                card4Box.setStyle("-fx-cursor: default;");
            }
            if (upcomingCardTitle != null) upcomingCardTitle.setText("Upcoming Events");
            if (upcomingCardSubtitle != null) upcomingCardSubtitle.setText("Scheduled campus club activities and workshops");
            if (activityCardTitle != null) activityCardTitle.setText("Your Activity");
            if (activityCardSubtitle != null) activityCardSubtitle.setText("Personalized campus participation summary");
        }

        if (navMyEventsBtn != null) {
            navMyEventsBtn.setVisible(!isAdmin);
            navMyEventsBtn.setManaged(!isAdmin);
        }
        if (navCertificatesBtn != null) {
            navCertificatesBtn.setVisible(!isAdmin);
            navCertificatesBtn.setManaged(!isAdmin);
        }
        if (navManageEventsBtn != null) {
            navManageEventsBtn.setVisible(isAdmin);
            navManageEventsBtn.setManaged(isAdmin);
        }
        if (navManageRegistrationsBtn != null) {
            navManageRegistrationsBtn.setVisible(isAdmin);
            navManageRegistrationsBtn.setManaged(isAdmin);
        }
        if (navManageAttendanceBtn != null) {
            navManageAttendanceBtn.setVisible(isAdmin);
            navManageAttendanceBtn.setManaged(isAdmin);
        }
        if (navManageCertificatesBtn != null) {
            navManageCertificatesBtn.setVisible(isAdmin);
            navManageCertificatesBtn.setManaged(isAdmin);
        }
        if (navManageFeedbackBtn != null) {
            navManageFeedbackBtn.setVisible(isAdmin);
            navManageFeedbackBtn.setManaged(isAdmin);
        }
        if (navAnalyticsBtn != null) {
            navAnalyticsBtn.setVisible(isAdmin);
            navAnalyticsBtn.setManaged(isAdmin);
        }
        if (navFeedbackBtn != null) {
            navFeedbackBtn.setVisible(!isAdmin);
            navFeedbackBtn.setManaged(!isAdmin);
        }

        if (upcomingCardActionBtn != null) {
            if (isAdmin) {
                upcomingCardActionBtn.setText("Manage Events →");
                upcomingCardActionBtn.setOnAction(e -> navigateToManageEvents());
            } else {
                upcomingCardActionBtn.setText("Explore Events →");
                upcomingCardActionBtn.setOnAction(e -> navigateToEvents());
            }
        }
        if (upcomingCardCreateBtn != null) {
            upcomingCardCreateBtn.setVisible(isAdmin);
            upcomingCardCreateBtn.setManaged(isAdmin);
        }

        // 2. Role-specific Dashboard Content
        if (isAdmin) {
            if (chartCard != null) {
                chartCard.setVisible(false);
                chartCard.setManaged(false);
            }
            if (portalBadgeLabel != null) {
                portalBadgeLabel.setText("Fall Term 2026 • Admin Portal");
            }
            if (portalStatusDot != null) {
                portalStatusDot.setStyle("-fx-fill: #3B82F6;");
            }
            loadAdminDashboardData();
        } else {
            if (chartCard != null) {
                chartCard.setVisible(true);
                chartCard.setManaged(true);
            }
            loadStudentDashboardData(user.getUserId());
        }
    }

    /**
     * Non-blocking background loader for student dashboard metrics.
     */
    private void loadStudentDashboardData(int studentId) {
        if (portalBadgeLabel != null) {
            portalBadgeLabel.setText("Connecting to database...");
        }

        new Thread(() -> {
            StudentDashboardDTO data = dashboardService.getDashboardData(studentId);
            Platform.runLater(() -> applyStudentDashboardData(data));
        }).start();
    }

    /**
     * Applies loaded dashboard data to the JavaFX UI controls.
     */
    private void applyStudentDashboardData(StudentDashboardDTO data) {
        if (card1TitleLabel != null) card1TitleLabel.setText("Total Events");
        if (card1BadgeLabel != null) {
            card1BadgeLabel.setText("Active");
            card1BadgeLabel.getStyleClass().setAll("badge-indigo");
        }

        if (card2TitleLabel != null) card2TitleLabel.setText("My Registrations");
        if (card2BadgeLabel != null) {
            card2BadgeLabel.setText("Registered");
            card2BadgeLabel.getStyleClass().setAll("badge-success");
        }
        if (card2Box != null) {
            card2Box.setOnMouseClicked(e -> navigateToMyEvents());
            card2Box.setStyle("-fx-cursor: hand;");
        }

        if (card3TitleLabel != null) card3TitleLabel.setText("Upcoming Events");
        if (card3BadgeLabel != null) {
            card3BadgeLabel.setText("Upcoming");
            card3BadgeLabel.getStyleClass().setAll("badge-purple");
        }

        if (card4TitleLabel != null) card4TitleLabel.setText("Events Attended");
        if (card4BadgeLabel != null) {
            card4BadgeLabel.setText("Attended");
            card4BadgeLabel.getStyleClass().setAll("badge-indigo");
        }
        if (card4Box != null) {
            card4Box.setOnMouseClicked(null);
            card4Box.setStyle("-fx-cursor: default;");
        }

        if (upcomingCardTitle != null) upcomingCardTitle.setText("Upcoming Events");
        if (upcomingCardSubtitle != null) upcomingCardSubtitle.setText("Scheduled campus club activities and workshops");
        if (activityCardTitle != null) activityCardTitle.setText("Your Activity");
        if (activityCardSubtitle != null) activityCardSubtitle.setText("Personalized campus participation summary");

        if (data.isDbOnline()) {
            if (portalBadgeLabel != null) {
                portalBadgeLabel.setText("Fall Term 2026 • Student Portal");
            }
            if (portalStatusDot != null) {
                portalStatusDot.setStyle("-fx-fill: #22C55E;");
            }

            // Update KPI Cards
            if (card1ValueLabel != null) card1ValueLabel.setText(String.valueOf(data.getTotalAvailableEvents()));
            if (card1SubtextLabel != null) card1SubtextLabel.setText("Available on campus");

            if (card2ValueLabel != null) card2ValueLabel.setText(String.valueOf(data.getRegisteredCount()));
            if (card2SubtextLabel != null) {
                card2SubtextLabel.setText(data.getRegisteredCount() == 1 ? "1 active registration" : data.getRegisteredCount() + " active registrations");
            }

            if (card3ValueLabel != null) card3ValueLabel.setText(String.valueOf(data.getUpcomingCount()));
            if (card3SubtextLabel != null) card3SubtextLabel.setText("Scheduled this term");

            if (card4ValueLabel != null) card4ValueLabel.setText(String.valueOf(data.getAttendedCount()));
            if (card4SubtextLabel != null) card4SubtextLabel.setText("Verified presence");

            // Update Upcoming Events with Campus Pulse Feed & Popular Events
            renderUpcomingEventsList(data.getUpcomingEvents(), data.getPulseFeed(), data.getPopularEvents());

            // Update Activity Summary with Derived Student Achievements
            renderStudentActivity(data.getRegisteredCount(), data.getAttendedCount(), data.getCertificatesCount(), data.getAchievements());

        } else {
            // Database offline / unreachable fallback state
            if (portalBadgeLabel != null) {
                portalBadgeLabel.setText("Database Offline • Local View");
            }
            if (portalStatusDot != null) {
                portalStatusDot.setStyle("-fx-fill: #F59E0B;");
            }

            if (card1ValueLabel != null) card1ValueLabel.setText("—");
            if (card1SubtextLabel != null) card1SubtextLabel.setText("Live data unavailable");

            if (card2ValueLabel != null) card2ValueLabel.setText("—");
            if (card2SubtextLabel != null) card2SubtextLabel.setText("Live data unavailable");

            if (card3ValueLabel != null) card3ValueLabel.setText("—");
            if (card3SubtextLabel != null) card3SubtextLabel.setText("Live data unavailable");

            if (card4ValueLabel != null) card4ValueLabel.setText("—");
            if (card4SubtextLabel != null) card4SubtextLabel.setText("Live data unavailable");

            renderOfflineUpcomingEvents();
            renderOfflineActivity();
        }
    }

    /**
     * Renders upcoming event cards, campus pulse feed, and popular events for student.
     */
    private void renderUpcomingEventsList(List<StudentDashboardDTO.EventItemDTO> items,
                                          List<CampusPulseActivityDTO> pulseFeed,
                                          List<PopularEventDTO> popularEvents) {
        if (upcomingEventsListContainer == null) return;
        upcomingEventsListContainer.getChildren().clear();

        // 1. Campus Milestone notification banner if present
        if (pulseFeed != null) {
            for (CampusPulseActivityDTO act : pulseFeed) {
                if (act.getType() == CampusPulseActivityDTO.ActivityType.CAMPUS_MILESTONE) {
                    HBox milestoneBox = new HBox(10);
                    milestoneBox.setAlignment(Pos.CENTER_LEFT);
                    milestoneBox.setStyle("-fx-background-color: rgba(99, 102, 241, 0.12); -fx-border-color: rgba(99, 102, 241, 0.28); -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 8px 12px;");

                    Label iconLabel = new Label(act.getIcon());
                    iconLabel.setStyle("-fx-font-size: 15px;");

                    VBox textCol = new VBox(2);
                    Label head = new Label(act.getHeadline());
                    head.setStyle("-fx-text-fill: #F5F5F7; -fx-font-weight: 700; -fx-font-size: 11.5px;");
                    Label sub = new Label(act.getSubtext());
                    sub.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 10.5px;");
                    textCol.getChildren().addAll(head, sub);

                    milestoneBox.getChildren().addAll(iconLabel, textCol);
                    upcomingEventsListContainer.getChildren().add(milestoneBox);
                    break;
                }
            }
        }

        // 2. Upcoming events
        if (items == null || items.isEmpty()) {
            VBox emptyBox = new VBox(6);
            emptyBox.getStyleClass().add("empty-state-box");

            Label emptyTitle = new Label("No upcoming events scheduled right now");
            emptyTitle.getStyleClass().add("empty-state-title");

            Label emptySub = new Label("Check back soon for new club workshops and competitions.");
            emptySub.getStyleClass().add("empty-state-sub");

            emptyBox.getChildren().addAll(emptyTitle, emptySub);
            upcomingEventsListContainer.getChildren().add(emptyBox);
        } else {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

            for (StudentDashboardDTO.EventItemDTO item : items) {
                Event ev = item.getEvent();
                String dateStr = (ev.getEventDate() != null) ? ev.getEventDate().format(dateFormatter) : "TBD";
                String timeStr = (ev.getEventTime() != null) ? ev.getEventTime().format(timeFormatter) : "";
                String fullTime = timeStr.isEmpty() ? dateStr : dateStr + " • " + timeStr;

                VBox card = createStudentEventCard(
                        ev.getEventName(),
                        item.getClubName(),
                        ev.getCategory() != null ? ev.getCategory() : "Campus Event",
                        fullTime,
                        ev.getVenue() != null ? ev.getVenue() : "Campus Venue",
                        item.getRegistrationStatus()
                );
                upcomingEventsListContainer.getChildren().add(card);
            }
        }

        // 3. Campus Pulse — Popular / Trending on Campus
        if (popularEvents != null && !popularEvents.isEmpty()) {
            VBox popularBox = new VBox(8);
            popularBox.getStyleClass().add("pulse-summary-box");
            popularBox.setStyle("-fx-background-color: rgba(20, 19, 42, 0.65); -fx-border-color: rgba(255, 255, 255, 0.08); -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 10px 12px;");

            Label popTitle = new Label("🔥  Campus Pulse — High Interest Events");
            popTitle.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12px; -fx-font-weight: 700;");
            popularBox.getChildren().add(popTitle);

            for (PopularEventDTO pop : popularEvents) {
                if (pop.getRegistrationCount() > 0) {
                    popularBox.getChildren().add(createPopularEventRow(pop));
                }
            }

            if (popularBox.getChildren().size() > 1) {
                upcomingEventsListContainer.getChildren().add(popularBox);
            }
        }
    }

    private HBox createPopularEventRow(PopularEventDTO pop) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("pulse-item");

        Label nameLabel = new Label(pop.getEventName());
        nameLabel.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 11.5px; -fx-font-weight: 600;");

        Label clubLabel = new Label("• " + pop.getClubName());
        clubLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label regBadge = new Label(pop.getRegistrationCount() + " registered");
        regBadge.getStyleClass().add("badge-indigo");
        regBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2px 7px;");

        row.getChildren().addAll(nameLabel, clubLabel, spacer, regBadge);
        return row;
    }

    /**
     * Constructs a single student upcoming event card with registration indicator.
     */
    private VBox createStudentEventCard(String title, String clubName, String category,
                                        String datetime, String venue, String regStatus) {
        VBox card = new VBox(8);
        card.getStyleClass().add("event-item-card");

        // Top line: Title and Registration Status Badge
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("event-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = new Label();
        if (regStatus != null) {
            statusBadge.setText("✔ Registered (" + regStatus + ")");
            statusBadge.getStyleClass().add("badge-success");
        } else {
            statusBadge.setText("Open for Registration");
            statusBadge.getStyleClass().add("badge-indigo");
        }

        topRow.getChildren().addAll(titleLabel, spacer, statusBadge);

        // Club & Category line
        HBox metaRow1 = new HBox(12);
        metaRow1.setAlignment(Pos.CENTER_LEFT);

        Label clubLabel = new Label("🏛  " + clubName);
        clubLabel.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 11.5px; -fx-font-weight: 600;");

        Label catLabel = new Label("•  " + category);
        catLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px;");

        metaRow1.getChildren().addAll(clubLabel, catLabel);

        // Schedule and Venue line
        HBox metaRow2 = new HBox(8);
        metaRow2.setAlignment(Pos.CENTER_LEFT);

        Label scheduleLabel = new Label("📅  " + datetime + "   •   📍  " + venue);
        scheduleLabel.getStyleClass().add("event-meta");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        Button viewDetailsBtn = new Button("View in Events →");
        viewDetailsBtn.getStyleClass().add("secondary-btn");
        viewDetailsBtn.setOnAction(e -> navigateToEvents());

        metaRow2.getChildren().addAll(scheduleLabel, spacer2, viewDetailsBtn);

        card.getChildren().addAll(topRow, metaRow1, metaRow2);
        return card;
    }

    /**
     * Renders student activity overview and lightweight achievement badges.
     */
    private void renderStudentActivity(int registered, int attended, int certificates,
                                       List<StudentAchievementDTO> achievements) {
        if (activitySummaryContainer == null) return;
        activitySummaryContainer.getChildren().clear();

        VBox statsList = new VBox(4);

        statsList.getChildren().addAll(
                createActivityStatRow("Events Registered", registered + " events", "#6366F1"),
                createActivityStatRow("Events Attended", attended + " events", "#22C55E"),
                createActivityStatRow("Certificates Earned", certificates + " issued", "#8B5CF6")
        );

        activitySummaryContainer.getChildren().add(statsList);

        if (registered == 0) {
            VBox emptyNotice = new VBox(6);
            emptyNotice.getStyleClass().add("empty-state-box");
            emptyNotice.setAlignment(Pos.CENTER);

            Label noticeTitle = new Label("No registrations yet");
            noticeTitle.getStyleClass().add("empty-state-title");

            Label noticeSub = new Label("Explore upcoming events to participate in campus activities!");
            noticeSub.getStyleClass().add("empty-state-sub");

            Button exploreBtn = new Button("Explore Events →");
            exploreBtn.getStyleClass().add("secondary-btn");
            exploreBtn.setOnAction(e -> navigateToEvents());

            emptyNotice.getChildren().addAll(noticeTitle, noticeSub, exploreBtn);
            activitySummaryContainer.getChildren().add(emptyNotice);
        } else {
            VBox summaryBox = new VBox(6);
            summaryBox.getStyleClass().add("pulse-summary-box");

            HBox summaryHeader = new HBox();
            summaryHeader.setAlignment(Pos.CENTER_LEFT);

            Label rateLabel = new Label("Participation Rate");
            rateLabel.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12px; -fx-font-weight: 700;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            int rate = (registered > 0) ? (attended * 100 / registered) : 0;
            Label percentLabel = new Label(rate + "% Attended");
            percentLabel.setStyle("-fx-text-fill: #22C55E; -fx-font-size: 12px; -fx-font-weight: 700;");

            summaryHeader.getChildren().addAll(rateLabel, spacer, percentLabel);

            ProgressBar rateBar = new ProgressBar((double) rate / 100);
            rateBar.setMaxWidth(Double.MAX_VALUE);

            Label note = new Label(attended + " of " + registered + " registered events attended");
            note.setStyle("-fx-text-fill: #68677A; -fx-font-size: 11px;");

            summaryBox.getChildren().addAll(summaryHeader, rateBar, note);
            activitySummaryContainer.getChildren().add(summaryBox);
        }

        // Lightweight Student Achievements Showcase (Phase 15)
        if (achievements != null && !achievements.isEmpty()) {
            VBox achBox = new VBox(8);
            achBox.getStyleClass().add("pulse-summary-box");
            achBox.setStyle("-fx-background-color: rgba(20, 19, 42, 0.7); -fx-border-color: rgba(99, 102, 241, 0.18); -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 10px 12px;");

            HBox achHeader = new HBox();
            achHeader.setAlignment(Pos.CENTER_LEFT);

            int unlockedCount = 0;
            for (StudentAchievementDTO ach : achievements) {
                if (ach.isUnlocked()) unlockedCount++;
            }

            Label achTitle = new Label("🏆  Achievements");
            achTitle.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12px; -fx-font-weight: 700;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label badgeCountLabel = new Label(unlockedCount + " of " + achievements.size() + " Unlocked");
            badgeCountLabel.setStyle("-fx-text-fill: #8B5CF6; -fx-font-size: 11px; -fx-font-weight: 700;");

            achHeader.getChildren().addAll(achTitle, spacer, badgeCountLabel);
            achBox.getChildren().add(achHeader);

            VBox badgeList = new VBox(4);
            for (StudentAchievementDTO badge : achievements) {
                badgeList.getChildren().add(createAchievementBadgeRow(badge));
            }
            achBox.getChildren().add(badgeList);
            activitySummaryContainer.getChildren().add(achBox);
        }
    }

    private HBox createAchievementBadgeRow(StudentAchievementDTO badge) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("pulse-item");

        Label iconLabel = new Label(badge.getIcon());
        iconLabel.setStyle("-fx-font-size: 13px;");

        VBox textCol = new VBox(1);
        Label titleLabel = new Label(badge.getTitle());
        titleLabel.setStyle(badge.isUnlocked()
                ? "-fx-text-fill: #F5F5F7; -fx-font-size: 11.5px; -fx-font-weight: 700;"
                : "-fx-text-fill: #68677A; -fx-font-size: 11.5px; -fx-font-weight: 600;");

        Label descLabel = new Label(badge.getDescription());
        descLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 10px;");
        textCol.getChildren().addAll(titleLabel, descLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusLabel = new Label(badge.getProgressLabel());
        if (badge.isUnlocked()) {
            statusLabel.getStyleClass().add("badge-success");
            statusLabel.setStyle("-fx-font-size: 10px; -fx-padding: 1px 6px;");
        } else {
            statusLabel.setStyle("-fx-background-color: rgba(255, 255, 255, 0.06); -fx-text-fill: #9695A8; -fx-font-size: 10px; -fx-padding: 1px 6px; -fx-background-radius: 8px;");
        }

        row.getChildren().addAll(iconLabel, textCol, spacer, statusLabel);
        return row;
    }

    private HBox createActivityStatRow(String label, String value, String valueColor) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("activity-stat-row");

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("activity-stat-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-text-fill: " + valueColor + "; -fx-font-size: 12.5px; -fx-font-weight: bold;");

        row.getChildren().addAll(labelNode, spacer, valueNode);
        return row;
    }

    private void renderOfflineUpcomingEvents() {
        if (upcomingEventsListContainer == null) return;
        upcomingEventsListContainer.getChildren().clear();

        VBox box = new VBox(6);
        box.getStyleClass().add("empty-state-box");

        Label title = new Label("Live data unavailable");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("MySQL database connection is offline. Showing offline interface.");
        sub.getStyleClass().add("empty-state-sub");

        box.getChildren().addAll(title, sub);
        upcomingEventsListContainer.getChildren().add(box);
    }

    private void renderOfflineActivity() {
        if (activitySummaryContainer == null) return;
        activitySummaryContainer.getChildren().clear();

        VBox box = new VBox(6);
        box.getStyleClass().add("empty-state-box");

        Label title = new Label("Activity summary unavailable");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Connect to MySQL to view verified participation records.");
        sub.getStyleClass().add("empty-state-sub");

        box.getChildren().addAll(title, sub);
        activitySummaryContainer.getChildren().add(box);
    }

    /**
     * Non-blocking background loader for campus-wide administrator metrics.
     */
    private void loadAdminDashboardData() {
        if (portalBadgeLabel != null) {
            portalBadgeLabel.setText("Connecting to database...");
        }

        new Thread(() -> {
            AdminDashboardDTO data = adminDashboardService.getDashboardData();
            Platform.runLater(() -> applyAdminDashboardData(data));
        }).start();
    }

    /**
     * Applies loaded admin dashboard data to the JavaFX UI controls.
     */
    private void applyAdminDashboardData(AdminDashboardDTO data) {
        // Configure card headers, badges, and subtexts for Admin
        if (card1TitleLabel != null) card1TitleLabel.setText("Total Events");
        if (card1BadgeLabel != null) {
            card1BadgeLabel.setText("Campus");
            card1BadgeLabel.getStyleClass().setAll("badge-indigo");
        }

        if (card2TitleLabel != null) card2TitleLabel.setText("Upcoming Events");
        if (card2BadgeLabel != null) {
            card2BadgeLabel.setText("Upcoming");
            card2BadgeLabel.getStyleClass().setAll("badge-purple");
        }
        if (card2Box != null) {
            card2Box.setOnMouseClicked(null);
            card2Box.setStyle("-fx-cursor: default;");
        }

        if (card3TitleLabel != null) card3TitleLabel.setText("Total Registrations");
        if (card3BadgeLabel != null) {
            card3BadgeLabel.setText("All Clubs");
            card3BadgeLabel.getStyleClass().setAll("badge-indigo");
        }

        if (card4TitleLabel != null) card4TitleLabel.setText("Pending Registrations");
        if (card4BadgeLabel != null) {
            card4BadgeLabel.setText("Pending");
            card4BadgeLabel.getStyleClass().setAll("badge-warning");
        }
        if (card4Box != null) {
            card4Box.setOnMouseClicked(e -> navigateToManageRegistrations());
            card4Box.setStyle("-fx-cursor: hand;");
        }

        if (upcomingCardTitle != null) upcomingCardTitle.setText("Campus Event Overview");
        if (upcomingCardSubtitle != null) upcomingCardSubtitle.setText("Recent & upcoming events across campus clubs");
        if (activityCardTitle != null) activityCardTitle.setText("Registration Overview");
        if (activityCardSubtitle != null) activityCardSubtitle.setText("Breakdown of campus-wide registration statuses");

        if (data.isDbOnline()) {
            if (portalBadgeLabel != null) {
                portalBadgeLabel.setText("Fall Term 2026 • Admin Portal");
            }
            if (portalStatusDot != null) {
                portalStatusDot.setStyle("-fx-fill: #3B82F6;");
            }

            if (card1ValueLabel != null) card1ValueLabel.setText(String.valueOf(data.getTotalEvents()));
            if (card1SubtextLabel != null) card1SubtextLabel.setText("Total campus events");

            if (card2ValueLabel != null) card2ValueLabel.setText(String.valueOf(data.getUpcomingEvents()));
            if (card2SubtextLabel != null) card2SubtextLabel.setText("Scheduled this term");

            if (card3ValueLabel != null) card3ValueLabel.setText(String.valueOf(data.getTotalRegistrations()));
            if (card3SubtextLabel != null) card3SubtextLabel.setText("Campus registrations");

            if (card4ValueLabel != null) card4ValueLabel.setText(String.valueOf(data.getPendingRegistrations()));
            if (card4SubtextLabel != null) card4SubtextLabel.setText("Awaiting review");

            renderAdminEventsList(data.getRecentEvents());
            renderAdminRegistrationOverview(data.getPendingRegistrations(), data.getApprovedRegistrations(), data.getRejectedRegistrations());
        } else {
            // Database offline fallback
            if (portalBadgeLabel != null) {
                portalBadgeLabel.setText("Database Offline • Local View");
            }
            if (portalStatusDot != null) {
                portalStatusDot.setStyle("-fx-fill: #F59E0B;");
            }

            if (card1ValueLabel != null) card1ValueLabel.setText("—");
            if (card1SubtextLabel != null) card1SubtextLabel.setText("Live data unavailable");

            if (card2ValueLabel != null) card2ValueLabel.setText("—");
            if (card2SubtextLabel != null) card2SubtextLabel.setText("Live data unavailable");

            if (card3ValueLabel != null) card3ValueLabel.setText("—");
            if (card3SubtextLabel != null) card3SubtextLabel.setText("Live data unavailable");

            if (card4ValueLabel != null) card4ValueLabel.setText("—");
            if (card4SubtextLabel != null) card4SubtextLabel.setText("Live data unavailable");

            renderOfflineUpcomingEvents();
            renderOfflineAdminActivity();
        }
    }

    /**
     * Renders upcoming / recent event cards or empty state for admin.
     */
    private void renderAdminEventsList(List<AdminDashboardDTO.AdminEventItemDTO> items) {
        if (upcomingEventsListContainer == null) return;
        upcomingEventsListContainer.getChildren().clear();

        if (items == null || items.isEmpty()) {
            VBox emptyBox = new VBox(6);
            emptyBox.getStyleClass().add("empty-state-box");

            Label emptyTitle = new Label("No campus events scheduled right now");
            emptyTitle.getStyleClass().add("empty-state-title");

            Label emptySub = new Label("Events created by campus clubs will appear here.");
            emptySub.getStyleClass().add("empty-state-sub");

            emptyBox.getChildren().addAll(emptyTitle, emptySub);
            upcomingEventsListContainer.getChildren().add(emptyBox);
            return;
        }

        for (AdminDashboardDTO.AdminEventItemDTO item : items) {
            Event ev = item.getEvent();
            VBox card = createAdminEventCard(ev, item.getClubName(), item.getRegistrationCount());
            upcomingEventsListContainer.getChildren().add(card);
        }
    }

    /**
     * Constructs a single event card for the administrator overview.
     */
    private VBox createAdminEventCard(Event ev, String clubName, int regCount) {
        VBox card = new VBox(8);
        card.getStyleClass().add("event-item-card");

        // Top line: Title and Registration Count Badge
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(ev.getEventName());
        titleLabel.getStyleClass().add("event-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String regText = (regCount == 1) ? "1 Registered" : regCount + " Registered";
        if (ev.getMaxParticipants() > 0) {
            regText = regCount + " / " + ev.getMaxParticipants() + " Registered";
        }
        Label regBadge = new Label("👥  " + regText);
        regBadge.getStyleClass().add("badge-indigo");

        topRow.getChildren().addAll(titleLabel, spacer, regBadge);

        // Club & Category line
        HBox metaRow1 = new HBox(12);
        metaRow1.setAlignment(Pos.CENTER_LEFT);

        Label clubLabel = new Label("🏛  " + (clubName != null ? clubName : "Campus Society"));
        clubLabel.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 11.5px; -fx-font-weight: 600;");

        Label catLabel = new Label("•  " + (ev.getCategory() != null ? ev.getCategory() : "General"));
        catLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px;");

        metaRow1.getChildren().addAll(clubLabel, catLabel);

        // Schedule, Venue, and View Details button
        HBox metaRow2 = new HBox(8);
        metaRow2.setAlignment(Pos.CENTER_LEFT);

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");
        String dateStr = (ev.getEventDate() != null) ? ev.getEventDate().format(dateFormatter) : "TBD";
        String timeStr = (ev.getEventTime() != null) ? ev.getEventTime().format(timeFormatter) : "";
        String fullTime = timeStr.isEmpty() ? dateStr : dateStr + " • " + timeStr;

        Label scheduleLabel = new Label("📅  " + fullTime + "   •   📍  " + (ev.getVenue() != null ? ev.getVenue() : "Campus Venue"));
        scheduleLabel.getStyleClass().add("event-meta");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        Button editBtn = new Button("✎ Edit");
        editBtn.getStyleClass().add("secondary-btn");
        editBtn.setOnAction(e -> openEditEventFlow(ev));

        Button viewDetailsBtn = new Button("View Details →");
        viewDetailsBtn.getStyleClass().add("secondary-btn");
        viewDetailsBtn.setOnAction(e -> navigateToEventDetails(ev));

        metaRow2.getChildren().addAll(scheduleLabel, spacer2, editBtn, viewDetailsBtn);

        card.getChildren().addAll(topRow, metaRow1, metaRow2);
        return card;
    }

    /**
     * Renders campus-wide registration distribution overview for admin.
     */
    private void renderAdminRegistrationOverview(int pending, int approved, int rejected) {
        if (activitySummaryContainer == null) return;
        activitySummaryContainer.getChildren().clear();

        int total = pending + approved + rejected;

        VBox statsList = new VBox(6);
        statsList.getChildren().addAll(
                createActivityStatRow("Pending Review", pending + " requests", "#F59E0B"),
                createActivityStatRow("Approved Registrations", approved + " confirmed", "#22C55E"),
                createActivityStatRow("Rejected / Declined", rejected + " declined", "#EF4444")
        );
        activitySummaryContainer.getChildren().add(statsList);

        if (total == 0) {
            VBox emptyNotice = new VBox(6);
            emptyNotice.getStyleClass().add("empty-state-box");
            emptyNotice.setAlignment(Pos.CENTER);

            Label noticeTitle = new Label("No registrations yet");
            noticeTitle.getStyleClass().add("empty-state-title");

            Label noticeSub = new Label("Campus registration totals and approval status will appear here.");
            noticeSub.getStyleClass().add("empty-state-sub");

            emptyNotice.getChildren().addAll(noticeTitle, noticeSub);
            activitySummaryContainer.getChildren().add(emptyNotice);
        } else {
            VBox summaryBox = new VBox(8);
            summaryBox.getStyleClass().add("pulse-summary-box");

            HBox summaryHeader = new HBox();
            summaryHeader.setAlignment(Pos.CENTER_LEFT);

            Label rateLabel = new Label("Approval Rate");
            rateLabel.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12px; -fx-font-weight: 700;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            int approvalRate = (approved * 100 / total);
            Label percentLabel = new Label(approvalRate + "% Approved");
            percentLabel.setStyle("-fx-text-fill: #22C55E; -fx-font-size: 12px; -fx-font-weight: 700;");

            summaryHeader.getChildren().addAll(rateLabel, spacer, percentLabel);

            ProgressBar rateBar = new ProgressBar((double) approvalRate / 100);
            rateBar.setMaxWidth(Double.MAX_VALUE);

            Label detailNote = new Label(total + " total registrations (" + pending + " awaiting review)");
            detailNote.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11px;");

            summaryBox.getChildren().addAll(summaryHeader, rateBar, detailNote);
            activitySummaryContainer.getChildren().add(summaryBox);
        }

        // Administrative note & action box
        VBox noteBox = new VBox(6);
        noteBox.setStyle("-fx-background-color: rgba(20, 19, 42, 0.6); -fx-background-radius: 8px; -fx-border-color: rgba(99, 102, 241, 0.2); -fx-border-radius: 8px; -fx-padding: 10px;");
        Label noteTitle = new Label("Administrative Actions");
        noteTitle.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 11.5px; -fx-font-weight: 700;");
        Label noteDesc = new Label("Registration approvals and attendee rosters are managed by system administrators.");
        noteDesc.setWrapText(true);
        noteDesc.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11px;");

        Button manageRegBtn = new Button("Manage Registrations →");
        manageRegBtn.getStyleClass().add("primary-btn");
        manageRegBtn.setStyle("-fx-font-size: 11.5px; -fx-padding: 5px 12px;");
        manageRegBtn.setOnAction(e -> navigateToManageRegistrations());

        noteBox.getChildren().addAll(noteTitle, noteDesc, manageRegBtn);
        activitySummaryContainer.getChildren().add(noteBox);
    }

    private void renderOfflineAdminActivity() {
        if (activitySummaryContainer == null) return;
        activitySummaryContainer.getChildren().clear();

        VBox box = new VBox(6);
        box.getStyleClass().add("empty-state-box");

        Label title = new Label("Registration summary unavailable");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("MySQL database connection is offline. Connect to MySQL to view verified campus records.");
        sub.getStyleClass().add("empty-state-sub");

        box.getChildren().addAll(title, sub);
        activitySummaryContainer.getChildren().add(box);
    }

    private String getTimeBasedGreeting() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(12, 0))) {
            return "Good morning";
        } else if (now.isBefore(LocalTime.of(17, 0))) {
            return "Good afternoon";
        } else {
            return "Good evening";
        }
    }

    /**
     * Constructs the compact left sidebar navigation.
     */
    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        VBox brandBox = new VBox(4);
        brandBox.getStyleClass().add("brand-box");

        HBox brandHeader = new HBox(8);
        brandHeader.setAlignment(Pos.CENTER_LEFT);

        Label brandTitle = new Label("CAMPUS");
        brandTitle.getStyleClass().add("brand-title");

        Label brandSubtitle = new Label("CONNECT");
        brandSubtitle.getStyleClass().add("brand-subtitle");

        Region brandSpacer = new Region();
        HBox.setHgrow(brandSpacer, Priority.ALWAYS);

        Label brandPill = new Label("v1.0");
        brandPill.getStyleClass().add("brand-pill");

        brandHeader.getChildren().addAll(brandTitle, brandSubtitle, brandSpacer, brandPill);
        brandBox.getChildren().add(brandHeader);

        VBox navList = new VBox(3);
        navList.setPadding(new Insets(12, 12, 12, 12));

        Label navMainHeading = new Label("OVERVIEW");
        navMainHeading.getStyleClass().add("nav-section-title");

        navDashboardBtn = createNavButton("⊞  Dashboard", true);
        navDashboardBtn.setOnAction(e -> navigateToDashboard());

        navEventsHeading = new Label("EVENTS");
        navEventsHeading.getStyleClass().add("nav-section-title");

        navEventsBtn = createNavButton("📅  Events", false);
        navEventsBtn.setOnAction(e -> navigateToEvents());

        navMyEventsBtn = createNavButton("🎟  My Events", false);
        navMyEventsBtn.setOnAction(e -> navigateToMyEvents());

        navManageEventsBtn = createNavButton("🛠  Manage Events", false);
        navManageEventsBtn.setOnAction(e -> navigateToManageEvents());

        navManageRegistrationsBtn = createNavButton("📋  Manage Registrations", false);
        navManageRegistrationsBtn.setOnAction(e -> navigateToManageRegistrations());

        navManageAttendanceBtn = createNavButton("✓  Manage Attendance", false);
        navManageAttendanceBtn.setOnAction(e -> navigateToManageAttendance());

        navManageCertificatesBtn = createNavButton("📜  Manage Certificates", false);
        navManageCertificatesBtn.setOnAction(e -> navigateToManageCertificates());

        navManageFeedbackBtn = createNavButton("💬  Manage Feedback", false);
        navManageFeedbackBtn.setOnAction(e -> navigateToManageFeedback());

        navAnalyticsBtn = createNavButton("📊  Analytics", false);
        navAnalyticsBtn.setOnAction(e -> navigateToAnalytics());

        navCampusHeading = new Label("CAMPUS");
        navCampusHeading.getStyleClass().add("nav-section-title");

        navClubs = createNavButton("🏛  Clubs", false);
        navClubs.setOnAction(e -> navigateToEvents());

        navCertificatesBtn = createNavButton("📜  My Certificates", false);
        navCertificatesBtn.setOnAction(e -> navigateToMyCertificates());

        navFeedbackBtn = createNavButton("💬  Feedback", false);
        navFeedbackBtn.setOnAction(e -> navigateToFeedback());

        navList.getChildren().addAll(
                navMainHeading, navDashboardBtn,
                navEventsHeading, navEventsBtn, navMyEventsBtn, navManageEventsBtn, navManageRegistrationsBtn,
                navCampusHeading, navClubs, navCertificatesBtn, navFeedbackBtn, navManageAttendanceBtn, navManageCertificatesBtn, navManageFeedbackBtn, navAnalyticsBtn
        );

        Region verticalSpacer = new Region();
        VBox.setVgrow(verticalSpacer, Priority.ALWAYS);

        VBox bottomNavList = new VBox(3);
        bottomNavList.setPadding(new Insets(6, 12, 12, 12));

        navSettings = createNavButton("⚙  Settings", false);
        navSettings.setOnAction(e -> showSettingsDialog());

        Button navLogout = new Button("⎋  Logout");
        navLogout.setMaxWidth(Double.MAX_VALUE);
        navLogout.setAlignment(Pos.CENTER_LEFT);
        navLogout.getStyleClass().add("nav-item");
        navLogout.setOnAction(e -> {
            Session.clearSession();
            if (onLogout != null) {
                onLogout.run();
            }
        });

        bottomNavList.getChildren().addAll(navSettings, navLogout);

        HBox userCard = new HBox(10);
        userCard.setAlignment(Pos.CENTER_LEFT);
        userCard.getStyleClass().add("nav-user-card");

        userAvatarCircle = new Circle(14);
        userAvatarCircle.setStyle("-fx-fill: linear-gradient(to bottom right, #6366F1, #8B5CF6);");

        VBox userDetails = new VBox(2);
        userNameLabel = new Label("User");
        userNameLabel.getStyleClass().add("user-name");

        userRoleLabel = new Label("Role");
        userRoleLabel.getStyleClass().add("user-role");
        userDetails.getChildren().addAll(userNameLabel, userRoleLabel);

        userCard.getChildren().addAll(userAvatarCircle, userDetails);

        sidebar.getChildren().addAll(brandBox, navList, verticalSpacer, bottomNavList, userCard);
        return sidebar;
    }

    private Button createNavButton(String title, boolean isActive) {
        Button btn = new Button(title);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);

        if (isActive) {
            btn.getStyleClass().add("nav-item-active");
        } else {
            btn.getStyleClass().add("nav-item");
        }

        btn.setOnAction(e -> setActiveNavButton(btn));

        navButtons.add(btn);
        return btn;
    }

    private ScrollPane createMainContentScrollPane() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("main-scroll-pane");
        scrollPane.setFitToWidth(true);

        VBox mainContainer = new VBox(24);
        mainContainer.getStyleClass().add("main-content");

        // 1. Header Area
        HBox headerBox = createHeaderArea();

        // 2. Metric Overview Cards (4 Cards)
        HBox metricsBox = createMetricCardsRow();

        // 3. Large Registrations Analytics Card (Preserved from Phase 1)
        chartCard = createRegistrationsChartCard();

        // 4. Bottom Split Layout (Upcoming Events + Your Activity)
        HBox bottomRow = createBottomCardsRow();

        mainContainer.getChildren().addAll(headerBox, metricsBox, chartCard, bottomRow);
        scrollPane.setContent(mainContainer);

        return scrollPane;
    }

    private HBox createHeaderArea() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label titleLabel = new Label("Dashboard");
        titleLabel.getStyleClass().add("header-title");

        headerSubtitleLabel = new Label("Welcome back  •  Here's what's happening on campus.");
        headerSubtitleLabel.getStyleClass().add("header-subtitle");

        titleBox.getChildren().addAll(titleLabel, headerSubtitleLabel);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox statusPill = new HBox(8);
        statusPill.setAlignment(Pos.CENTER);
        statusPill.getStyleClass().add("status-pill");

        portalStatusDot = new Circle(4);
        portalStatusDot.setStyle("-fx-fill: #22C55E;");

        portalBadgeLabel = new Label("Fall Term 2026 • Live");
        portalBadgeLabel.getStyleClass().add("status-pill-text");

        statusPill.getChildren().addAll(portalStatusDot, portalBadgeLabel);
        header.getChildren().addAll(titleBox, headerSpacer, statusPill);
        return header;
    }

    /**
     * Metric Overview Row (4 KPI Cards).
     */
    private HBox createMetricCardsRow() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);

        // A. Card 1 (Student: Total Events / Admin: Total Events)
        card1TitleLabel = new Label("Total Events");
        card1TitleLabel.getStyleClass().add("metric-label");
        card1ValueLabel = new Label("—");
        card1ValueLabel.getStyleClass().add("metric-value");
        card1SubtextLabel = new Label("Available on campus");
        card1SubtextLabel.getStyleClass().add("metric-subtext");
        card1BadgeLabel = new Label("Active");
        card1BadgeLabel.getStyleClass().add("badge-indigo");
        VBox card1Box = buildCustomMetricCardBox(card1TitleLabel, card1ValueLabel, card1SubtextLabel, card1BadgeLabel);

        // B. Card 2 (Student: My Registrations / Admin: Upcoming Events)
        card2TitleLabel = new Label("My Registrations");
        card2TitleLabel.getStyleClass().add("metric-label");
        card2ValueLabel = new Label("—");
        card2ValueLabel.getStyleClass().add("metric-value");
        card2SubtextLabel = new Label("Registered events");
        card2SubtextLabel.getStyleClass().add("metric-subtext");
        card2BadgeLabel = new Label("Registered");
        card2BadgeLabel.getStyleClass().add("badge-success");
        card2Box = buildCustomMetricCardBox(card2TitleLabel, card2ValueLabel, card2SubtextLabel, card2BadgeLabel);
        card2Box.setOnMouseClicked(e -> navigateToMyEvents());
        card2Box.setStyle("-fx-cursor: hand;");

        // C. Card 3 (Student: Upcoming Events / Admin: Total Registrations)
        card3TitleLabel = new Label("Upcoming Events");
        card3TitleLabel.getStyleClass().add("metric-label");
        card3ValueLabel = new Label("—");
        card3ValueLabel.getStyleClass().add("metric-value");
        card3SubtextLabel = new Label("Scheduled this term");
        card3SubtextLabel.getStyleClass().add("metric-subtext");
        card3BadgeLabel = new Label("Upcoming");
        card3BadgeLabel.getStyleClass().add("badge-purple");
        VBox card3Box = buildCustomMetricCardBox(card3TitleLabel, card3ValueLabel, card3SubtextLabel, card3BadgeLabel);

        // D. Card 4 (Student: Events Attended / Admin: Pending Registrations)
        card4TitleLabel = new Label("Events Attended");
        card4TitleLabel.getStyleClass().add("metric-label");
        card4ValueLabel = new Label("—");
        card4ValueLabel.getStyleClass().add("metric-value");
        card4SubtextLabel = new Label("Verified presence");
        card4SubtextLabel.getStyleClass().add("metric-subtext");
        card4BadgeLabel = new Label("Attended");
        card4BadgeLabel.getStyleClass().add("badge-indigo");
        card4Box = buildCustomMetricCardBox(card4TitleLabel, card4ValueLabel, card4SubtextLabel, card4BadgeLabel);

        HBox.setHgrow(card1Box, Priority.ALWAYS);
        HBox.setHgrow(card2Box, Priority.ALWAYS);
        HBox.setHgrow(card3Box, Priority.ALWAYS);
        HBox.setHgrow(card4Box, Priority.ALWAYS);

        row.getChildren().addAll(card1Box, card2Box, card3Box, card4Box);
        return row;
    }

    private VBox buildCustomMetricCardBox(Label cardLabel, Label valueLabel, Label subtextLabel, Label badge) {
        VBox card = new VBox(8);
        card.getStyleClass().add("metric-card");

        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topRow.getChildren().addAll(cardLabel, spacer, badge);
        card.getChildren().addAll(topRow, valueLabel, subtextLabel);
        return card;
    }

    private VBox createRegistrationsChartCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("dashboard-card");

        HBox cardHeader = new HBox();
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        VBox headerText = new VBox(3);
        Label title = new Label("Campus Event Participation Trends");
        title.getStyleClass().add("card-title");

        Label subtitle = new Label("Monthly student participation and attendance trends across campus");
        subtitle.getStyleClass().add("card-subtitle");

        headerText.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox filterControls = new HBox(8);
        Button semBtn = new Button("Fall 2026");
        semBtn.getStyleClass().add("secondary-btn");

        Button allClubsBtn = new Button("All Clubs");
        allClubsBtn.getStyleClass().add("secondary-btn");

        filterControls.getChildren().addAll(semBtn, allClubsBtn);
        cardHeader.getChildren().addAll(headerText, spacer, filterControls);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis(0, 350, 50);

        AreaChart<String, Number> areaChart = new AreaChart<>(xAxis, yAxis);
        areaChart.setPrefHeight(230);
        areaChart.setAnimated(false);
        areaChart.setLegendVisible(true);

        XYChart.Series<String, Number> regSeries = new XYChart.Series<>();
        regSeries.setName("Total Registrations");
        regSeries.getData().add(new XYChart.Data<>("May", 55));
        regSeries.getData().add(new XYChart.Data<>("Jun", 110));
        regSeries.getData().add(new XYChart.Data<>("Jul", 185));
        regSeries.getData().add(new XYChart.Data<>("Aug", 135));
        regSeries.getData().add(new XYChart.Data<>("Sep", 240));
        regSeries.getData().add(new XYChart.Data<>("Oct", 310));

        XYChart.Series<String, Number> attSeries = new XYChart.Series<>();
        attSeries.setName("Verified Attendance");
        attSeries.getData().add(new XYChart.Data<>("May", 42));
        attSeries.getData().add(new XYChart.Data<>("Jun", 95));
        attSeries.getData().add(new XYChart.Data<>("Jul", 155));
        attSeries.getData().add(new XYChart.Data<>("Aug", 110));
        attSeries.getData().add(new XYChart.Data<>("Sep", 205));
        attSeries.getData().add(new XYChart.Data<>("Oct", 275));

        areaChart.getData().add(regSeries);
        areaChart.getData().add(attSeries);

        card.getChildren().addAll(cardHeader, areaChart);
        return card;
    }

    private HBox createBottomCardsRow() {
        HBox row = new HBox(18);
        row.setAlignment(Pos.TOP_LEFT);

        VBox upcomingCard = createUpcomingEventsCard();
        VBox activityCard = createActivitySummaryCard();

        HBox.setHgrow(upcomingCard, Priority.ALWAYS);
        HBox.setHgrow(activityCard, Priority.ALWAYS);

        upcomingCard.prefWidthProperty().bind(row.widthProperty().multiply(0.58));
        activityCard.prefWidthProperty().bind(row.widthProperty().multiply(0.42));

        row.getChildren().addAll(upcomingCard, activityCard);
        return row;
    }

    private VBox createUpcomingEventsCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("dashboard-card");

        HBox cardHeader = new HBox();
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        VBox headerText = new VBox(3);
        upcomingCardTitle = new Label("Upcoming Events");
        upcomingCardTitle.getStyleClass().add("card-title");

        upcomingCardSubtitle = new Label("Scheduled campus club activities and workshops");
        upcomingCardSubtitle.getStyleClass().add("card-subtitle");

        headerText.getChildren().addAll(upcomingCardTitle, upcomingCardSubtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox cardActions = new HBox(8);
        cardActions.setAlignment(Pos.CENTER_RIGHT);

        upcomingCardCreateBtn = new Button("+ Create Event");
        upcomingCardCreateBtn.getStyleClass().add("primary-btn");
        upcomingCardCreateBtn.setOnAction(e -> openCreateEventFlow());
        upcomingCardCreateBtn.setVisible(false);
        upcomingCardCreateBtn.setManaged(false);

        upcomingCardActionBtn = new Button("Explore Events →");
        upcomingCardActionBtn.getStyleClass().add("secondary-btn");
        upcomingCardActionBtn.setOnAction(e -> navigateToEvents());

        cardActions.getChildren().addAll(upcomingCardCreateBtn, upcomingCardActionBtn);

        cardHeader.getChildren().addAll(headerText, spacer, cardActions);

        upcomingEventsListContainer = new VBox(10);
        card.getChildren().addAll(cardHeader, upcomingEventsListContainer);
        return card;
    }

    private VBox createActivitySummaryCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("dashboard-card");

        VBox headerText = new VBox(3);
        activityCardTitle = new Label("Your Activity");
        activityCardTitle.getStyleClass().add("card-title");

        activityCardSubtitle = new Label("Personalized campus participation summary");
        activityCardSubtitle.getStyleClass().add("card-subtitle");

        headerText.getChildren().addAll(activityCardTitle, activityCardSubtitle);

        activitySummaryContainer = new VBox(12);
        card.getChildren().addAll(headerText, activitySummaryContainer);
        return card;
    }

    public void showSettingsDialog() {
        if (modalOverlay == null) return;
        modalOverlay.getChildren().clear();

        User user = Session.getCurrentUser();
        String name = user != null ? user.getName() : "Guest";
        String email = user != null ? user.getEmail() : "N/A";
        String role = (user != null && "ADMIN".equalsIgnoreCase(user.getRole()))
                ? "System Administrator" : "Student Member";

        VBox dialogCard = new VBox(16);
        dialogCard.getStyleClass().add("modal-dialog-card");
        dialogCard.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(3);
        Label title = new Label("Account & Environment Settings");
        title.getStyleClass().add("modal-title");
        Label subtitle = new Label("Campus Connect desktop runtime and profile configuration");
        subtitle.getStyleClass().add("modal-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("secondary-btn");
        closeBtn.setStyle("-fx-padding: 4px 10px; -fx-min-width: 32px; -fx-alignment: center;");
        closeBtn.setOnAction(e -> hideModal());
        header.getChildren().addAll(titleBox, spacer, closeBtn);

        VBox infoBox = new VBox(8);
        infoBox.getStyleClass().add("info-pill-box");

        infoBox.getChildren().addAll(
                createSettingRow("Authenticated User", name),
                createSettingRow("Campus Email", email),
                createSettingRow("Portal Access Role", role),
                createSettingRow("Database Target", "MySQL • campus_connect (Port 3306)"),
                createSettingRow("Desktop Runtime", "Java 21 • JavaFX Dark SaaS Interface")
        );

        Label notice = new Label("Note: University single sign-on credentials and role assignments are administered centrally.");
        notice.setStyle("-fx-text-fill: #68677A; -fx-font-size: 11px;");
        notice.setWrapText(true);

        HBox actions = new HBox();
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button doneBtn = new Button("Done");
        doneBtn.getStyleClass().add("primary-btn");
        doneBtn.setOnAction(e -> hideModal());
        actions.getChildren().add(doneBtn);

        dialogCard.getChildren().addAll(header, infoBox, notice, actions);
        modalOverlay.getChildren().add(dialogCard);
        modalOverlay.setVisible(true);
    }

    private HBox createSettingRow(String label, String value) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label l = new Label(label + ":");
        l.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px; -fx-font-weight: 600;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label v = new Label(value);
        v.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12px; -fx-font-weight: 700;");
        row.getChildren().addAll(l, sp, v);
        return row;
    }

    private void hideModal() {
        if (modalOverlay != null) {
            modalOverlay.setVisible(false);
            modalOverlay.getChildren().clear();
        }
        setActiveNavButton(navDashboardBtn);
    }

    public Parent getView() {
        return rootStack != null ? rootStack : rootLayout;
    }
}
