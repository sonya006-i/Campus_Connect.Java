package controller;

import dao.EventDAO;
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
import model.Event;
import service.RegistrationService;
import service.RegistrationService.Eligibility;
import service.RegistrationService.EventStatusInfo;
import service.RegistrationService.RegistrationResult;
import util.DBConnection;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * EventsController — Controls event discovery, search, category filtering,
 * event detail views, and student event registration.
 *
 * Implements Phase 5 (Browsing & Discovery) and Phase 6 (Event Registration).
 * Follows AGENTS.md:
 * - Single combined SQL search/filter query via EventDAO
 * - Clean RegistrationService business logic layer & validations
 * - Atomic database insert with database-generated registration ID
 * - Non-blocking asynchronous threading for database operations
 * - Graceful offline, full-capacity, deadline, and duplicate registration handling
 */
public class EventsController {

    private final EventDAO eventDAO;
    private final RegistrationService registrationService;

    private StackPane rootContainer;
    private ScrollPane discoveryScrollPane;
    private ScrollPane detailScrollPane;
    private StackPane modalOverlay;

    // Discovery UI Controls
    private TextField searchField;
    private ComboBox<String> categoryCombo;
    private Label resultsCountLabel;
    private VBox cardsContainer;

    // Formatting helpers
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    private Runnable onNavigateMyEvents;

    public EventsController() {
        this.eventDAO = new EventDAO();
        this.registrationService = new RegistrationService();
        buildViews();
    }

    public void setOnNavigateMyEvents(Runnable onNavigateMyEvents) {
        this.onNavigateMyEvents = onNavigateMyEvents;
    }

    private void buildViews() {
        rootContainer = new StackPane();
        rootContainer.getStyleClass().add("root");

        // 1. Build Discovery View
        discoveryScrollPane = buildDiscoveryView();

        // 2. Build Detail View (initially hidden)
        detailScrollPane = new ScrollPane();
        detailScrollPane.getStyleClass().add("main-scroll-pane");
        detailScrollPane.setFitToWidth(true);
        detailScrollPane.setVisible(false);

        // 3. Modal Overlay for Confirmation & Success dialogs (initially hidden)
        modalOverlay = new StackPane();
        modalOverlay.getStyleClass().add("dialog-overlay");
        modalOverlay.setVisible(false);
        modalOverlay.setPickOnBounds(true);

        rootContainer.getChildren().addAll(discoveryScrollPane, detailScrollPane, modalOverlay);
    }

    /**
     * Builds the main event discovery list with search, filter, and card stream.
     */
    private ScrollPane buildDiscoveryView() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("main-scroll-pane");
        scrollPane.setFitToWidth(true);

        VBox contentBox = new VBox(22);
        contentBox.getStyleClass().add("main-content");

        // Header Section
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label titleLabel = new Label("Discover Events");
        titleLabel.getStyleClass().add("header-title");

        Label subtitleLabel = new Label("Find workshops, competitions, cultural events and more across campus.");
        subtitleLabel.getStyleClass().add("header-subtitle");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        resultsCountLabel = new Label("Loading events...");
        resultsCountLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 13px; -fx-font-weight: 600;");

        headerBox.getChildren().addAll(titleBox, headerSpacer, resultsCountLabel);

        // Search & Filter Toolbar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(12, 16, 12, 16));
        filterBar.getStyleClass().add("dashboard-card");

