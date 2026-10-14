package controller;

import dao.RegistrationDAO;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.Event;
import model.StudentRegistrationDTO;
import model.User;
import util.DBConnection;
import util.Session;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * MyEventsController — Manages the student "My Events" view.
 *
 * Implements Phase 7: My Events.
 * Follows AGENTS.md:
 * - Scoped strictly to current student (Session.getCurrentUser())
 * - In-memory local filtering for status (All, Pending, Approved, Rejected)
 * - Single optimized JOIN query via RegistrationDAO (no N+1 round trips)
 * - Non-blocking asynchronous threading for database retrieval
 * - Graceful handling of empty states and offline database
 */
public class MyEventsController {

    private final RegistrationDAO registrationDAO;

    private ScrollPane rootScrollPane;
    private VBox contentBox;
    private VBox cardsContainer;

    // Metrics UI
    private Label totalMetricValue;
    private Label pendingMetricValue;
    private Label approvedMetricValue;
    private Label rejectedMetricValue;

    // Filter Buttons
    private Button filterAllBtn;
    private Button filterPendingBtn;
    private Button filterApprovedBtn;
    private Button filterRejectedBtn;

    // In-memory master list and active filter
    private final List<StudentRegistrationDTO> masterRegistrations = new ArrayList<>();
    private String currentFilter = "ALL";

    // Navigation callbacks
    private Runnable onExploreEvents;
    private Consumer<Event> onViewEventDetails;
    private Runnable onNavigateDashboard;

    // Formatters
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    public MyEventsController() {
        this.registrationDAO = new RegistrationDAO();
        buildView();
    }

    public void setOnExploreEvents(Runnable onExploreEvents) {
        this.onExploreEvents = onExploreEvents;
    }

    public void setOnViewEventDetails(Consumer<Event> onViewEventDetails) {
        this.onViewEventDetails = onViewEventDetails;
    }

    public void setOnNavigateDashboard(Runnable onNavigateDashboard) {
        this.onNavigateDashboard = onNavigateDashboard;
    }

    private void buildView() {
        rootScrollPane = new ScrollPane();
        rootScrollPane.getStyleClass().add("main-scroll-pane");
        rootScrollPane.setFitToWidth(true);

        contentBox = new VBox(22);
        contentBox.getStyleClass().add("main-content");

        // 1. Header Section
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label titleLabel = new Label("My Events");
        titleLabel.getStyleClass().add("header-title");

        Label subtitleLabel = new Label("Track and manage your campus event registrations and confirmations.");
        subtitleLabel.getStyleClass().add("header-subtitle");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button refreshBtn = new Button("↻ Refresh");
        refreshBtn.getStyleClass().add("secondary-btn");
        refreshBtn.setOnAction(e -> loadInitialData());

        headerBox.getChildren().addAll(titleBox, headerSpacer, refreshBtn);

        // 2. Summary Metrics Row
        HBox metricsRow = buildMetricsRow();

        // 3. Status Filter Toolbar
        HBox filterToolbar = buildFilterToolbar();

        // 4. Cards Stream Container
        cardsContainer = new VBox(14);

        contentBox.getChildren().addAll(headerBox, metricsRow, filterToolbar, cardsContainer);
        rootScrollPane.setContent(contentBox);
    }

    private HBox buildMetricsRow() {
        HBox row = new HBox(16);

        VBox totalCard = createCompactMetricCard("Total Registered", "#6366F1");
        totalMetricValue = (Label) totalCard.getChildren().get(1);

        VBox pendingCard = createCompactMetricCard("Pending Approval", "#F59E0B");
        pendingMetricValue = (Label) pendingCard.getChildren().get(1);

        VBox approvedCard = createCompactMetricCard("Approved", "#22C55E");
        approvedMetricValue = (Label) approvedCard.getChildren().get(1);

        VBox rejectedCard = createCompactMetricCard("Rejected", "#EF4444");
        rejectedMetricValue = (Label) rejectedCard.getChildren().get(1);

        HBox.setHgrow(totalCard, Priority.ALWAYS);
        HBox.setHgrow(pendingCard, Priority.ALWAYS);
        HBox.setHgrow(approvedCard, Priority.ALWAYS);
        HBox.setHgrow(rejectedCard, Priority.ALWAYS);

        row.getChildren().addAll(totalCard, pendingCard, approvedCard, rejectedCard);
        return row;
    }

