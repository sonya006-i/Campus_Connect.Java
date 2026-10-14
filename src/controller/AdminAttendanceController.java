package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import model.Event;
import model.EventAttendanceDTO;
import service.AttendanceManagementService;
import service.AttendanceManagementService.AttendanceSummaryDTO;
import service.AttendanceManagementService.OperationResult;
import util.DBConnection;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AdminAttendanceController — Administrator Attendance Tracking & Management View.
 *
 * Implements Phase 11: Attendance Management per AGENTS.md:
 * - Administrator-only event attendance roster tracking
 * - Event selection with live participant loading
 * - Filter by Approved Only (default), All, Present, Absent, and Unmarked
 * - Mark individual attendees as Present or Absent
 * - Safe reset/unmarking mechanism to correct accidental marks
 * - Real-time KPI summary bar (Approved, Present, Absent, Unmarked, Attendance Rate %)
 * - Prevents marking pending/rejected registrations as present
 * - Asynchronous background loading with dark SaaS dashboard aesthetics
 */
public class AdminAttendanceController {

    private final AttendanceManagementService attendanceService;

    private StackPane rootContainer;
    private ScrollPane mainScrollPane;
    private StackPane modalOverlay;

    // Event Selector UI
    private ComboBox<EventSelectItem> eventCombo;
    private Label eventDetailsLabel;
    private Button markAllPresentBtn;

    // KPI Counters
    private Label approvedCounterLabel;
    private Label presentCounterLabel;
    private Label absentCounterLabel;
    private Label unmarkedCounterLabel;
    private Label ratePercentLabel;
    private ProgressBar rateProgressBar;

    // Filters and Toolbar UI
    private TextField searchField;
    private Button filterApprovedOnlyBtn;
    private Button filterAllBtn;
    private Button filterPresentBtn;
    private Button filterAbsentBtn;
    private Button filterUnmarkedBtn;
    private Label resultsCountLabel;
    private VBox rosterListContainer;

    // Banner notifications
    private HBox feedbackBanner;
    private Label feedbackText;
    private Button dismissFeedbackBtn;

    // Active State
    private String activeFilter = "APPROVED_ONLY"; // "APPROVED_ONLY", "ALL", "PRESENT", "ABSENT", "UNMARKED"
    private final List<Event> cachedEvents = new ArrayList<>();
    private final List<EventAttendanceDTO> cachedRoster = new ArrayList<>();
    private int selectedEventId = -1;

    // Formatting helpers
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    // Navigation callbacks
    private Runnable onNavigateDashboard;

    public AdminAttendanceController() {
        this.attendanceService = new AttendanceManagementService();
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
        Label titleLabel = new Label("Attendance Management");
        titleLabel.getStyleClass().add("header-title");

        Label subtitleLabel = new Label("Track participant attendance, verify attendee presence, and manage event rosters.");
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
        refreshBtn.setOnAction(e -> reloadCurrentEventRoster());

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

        // Event Selector Card
        HBox eventSelectorBar = createEventSelectorBar();

        // KPI Summary Bar
        HBox kpiStrip = createKpiSummaryBar();

        // Search & Filter Toolbar
        HBox filterBar = createFilterToolbar();

        // Attendance Roster List Container
        rosterListContainer = new VBox(12);

        contentBox.getChildren().addAll(headerBox, feedbackBanner, eventSelectorBar, kpiStrip, filterBar, rosterListContainer);
        mainScrollPane.setContent(contentBox);

        // 2. Modal Overlay Container
        modalOverlay = new StackPane();
        modalOverlay.getStyleClass().add("dialog-overlay");
        modalOverlay.setVisible(false);
        modalOverlay.setPickOnBounds(true);

        rootContainer.getChildren().addAll(mainScrollPane, modalOverlay);
    }

