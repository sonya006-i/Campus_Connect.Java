package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import model.AdminRegistrationDTO;
import model.Event;
import service.RegistrationManagementService;
import service.RegistrationManagementService.OperationResult;
import service.RegistrationManagementService.RegistrationSummaryDTO;
import util.DBConnection;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AdminRegistrationsController — Administrator Registration Review & Management View.
 *
 * Implements Phase 10: Registration Management per AGENTS.md:
 * - Administrator-only registration review, approval, and rejection
 * - Live KPI metric pills (Total, Pending, Approved, Rejected)
 * - Multi-criteria search (student name, email, event title, club)
 * - Status filtering (All, Pending, Approved, Rejected) and Event filtering
 * - Explicit confirmation modal before rejection to prevent accidental clicks
 * - Capacity-safe approval verification and atomic concurrency protection
 * - Asynchronous database loading with modern dark SaaS aesthetics
 */
public class AdminRegistrationsController {

    private final RegistrationManagementService registrationService;

    private StackPane rootContainer;
    private ScrollPane mainScrollPane;
    private StackPane modalOverlay;

    // KPI Counters
    private Label totalCounterLabel;
    private Label pendingCounterLabel;
    private Label approvedCounterLabel;
    private Label rejectedCounterLabel;

    // Filters and Toolbar UI
    private TextField searchField;
    private Button filterAllBtn;
    private Button filterPendingBtn;
    private Button filterApprovedBtn;
    private Button filterRejectedBtn;
    private ComboBox<EventFilterItem> eventFilterCombo;
    private Label resultsCountLabel;
    private VBox registrationsListContainer;

    // Banner notifications
    private HBox feedbackBanner;
    private Label feedbackText;
    private Button dismissFeedbackBtn;

    // Active Filter State
    private String activeStatusFilter = "ALL"; // "ALL", "PENDING", "APPROVED", "REJECTED"
    private final List<AdminRegistrationDTO> cachedRegistrations = new ArrayList<>();
    private final List<Event> cachedEvents = new ArrayList<>();

    // Formatting helpers
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    // Navigation callbacks
    private Runnable onNavigateDashboard;

    public AdminRegistrationsController() {
        this.registrationService = new RegistrationManagementService();
        buildView();
    }

    public void setOnNavigateDashboard(Runnable onNavigateDashboard) {
        this.onNavigateDashboard = onNavigateDashboard;
    }

    public Parent getView() {
        return rootContainer;
    }

    private void buildView() {
        rootContainer = new StackPane();
        rootContainer.getStyleClass().add("root");

        // 1. Main Scrollable Content
        mainScrollPane = new ScrollPane();
        mainScrollPane.getStyleClass().add("main-scroll-pane");
        mainScrollPane.setFitToWidth(true);

        VBox contentBox = new VBox(20);
        contentBox.getStyleClass().add("main-content");

        // Top Header
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label titleLabel = new Label("Registration Management");
        titleLabel.getStyleClass().add("header-title");

        Label subtitleLabel = new Label("Review student applications, approve confirmed attendees, or reject invalid requests.");
        subtitleLabel.getStyleClass().add("header-subtitle");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox headerActions = new HBox(10);
        headerActions.setAlignment(Pos.CENTER_RIGHT);

        Button backBtn = new Button("⊞ Dashboard");
        backBtn.getStyleClass().add("secondary-btn");
        backBtn.setOnAction(e -> {
            if (onNavigateDashboard != null) onNavigateDashboard.run();
        });

        Button refreshBtn = new Button("↻ Refresh");
        refreshBtn.getStyleClass().add("secondary-btn");
        refreshBtn.setOnAction(e -> loadInitialData());

        headerActions.getChildren().addAll(backBtn, refreshBtn);
        headerBox.getChildren().addAll(titleBox, headerSpacer, headerActions);

        // Feedback Banner
        feedbackBanner = new HBox(12);
        feedbackBanner.setAlignment(Pos.CENTER_LEFT);
        feedbackBanner.setVisible(false);
        feedbackBanner.setManaged(false);

        feedbackText = new Label();

        Region bannerSpacer = new Region();
        HBox.setHgrow(bannerSpacer, Priority.ALWAYS);

        dismissFeedbackBtn = new Button("✕");
        dismissFeedbackBtn.getStyleClass().add("secondary-btn");
        dismissFeedbackBtn.setStyle("-fx-padding: 2px 8px; -fx-font-size: 11px;");
        dismissFeedbackBtn.setOnAction(e -> hideFeedback());

        feedbackBanner.getChildren().addAll(feedbackText, bannerSpacer, dismissFeedbackBtn);

        // KPI Summary Bar
        HBox kpiStrip = createKpiSummaryBar();

        // Search & Filter Toolbar
        HBox filterBar = createFilterToolbar();

        // Registrations List Container
        registrationsListContainer = new VBox(14);

        contentBox.getChildren().addAll(headerBox, feedbackBanner, kpiStrip, filterBar, registrationsListContainer);
        mainScrollPane.setContent(contentBox);

        // 2. Modal Overlay Container
        modalOverlay = new StackPane();
        modalOverlay.getStyleClass().add("dialog-overlay");
        modalOverlay.setVisible(false);
        modalOverlay.setPickOnBounds(true);

        rootContainer.getChildren().addAll(mainScrollPane, modalOverlay);
    }