    private VBox createCompactMetricCard(String labelText, String accentColor) {
        VBox card = new VBox(4);
        card.getStyleClass().add("metric-card");

        Label label = new Label(labelText);
        label.getStyleClass().add("metric-label");

        Label value = new Label("—");
        value.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 26px; -fx-font-weight: 800;");

        card.getChildren().addAll(label, value);
        return card;
    }

    private HBox buildFilterToolbar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.getStyleClass().add("dashboard-card");

        Label filterLabel = new Label("Filter by Status:");
        filterLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px; -fx-font-weight: 600;");

        filterAllBtn = createFilterButton("All", "ALL");
        filterPendingBtn = createFilterButton("Pending", "PENDING");
        filterApprovedBtn = createFilterButton("Approved", "APPROVED");
        filterRejectedBtn = createFilterButton("Rejected", "REJECTED");

        bar.getChildren().addAll(filterLabel, filterAllBtn, filterPendingBtn, filterApprovedBtn, filterRejectedBtn);
        return bar;
    }

    private Button createFilterButton(String text, String filterKey) {
        Button btn = new Button(text);
        btn.getStyleClass().add("filter-pill");
        if ("ALL".equals(filterKey)) {
            btn.getStyleClass().add("filter-pill-active");
        }

        btn.setOnAction(e -> {
            currentFilter = filterKey;
            updateFilterButtonStyles();
            renderFilteredList();
        });
        return btn;
    }

    private void updateFilterButtonStyles() {
        setFilterBtnState(filterAllBtn, "ALL".equals(currentFilter));
        setFilterBtnState(filterPendingBtn, "PENDING".equals(currentFilter));
        setFilterBtnState(filterApprovedBtn, "APPROVED".equals(currentFilter));
        setFilterBtnState(filterRejectedBtn, "REJECTED".equals(currentFilter));
    }

    private void setFilterBtnState(Button btn, boolean isActive) {
        btn.getStyleClass().removeAll("filter-pill", "filter-pill-active");
        if (isActive) {
            btn.getStyleClass().add("filter-pill-active");
        } else {
            btn.getStyleClass().add("filter-pill");
        }
    }

    /**
     * Loads registrations asynchronously for the current authenticated student.
     */
    public void loadInitialData() {
        User user = Session.getCurrentUser();
        if (user == null) {
            renderUnauthorizedState("Please log in to view your registrations.");
            return;
        }

        String role = user.getRole() != null ? user.getRole().toUpperCase() : "";
        if (!"STUDENT".equals(role)) {
            renderAdminNotice();
            return;
        }

        // Show loading state
        cardsContainer.getChildren().clear();
        Label loadingLabel = new Label("Loading your registrations...");
        loadingLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 13px;");
        cardsContainer.getChildren().add(loadingLabel);

        new Thread(() -> {
            boolean isOnline = DBConnection.testConnection();
            List<StudentRegistrationDTO> results = isOnline
                    ? registrationDAO.getStudentRegistrationsWithEvents(user.getUserId())
                    : null;

            Platform.runLater(() -> {
                if (!isOnline) {
                    renderOfflineState();
                } else {
                    masterRegistrations.clear();
                    if (results != null) {
                        masterRegistrations.addAll(results);
                    }
                    updateMetrics();
                    renderFilteredList();
                }
            });
        }).start();
    }

    private void updateMetrics() {
        int total = masterRegistrations.size();
        int pending = 0;
        int approved = 0;
        int rejected = 0;

        for (StudentRegistrationDTO item : masterRegistrations) {
            String st = item.getStatus();
            if ("PENDING".equals(st)) {
                pending++;
            } else if ("APPROVED".equals(st)) {
                approved++;
            } else if ("REJECTED".equals(st)) {
                rejected++;
            }
        }

        totalMetricValue.setText(String.valueOf(total));
        pendingMetricValue.setText(String.valueOf(pending));
        approvedMetricValue.setText(String.valueOf(approved));
        rejectedMetricValue.setText(String.valueOf(rejected));

        filterAllBtn.setText("All (" + total + ")");
        filterPendingBtn.setText("Pending (" + pending + ")");
        filterApprovedBtn.setText("Approved (" + approved + ")");
        filterRejectedBtn.setText("Rejected (" + rejected + ")");
    }

    /**
     * Filters masterRegistrations locally in memory without hitting the database.
     */
    private void renderFilteredList() {
        cardsContainer.getChildren().clear();

        if (masterRegistrations.isEmpty()) {
            renderZeroRegistrationsState();
            return;
        }

        List<StudentRegistrationDTO> filtered;
        if ("ALL".equals(currentFilter)) {
            filtered = masterRegistrations;
        } else {
            filtered = masterRegistrations.stream()
                    .filter(item -> currentFilter.equalsIgnoreCase(item.getStatus()))
                    .collect(Collectors.toList());
        }

        if (filtered.isEmpty()) {
            renderFilteredEmptyState(currentFilter);
            return;
        }

        for (StudentRegistrationDTO item : filtered) {
            VBox card = createRegistrationCard(item);
            cardsContainer.getChildren().add(card);
        }
    }

    /**
     * Builds a single modern dark card for a registered event.
     */
    private VBox createRegistrationCard(StudentRegistrationDTO item) {
        VBox card = new VBox(12);
        card.getStyleClass().add("event-grid-card");

        // Top line: Event Title + Status Badge
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(item.getEventName());
        titleLabel.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 16px; -fx-font-weight: 800;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = new Label();
        String st = item.getStatus();
        if ("APPROVED".equals(st)) {
            statusBadge.setText("✔ Approved");
            statusBadge.getStyleClass().add("badge-success");
        } else if ("REJECTED".equals(st)) {
            statusBadge.setText("✖ Rejected");
            statusBadge.getStyleClass().add("badge-danger");
        } else {
            statusBadge.setText("⏳ Pending Approval");
            statusBadge.getStyleClass().add("badge-warning");
        }

        topRow.getChildren().addAll(titleLabel, spacer, statusBadge);

        // Meta line 1: Club, Category, and Registration ID
        HBox metaRow1 = new HBox(12);
        metaRow1.setAlignment(Pos.CENTER_LEFT);

        Label clubLabel = new Label("🏛  " + item.getClubName());
        clubLabel.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 12px; -fx-font-weight: 600;");

        Label catLabel = new Label("•  " + item.getCategory());
        catLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");

        Region spacerMid = new Region();
        HBox.setHgrow(spacerMid, Priority.ALWAYS);

        Label regIdLabel = new Label("#" + item.getFormattedRegistrationId());
        regIdLabel.getStyleClass().add("reg-id-pill");

        metaRow1.getChildren().addAll(clubLabel, catLabel, spacerMid, regIdLabel);

        // Meta line 2: Date, Time, Venue, and Registration Date
        HBox metaRow2 = new HBox(14);
        metaRow2.setAlignment(Pos.CENTER_LEFT);

        String evDateStr = (item.getEventDate() != null) ? item.getEventDate().format(dateFormatter) : "TBD";
        String evTimeStr = (item.getEventTime() != null) ? item.getEventTime().format(timeFormatter) : "";
        String timing = evTimeStr.isEmpty() ? evDateStr : evDateStr + " • " + evTimeStr;

        Label scheduleLabel = new Label("📅  " + timing + "   •   📍  " + item.getVenue());
        scheduleLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px;");

        metaRow2.getChildren().add(scheduleLabel);

        // Bottom line: Registered on timestamp and Action button
        HBox bottomRow = new HBox(10);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        String regDateStr = (item.getRegistrationDate() != null)
                ? item.getRegistrationDate().format(dateFormatter)
                : "Recent";
        Label regDateLabel = new Label("📝  Registered on: " + regDateStr);
        regDateLabel.setStyle("-fx-text-fill: #68677A; -fx-font-size: 12px;");

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        Button viewDetailsBtn = new Button("View Event Details →");
        viewDetailsBtn.getStyleClass().add("secondary-btn");
        if (onViewEventDetails != null && item.getEvent() != null) {
            viewDetailsBtn.setOnAction(e -> onViewEventDetails.accept(item.getEvent()));
        }

        bottomRow.getChildren().addAll(regDateLabel, bottomSpacer, viewDetailsBtn);

        card.getChildren().addAll(topRow, metaRow1, metaRow2, bottomRow);

        // Clicking anywhere on the card navigates to event details
        if (onViewEventDetails != null && item.getEvent() != null) {
            card.setOnMouseClicked(e -> onViewEventDetails.accept(item.getEvent()));
        }

        return card;
    }

    private void renderZeroRegistrationsState() {
        VBox emptyBox = new VBox(10);
        emptyBox.getStyleClass().add("empty-state-box");
        emptyBox.setAlignment(Pos.CENTER);

        Label title = new Label("You haven't registered for any events yet.");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Discover upcoming campus club activities, workshops, and competitions to sign up.");
        sub.getStyleClass().add("empty-state-sub");

        Button exploreBtn = new Button("Explore Events →");
        exploreBtn.getStyleClass().add("primary-btn");
        if (onExploreEvents != null) {
            exploreBtn.setOnAction(e -> onExploreEvents.run());
        }

        emptyBox.getChildren().addAll(title, sub, exploreBtn);
        cardsContainer.getChildren().add(emptyBox);
    }

    private void renderFilteredEmptyState(String filter) {
        VBox emptyBox = new VBox(10);
        emptyBox.getStyleClass().add("empty-state-box");
        emptyBox.setAlignment(Pos.CENTER);

        Label title = new Label("No " + filter.toLowerCase() + " registrations found.");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("You don't have any event registrations matching the \"" + filter + "\" status filter.");
        sub.getStyleClass().add("empty-state-sub");

        Button resetBtn = new Button("Show All Registrations");
        resetBtn.getStyleClass().add("secondary-btn");
        resetBtn.setOnAction(e -> {
            currentFilter = "ALL";
            updateFilterButtonStyles();
            renderFilteredList();
        });

        emptyBox.getChildren().addAll(title, sub, resetBtn);
        cardsContainer.getChildren().add(emptyBox);
    }

    private void renderOfflineState() {
        cardsContainer.getChildren().clear();

        totalMetricValue.setText("—");
        pendingMetricValue.setText("—");
        approvedMetricValue.setText("—");
        rejectedMetricValue.setText("—");

        VBox offlineBox = new VBox(10);
        offlineBox.getStyleClass().add("empty-state-box");
        offlineBox.setAlignment(Pos.CENTER);

        Label title = new Label("Registration data unavailable");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Unable to reach the MySQL database server. Please verify your connection.");
        sub.getStyleClass().add("empty-state-sub");

        Button retryBtn = new Button("↻ Retry Connection");
        retryBtn.getStyleClass().add("secondary-btn");
        retryBtn.setOnAction(e -> loadInitialData());

        offlineBox.getChildren().addAll(title, sub, retryBtn);
        cardsContainer.getChildren().add(offlineBox);
    }

    private void renderAdminNotice() {
        cardsContainer.getChildren().clear();

        totalMetricValue.setText("—");
        pendingMetricValue.setText("—");
        approvedMetricValue.setText("—");
        rejectedMetricValue.setText("—");

        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label("Administrator Portal");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("My Events is dedicated to student accounts to track registrations. Admin event management will be available in later phases.");
        sub.getStyleClass().add("empty-state-sub");

        Button dashBtn = new Button("Return to Dashboard");
        dashBtn.getStyleClass().add("primary-btn");
        if (onNavigateDashboard != null) {
            dashBtn.setOnAction(e -> onNavigateDashboard.run());
        }

        box.getChildren().addAll(title, sub, dashBtn);
        cardsContainer.getChildren().add(box);
    }

    private void renderUnauthorizedState(String msg) {
        cardsContainer.getChildren().clear();

        VBox box = new VBox(8);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label("Sign In Required");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label(msg);
        sub.getStyleClass().add("empty-state-sub");

        box.getChildren().addAll(title, sub);
        cardsContainer.getChildren().add(box);
    }

    public Parent getView() {
        return rootScrollPane;
    }
}