    /**
     * Constructs the Event Selector top bar with event dropdown and quick actions.
     */
    private HBox createEventSelectorBar() {
        HBox bar = new HBox(14);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 18, 14, 18));
        bar.getStyleClass().add("dashboard-card");

        Label label = new Label("Select Event:");
        label.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 13px; -fx-font-weight: 700;");
        label.setMinWidth(Region.USE_PREF_SIZE);

        eventCombo = new ComboBox<>();
        eventCombo.getStyleClass().add("combo-box");
        eventCombo.setPrefWidth(280);
        eventCombo.setMinWidth(220);
        eventCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(EventSelectItem item) {
                return item != null ? item.getDisplayText() : "Select Event...";
            }

            @Override
            public EventSelectItem fromString(String string) {
                return null;
            }
        });
        eventCombo.setOnAction(e -> {
            EventSelectItem selected = eventCombo.getValue();
            if (selected != null && selected.getEventId() > 0) {
                selectedEventId = selected.getEventId();
                updateEventDetailsHeader(selected.getEvent());
                loadEventRoster(selectedEventId);
            }
        });

        eventDetailsLabel = new Label("Choose an event to view attendee roster.");
        eventDetailsLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");
        eventDetailsLabel.setMinWidth(0);
        HBox.setHgrow(eventDetailsLabel, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        markAllPresentBtn = new Button("✓ Mark All Approved Present");
        markAllPresentBtn.getStyleClass().add("action-approve-btn");
        markAllPresentBtn.setMinWidth(Region.USE_PREF_SIZE);
        markAllPresentBtn.setOnAction(e -> openBatchConfirmDialog());

        bar.getChildren().addAll(label, eventCombo, eventDetailsLabel, spacer, markAllPresentBtn);
        return bar;
    }

    private void updateEventDetailsHeader(Event ev) {
        if (ev == null) {
            eventDetailsLabel.setText("");
            return;
        }

        String dateStr = (ev.getEventDate() != null) ? ev.getEventDate().format(dateFormatter) : "TBD";
        String timeStr = (ev.getEventTime() != null) ? ev.getEventTime().format(timeFormatter) : "";
        String sched = timeStr.isEmpty() ? dateStr : dateStr + " • " + timeStr;
        String venue = (ev.getVenue() != null) ? ev.getVenue() : "Campus";
        String club = (ev.getClubName() != null) ? ev.getClubName() : "Club #" + ev.getClubId();

        eventDetailsLabel.setText("📅 " + sched + "   •   📍 " + venue + "   •   🏛 " + club);
    }

    /**
     * Constructs the 4 KPI count pills + progress indicator across the top.
     */
    private HBox createKpiSummaryBar() {
        HBox bar = new HBox(14);
        bar.setAlignment(Pos.CENTER_LEFT);

        HBox pill1 = createStatPill("Approved Participants", "--", "#6366F1");
        approvedCounterLabel = (Label) pill1.getUserData();

        HBox pill2 = createStatPill("Marked Present", "--", "#22C55E");
        presentCounterLabel = (Label) pill2.getUserData();

        HBox pill3 = createStatPill("Marked Absent", "--", "#EF4444");
        absentCounterLabel = (Label) pill3.getUserData();

        HBox pill4 = createStatPill("Unmarked / Awaiting", "--", "#F59E0B");
        unmarkedCounterLabel = (Label) pill4.getUserData();

        // Rate pill with progress bar
        HBox rateBox = new HBox(12);
        rateBox.setAlignment(Pos.CENTER_LEFT);
        rateBox.getStyleClass().add("stat-counter-pill");

        VBox rateTextBox = new VBox(2);
        ratePercentLabel = new Label("0% Rate");
        ratePercentLabel.getStyleClass().add("stat-counter-number");
        ratePercentLabel.setStyle("-fx-text-fill: #22C55E; -fx-font-size: 16px;");

        rateProgressBar = new ProgressBar(0.0);
        rateProgressBar.setPrefWidth(120);

        Label rateTitle = new Label("Attendance Rate");
        rateTitle.getStyleClass().add("stat-counter-label");

        rateTextBox.getChildren().addAll(ratePercentLabel, rateProgressBar, rateTitle);
        rateBox.getChildren().add(rateTextBox);

        HBox.setHgrow(pill1, Priority.ALWAYS);
        HBox.setHgrow(pill2, Priority.ALWAYS);
        HBox.setHgrow(pill3, Priority.ALWAYS);
        HBox.setHgrow(pill4, Priority.ALWAYS);
        HBox.setHgrow(rateBox, Priority.ALWAYS);

        bar.getChildren().addAll(pill1, pill2, pill3, pill4, rateBox);
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
        searchField.setPromptText("Search name, email, or ID...");
        searchField.getStyleClass().add("text-input");
        searchField.setPrefWidth(280);
        searchField.setMinWidth(180);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Status Filter Buttons
        HBox filterPillsBox = new HBox(6);
        filterPillsBox.setAlignment(Pos.CENTER_LEFT);

        filterApprovedOnlyBtn = createFilterPill("Approved Only", "APPROVED_ONLY", true);
        filterAllBtn = createFilterPill("All Registrations", "ALL", false);
        filterPresentBtn = createFilterPill("Present", "PRESENT", false);
        filterAbsentBtn = createFilterPill("Absent", "ABSENT", false);
        filterUnmarkedBtn = createFilterPill("Unmarked", "UNMARKED", false);

        filterPillsBox.getChildren().addAll(
                filterApprovedOnlyBtn, filterAllBtn, filterPresentBtn, filterAbsentBtn, filterUnmarkedBtn
        );

        Button resetBtn = new Button("✕ Reset");
        resetBtn.getStyleClass().add("secondary-btn");
        resetBtn.setMinWidth(Region.USE_PREF_SIZE);
        resetBtn.setOnAction(e -> resetFilters());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        resultsCountLabel = new Label("Select an event above.");
        resultsCountLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 13px; -fx-font-weight: 600;");
        resultsCountLabel.setMinWidth(Region.USE_PREF_SIZE);

        bar.getChildren().addAll(searchField, filterPillsBox, resetBtn, spacer, resultsCountLabel);
        return bar;
    }

    private Button createFilterPill(String text, String filterValue, boolean active) {
        Button btn = new Button(text);
        if (active) {
            btn.getStyleClass().add("filter-pill-active");
        } else {
            btn.getStyleClass().add("filter-pill");
        }

        btn.setOnAction(e -> {
            activeFilter = filterValue;
            updateFilterPillsStyling();
            applyFilters();
        });

        return btn;
    }

    private void updateFilterPillsStyling() {
        setBtnActive(filterApprovedOnlyBtn, "APPROVED_ONLY".equals(activeFilter));
        setBtnActive(filterAllBtn, "ALL".equals(activeFilter));
        setBtnActive(filterPresentBtn, "PRESENT".equals(activeFilter));
        setBtnActive(filterAbsentBtn, "ABSENT".equals(activeFilter));
        setBtnActive(filterUnmarkedBtn, "UNMARKED".equals(activeFilter));
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
     * Loads initial list of events asynchronously.
     */
    public void loadInitialData() {
        if (!attendanceService.isCurrentUserAdmin()) {
            renderAccessDeniedState();
            return;
        }

        resultsCountLabel.setText("Connecting to database...");
        new Thread(() -> {
            List<Event> events = attendanceService.getAllEvents();

            Platform.runLater(() -> {
                cachedEvents.clear();
                cachedEvents.addAll(events);

                eventCombo.getItems().clear();
                for (Event ev : events) {
                    eventCombo.getItems().add(new EventSelectItem(ev.getEventId(), ev.getEventName(), ev));
                }

                if (!eventCombo.getItems().isEmpty()) {
                    eventCombo.getSelectionModel().selectFirst();
                    EventSelectItem first = eventCombo.getItems().get(0);
                    selectedEventId = first.getEventId();
                    updateEventDetailsHeader(first.getEvent());
                    loadEventRoster(selectedEventId);
                } else {
                    renderNoEventsState();
                }
            });
        }).start();
    }

    /**
     * Reloads attendance roster for the currently selected event.
     */
    private void reloadCurrentEventRoster() {
        if (selectedEventId > 0) {
            loadEventRoster(selectedEventId);
        } else {
            loadInitialData();
        }
    }

    /**
     * Loads participants and attendance data for a specific event asynchronously.
     */
    private void loadEventRoster(int eventId) {
        if (!attendanceService.isCurrentUserAdmin()) {
            renderAccessDeniedState();
            return;
        }

        resultsCountLabel.setText("Loading attendance roster...");
        new Thread(() -> {
            AttendanceSummaryDTO summary = attendanceService.getAttendanceSummary(eventId);
            List<EventAttendanceDTO> roster = attendanceService.getEventAttendanceRoster(eventId);

            Platform.runLater(() -> {
                // Update KPI Cards
                if (summary.isDbOnline()) {
                    approvedCounterLabel.setText(String.valueOf(summary.getTotalApproved()));
                    presentCounterLabel.setText(String.valueOf(summary.getMarkedPresent()));
                    absentCounterLabel.setText(String.valueOf(summary.getMarkedAbsent()));
                    unmarkedCounterLabel.setText(String.valueOf(summary.getUnmarked()));
                    ratePercentLabel.setText(summary.getAttendanceRatePercent() + "% Rate");
                    rateProgressBar.setProgress((double) summary.getAttendanceRatePercent() / 100);
                } else {
                    approvedCounterLabel.setText("--");
                    presentCounterLabel.setText("--");
                    absentCounterLabel.setText("--");
                    unmarkedCounterLabel.setText("--");
                    ratePercentLabel.setText("--");
                    rateProgressBar.setProgress(0.0);
                }

                cachedRoster.clear();
                cachedRoster.addAll(roster);

                applyFilters();
            });
        }).start();
    }

    /**
     * Filters cached event participants based on active toolbar settings.
     */
    private void applyFilters() {
        if (!attendanceService.isCurrentUserAdmin()) {
            renderAccessDeniedState();
            return;
        }

        String keyword = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";

        List<EventAttendanceDTO> filtered = new ArrayList<>();

        for (EventAttendanceDTO p : cachedRoster) {
            // Status/Roster filter
            if ("APPROVED_ONLY".equals(activeFilter)) {
                if (!p.isApproved()) {
                    continue;
                }
            } else if ("PRESENT".equals(activeFilter)) {
                if (!p.isPresent()) {
                    continue;
                }
            } else if ("ABSENT".equals(activeFilter)) {
                if (!p.isAbsent()) {
                    continue;
                }
            } else if ("UNMARKED".equals(activeFilter)) {
                if (!p.isUnmarked() || !p.isApproved()) {
                    continue;
                }
            }
            // "ALL" shows everything

            // Keyword filter
            if (!keyword.isEmpty()) {
                boolean matches = p.getStudentName().toLowerCase().contains(keyword)
                        || p.getStudentEmail().toLowerCase().contains(keyword)
                        || p.getFormattedRegistrationId().toLowerCase().contains(keyword);
                if (!matches) {
                    continue;
                }
            }

            filtered.add(p);
        }

        renderRosterList(filtered);
    }

    private void resetFilters() {
        searchField.clear();
        activeFilter = "APPROVED_ONLY";
        updateFilterPillsStyling();
        applyFilters();
    }

    /**
     * Renders attendance cards into the UI container.
     */
    private void renderRosterList(List<EventAttendanceDTO> participants) {
        rosterListContainer.getChildren().clear();

        if (!DBConnection.testConnection() && cachedRoster.isEmpty()) {
            renderOfflineState();
            return;
        }

        if (participants.isEmpty()) {
            renderEmptyState();
            return;
        }

        resultsCountLabel.setText("Showing " + participants.size() + " participant" + (participants.size() == 1 ? "" : "s"));

        for (EventAttendanceDTO p : participants) {
            rosterListContainer.getChildren().add(createParticipantCard(p));
        }
    }

    /**
     * Creates a single rich attendance participant card.
     */
    private VBox createParticipantCard(EventAttendanceDTO p) {
        VBox card = new VBox(10);
        card.getStyleClass().add("dashboard-card");
        card.setPadding(new Insets(16, 20, 16, 20));

        // Top line: Student Name, Email, Reg ID, Reg Status, Attendance Badge
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(p.getStudentName());
        nameLabel.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 15px; -fx-font-weight: 800;");

        Label emailLabel = new Label("• " + p.getStudentEmail());
        emailLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 13px;");

        Label idBadge = new Label(p.getFormattedRegistrationId());
        idBadge.getStyleClass().add("badge-neutral");

        Label regStatusBadge = new Label(p.getRegistrationStatus());
        if (p.isApproved()) {
            regStatusBadge.getStyleClass().setAll("badge-success");
        } else if (p.isPending()) {
            regStatusBadge.getStyleClass().setAll("badge-warning");
        } else {
            regStatusBadge.getStyleClass().setAll("badge-danger");
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Attendance Status Badge
        Label attBadge = new Label();
        if (p.isPresent()) {
            attBadge.setText("✓ Present");
            attBadge.getStyleClass().setAll("badge-success");
        } else if (p.isAbsent()) {
            attBadge.setText("✕ Absent");
            attBadge.getStyleClass().setAll("badge-danger");
        } else {
            attBadge.setText("⏳ Not Marked");
            attBadge.getStyleClass().setAll("badge-neutral");
        }

        topRow.getChildren().addAll(nameLabel, emailLabel, idBadge, regStatusBadge, spacer, attBadge);

        // Bottom line: Action buttons or Ineligibility Notice
        HBox bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);

        if (p.isApproved()) {
            // Present Button
            Button markPresentBtn = new Button("✓ Present");
            markPresentBtn.setMinWidth(Region.USE_PREF_SIZE);
            if (p.isPresent()) {
                markPresentBtn.getStyleClass().add("action-approve-btn-solid");
                markPresentBtn.setDisable(true); // Already present
            } else {
                markPresentBtn.getStyleClass().add("action-approve-btn");
                markPresentBtn.setOnAction(e -> executeMarkAttendance(p.getEventId(), p.getStudentId(), true));
            }

            // Absent Button
            Button markAbsentBtn = new Button("✕ Absent");
            markAbsentBtn.setMinWidth(Region.USE_PREF_SIZE);
            if (p.isAbsent()) {
                markAbsentBtn.getStyleClass().add("danger-btn-solid");
                markAbsentBtn.setDisable(true); // Already absent
            } else {
                markAbsentBtn.getStyleClass().add("danger-btn");
                markAbsentBtn.setOnAction(e -> executeMarkAttendance(p.getEventId(), p.getStudentId(), false));
            }

            bottomRow.getChildren().addAll(markPresentBtn, markAbsentBtn);

            // If attendance was recorded, allow unmarking/correcting
            if (p.isAttendanceRecorded()) {
                Button unmarkBtn = new Button("↺ Reset / Unmark");
                unmarkBtn.getStyleClass().add("secondary-btn");
                unmarkBtn.setMinWidth(Region.USE_PREF_SIZE);
                unmarkBtn.setOnAction(e -> openUnmarkConfirmDialog(p));
                bottomRow.getChildren().add(unmarkBtn);
            }

        } else {
            Label ineligLabel = new Label("⚠️ Attendance can only be marked for participants with APPROVED registrations.");
            ineligLabel.setStyle("-fx-text-fill: #F59E0B; -fx-font-size: 12px; -fx-font-style: italic;");
            Region ineligSpacer = new Region();
            HBox.setHgrow(ineligSpacer, Priority.ALWAYS);
            bottomRow.getChildren().addAll(ineligSpacer, ineligLabel);
        }

        card.getChildren().addAll(topRow, bottomRow);
        return card;
    }

    /**
     * Executes the mark attendance action asynchronously.
     */
    private void executeMarkAttendance(int eventId, int studentId, boolean present) {
        new Thread(() -> {
            OperationResult result = attendanceService.markAttendance(eventId, studentId, present);
            Platform.runLater(() -> {
                if (result.isSuccess()) {
                    showToast(result.getMessage(), true);
                    reloadCurrentEventRoster();
                } else {
                    showToast(result.getMessage(), false);
                }
            });
        }).start();
    }

    /**
     * Opens modal dialog before unmarking an attendee to prevent accidental resets.
     */
    private void openUnmarkConfirmDialog(EventAttendanceDTO p) {
        modalOverlay.getChildren().clear();

        VBox dialogCard = new VBox(16);
        dialogCard.getStyleClass().add("modal-dialog-large");

        // Header
        HBox modalHeader = new HBox();
        modalHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(3);
        Label modalTitle = new Label("Reset Attendance Mark");
        modalTitle.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 18px; -fx-font-weight: 800;");

        Label modalSubtitle = new Label("Correct an accidental mark by clearing participant attendance.");
        modalSubtitle.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px;");
        titleBox.getChildren().addAll(modalTitle, modalSubtitle);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("secondary-btn");
        closeBtn.setOnAction(e -> closeModal());

        modalHeader.getChildren().addAll(titleBox, headerSpacer, closeBtn);

        // Details
        VBox detailsBox = new VBox(8);
        detailsBox.setStyle("-fx-background-color: #0B0A1A; -fx-padding: 14px; -fx-background-radius: 8px; -fx-border-color: rgba(255,255,255,0.06); -fx-border-radius: 8px;");

        Label sName = new Label("Student: " + p.getStudentName() + " (" + p.getStudentEmail() + ")");
        sName.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 13.5px; -fx-font-weight: bold;");

        Label currentMark = new Label("Current Status: " + (p.isPresent() ? "Marked Present" : "Marked Absent"));
        currentMark.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 13px;");

        Label note = new Label("Clearing will reset the participant back to 'Not Marked' (unrecorded).");
        note.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");

        detailsBox.getChildren().addAll(sName, currentMark, note);

        // Buttons
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button confirmResetBtn = new Button("Confirm Reset");
        confirmResetBtn.getStyleClass().add("danger-btn-solid");
        confirmResetBtn.setOnAction(e -> {
            closeModal();
            executeUnmarkAttendance(p.getEventId(), p.getStudentId());
        });

        btnBox.getChildren().addAll(cancelBtn, confirmResetBtn);

        dialogCard.getChildren().addAll(modalHeader, detailsBox, btnBox);
        modalOverlay.getChildren().add(dialogCard);
        modalOverlay.setVisible(true);
    }

    private void executeUnmarkAttendance(int eventId, int studentId) {
        new Thread(() -> {
            OperationResult result = attendanceService.unmarkAttendance(eventId, studentId);
            Platform.runLater(() -> {
                if (result.isSuccess()) {
                    showToast(result.getMessage(), true);
                    reloadCurrentEventRoster();
                } else {
                    showToast(result.getMessage(), false);
                }
            });
        }).start();
    }

    /**
     * Opens modal confirmation for batch marking all approved attendees as present.
     */
    private void openBatchConfirmDialog() {
        if (selectedEventId <= 0) return;

        modalOverlay.getChildren().clear();

        VBox dialogCard = new VBox(16);
        dialogCard.getStyleClass().add("modal-dialog-large");

        // Header
        HBox modalHeader = new HBox();
        modalHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(3);
        Label modalTitle = new Label("Mark All Approved as Present");
        modalTitle.setStyle("-fx-text-fill: #22C55E; -fx-font-size: 18px; -fx-font-weight: 800;");

        Label modalSubtitle = new Label("Batch attendance confirmation for confirmed event participants.");
        modalSubtitle.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px;");
        titleBox.getChildren().addAll(modalTitle, modalSubtitle);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("secondary-btn");
        closeBtn.setOnAction(e -> closeModal());

        modalHeader.getChildren().addAll(titleBox, headerSpacer, closeBtn);

        Label desc = new Label("This will record PRESENT attendance for all approved participants for this event.\nAny previously unmarked or absent participants will be set to Present.");
        desc.setStyle("-fx-text-fill: #D1D5DB; -fx-font-size: 13px;");
        desc.setWrapText(true);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button confirmBatchBtn = new Button("✓ Confirm Batch Attendance");
        confirmBatchBtn.getStyleClass().add("action-approve-btn-solid");
        confirmBatchBtn.setOnAction(e -> {
            closeModal();
            executeBatchMarkAllPresent(selectedEventId);
        });

        btnBox.getChildren().addAll(cancelBtn, confirmBatchBtn);

        dialogCard.getChildren().addAll(modalHeader, desc, btnBox);
        modalOverlay.getChildren().add(dialogCard);
        modalOverlay.setVisible(true);
    }

    private void executeBatchMarkAllPresent(int eventId) {
        new Thread(() -> {
            OperationResult result = attendanceService.markAllApprovedPresent(eventId);
            Platform.runLater(() -> {
                if (result.isSuccess()) {
                    showToast(result.getMessage(), true);
                    reloadCurrentEventRoster();
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
        resultsCountLabel.setText("0 participants found");
        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label("No participants match your criteria");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Try switching the filter to 'All Registrations' or adjusting your search.");
        sub.getStyleClass().add("empty-state-sub");

        Button resetBtn = new Button("Reset All Filters");
        resetBtn.getStyleClass().add("secondary-btn");
        resetBtn.setOnAction(e -> resetFilters());

        box.getChildren().addAll(title, sub, resetBtn);
        rosterListContainer.getChildren().add(box);
    }

    private void renderNoEventsState() {
        resultsCountLabel.setText("No events found");
        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label("No campus events found");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Create an event first in Event Management to track attendance.");
        sub.getStyleClass().add("empty-state-sub");

        box.getChildren().addAll(title, sub);
        rosterListContainer.getChildren().add(box);
    }

    private void renderOfflineState() {
        resultsCountLabel.setText("Database Offline");
        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label("Database is offline");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Unable to load attendance records. Please verify MySQL server connection.");
        sub.getStyleClass().add("empty-state-sub");

        Button retryBtn = new Button("↻ Retry Connection");
        retryBtn.getStyleClass().add("primary-btn");
        retryBtn.setOnAction(e -> loadInitialData());

        box.getChildren().addAll(title, sub, retryBtn);
        rosterListContainer.getChildren().add(box);
    }

    private void renderAccessDeniedState() {
        rosterListContainer.getChildren().clear();
        resultsCountLabel.setText("Access Denied");

        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label("Administrator Access Required");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Attendance tracking and verification is restricted to system administrators.");
        sub.getStyleClass().add("empty-state-sub");

        box.getChildren().addAll(title, sub);
        rosterListContainer.getChildren().add(box);
    }

    /**
     * Item helper for the Event ComboBox selector.
     */
    private static class EventSelectItem {
        private final int eventId;
        private final String eventName;
        private final Event event;

        public EventSelectItem(int eventId, String eventName, Event event) {
            this.eventId = eventId;
            this.eventName = eventName;
            this.event = event;
        }

        public int getEventId() {
            return eventId;
        }

        public String getEventName() {
            return eventName;
        }

        public Event getEvent() {
            return event;
        }

        public String getDisplayText() {
            String club = (event != null && event.getClubName() != null) ? " • " + event.getClubName() : "";
            return eventName + club;
        }

        @Override
        public String toString() {
            return getDisplayText();
        }
    }
}