    /**
     * Constructs the 4 KPI count pills across the top.
     */
    private HBox createKpiSummaryBar() {
        HBox bar = new HBox(14);
        bar.setAlignment(Pos.CENTER_LEFT);

        HBox pill1 = createStatPill("Total Registrations", "--", "#6366F1");
        totalCounterLabel = (Label) pill1.getUserData();

        HBox pill2 = createStatPill("Pending Review", "--", "#F59E0B");
        pendingCounterLabel = (Label) pill2.getUserData();

        HBox pill3 = createStatPill("Approved", "--", "#22C55E");
        approvedCounterLabel = (Label) pill3.getUserData();

        HBox pill4 = createStatPill("Rejected", "--", "#EF4444");
        rejectedCounterLabel = (Label) pill4.getUserData();

        HBox.setHgrow(pill1, Priority.ALWAYS);
        HBox.setHgrow(pill2, Priority.ALWAYS);
        HBox.setHgrow(pill3, Priority.ALWAYS);
        HBox.setHgrow(pill4, Priority.ALWAYS);

        bar.getChildren().addAll(pill1, pill2, pill3, pill4);
        return bar;
    }

    private HBox createStatPill(String title, String defaultValue, String accentColor) {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("stat-counter-pill");

        VBox textBox = new VBox(2);
        Label numLabel = new Label(defaultValue);
        numLabel.getStyleClass().add("stat-counter-number");
        numLabel.setStyle("-fx-text-fill: " + accentColor + ";");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-counter-label");

        textBox.getChildren().addAll(numLabel, titleLabel);
        box.getChildren().add(textBox);
        box.setUserData(numLabel);

        return box;
    }

    /**
     * Constructs the search bar and filter controls.
     */
    private HBox createFilterToolbar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.getStyleClass().add("dashboard-card");

        // Keyword Search Field
        searchField = new TextField();
        searchField.setPromptText("Search student, event, or club...");
        searchField.getStyleClass().add("text-input");
        searchField.setPrefWidth(280);
        searchField.setMinWidth(180);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Status Filter Buttons
        HBox statusPillsBox = new HBox(6);
        statusPillsBox.setAlignment(Pos.CENTER_LEFT);

        filterAllBtn = createFilterPill("All", "ALL", true);
        filterPendingBtn = createFilterPill("Pending", "PENDING", false);
        filterApprovedBtn = createFilterPill("Approved", "APPROVED", false);
        filterRejectedBtn = createFilterPill("Rejected", "REJECTED", false);