        searchField = new TextField();
        searchField.setPromptText("Search events by name, venue, or keyword...");
        searchField.getStyleClass().add("text-input");
        searchField.setPrefWidth(380);
        searchField.setMinWidth(200);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> triggerSearch());

        categoryCombo = new ComboBox<>();
        categoryCombo.getStyleClass().add("combo-box");
        categoryCombo.setPrefWidth(180);
        categoryCombo.setMinWidth(140);
        categoryCombo.getItems().add("All Categories");
        categoryCombo.setValue("All Categories");
        categoryCombo.setOnAction(e -> triggerSearch());

        Button clearBtn = new Button("✕ Clear");
        clearBtn.getStyleClass().add("secondary-btn");
        clearBtn.setMinWidth(Region.USE_PREF_SIZE);
        clearBtn.setOnAction(e -> clearFilters());

        filterBar.getChildren().addAll(searchField, categoryCombo, clearBtn);

        // Event Cards Stream Container
        cardsContainer = new VBox(14);

        contentBox.getChildren().addAll(headerBox, filterBar, cardsContainer);
        scrollPane.setContent(contentBox);
        return scrollPane;
    }

    /**
     * Initializes categories and loads event data asynchronously.
     */
    public void loadInitialData() {
        showDiscoveryView();
        loadCategories();
        triggerSearch();
    }

    /**
     * Loads distinct categories from the database into the filter combo.
     */
    private void loadCategories() {
        new Thread(() -> {
            List<String> categories = eventDAO.getDistinctCategories();
            Platform.runLater(() -> {
                String current = categoryCombo.getValue();
                categoryCombo.getItems().clear();
                categoryCombo.getItems().add("All Categories");
                if (categories != null) {
                    categoryCombo.getItems().addAll(categories);
                }
                if (current != null && categoryCombo.getItems().contains(current)) {
                    categoryCombo.setValue(current);
                } else {
                    categoryCombo.setValue("All Categories");
                }
            });
        }).start();
    }

    /**
     * Queries database for events matching current search and category filters.
     */
    public void triggerSearch() {
        String keyword = searchField != null ? searchField.getText() : null;
        String category = categoryCombo != null ? categoryCombo.getValue() : "All Categories";

        if (resultsCountLabel != null) {
            resultsCountLabel.setText("Searching events...");
        }

        new Thread(() -> {
            List<Event> results = null;
            boolean isOnline = true;
            try {
                results = eventDAO.searchAndFilter(keyword, category);
            } catch (Exception ex) {
                isOnline = false;
            }

            final boolean onlineFinal = isOnline;
            final List<Event> finalResults = results;
            Platform.runLater(() -> {
                if (!onlineFinal) {
                    renderOfflineState();
                } else if (finalResults == null || finalResults.isEmpty()) {
                    renderEmptyState(keyword, category);
                } else {
                    renderEventCards(finalResults);
                }
            });
        }).start();
    }

    private void renderEventCards(List<Event> events) {
        cardsContainer.getChildren().clear();
        resultsCountLabel.setText("Showing " + events.size() + (events.size() == 1 ? " event" : " events"));

        for (Event event : events) {
            VBox card = createEventGridCard(event);
            cardsContainer.getChildren().add(card);
        }
    }

    /**
     * Builds a single modern dark card for an event.
     */
    private VBox createEventGridCard(Event event) {
        VBox card = new VBox(10);
        card.getStyleClass().add("event-grid-card");

        // Top line: Name + Category
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(event.getEventName());
        titleLabel.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 16px; -fx-font-weight: 800;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label catBadge = new Label(event.getCategory() != null ? event.getCategory() : "Campus");
        catBadge.getStyleClass().add(getCategoryBadgeClass(event.getCategory()));

        topRow.getChildren().addAll(titleLabel, spacer, catBadge);

        // Club info
        String clubName = event.getClubName() != null ? event.getClubName() : "Campus Society";
        Label clubLabel = new Label("🏛  " + clubName);
        clubLabel.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 12px; -fx-font-weight: 600;");

        // Schedule & Venue
        String dateStr = (event.getEventDate() != null) ? event.getEventDate().format(dateFormatter) : "TBD";
        String timeStr = (event.getEventTime() != null) ? event.getEventTime().format(timeFormatter) : "";
        String timing = timeStr.isEmpty() ? dateStr : dateStr + " • " + timeStr;
        String venue = (event.getVenue() != null && !event.getVenue().isEmpty()) ? event.getVenue() : "Campus Grounds";

        Label metaLabel = new Label("📅  " + timing + "   •   📍  " + venue);
        metaLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px;");

        // Description preview
        String desc = event.getDescription() != null ? event.getDescription() : "No description available.";
        if (desc.length() > 140) {
            desc = desc.substring(0, 137) + "...";
        }
        Label descLabel = new Label(desc);
        descLabel.setStyle("-fx-text-fill: #68677A; -fx-font-size: 12.5px;");
        descLabel.setWrapText(true);

        // Bottom status line: Capacity, Deadline, and CTA
        HBox bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        String deadlineStr = (event.getRegistrationDeadline() != null)
                ? event.getRegistrationDeadline().format(dateFormatter)
                : "Open";
        Label capacityLabel = new Label("👥  Capacity: " + event.getMaxParticipants() + "   •   ⏳  Register by: " + deadlineStr);
        capacityLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px; -fx-font-weight: 600;");

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        Button detailsBtn = new Button("View Details →");
        detailsBtn.getStyleClass().add("primary-btn");
        detailsBtn.setMinWidth(Region.USE_PREF_SIZE);
        detailsBtn.setOnAction(e -> openEventDetailView(event));

        bottomRow.getChildren().addAll(capacityLabel, bottomSpacer, detailsBtn);

        card.getChildren().addAll(topRow, clubLabel, metaLabel, descLabel, bottomRow);

        // Clicking anywhere on the card opens details
        card.setOnMouseClicked(e -> openEventDetailView(event));
        return card;
    }

    /**
     * Renders the comprehensive Event Detail View with Phase 6 registration controls.
     */
    public void openEventDetailView(Event event) {
        openEventDetailView(event, null);
    }

    /**
     * Renders the Event Detail View with an optional custom back callback (e.g. from My Events).
     */
    public void openEventDetailView(Event event, Runnable onBackCustom) {
        VBox contentBox = new VBox(20);
        contentBox.getStyleClass().add("main-content");

        // 1. Back button
        String backText = (onBackCustom != null) ? "← Back to My Events" : "← Back to Events";
        Button backBtn = new Button(backText);
        backBtn.getStyleClass().add("back-button");
        backBtn.setOnAction(e -> {
            if (onBackCustom != null) {
                onBackCustom.run();
            } else {
                showDiscoveryView();
            }
        });

        // 2. Main Detail Card
        VBox detailCard = new VBox(20);
        detailCard.getStyleClass().add("event-detail-card");

        // Header line: Category + Club
        HBox headerLine = new HBox(10);
        headerLine.setAlignment(Pos.CENTER_LEFT);

        Label catBadge = new Label(event.getCategory() != null ? event.getCategory() : "Campus");
        catBadge.getStyleClass().add(getCategoryBadgeClass(event.getCategory()));

        Label clubLabel = new Label("Hosted by " + (event.getClubName() != null ? event.getClubName() : "Campus Society"));
        clubLabel.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 13px; -fx-font-weight: bold;");

        headerLine.getChildren().addAll(catBadge, clubLabel);

        // Title
        Label titleLabel = new Label(event.getEventName());
        titleLabel.getStyleClass().add("event-detail-title");

        // Key Logistics Box
        HBox logisticsBox = new HBox(18);
        logisticsBox.setAlignment(Pos.CENTER_LEFT);
        logisticsBox.getStyleClass().add("info-pill-box");

        String dateStr = (event.getEventDate() != null) ? event.getEventDate().format(dateFormatter) : "TBD";
        String timeStr = (event.getEventTime() != null) ? event.getEventTime().format(timeFormatter) : "";
        String timing = timeStr.isEmpty() ? dateStr : dateStr + " • " + timeStr;

        VBox timeCol = createLogisticsCol("Date & Time", timing);
        VBox venueCol = createLogisticsCol("Venue", event.getVenue() != null ? event.getVenue() : "Campus Grounds");
        VBox capCol = createLogisticsCol("Participant Limit", event.getMaxParticipants() + " attendees");
        String deadlineStr = (event.getRegistrationDeadline() != null) ? event.getRegistrationDeadline().format(dateFormatter) : "Open";
        VBox deadCol = createLogisticsCol("Registration Closes", deadlineStr);

        HBox.setHgrow(timeCol, Priority.ALWAYS);
        HBox.setHgrow(venueCol, Priority.ALWAYS);
        HBox.setHgrow(capCol, Priority.ALWAYS);
        HBox.setHgrow(deadCol, Priority.ALWAYS);

        logisticsBox.getChildren().addAll(timeCol, venueCol, capCol, deadCol);

        // Description Section
        VBox descBox = new VBox(8);
        Label descHeading = new Label("About This Event");
        descHeading.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 15px; -fx-font-weight: bold;");

        String fullDesc = (event.getDescription() != null && !event.getDescription().trim().isEmpty())
                ? event.getDescription()
                : "No description available.";
        Label descContent = new Label(fullDesc);
        descContent.getStyleClass().add("event-detail-desc");
        descContent.setWrapText(true);

        descBox.getChildren().addAll(descHeading, descContent);

        // 3. Phase 6 Dynamic Registration Action Area
        VBox actionBox = new VBox(10);
        actionBox.getStyleClass().add("action-notice-box");

        HBox actionHeader = new HBox();
        actionHeader.setAlignment(Pos.CENTER_LEFT);

        VBox actionText = new VBox(3);
        HBox titleStatusRow = new HBox(10);
        titleStatusRow.setAlignment(Pos.CENTER_LEFT);

        Label actionTitle = new Label("Event Registration");
        actionTitle.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label actionStatusBadge = new Label("Checking...");
        actionStatusBadge.getStyleClass().add("badge-neutral");

        titleStatusRow.getChildren().addAll(actionTitle, actionStatusBadge);

        Label actionNote = new Label("Verifying your registration eligibility...");
        actionNote.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");
        actionText.getChildren().addAll(titleStatusRow, actionNote);

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        Button registerBtn = new Button("Checking...");
        registerBtn.getStyleClass().add("primary-btn");
        registerBtn.setDisable(true);

        actionHeader.getChildren().addAll(actionText, actionSpacer, registerBtn);
        actionBox.getChildren().add(actionHeader);

        // Asynchronously check eligibility and bind registration controls
        new Thread(() -> {
            EventStatusInfo statusInfo = registrationService.getEventStatusInfo(event);
            Platform.runLater(() -> applyRegistrationStatusToUI(event, statusInfo, actionStatusBadge, actionNote, registerBtn));
        }).start();

        detailCard.getChildren().addAll(headerLine, titleLabel, logisticsBox, descBox, actionBox);
        contentBox.getChildren().addAll(backBtn, detailCard);

        detailScrollPane.setContent(contentBox);

        // Switch to detail view
        discoveryScrollPane.setVisible(false);
        detailScrollPane.setVisible(true);
    }

    /**
     * Binds the evaluated registration eligibility status to the action box.
     */
    private void applyRegistrationStatusToUI(Event event, EventStatusInfo info,
                                            Label statusBadge, Label noteLabel, Button registerBtn) {
        Eligibility eligibility = info.getEligibility();

        switch (eligibility) {
            case AVAILABLE:
                statusBadge.setText("● Open for Registration");
                statusBadge.getStyleClass().setAll("badge-indigo");
                noteLabel.setText(info.getMessage());
                registerBtn.setText("Register for Event");
                registerBtn.setDisable(false);
                registerBtn.setOnAction(e -> showConfirmationDialog(event));
                break;

            case ALREADY_REGISTERED:
                String st = (info.getRegistrationStatus() != null) ? info.getRegistrationStatus().toUpperCase() : "PENDING";
                statusBadge.setText("✔ Registered (" + st + ")");
                if ("APPROVED".equals(st)) {
                    statusBadge.getStyleClass().setAll("badge-success");
                } else if ("REJECTED".equals(st)) {
                    statusBadge.getStyleClass().setAll("badge-danger");
                } else {
                    statusBadge.getStyleClass().setAll("badge-warning");
                }

                String regText = (info.getFormattedRegId() != null) ? "Registration ID: #" + info.getFormattedRegId() + "   •   " : "";
                regText += (info.getRegistrationDate() != null) ? "Date: " + info.getRegistrationDate().format(dateFormatter) : "";
                noteLabel.setText(regText.isEmpty() ? "You are registered for this event." : regText);

                registerBtn.setText("Already Registered");
                registerBtn.setDisable(true);
                break;

            case DEADLINE_PASSED:
                statusBadge.setText("✕ Closed");
                statusBadge.getStyleClass().setAll("badge-neutral");
                noteLabel.setText(info.getMessage());
                registerBtn.setText("Registration Closed");
                registerBtn.setDisable(true);
                break;

            case EVENT_FULL:
                statusBadge.setText("✕ Full");
                statusBadge.getStyleClass().setAll("badge-danger");
                noteLabel.setText(info.getMessage());
                registerBtn.setText("Event Full");
                registerBtn.setDisable(true);
                break;

            case STUDENT_ONLY:
                statusBadge.setText("Admin Mode");
                statusBadge.getStyleClass().setAll("badge-indigo");
                noteLabel.setText("Registration is available to students. Administrator view is read-only.");
                registerBtn.setText("Student Registration Only");
                registerBtn.setDisable(true);
                break;

            case NOT_LOGGED_IN:
                statusBadge.setText("Sign In Required");
                statusBadge.getStyleClass().setAll("badge-danger");
                noteLabel.setText("Please log in as a student to register for events.");
                registerBtn.setText("Login Required");
                registerBtn.setDisable(true);
                break;

            case DB_OFFLINE:
            default:
                statusBadge.setText("● Offline");
                statusBadge.getStyleClass().setAll("badge-warning");
                noteLabel.setText(info.getMessage());
                registerBtn.setText("Database Offline");
                registerBtn.setDisable(true);
                break;
        }
    }

    /**
     * Displays a clean modal confirmation dialog before database insertion.
     */
    private void showConfirmationDialog(Event event) {
        modalOverlay.getChildren().clear();

        VBox card = new VBox(18);
        card.getStyleClass().add("modal-dialog-card");

        Label title = new Label("Register for Event?");
        title.getStyleClass().add("modal-title");

        Label sub = new Label("Please review event details before confirming your registration:");
        sub.getStyleClass().add("modal-subtitle");

        VBox summaryBox = new VBox(6);
        summaryBox.getStyleClass().add("info-pill-box");

        Label evTitle = new Label(event.getEventName());
        evTitle.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 15px; -fx-font-weight: bold;");

        String dateStr = (event.getEventDate() != null) ? event.getEventDate().format(dateFormatter) : "TBD";
        String timeStr = (event.getEventTime() != null) ? event.getEventTime().format(timeFormatter) : "";
        String timing = timeStr.isEmpty() ? dateStr : dateStr + " • " + timeStr;
        String venue = (event.getVenue() != null && !event.getVenue().isEmpty()) ? event.getVenue() : "Campus Grounds";
        String club = (event.getClubName() != null) ? event.getClubName() : "Campus Society";

        Label details = new Label("📅 " + timing + "   •   📍 " + venue);
        details.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");

        Label clubLabel = new Label("🏛 " + club);
        clubLabel.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 12px; -fx-font-weight: 600;");

        summaryBox.getChildren().addAll(evTitle, details, clubLabel);

        Label notice = new Label("Your registration status will be set to PENDING upon submission.");
        notice.setStyle("-fx-text-fill: #68677A; -fx-font-size: 11.5px;");

        HBox buttonRow = new HBox(12);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> hideModal());

        Button confirmBtn = new Button("Confirm Registration");
        confirmBtn.getStyleClass().add("primary-btn");

        confirmBtn.setOnAction(e -> {
            confirmBtn.setText("Registering...");
            confirmBtn.setDisable(true);
            cancelBtn.setDisable(true);

            new Thread(() -> {
                RegistrationResult result = registrationService.registerCurrentStudent(event);
                Platform.runLater(() -> {
                    hideModal();
                    if (result.isSuccess()) {
                        showSuccessDialog(result);
                        // Refresh event detail view to reflect registered state
                        openEventDetailView(event);
                    } else {
                        showErrorDialog(result.getMessage());
                        openEventDetailView(event);
                    }
                });
            }).start();
        });

        buttonRow.getChildren().addAll(cancelBtn, confirmBtn);

        card.getChildren().addAll(title, sub, summaryBox, notice, buttonRow);
        modalOverlay.getChildren().add(card);
        modalOverlay.setVisible(true);
    }

    /**
     * Displays the registration success modal with verified database registration ID.
     */
    private void showSuccessDialog(RegistrationResult result) {
        modalOverlay.getChildren().clear();

        VBox card = new VBox(18);
        card.getStyleClass().add("modal-dialog-card");

        Label title = new Label("Registration Successful ✓");
        title.setStyle("-fx-text-fill: #22C55E; -fx-font-size: 19px; -fx-font-weight: 800;");

        Label sub = new Label("You have successfully registered for:");
        sub.getStyleClass().add("modal-subtitle");

        Label evName = new Label(result.getEvent() != null ? result.getEvent().getEventName() : "Campus Event");
        evName.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 16px; -fx-font-weight: bold;");

        // Verified Registration Details Box
        VBox detailsBox = new VBox(10);
        detailsBox.getStyleClass().add("info-pill-box");

        HBox idRow = new HBox(10);
        idRow.setAlignment(Pos.CENTER_LEFT);
        Label idTitle = new Label("Registration ID:");
        idTitle.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px; -fx-font-weight: 600;");
        Label idVal = new Label("#" + result.getFormattedRegId());
        idVal.getStyleClass().add("reg-id-pill");
        idRow.getChildren().addAll(idTitle, idVal);

        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        Label statusTitle = new Label("Status:");
        statusTitle.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px; -fx-font-weight: 600;");
        Label statusVal = new Label(result.getStatus());
        statusVal.getStyleClass().add("badge-warning");
        statusRow.getChildren().addAll(statusTitle, statusVal);

        HBox dateRow = new HBox(10);
        dateRow.setAlignment(Pos.CENTER_LEFT);
        Label dateTitle = new Label("Registration Date:");
        dateTitle.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px; -fx-font-weight: 600;");
        String dateFormatted = (result.getRegistrationDate() != null)
                ? result.getRegistrationDate().format(dateFormatter)
                : "Today";
        Label dateVal = new Label(dateFormatted);
        dateVal.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12.5px; -fx-font-weight: bold;");
        dateRow.getChildren().addAll(dateTitle, dateVal);

        detailsBox.getChildren().addAll(idRow, statusRow, dateRow);

        // Buttons
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        Button myEventsBtn = new Button("View My Events");
        myEventsBtn.getStyleClass().add("secondary-btn");
        myEventsBtn.setOnAction(e -> {
            hideModal();
            if (onNavigateMyEvents != null) {
                onNavigateMyEvents.run();
            }
        });

        Button backToEventsBtn = new Button("Back to Events");
        backToEventsBtn.getStyleClass().add("primary-btn");
        backToEventsBtn.setOnAction(e -> {
            hideModal();
            showDiscoveryView();
        });

        Button stayBtn = new Button("Done");
        stayBtn.getStyleClass().add("secondary-btn");
        stayBtn.setOnAction(e -> hideModal());

        buttonRow.getChildren().addAll(myEventsBtn, stayBtn, backToEventsBtn);

        card.getChildren().addAll(title, sub, evName, detailsBox, buttonRow);
        modalOverlay.getChildren().add(card);
        modalOverlay.setVisible(true);
    }

    /**
     * Displays a clean non-crashing registration error modal.
     */
    private void showErrorDialog(String errorMessage) {
        modalOverlay.getChildren().clear();

        VBox card = new VBox(16);
        card.getStyleClass().add("modal-dialog-card");

        Label title = new Label("Registration Notice");
        title.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 18px; -fx-font-weight: 800;");

        Label message = new Label(errorMessage);
        message.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 13.5px;");
        message.setWrapText(true);

        HBox btnRow = new HBox();
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button dismissBtn = new Button("Dismiss");
        dismissBtn.getStyleClass().add("secondary-btn");
        dismissBtn.setOnAction(e -> hideModal());
        btnRow.getChildren().add(dismissBtn);

        card.getChildren().addAll(title, message, btnRow);
        modalOverlay.getChildren().add(card);
        modalOverlay.setVisible(true);
    }

    private void hideModal() {
        modalOverlay.setVisible(false);
        modalOverlay.getChildren().clear();
    }

    private VBox createLogisticsCol(String title, String val) {
        VBox col = new VBox(4);
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #68677A; -fx-font-size: 11px; -fx-font-weight: 600;");

        Label v = new Label(val);
        v.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 13px; -fx-font-weight: bold;");
        v.setWrapText(true);

        col.getChildren().addAll(t, v);
        return col;
    }

    private void showDiscoveryView() {
        detailScrollPane.setVisible(false);
        discoveryScrollPane.setVisible(true);
    }

    private void clearFilters() {
        if (searchField != null) searchField.clear();
        if (categoryCombo != null) categoryCombo.setValue("All Categories");
        triggerSearch();
    }

    private void renderEmptyState(String keyword, String category) {
        cardsContainer.getChildren().clear();
        resultsCountLabel.setText("0 events found");

        VBox emptyBox = new VBox(8);
        emptyBox.getStyleClass().add("empty-state-box");
        emptyBox.setAlignment(Pos.CENTER);

        boolean hasFilters = (keyword != null && !keyword.trim().isEmpty()) ||
                (category != null && !category.equalsIgnoreCase("All Categories"));

        Label title = new Label(hasFilters ? "No events match your search" : "No events available");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label(hasFilters
                ? "Try adjusting your keywords or selecting a different category filter."
                : "Check back soon for upcoming campus club activities.");
        sub.getStyleClass().add("empty-state-sub");

        emptyBox.getChildren().addAll(title, sub);

        if (hasFilters) {
            Button resetBtn = new Button("Clear Search & Filters");
            resetBtn.getStyleClass().add("secondary-btn");
            resetBtn.setOnAction(e -> clearFilters());
            emptyBox.getChildren().add(resetBtn);
        }

        cardsContainer.getChildren().add(emptyBox);
    }

    private void renderOfflineState() {
        cardsContainer.getChildren().clear();
        resultsCountLabel.setText("Database Offline");

        VBox offlineBox = new VBox(8);
        offlineBox.getStyleClass().add("empty-state-box");
        offlineBox.setAlignment(Pos.CENTER);

        Label title = new Label("Event data unavailable");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Unable to reach the MySQL database server. Please verify your connection.");
        sub.getStyleClass().add("empty-state-sub");

        offlineBox.getChildren().addAll(title, sub);
        cardsContainer.getChildren().add(offlineBox);
    }

    private String getCategoryBadgeClass(String category) {
        if (category == null) return "badge-indigo";
        String lower = category.toLowerCase();
        if (lower.contains("tech") || lower.contains("code")) return "badge-indigo";
        if (lower.contains("robot") || lower.contains("ai")) return "badge-purple";
        if (lower.contains("art") || lower.contains("music") || lower.contains("fashion")) return "badge-success";
        return "badge-indigo";
    }

    public Parent getView() {
        return rootContainer;
    }
}
