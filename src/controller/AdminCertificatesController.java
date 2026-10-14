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
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import model.AdminCertificateParticipantDTO;
import model.Event;
import model.StudentCertificateDTO;
import service.CertificateManagementService;
import service.CertificateManagementService.BatchIssuanceResult;
import service.CertificateManagementService.EventCertificateSummaryDTO;
import service.CertificateManagementService.OperationResult;
import util.DBConnection;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminCertificatesController — Administrator Certificate Management View.
 *
 * Implements Phase 12 per AGENTS.md:
 * - Administrator-only event selection and certificate issuance
 * - View eligible participants and issuance status
 * - Strict eligibility checking (Approved registration + Present attendance)
 * - Safe individual and bulk issuance with explicit confirmation dialogs
 * - Duplicate prevention safe against repeated requests
 * - Interactive search and filtering
 * - In-app certificate preview for verified issued certificates
 * - Asynchronous background loading with dark SaaS dashboard aesthetics
 */
public class AdminCertificatesController {

    private final CertificateManagementService certificateService;

    private StackPane rootContainer;
    private ScrollPane mainScrollPane;
    private StackPane modalOverlay;

    // Event Selector UI
    private ComboBox<EventSelectItem> eventCombo;
    private Label eventDetailsLabel;
    private Button issueAllEligibleBtn;

    // KPI Counters
    private Label totalParticipantsLabel;
    private Label approvedPresentLabel;
    private Label issuedCountLabel;
    private Label eligibleUnissuedLabel;
    private Label ratePercentLabel;
    private ProgressBar rateProgressBar;

    // Filters and Toolbar UI
    private TextField searchField;
    private Button filterAllBtn;
    private Button filterEligibleBtn;
    private Button filterIssuedBtn;
    private Button filterIneligibleBtn;
    private Label resultsCountLabel;
    private VBox rosterListContainer;

    // Banner notifications
    private HBox feedbackBanner;
    private Label feedbackText;
    private Button dismissFeedbackBtn;

    // Active State
    private String activeFilter = "ALL"; // "ALL", "ELIGIBLE", "ISSUED", "INELIGIBLE"
    private final List<Event> cachedEvents = new ArrayList<>();
    private final List<AdminCertificateParticipantDTO> cachedRoster = new ArrayList<>();
    private int selectedEventId = -1;

    // Formatting helpers
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");
    private final DateTimeFormatter fullDateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    // Navigation callbacks
    private Runnable onNavigateDashboard;

    public AdminCertificatesController() {
        this.certificateService = new CertificateManagementService();
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

        mainScrollPane = new ScrollPane();
        mainScrollPane.getStyleClass().add("main-scroll-pane");
        mainScrollPane.setFitToWidth(true);

        VBox contentBox = new VBox(20);
        contentBox.getStyleClass().add("main-content");

        // Top Header
        HBox headerBox = createHeader();

        // Feedback Banner
        feedbackBanner = createFeedbackBanner();

        // Event Selector Card
        HBox eventSelectorBar = createEventSelectorBar();

        // KPI Summary Bar
        HBox kpiStrip = createKpiSummaryBar();

        // Search & Filter Toolbar
        HBox filterBar = createFilterToolbar();

        // Participants Roster List Container
        rosterListContainer = new VBox(12);

        contentBox.getChildren().addAll(headerBox, feedbackBanner, eventSelectorBar, kpiStrip, filterBar, rosterListContainer);
        mainScrollPane.setContent(contentBox);

        // Modal Overlay Container
        modalOverlay = new StackPane();
        modalOverlay.getStyleClass().add("dialog-overlay");
        modalOverlay.setVisible(false);
        modalOverlay.setPickOnBounds(true);

        rootContainer.getChildren().addAll(mainScrollPane, modalOverlay);
    }