        statusPillsBox.getChildren().addAll(filterAllBtn, filterPendingBtn, filterApprovedBtn, filterRejectedBtn);

        // Event dropdown
        eventFilterCombo = new ComboBox<>();
        eventFilterCombo.getStyleClass().add("combo-box");
        eventFilterCombo.setPrefWidth(190);
        eventFilterCombo.setMinWidth(140);
        eventFilterCombo.getItems().add(new EventFilterItem(-1, "All Events"));
        eventFilterCombo.setValue(eventFilterCombo.getItems().get(0));
        eventFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(EventFilterItem item) {
                return item != null ? item.getEventName() : "";
            }

            @Override
            public EventFilterItem fromString(String string) {
                return null;
            }
        });
        eventFilterCombo.setOnAction(e -> applyFilters());

        Button resetBtn = new Button("✕ Reset");
        resetBtn.getStyleClass().add("secondary-btn");
        resetBtn.setMinWidth(Region.USE_PREF_SIZE);
        resetBtn.setOnAction(e -> resetFilters());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        resultsCountLabel = new Label("Loading registrations...");
        resultsCountLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 13px; -fx-font-weight: 600;");
        resultsCountLabel.setMinWidth(Region.USE_PREF_SIZE);

        bar.getChildren().addAll(searchField, statusPillsBox, eventFilterCombo, resetBtn, spacer, resultsCountLabel);
        return bar;
    }

    private Button createFilterPill(String text, String statusValue, boolean active) {
        Button btn = new Button(text);
        if (active) {
            btn.getStyleClass().add("filter-pill-active");
        } else {
            btn.getStyleClass().add("filter-pill");
        }

        btn.setOnAction(e -> {
            activeStatusFilter = statusValue;
            updateFilterPillsStyling();
            applyFilters();
        });

        return btn;
    }

    private void updateFilterPillsStyling() {
        setBtnActive(filterAllBtn, "ALL".equals(activeStatusFilter));
        setBtnActive(filterPendingBtn, "PENDING".equals(activeStatusFilter));
        setBtnActive(filterApprovedBtn, "APPROVED".equals(activeStatusFilter));
        setBtnActive(filterRejectedBtn, "REJECTED".equals(activeStatusFilter));
    }

    private void setBtnActive(Button btn, boolean active) {
        btn.getStyleClass().removeAll("filter-pill", "filter-pill-active");
        if (active) {
            btn.getStyleClass().add("filter-pill-active");
        } else {
            btn.getStyleClass().add("filter-pill");
        }
    }

    /**
     * Loads initial data asynchronously from the database.
     */
    public void loadInitialData() {
        if (!registrationService.isCurrentUserAdmin()) {
            renderAccessDeniedState();
            return;
        }

        resultsCountLabel.setText("Connecting to database...");
        new Thread(() -> {
            RegistrationSummaryDTO summary = registrationService.getRegistrationSummary();
            List<Event> events = registrationService.getAllEventsForFilter();
            List<AdminRegistrationDTO> list = registrationService.getRegistrations(null, null, null);

            Platform.runLater(() -> {
                // Update KPI counts
                if (summary.isDbOnline()) {
                    totalCounterLabel.setText(String.valueOf(summary.getTotal()));
                    pendingCounterLabel.setText(String.valueOf(summary.getPending()));
                    approvedCounterLabel.setText(String.valueOf(summary.getApproved()));
                    rejectedCounterLabel.setText(String.valueOf(summary.getRejected()));
                } else {
                    totalCounterLabel.setText("--");
                    pendingCounterLabel.setText("--");
                    approvedCounterLabel.setText("--");
                    rejectedCounterLabel.setText("--");
                }

                // Update Event Filter Combo
                cachedEvents.clear();
                cachedEvents.addAll(events);

                eventFilterCombo.getItems().clear();
                eventFilterCombo.getItems().add(new EventFilterItem(-1, "All Events"));
                for (Event ev : events) {
                    eventFilterCombo.getItems().add(new EventFilterItem(ev.getEventId(), ev.getEventName()));
                }
                eventFilterCombo.getSelectionModel().selectFirst();

                // Update Registrations List
                cachedRegistrations.clear();
                cachedRegistrations.addAll(list);

                applyFilters();
            });
        }).start();
    }

    /**
     * Applies search and status filters to the in-memory registrations list.
     */
    private void applyFilters() {
        if (!registrationService.isCurrentUserAdmin()) {
            renderAccessDeniedState();
            return;
        }

        String keyword = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        EventFilterItem selectedEvent = eventFilterCombo.getValue();
        int filterEventId = (selectedEvent != null) ? selectedEvent.getEventId() : -1;

        List<AdminRegistrationDTO> filtered = new ArrayList<>();

        for (AdminRegistrationDTO reg : cachedRegistrations) {
            // Status filter
            if (!"ALL".equals(activeStatusFilter)) {
                if (!activeStatusFilter.equalsIgnoreCase(reg.getStatus())) {
                    continue;
                }
            }

            // Event filter
            if (filterEventId > 0 && reg.getEventId() != filterEventId) {
                continue;
            }

            // Keyword filter
            if (!keyword.isEmpty()) {
                boolean matches = reg.getStudentName().toLowerCase().contains(keyword)
                        || reg.getStudentEmail().toLowerCase().contains(keyword)
                        || reg.getEventName().toLowerCase().contains(keyword)
                        || reg.getClubName().toLowerCase().contains(keyword)
                        || reg.getFormattedRegistrationId().toLowerCase().contains(keyword);
                if (!matches) {
                    continue;
                }
            }

            filtered.add(reg);
        }

        renderRegistrationsList(filtered);
    }

    private void resetFilters() {
        searchField.clear();
        activeStatusFilter = "ALL";
        updateFilterPillsStyling();
        if (!eventFilterCombo.getItems().isEmpty()) {
            eventFilterCombo.getSelectionModel().selectFirst();
        }
        applyFilters();
    }

    /**
     * Renders registration cards into the UI container.
     */
    private void renderRegistrationsList(List<AdminRegistrationDTO> registrations) {
        registrationsListContainer.getChildren().clear();

        if (!DBConnection.testConnection() && cachedRegistrations.isEmpty()) {
            renderOfflineState();
            return;
        }

        if (registrations.isEmpty()) {
            renderEmptyState();
            return;
        }

        resultsCountLabel.setText("Showing " + registrations.size() + " registration" + (registrations.size() == 1 ? "" : "s"));

        for (AdminRegistrationDTO reg : registrations) {
            registrationsListContainer.getChildren().add(createRegistrationCard(reg));
        }
    }

    /**
     * Creates a single rich registration review card with status badges and action buttons.
     */
    private VBox createRegistrationCard(AdminRegistrationDTO reg) {
        VBox card = new VBox(12);
        card.getStyleClass().add("dashboard-card");
        card.setPadding(new Insets(18, 20, 18, 20));

        // Top Row: Student details, ID pill, status badge
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label studentNameLabel = new Label(reg.getStudentName());
        studentNameLabel.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 15px; -fx-font-weight: 800;");

        Label studentEmailLabel = new Label("• " + reg.getStudentEmail());
        studentEmailLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 13px;");

        Label idBadge = new Label(reg.getFormattedRegistrationId());
        idBadge.getStyleClass().add("badge-neutral");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        // Status Badge
        Label statusBadge = new Label();
        if (reg.isPending()) {
            statusBadge.setText("Pending Review");
            statusBadge.getStyleClass().setAll("badge-warning");
        } else if (reg.isApproved()) {
            statusBadge.setText("Approved");
            statusBadge.getStyleClass().setAll("badge-success");
        } else {
            statusBadge.setText("Rejected");
            statusBadge.getStyleClass().setAll("badge-danger");
        }

        topRow.getChildren().addAll(studentNameLabel, studentEmailLabel, idBadge, topSpacer, statusBadge);

        // Middle Row: Event Name, Club, Category
        HBox midRow = new HBox(10);
        midRow.setAlignment(Pos.CENTER_LEFT);

        Label eventTitle = new Label(reg.getEventName());
        eventTitle.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 14px; -fx-font-weight: 700;");

        Label clubLabel = new Label("🏛 " + reg.getClubName());
        clubLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");

        Label catBadge = new Label(reg.getCategory());
        catBadge.getStyleClass().add("badge-indigo");

        midRow.getChildren().addAll(eventTitle, clubLabel, catBadge);

        // Bottom Row: Metadata (Dates, Venue, Max Cap) & Actions
        HBox bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        String regDateStr = reg.getRegistrationDate() != null ? reg.getRegistrationDate().format(dateFormatter) : "Recent";
        Label regDateLabel = new Label("📅 Registered: " + regDateStr);
        regDateLabel.getStyleClass().add("event-meta");

        String evDateStr = reg.getEventDate() != null ? reg.getEventDate().format(dateFormatter) : "TBD";
        String evTimeStr = reg.getEventTime() != null ? reg.getEventTime().format(timeFormatter) : "";
        String schedule = evTimeStr.isEmpty() ? evDateStr : evDateStr + " • " + evTimeStr;

        Label scheduleLabel = new Label("🗓 Event: " + schedule);
        scheduleLabel.getStyleClass().add("event-meta");

        Label venueLabel = new Label("📍 " + reg.getVenue());
        venueLabel.getStyleClass().add("event-meta");

        Label capLabel = new Label("👥 Cap: " + reg.getMaxParticipants());
        capLabel.getStyleClass().add("event-meta");

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        HBox actionsBox = new HBox(8);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);

        if (reg.isPending()) {
            Button approveBtn = new Button("✓ Approve");
            approveBtn.getStyleClass().add("action-approve-btn");
            approveBtn.setMinWidth(Region.USE_PREF_SIZE);
            approveBtn.setOnAction(e -> openApproveConfirmDialog(reg));

            Button rejectBtn = new Button("✕ Reject");
            rejectBtn.getStyleClass().add("danger-btn");
            rejectBtn.setMinWidth(Region.USE_PREF_SIZE);
            rejectBtn.setOnAction(e -> openRejectConfirmDialog(reg));

            actionsBox.getChildren().addAll(approveBtn, rejectBtn);
        } else if (reg.isApproved()) {
            Label confirmedLabel = new Label("✓ Confirmed Attendee");
            confirmedLabel.setStyle("-fx-text-fill: #22C55E; -fx-font-size: 12px; -fx-font-weight: 600;");
            actionsBox.getChildren().add(confirmedLabel);
        } else {
            Label rejectedLabel = new Label("✕ Registration Declined");
            rejectedLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12px; -fx-font-weight: 600;");
            actionsBox.getChildren().add(rejectedLabel);
        }

        bottomRow.getChildren().addAll(regDateLabel, scheduleLabel, venueLabel, capLabel, bottomSpacer, actionsBox);

        card.getChildren().addAll(topRow, midRow, bottomRow);
        return card;
    }

    /**
     * Opens explicit confirmation modal before approving a registration.
     */
    private void openApproveConfirmDialog(AdminRegistrationDTO reg) {
        modalOverlay.getChildren().clear();

        VBox dialogCard = new VBox(16);
        dialogCard.getStyleClass().add("modal-dialog-large");

        // Header
        HBox modalHeader = new HBox();
        modalHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(3);
        Label modalTitle = new Label("Approve Registration");
        modalTitle.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 18px; -fx-font-weight: 800;");

        Label modalSubtitle = new Label("Confirm approval for student registration request.");
        modalSubtitle.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px;");
        titleBox.getChildren().addAll(modalTitle, modalSubtitle);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("secondary-btn");
        closeBtn.setOnAction(e -> closeModal());

        modalHeader.getChildren().addAll(titleBox, headerSpacer, closeBtn);

        // Details card
        VBox detailsCard = new VBox(8);
        detailsCard.setStyle("-fx-background-color: #0B0A1A; -fx-padding: 14px; -fx-background-radius: 8px; -fx-border-color: rgba(255,255,255,0.06); -fx-border-radius: 8px;");

        Label sName = new Label("Student: " + reg.getStudentName() + " (" + reg.getStudentEmail() + ")");
        sName.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 13.5px; -fx-font-weight: bold;");

        Label eName = new Label("Event: " + reg.getEventName() + " • " + reg.getClubName());
        eName.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 13px; -fx-font-weight: 600;");

        Label capInfo = new Label("Max Capacity: " + (reg.getMaxParticipants() > 0 ? reg.getMaxParticipants() + " participants" : "Unlimited"));
        capInfo.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");

        Label note = new Label("Approving verifies remaining seat capacity and grants official event attendance access.");
        note.setStyle("-fx-text-fill: #22C55E; -fx-font-size: 12px;");

        detailsCard.getChildren().addAll(sName, eName, capInfo, note);

        // Buttons
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button confirmApproveBtn = new Button("✓ Confirm Approval");
        confirmApproveBtn.getStyleClass().add("action-approve-btn-solid");
        confirmApproveBtn.setOnAction(e -> {
            confirmApproveBtn.setDisable(true);
            executeApproval(reg.getRegistrationId());
        });

        btnBox.getChildren().addAll(cancelBtn, confirmApproveBtn);

        dialogCard.getChildren().addAll(modalHeader, detailsCard, btnBox);
        modalOverlay.getChildren().add(dialogCard);
        modalOverlay.setVisible(true);
    }

    /**
     * Executes the approval transaction asynchronously.
     */
    private void executeApproval(int registrationId) {
        new Thread(() -> {
            OperationResult result = registrationService.approveRegistration(registrationId);
            Platform.runLater(() -> {
                closeModal();
                if (result.isSuccess()) {
                    showToast(result.getMessage(), true);
                    loadInitialData(); // Reload live data
                } else {
                    showToast(result.getMessage(), false);
                }
            });
        }).start();
    }

    /**
     * Opens explicit confirmation modal before rejecting a registration.
     * Prevents accidental clicks and explains consequences.
     */
    private void openRejectConfirmDialog(AdminRegistrationDTO reg) {
        modalOverlay.getChildren().clear();

        VBox dialogCard = new VBox(16);
        dialogCard.getStyleClass().add("modal-dialog-large");

        // Header
        HBox modalHeader = new HBox();
        modalHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(3);
        Label modalTitle = new Label("Reject Registration");
        modalTitle.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 18px; -fx-font-weight: 800;");

        Label modalSubtitle = new Label("Explicit confirmation required before rejecting this student's registration.");
        modalSubtitle.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px;");
        titleBox.getChildren().addAll(modalTitle, modalSubtitle);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("secondary-btn");
        closeBtn.setOnAction(e -> closeModal());

        modalHeader.getChildren().addAll(titleBox, headerSpacer, closeBtn);

        // Warning Box
        VBox warningBox = new VBox(8);
        warningBox.setStyle("-fx-background-color: rgba(239, 68, 68, 0.10); -fx-padding: 14px; -fx-background-radius: 8px; -fx-border-color: rgba(239, 68, 68, 0.35); -fx-border-radius: 8px;");

        Label studentInfo = new Label("Are you sure you want to reject registration " + reg.getFormattedRegistrationId() + "?");
        studentInfo.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 13.5px; -fx-font-weight: bold;");

        Label details = new Label("• Student: " + reg.getStudentName() + " (" + reg.getStudentEmail() + ")\n"
                + "• Event: " + reg.getEventName() + " (" + reg.getClubName() + ")");
        details.setStyle("-fx-text-fill: #D1D5DB; -fx-font-size: 12.5px;");

        Label consequence = new Label("This action will mark the registration as REJECTED. The student will be unable to attend unless re-registered.");
        consequence.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12px; -fx-font-weight: 600;");
        consequence.setWrapText(true);

        warningBox.getChildren().addAll(studentInfo, details, consequence);

        // Buttons
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button confirmRejectBtn = new Button("✕ Confirm Rejection");
        confirmRejectBtn.getStyleClass().add("danger-btn-solid");
        confirmRejectBtn.setOnAction(e -> {
            confirmRejectBtn.setDisable(true);
            executeRejection(reg.getRegistrationId());
        });

        btnBox.getChildren().addAll(cancelBtn, confirmRejectBtn);

        dialogCard.getChildren().addAll(modalHeader, warningBox, btnBox);
        modalOverlay.getChildren().add(dialogCard);
        modalOverlay.setVisible(true);
    }

    /**
     * Executes the rejection transaction asynchronously.
     */
    private void executeRejection(int registrationId) {
        new Thread(() -> {
            OperationResult result = registrationService.rejectRegistration(registrationId);
            Platform.runLater(() -> {
                closeModal();
                if (result.isSuccess()) {
                    showToast(result.getMessage(), true);
                    loadInitialData(); // Reload live data
                } else {
                    showToast(result.getMessage(), false);
                }
            });
        }).start();
    }

    private void closeModal() {
        modalOverlay.setVisible(false);
        modalOverlay.getChildren().clear();
    }

    private void showToast(String message, boolean success) {
        feedbackBanner.getStyleClass().removeAll("success-banner", "error-banner");
        feedbackText.getStyleClass().removeAll("success-banner-text", "error-text");

        if (success) {
            feedbackBanner.getStyleClass().add("success-banner");
            feedbackText.getStyleClass().add("success-banner-text");
        } else {
            feedbackBanner.getStyleClass().add("error-banner");
            feedbackText.getStyleClass().add("error-text");
        }

        feedbackText.setText(message);
        feedbackBanner.setVisible(true);
        feedbackBanner.setManaged(true);
    }

    private void hideFeedback() {
        feedbackBanner.setVisible(false);
        feedbackBanner.setManaged(false);
    }

    private void renderEmptyState() {
        resultsCountLabel.setText("0 registrations found");
        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label("No registrations match your criteria");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Try adjusting your status filter, event selection, or search keywords.");
        sub.getStyleClass().add("empty-state-sub");

        Button resetBtn = new Button("Reset All Filters");
        resetBtn.getStyleClass().add("secondary-btn");
        resetBtn.setOnAction(e -> resetFilters());

        box.getChildren().addAll(title, sub, resetBtn);
        registrationsListContainer.getChildren().add(box);
    }

    private void renderOfflineState() {
        resultsCountLabel.setText("Database Offline");
        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label("Database is offline");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Unable to load registration records. Please ensure your MySQL server is running.");
        sub.getStyleClass().add("empty-state-sub");

        Button retryBtn = new Button("↻ Retry Connection");
        retryBtn.getStyleClass().add("primary-btn");
        retryBtn.setOnAction(e -> loadInitialData());

        box.getChildren().addAll(title, sub, retryBtn);
        registrationsListContainer.getChildren().add(box);
    }

    private void renderAccessDeniedState() {
        registrationsListContainer.getChildren().clear();
        resultsCountLabel.setText("Access Denied");

        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label("Administrator Access Required");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Registration review and management is restricted to system administrators.");
        sub.getStyleClass().add("empty-state-sub");

        box.getChildren().addAll(title, sub);
        registrationsListContainer.getChildren().add(box);
    }

    /**
     * Filter helper item for the Event ComboBox.
     */
    private static class EventFilterItem {
        private final int eventId;
        private final String eventName;

        public EventFilterItem(int eventId, String eventName) {
            this.eventId = eventId;
            this.eventName = eventName;
        }

        public int getEventId() {
            return eventId;
        }

        public String getEventName() {
            return eventName;
        }

        @Override
        public String toString() {
            return eventName;
        }
    }
}