    private HBox createHeader() {
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label titleLabel = new Label("Certificate Management");
        titleLabel.getStyleClass().add("header-title");

        Label subtitleLabel = new Label("Issue and verify academic participation certificates for eligible attendees.");
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
        return headerBox;
    }

    private HBox createFeedbackBanner() {
        HBox banner = new HBox(12);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setVisible(false);
        banner.setManaged(false);

        feedbackText = new Label();

        Region bannerSpacer = new Region();
        HBox.setHgrow(bannerSpacer, Priority.ALWAYS);

        dismissFeedbackBtn = new Button("✕");
        dismissFeedbackBtn.getStyleClass().add("secondary-btn");
        dismissFeedbackBtn.setStyle("-fx-padding: 2px 8px; -fx-font-size: 11px;");
        dismissFeedbackBtn.setOnAction(e -> hideFeedback());

        banner.getChildren().addAll(feedbackText, bannerSpacer, dismissFeedbackBtn);
        return banner;
    }

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

        eventDetailsLabel = new Label("Choose an event to manage certificates.");
        eventDetailsLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");
        eventDetailsLabel.setMinWidth(0);
        HBox.setHgrow(eventDetailsLabel, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        issueAllEligibleBtn = new Button("📜 Issue All Eligible");
        issueAllEligibleBtn.getStyleClass().add("gold-btn-solid");
        issueAllEligibleBtn.setMinWidth(Region.USE_PREF_SIZE);
        issueAllEligibleBtn.setDisable(true);
        issueAllEligibleBtn.setOnAction(e -> openBulkConfirmDialog());

        bar.getChildren().addAll(label, eventCombo, eventDetailsLabel, spacer, issueAllEligibleBtn);
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

        eventDetailsLabel.setText(club + "  •  " + sched + "  •  " + venue);
    }

    private HBox createKpiSummaryBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        HBox totalCard = createStatPill("Total In Roster", totalParticipantsLabel = new Label("0"), "#9695A8");
        HBox presentCard = createStatPill("Approved Present", approvedPresentLabel = new Label("0"), "#3B82F6");
        HBox issuedCard = createStatPill("Issued Certificates", issuedCountLabel = new Label("0"), "#F59E0B");
        HBox eligibleCard = createStatPill("Eligible Unissued", eligibleUnissuedLabel = new Label("0"), "#10B981");

        VBox rateBox = new VBox(4);
        rateBox.getStyleClass().add("stat-counter-pill");
        rateBox.setPrefWidth(210);

        HBox rateHeader = new HBox(6);
        rateHeader.setAlignment(Pos.CENTER_LEFT);
        Label rateTitle = new Label("Issuance Rate");
        rateTitle.getStyleClass().add("stat-counter-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ratePercentLabel = new Label("0%");
        ratePercentLabel.setStyle("-fx-text-fill: #F59E0B; -fx-font-size: 13px; -fx-font-weight: 800;");
        rateHeader.getChildren().addAll(rateTitle, spacer, ratePercentLabel);

        rateProgressBar = new ProgressBar(0.0);
        rateProgressBar.setMaxWidth(Double.MAX_VALUE);
        rateProgressBar.getStyleClass().add("progress-bar");
        rateProgressBar.setStyle("-fx-accent: #F59E0B; -fx-pref-height: 8px;");

        rateBox.getChildren().addAll(rateHeader, rateProgressBar);

        HBox.setHgrow(totalCard, Priority.ALWAYS);
        HBox.setHgrow(presentCard, Priority.ALWAYS);
        HBox.setHgrow(issuedCard, Priority.ALWAYS);
        HBox.setHgrow(eligibleCard, Priority.ALWAYS);
        HBox.setHgrow(rateBox, Priority.ALWAYS);

        bar.getChildren().addAll(totalCard, presentCard, issuedCard, eligibleCard, rateBox);
        return bar;
    }

    private HBox createStatPill(String label, Label valueLabel, String hexColor) {
        HBox pill = new HBox(10);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.getStyleClass().add("stat-counter-pill");
        pill.setPrefWidth(170);

        VBox textBox = new VBox(2);
        valueLabel.getStyleClass().add("stat-counter-number");
        valueLabel.setStyle("-fx-text-fill: " + hexColor + ";");

        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("stat-counter-label");

        textBox.getChildren().addAll(valueLabel, nameLabel);
        pill.getChildren().add(textBox);
        return pill;
    }

    private HBox createFilterToolbar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.getStyleClass().add("dashboard-card");

        searchField = new TextField();
        searchField.setPromptText("Search student, email, or ID...");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(280);
        searchField.setMinWidth(180);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyActiveFilterAndSearch());

        HBox filterButtonGroup = new HBox(6);
        filterButtonGroup.setAlignment(Pos.CENTER_LEFT);

        filterAllBtn = createFilterButton("All Participants", "ALL");
        filterEligibleBtn = createFilterButton("Eligible & Unissued", "ELIGIBLE");
        filterIssuedBtn = createFilterButton("Issued", "ISSUED");
        filterIneligibleBtn = createFilterButton("Ineligible", "INELIGIBLE");

        filterButtonGroup.getChildren().addAll(filterAllBtn, filterEligibleBtn, filterIssuedBtn, filterIneligibleBtn);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        resultsCountLabel = new Label("0 participants listed");
        resultsCountLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");
        resultsCountLabel.setMinWidth(Region.USE_PREF_SIZE);

        bar.getChildren().addAll(searchField, filterButtonGroup, spacer, resultsCountLabel);
        return bar;
    }

    private Button createFilterButton(String text, String filterKey) {
        Button btn = new Button(text);
        if ("ALL".equals(filterKey)) {
            btn.getStyleClass().add("filter-chip-active");
        } else {
            btn.getStyleClass().add("filter-chip");
        }
        btn.setOnAction(e -> {
            activeFilter = filterKey;
            updateFilterButtonStyles();
            applyActiveFilterAndSearch();
        });
        return btn;
    }

    private void updateFilterButtonStyles() {
        setFilterBtnStyle(filterAllBtn, "ALL".equals(activeFilter));
        setFilterBtnStyle(filterEligibleBtn, "ELIGIBLE".equals(activeFilter));
        setFilterBtnStyle(filterIssuedBtn, "ISSUED".equals(activeFilter));
        setFilterBtnStyle(filterIneligibleBtn, "INELIGIBLE".equals(activeFilter));
    }

    private void setFilterBtnStyle(Button btn, boolean isActive) {
        btn.getStyleClass().removeAll("filter-chip", "filter-chip-active");
        btn.getStyleClass().add(isActive ? "filter-chip-active" : "filter-chip");
    }

    /**
     * Loads events asynchronously to populate the event dropdown.
     */
    public void loadInitialData() {
        showLoadingState("Loading campus events...");
        Thread loadThread = new Thread(() -> {
            List<Event> events = certificateService.getAllEvents();
            Platform.runLater(() -> {
                cachedEvents.clear();
                cachedEvents.addAll(events);
                populateEventSelector(events);
            });
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void populateEventSelector(List<Event> events) {
        eventCombo.getItems().clear();
        if (events == null || events.isEmpty()) {
            showEmptyState("No Events Found", "No campus events are currently registered in the database.");
            return;
        }

        for (Event ev : events) {
            eventCombo.getItems().add(new EventSelectItem(ev));
        }

        if (!eventCombo.getItems().isEmpty()) {
            eventCombo.setValue(eventCombo.getItems().get(0));
            selectedEventId = eventCombo.getValue().getEventId();
            updateEventDetailsHeader(eventCombo.getValue().getEvent());
            loadEventRoster(selectedEventId);
        }
    }

    private void reloadCurrentEventRoster() {
        if (selectedEventId > 0) {
            loadEventRoster(selectedEventId);
        } else {
            loadInitialData();
        }
    }

    private void loadEventRoster(int eventId) {
        showLoadingState("Loading participant certificate records...");
        Thread loadThread = new Thread(() -> {
            boolean dbOnline = DBConnection.testConnection();
            List<AdminCertificateParticipantDTO> roster = certificateService.getParticipantRoster(eventId);
            EventCertificateSummaryDTO summary = certificateService.getEventCertificateSummary(eventId);

            Platform.runLater(() -> {
                cachedRoster.clear();
                cachedRoster.addAll(roster);

                if (!dbOnline && roster.isEmpty()) {
                    showOfflineState();
                    updateKpis(summary);
                    issueAllEligibleBtn.setDisable(true);
                } else {
                    updateKpis(summary);
                    applyActiveFilterAndSearch();
                    issueAllEligibleBtn.setDisable(summary.getEligibleUnissuedCount() <= 0);
                }
            });
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void updateKpis(EventCertificateSummaryDTO summary) {
        totalParticipantsLabel.setText(String.valueOf(summary.getTotalParticipants()));
        approvedPresentLabel.setText(String.valueOf(summary.getApprovedPresentCount()));
        issuedCountLabel.setText(String.valueOf(summary.getIssuedCount()));
        eligibleUnissuedLabel.setText(String.valueOf(summary.getEligibleUnissuedCount()));

        double rate = summary.getIssuanceRatePercent();
        ratePercentLabel.setText(String.format("%.0f%%", rate));
        rateProgressBar.setProgress(Math.min(1.0, Math.max(0.0, rate / 100.0)));
    }

    private void applyActiveFilterAndSearch() {
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";

        List<AdminCertificateParticipantDTO> filtered = cachedRoster.stream()
                .filter(p -> {
                    // Filter match
                    return switch (activeFilter) {
                        case "ELIGIBLE" -> p.isEligible();
                        case "ISSUED" -> p.isCertificateIssued();
                        case "INELIGIBLE" -> !p.isEligible() && !p.isCertificateIssued();
                        default -> true;
                    };
                })
                .filter(p -> {
                    // Search match
                    if (query.isEmpty()) return true;
                    boolean matchesName = p.getStudentName() != null && p.getStudentName().toLowerCase().contains(query);
                    boolean matchesEmail = p.getStudentEmail() != null && p.getStudentEmail().toLowerCase().contains(query);
                    boolean matchesId = String.valueOf(p.getStudentId()).contains(query)
                            || String.valueOf(p.getRegistrationId()).contains(query);
                    return matchesName || matchesEmail || matchesId;
                })
                .collect(Collectors.toList());

        resultsCountLabel.setText(filtered.size() + " of " + cachedRoster.size() + " participants listed");
        renderRosterList(filtered);
    }

    private void renderRosterList(List<AdminCertificateParticipantDTO> roster) {
        rosterListContainer.getChildren().clear();

        if (roster.isEmpty()) {
            if (cachedRoster.isEmpty()) {
                showEmptyState("No Registrations Found", "No students have registered for this event yet.");
            } else {
                showEmptyState("No Matching Participants", "No participants match the active filter or search query.");
            }
            return;
        }

        for (AdminCertificateParticipantDTO p : roster) {
            rosterListContainer.getChildren().add(createParticipantRow(p));
        }
    }

    private HBox createParticipantRow(AdminCertificateParticipantDTO p) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.getStyleClass().add("dashboard-card");

        // Student Info
        VBox studentInfo = new VBox(2);
        studentInfo.setPrefWidth(220);

        Label nameLabel = new Label(p.getStudentName());
        nameLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 14px; -fx-font-weight: 700;");

        Label emailLabel = new Label(p.getStudentEmail() != null ? p.getStudentEmail() : "No email");
        emailLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px;");

        studentInfo.getChildren().addAll(nameLabel, emailLabel);

        // Registration Status Badge
        VBox regBox = new VBox(2);
        regBox.setPrefWidth(120);
        Label regTitle = new Label("Registration");
        regTitle.setStyle("-fx-text-fill: #64748B; -fx-font-size: 10px; -fx-font-weight: 700;");

        Label regBadge = new Label(p.getRegistrationStatus());
        if ("APPROVED".equalsIgnoreCase(p.getRegistrationStatus())) {
            regBadge.setStyle("-fx-background-color: rgba(34, 197, 94, 0.15); -fx-text-fill: #22C55E; -fx-padding: 2px 8px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: 700;");
        } else if ("REJECTED".equalsIgnoreCase(p.getRegistrationStatus())) {
            regBadge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.15); -fx-text-fill: #EF4444; -fx-padding: 2px 8px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: 700;");
        } else {
            regBadge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #F59E0B; -fx-padding: 2px 8px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: 700;");
        }
        regBox.getChildren().addAll(regTitle, regBadge);

        // Attendance Status Badge
        VBox attBox = new VBox(2);
        attBox.setPrefWidth(120);
        Label attTitle = new Label("Attendance");
        attTitle.setStyle("-fx-text-fill: #64748B; -fx-font-size: 10px; -fx-font-weight: 700;");

        Label attBadge = new Label();
        if (!p.isAttendanceRecorded()) {
            attBadge.setText("UNMARKED");
            attBadge.setStyle("-fx-background-color: rgba(100, 116, 139, 0.15); -fx-text-fill: #94A3B8; -fx-padding: 2px 8px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: 700;");
        } else if (p.isPresent()) {
            attBadge.setText("PRESENT");
            attBadge.setStyle("-fx-background-color: rgba(59, 130, 246, 0.15); -fx-text-fill: #3B82F6; -fx-padding: 2px 8px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: 700;");
        } else {
            attBadge.setText("ABSENT");
            attBadge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.15); -fx-text-fill: #EF4444; -fx-padding: 2px 8px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: 700;");
        }
        attBox.getChildren().addAll(attTitle, attBadge);

        // Certificate Status Badge
        VBox certBox = new VBox(2);
        certBox.setPrefWidth(140);
        Label certTitle = new Label("Certificate");
        certTitle.setStyle("-fx-text-fill: #64748B; -fx-font-size: 10px; -fx-font-weight: 700;");

        Label certBadge = new Label();
        if (p.isCertificateIssued()) {
            certBadge.setText("ISSUED (" + (p.getGeneratedDate() != null ? p.getGeneratedDate().format(dateFormatter) : "N/A") + ")");
            certBadge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #F59E0B; -fx-padding: 2px 8px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: 700;");
        } else if (p.isEligible()) {
            certBadge.setText("ELIGIBLE");
            certBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.15); -fx-text-fill: #10B981; -fx-padding: 2px 8px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: 700;");
        } else {
            certBadge.setText("INELIGIBLE");
            certBadge.setStyle("-fx-background-color: rgba(100, 116, 139, 0.15); -fx-text-fill: #64748B; -fx-padding: 2px 8px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: 600;");
        }
        certBox.getChildren().addAll(certTitle, certBadge);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Action Button
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        if (p.isCertificateIssued()) {
            Button previewBtn = new Button("📜 View");
            previewBtn.getStyleClass().add("secondary-btn");
            previewBtn.setMinWidth(Region.USE_PREF_SIZE);
            previewBtn.setOnAction(e -> openAdminCertificatePreview(p.getCertificateId()));
            actions.getChildren().add(previewBtn);
        } else if (p.isEligible()) {
            Button issueBtn = new Button("Issue Certificate");
            issueBtn.getStyleClass().add("gold-btn-solid");
            issueBtn.setMinWidth(Region.USE_PREF_SIZE);
            issueBtn.setOnAction(e -> openSingleIssueConfirmDialog(p));
            actions.getChildren().add(issueBtn);
        } else {
            Label reasonLabel = new Label(getIneligibilityReason(p));
            reasonLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px; -fx-font-style: italic;");
            actions.getChildren().add(reasonLabel);
        }

        row.getChildren().addAll(studentInfo, regBox, attBox, certBox, spacer, actions);
        return row;
    }

    private String getIneligibilityReason(AdminCertificateParticipantDTO p) {
        if (!"APPROVED".equalsIgnoreCase(p.getRegistrationStatus())) {
            return "Registration " + p.getRegistrationStatus().toLowerCase();
        }
        if (!p.isAttendanceRecorded()) {
            return "Attendance unmarked";
        }
        if (!p.isPresent()) {
            return "Marked absent";
        }
        return "Not eligible";
    }

    private void openSingleIssueConfirmDialog(AdminCertificateParticipantDTO p) {
        VBox dialogBox = new VBox(16);
        dialogBox.getStyleClass().add("dialog-box");
        dialogBox.setStyle("-fx-pref-width: 440px;");

        Label title = new Label("Issue Certificate");
        title.getStyleClass().add("dialog-title");

        Label desc = new Label("Are you sure you want to issue an official Certificate of Participation to "
                + p.getStudentName() + "?\n\nThis will generate a permanent verified certificate record.");
        desc.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 13px;");
        desc.setWrapText(true);

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button confirmBtn = new Button("Confirm & Issue");
        confirmBtn.getStyleClass().add("gold-btn-solid");
        confirmBtn.setOnAction(e -> {
            closeModal();
            issueSingleCertificate(p);
        });

        btnRow.getChildren().addAll(cancelBtn, confirmBtn);
        dialogBox.getChildren().addAll(title, desc, btnRow);

        modalOverlay.getChildren().clear();
        modalOverlay.getChildren().add(dialogBox);
        modalOverlay.setVisible(true);
    }

    private void issueSingleCertificate(AdminCertificateParticipantDTO p) {
        showFeedback("Issuing certificate for " + p.getStudentName() + "...", false);
        Thread thread = new Thread(() -> {
            OperationResult result = certificateService.issueCertificate(selectedEventId, p.getStudentId());
            Platform.runLater(() -> {
                showFeedback(result.getMessage(), !result.isSuccess());
                loadEventRoster(selectedEventId);
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void openBulkConfirmDialog() {
        long eligibleCount = cachedRoster.stream().filter(AdminCertificateParticipantDTO::isEligible).count();
        if (eligibleCount <= 0) {
            showFeedback("No eligible participants to issue certificates to.", true);
            return;
        }

        VBox dialogBox = new VBox(16);
        dialogBox.getStyleClass().add("dialog-box");
        dialogBox.setStyle("-fx-pref-width: 460px;");

        Label title = new Label("Bulk Certificate Issuance");
        title.getStyleClass().add("dialog-title");

        Label desc = new Label("You are about to issue certificates to ALL " + eligibleCount
                + " eligible participants for this event.\n\n"
                + "Eligibility Criteria:\n"
                + "• Approved registration\n"
                + "• Verified attendance (Present)\n"
                + "• No existing duplicate certificate\n\n"
                + "Do you want to proceed?");
        desc.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 13px;");
        desc.setWrapText(true);

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button confirmBtn = new Button("Issue " + eligibleCount + " Certificates");
        confirmBtn.getStyleClass().add("gold-btn-solid");
        confirmBtn.setOnAction(e -> {
            closeModal();
            executeBulkIssuance();
        });

        btnRow.getChildren().addAll(cancelBtn, confirmBtn);
        dialogBox.getChildren().addAll(title, desc, btnRow);

        modalOverlay.getChildren().clear();
        modalOverlay.getChildren().add(dialogBox);
        modalOverlay.setVisible(true);
    }

    private void executeBulkIssuance() {
        showFeedback("Executing bulk certificate issuance...", false);
        Thread thread = new Thread(() -> {
            BatchIssuanceResult result = certificateService.issueAllEligibleCertificates(selectedEventId);
            Platform.runLater(() -> {
                showFeedback(result.getMessage(), !result.isSuccess());
                loadEventRoster(selectedEventId);
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void openAdminCertificatePreview(int certificateId) {
        showFeedback("Loading certificate preview...", false);
        Thread thread = new Thread(() -> {
            StudentCertificateDTO cert = certificateService.getCertificatePreview(certificateId);
            Platform.runLater(() -> {
                hideFeedback();
                if (cert != null) {
                    renderCertificatePreviewModal(cert);
                } else {
                    showFeedback("Unable to load certificate preview.", true);
                }
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void renderCertificatePreviewModal(StudentCertificateDTO cert) {
        VBox certBox = new VBox(18);
        certBox.getStyleClass().add("certificate-preview-dialog");
        certBox.setAlignment(Pos.CENTER);

        // Header
        VBox topBranding = new VBox(4);
        topBranding.setAlignment(Pos.CENTER);

        Label campusLabel = new Label("CAMPUS CONNECT • UNIVERSITY EVENT CREDENTIAL");
        campusLabel.getStyleClass().add("certificate-brand");

        Label certHeading = new Label("CERTIFICATE OF PARTICIPATION");
        certHeading.getStyleClass().add("certificate-title");

        Label certSub = new Label("This document certifies verified attendance and active participation");
        certSub.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px; -fx-font-style: italic;");

        topBranding.getChildren().addAll(campusLabel, certHeading, certSub);

        Separator sep1 = new Separator();
        sep1.setStyle("-fx-background-color: rgba(245, 158, 11, 0.3);");

        // Recipient
        VBox recipientBox = new VBox(6);
        recipientBox.setAlignment(Pos.CENTER);

        Label presentedTo = new Label("PROUDLY PRESENTED TO");
        presentedTo.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px; -fx-letter-spacing: 2px; -fx-font-weight: 700;");

        Label studentName = new Label(cert.getStudentName());
        studentName.getStyleClass().add("certificate-recipient");

        Label studentEmail = new Label(cert.getStudentEmail() != null ? cert.getStudentEmail() : "");
        studentEmail.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px;");

        recipientBox.getChildren().addAll(presentedTo, studentName, studentEmail);

        // Event Details
        VBox eventBox = new VBox(6);
        eventBox.setAlignment(Pos.CENTER);

        Label inRecognitionOf = new Label("for successfully attending and participating in the campus event");
        inRecognitionOf.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 13px;");

        Label eventName = new Label(cert.getEventName());
        eventName.setStyle("-fx-text-fill: #F59E0B; -fx-font-size: 18px; -fx-font-weight: 800;");

        String eventMeta = "Organized by " + (cert.getClubName() != null ? cert.getClubName() : "Campus Club")
                + "  •  " + (cert.getEventDate() != null ? cert.getEventDate().format(fullDateFormatter) : "Date TBD")
                + (cert.getVenue() != null ? " at " + cert.getVenue() : "");
        Label eventMetaLabel = new Label(eventMeta);
        eventMetaLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");

        eventBox.getChildren().addAll(inRecognitionOf, eventName, eventMetaLabel);

        Separator sep2 = new Separator();
        sep2.setStyle("-fx-background-color: rgba(245, 158, 11, 0.3);");

        // Footer / Verification Bar
        HBox footerRow = new HBox(20);
        footerRow.setAlignment(Pos.CENTER);

        VBox certIdBox = new VBox(2);
        certIdBox.setAlignment(Pos.CENTER_LEFT);
        Label certIdTitle = new Label("CERTIFICATE NUMBER");
        certIdTitle.setStyle("-fx-text-fill: #64748B; -fx-font-size: 10px; -fx-font-weight: 700;");
        Label certIdVal = new Label(cert.getFormattedCertId());
        certIdVal.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12px; -fx-font-weight: 700; -fx-font-family: monospace;");
        certIdBox.getChildren().addAll(certIdTitle, certIdVal);

        VBox sealBox = new VBox(2);
        sealBox.setAlignment(Pos.CENTER);
        Label sealLabel = new Label("★ VERIFIED ATTENDANCE RECORD ★");
        sealLabel.getStyleClass().add("certificate-seal");
        sealBox.getChildren().add(sealLabel);

        VBox dateBox = new VBox(2);
        dateBox.setAlignment(Pos.CENTER_RIGHT);
        Label dateTitle = new Label("DATE ISSUED");
        dateTitle.setStyle("-fx-text-fill: #64748B; -fx-font-size: 10px; -fx-font-weight: 700;");
        Label dateVal = new Label(cert.getGeneratedDate() != null ? cert.getGeneratedDate().format(fullDateFormatter) : "N/A");
        dateVal.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12px; -fx-font-weight: 700;");
        dateBox.getChildren().addAll(dateTitle, dateVal);

        footerRow.getChildren().addAll(certIdBox, sealBox, dateBox);

        Label verifyNote = new Label("Verification Identifier: " + cert.getVerificationCode() + "  •  Admin Verified Record");
        verifyNote.setStyle("-fx-text-fill: #475569; -fx-font-size: 10.5px; -fx-font-family: monospace;");

        HBox buttonRow = new HBox(12);
        buttonRow.setAlignment(Pos.CENTER);

        Button closeBtn = new Button("Close Preview");
        closeBtn.getStyleClass().add("primary-btn");
        closeBtn.setOnAction(e -> closeModal());

        buttonRow.getChildren().add(closeBtn);

        certBox.getChildren().addAll(topBranding, sep1, recipientBox, eventBox, sep2, footerRow, verifyNote, buttonRow);

        modalOverlay.getChildren().clear();
        modalOverlay.getChildren().add(certBox);
        modalOverlay.setVisible(true);
    }

    private void closeModal() {
        modalOverlay.getChildren().clear();
        modalOverlay.setVisible(false);
    }

    private void showLoadingState(String message) {
        rosterListContainer.getChildren().clear();
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 20, 40, 20));

        Label label = new Label(message);
        label.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 14px;");

        box.getChildren().add(label);
        rosterListContainer.getChildren().add(box);
    }

    private void showEmptyState(String title, String subtitle) {
        rosterListContainer.getChildren().clear();
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state-box");
        box.setPadding(new Insets(30, 20, 30, 20));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");

        Label subLabel = new Label(subtitle);
        subLabel.getStyleClass().add("empty-state-sub");

        box.getChildren().addAll(titleLabel, subLabel);
        rosterListContainer.getChildren().add(box);
    }

    private void showOfflineState() {
        showEmptyState("Database Offline", "Unable to connect to MySQL database. Participant records cannot be retrieved right now.");
    }

    private void showFeedback(String message, boolean isError) {
        feedbackText.setText(message);
        feedbackBanner.getStyleClass().setAll(isError ? "banner-danger" : "banner-success");
        feedbackBanner.setVisible(true);
        feedbackBanner.setManaged(true);
    }

    private void hideFeedback() {
        feedbackBanner.setVisible(false);
        feedbackBanner.setManaged(false);
    }

    /**
     * Helper wrapper for ComboBox items.
     */
    public static class EventSelectItem {
        private final Event event;

        public EventSelectItem(Event event) {
            this.event = event;
        }

        public Event getEvent() {
            return event;
        }

        public int getEventId() {
            return event != null ? event.getEventId() : -1;
        }

        public String getDisplayText() {
            if (event == null) return "Unknown Event";
            String dateStr = event.getEventDate() != null ? event.getEventDate().toString() : "TBD";
            return event.getEventName() + " (" + dateStr + ")";
        }
    }
}
