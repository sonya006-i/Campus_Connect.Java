package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import model.Club;
import model.Event;
import service.EventManagementService;
import service.EventManagementService.OperationResult;
import util.DBConnection;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * AdminEventsController — Administrative Event Management view.
 *
 * Implements Phase 9: Event Management per AGENTS.md:
 * - List, search, and multi-filter campus events
 * - Create new events with full parameter validation and club assignment
 * - Edit existing events with pre-populated forms
 * - Delete events safely with confirmation modal and cascade warning
 * - Asynchronous background database execution with smooth UI updates
 * - Matches modern dark SaaS dashboard aesthetic
 */
public class AdminEventsController {

    private final EventManagementService eventService;

    private StackPane rootContainer;
    private ScrollPane mainScrollPane;
    private StackPane modalOverlay;

    // Filters and Toolbar UI
    private TextField searchField;
    private ComboBox<ClubFilterItem> clubFilterCombo;
    private ComboBox<String> categoryFilterCombo;
    private Label resultsCountLabel;
    private VBox eventsListContainer;
    private HBox toastBanner;
    private Label toastText;

    // In-memory cached data
    private final List<Event> masterEvents = new ArrayList<>();
    private final List<Club> masterClubs = new ArrayList<>();

    // Formatting helpers
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    // Navigation callbacks
    private Runnable onNavigateDashboard;
    private Consumer<Event> onViewEventDetails;

    public AdminEventsController() {
        this.eventService = new EventManagementService();
        buildView();
    }

    public void setOnNavigateDashboard(Runnable onNavigateDashboard) {
        this.onNavigateDashboard = onNavigateDashboard;
    }

    public void setOnViewEventDetails(Consumer<Event> onViewEventDetails) {
        this.onViewEventDetails = onViewEventDetails;
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
        Label titleLabel = new Label("Event Management");
        titleLabel.getStyleClass().add("header-title");

        Label subtitleLabel = new Label("Create, edit, schedule, and oversee campus events across all student clubs.");
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

        Button createEventBtn = new Button("+ Create New Event");
        createEventBtn.getStyleClass().add("primary-btn");
        createEventBtn.setOnAction(e -> openCreateEventDialog());

        headerActions.getChildren().addAll(backBtn, createEventBtn);
        headerBox.getChildren().addAll(titleBox, headerSpacer, headerActions);

        // Toast Banner (hidden by default)
        toastBanner = new HBox(12);
        toastBanner.setAlignment(Pos.CENTER_LEFT);
        toastBanner.getStyleClass().add("success-banner");
        toastBanner.setVisible(false);
        toastBanner.setManaged(false);

        toastText = new Label();
        toastText.getStyleClass().add("success-banner-text");

        Region toastSpacer = new Region();
        HBox.setHgrow(toastSpacer, Priority.ALWAYS);

        Button dismissToastBtn = new Button("✕");
        dismissToastBtn.getStyleClass().add("secondary-btn");
        dismissToastBtn.setStyle("-fx-padding: 2px 8px; -fx-font-size: 11px;");
        dismissToastBtn.setOnAction(e -> hideToast());

        toastBanner.getChildren().addAll(toastText, toastSpacer, dismissToastBtn);

        // Search & Filter Toolbar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(12, 16, 12, 16));
        filterBar.getStyleClass().add("dashboard-card");

        searchField = new TextField();
        searchField.setPromptText("Search events by title, venue, club, or keyword...");
        searchField.getStyleClass().add("text-input");
        searchField.setPrefWidth(320);
        searchField.setMinWidth(180);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        clubFilterCombo = new ComboBox<>();
        clubFilterCombo.getStyleClass().add("combo-box");
        clubFilterCombo.setPrefWidth(200);
        clubFilterCombo.setMinWidth(140);
        clubFilterCombo.getItems().add(new ClubFilterItem(-1, "All Clubs"));
        clubFilterCombo.setValue(clubFilterCombo.getItems().get(0));
        clubFilterCombo.setOnAction(e -> applyFilters());

        categoryFilterCombo = new ComboBox<>();
        categoryFilterCombo.getStyleClass().add("combo-box");
        categoryFilterCombo.setPrefWidth(160);
        categoryFilterCombo.setMinWidth(130);
        categoryFilterCombo.getItems().add("All Categories");
        categoryFilterCombo.setValue("All Categories");
        categoryFilterCombo.setOnAction(e -> applyFilters());

        Button resetBtn = new Button("✕ Reset");
        resetBtn.getStyleClass().add("secondary-btn");
        resetBtn.setMinWidth(Region.USE_PREF_SIZE);
        resetBtn.setOnAction(e -> resetFilters());

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        resultsCountLabel = new Label("Loading events...");
        resultsCountLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 13px; -fx-font-weight: 600;");
        resultsCountLabel.setMinWidth(Region.USE_PREF_SIZE);

        filterBar.getChildren().addAll(searchField, clubFilterCombo, categoryFilterCombo, resetBtn, filterSpacer, resultsCountLabel);

        // Events List Container
        eventsListContainer = new VBox(14);

        contentBox.getChildren().addAll(headerBox, toastBanner, filterBar, eventsListContainer);
        mainScrollPane.setContent(contentBox);

        // 2. Modal Overlay Container
        modalOverlay = new StackPane();
        modalOverlay.getStyleClass().add("dialog-overlay");
        modalOverlay.setVisible(false);
        modalOverlay.setPickOnBounds(true);

        rootContainer.getChildren().addAll(mainScrollPane, modalOverlay);
    }

    /**
     * Loads initial data asynchronously from the database.
     */
    public void loadInitialData() {
        resultsCountLabel.setText("Connecting to database...");
        new Thread(() -> {
            List<Club> clubs = eventService.getAllClubs();
            List<String> categories = eventService.getCategories();
            List<Event> events = eventService.getAllEvents();

            Platform.runLater(() -> {
                masterClubs.clear();
                masterClubs.addAll(clubs);

                clubFilterCombo.getItems().clear();
                clubFilterCombo.getItems().add(new ClubFilterItem(-1, "All Clubs"));
                for (Club c : clubs) {
                    clubFilterCombo.getItems().add(new ClubFilterItem(c.getClubId(), c.getClubName()));
                }
                clubFilterCombo.getSelectionModel().selectFirst();

                categoryFilterCombo.getItems().clear();
                categoryFilterCombo.getItems().add("All Categories");
                if (categories != null) {
                    for (String cat : categories) {
                        if (!categoryFilterCombo.getItems().contains(cat)) {
                            categoryFilterCombo.getItems().add(cat);
                        }
                    }
                }
                categoryFilterCombo.getSelectionModel().selectFirst();

                masterEvents.clear();
                masterEvents.addAll(events);

                applyFilters();
            });
        }).start();
    }

    /**
     * Filters cached master events based on active controls.
     */
    private void applyFilters() {
        String keyword = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        ClubFilterItem selectedClub = clubFilterCombo.getValue();
        int filterClubId = (selectedClub != null) ? selectedClub.getClubId() : -1;
        String selectedCategory = categoryFilterCombo.getValue();
        boolean filterCategory = selectedCategory != null && !selectedCategory.equals("All Categories");

        List<Event> filtered = new ArrayList<>();
        for (Event ev : masterEvents) {
            boolean matchesKeyword = keyword.isEmpty()
                    || (ev.getEventName() != null && ev.getEventName().toLowerCase().contains(keyword))
                    || (ev.getVenue() != null && ev.getVenue().toLowerCase().contains(keyword))
                    || (ev.getDescription() != null && ev.getDescription().toLowerCase().contains(keyword))
                    || (ev.getClubName() != null && ev.getClubName().toLowerCase().contains(keyword))
                    || (ev.getCategory() != null && ev.getCategory().toLowerCase().contains(keyword));

            boolean matchesClub = (filterClubId <= 0) || (ev.getClubId() == filterClubId);
            boolean matchesCat = !filterCategory || (ev.getCategory() != null && ev.getCategory().equalsIgnoreCase(selectedCategory));

            if (matchesKeyword && matchesClub && matchesCat) {
                filtered.add(ev);
            }
        }

        renderEventsList(filtered);
    }

    private void resetFilters() {
        searchField.clear();
        clubFilterCombo.getSelectionModel().selectFirst();
        categoryFilterCombo.getSelectionModel().selectFirst();
        applyFilters();
    }

    /**
     * Renders event cards in the administrative list.
     */
    private void renderEventsList(List<Event> events) {
        eventsListContainer.getChildren().clear();

        if (events == null || events.isEmpty()) {
            resultsCountLabel.setText("0 events found");
            VBox emptyBox = new VBox(10);
            emptyBox.getStyleClass().add("empty-state-box");
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40, 20, 40, 20));

            Label emptyTitle = new Label("No campus events found");
            emptyTitle.getStyleClass().add("empty-state-title");

            Label emptySub = new Label("Try adjusting your search criteria or create a new event.");
            emptySub.getStyleClass().add("empty-state-sub");

            Button createBtn = new Button("+ Create New Event");
            createBtn.getStyleClass().add("primary-btn");
            createBtn.setOnAction(e -> openCreateEventDialog());

            emptyBox.getChildren().addAll(emptyTitle, emptySub, createBtn);
            eventsListContainer.getChildren().add(emptyBox);
            return;
        }

        resultsCountLabel.setText(events.size() + (events.size() == 1 ? " event listed" : " events listed"));

        for (Event ev : events) {
            VBox card = createAdminEventRowCard(ev);
            eventsListContainer.getChildren().add(card);
        }
    }

    /**
     * Constructs a single event management row card.
     */
    private VBox createAdminEventRowCard(Event ev) {
        VBox card = new VBox(10);
        card.getStyleClass().add("event-item-card");
        card.setPadding(new Insets(16, 20, 16, 20));

        // Top Row: Title, Club, Category & Status Pill
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(ev.getEventName());
        titleLabel.getStyleClass().add("event-title");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");

        String clubDisplay = ev.getClubName() != null ? ev.getClubName() : resolveClubName(ev.getClubId());
        Label clubLabel = new Label("🏛 " + clubDisplay);
        clubLabel.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 12px; -fx-font-weight: 700;");

        Label catBadge = new Label(ev.getCategory() != null ? ev.getCategory() : "General");
        catBadge.getStyleClass().add("badge-indigo");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        // Status badge
        Label statusBadge = new Label();
        LocalDate now = LocalDate.now();
        if (ev.getEventDate() != null && ev.getEventDate().isBefore(now)) {
            statusBadge.setText("Completed");
            statusBadge.getStyleClass().setAll("badge-indigo");
            statusBadge.setStyle("-fx-opacity: 0.7;");
        } else if (ev.getRegistrationDeadline() != null && ev.getRegistrationDeadline().isBefore(now)) {
            statusBadge.setText("Closed");
            statusBadge.getStyleClass().setAll("badge-warning");
        } else {
            statusBadge.setText("Upcoming");
            statusBadge.getStyleClass().setAll("badge-success");
        }

        topRow.getChildren().addAll(titleLabel, clubLabel, catBadge, topSpacer, statusBadge);

        // Middle Row: Description excerpt
        Label descLabel = new Label(ev.getDescription() != null && !ev.getDescription().trim().isEmpty()
                ? ev.getDescription()
                : "No event description provided.");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12.5px;");

        // Bottom Row: Metadata & Administrative Action Buttons
        HBox bottomRow = new HBox(14);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        String dateStr = (ev.getEventDate() != null) ? ev.getEventDate().format(dateFormatter) : "TBD";
        String timeStr = (ev.getEventTime() != null) ? ev.getEventTime().format(timeFormatter) : "";
        String fullSchedule = timeStr.isEmpty() ? dateStr : dateStr + " • " + timeStr;

        Label scheduleLabel = new Label("📅 " + fullSchedule);
        scheduleLabel.getStyleClass().add("event-meta");

        Label venueLabel = new Label("📍 " + (ev.getVenue() != null ? ev.getVenue() : "TBD"));
        venueLabel.getStyleClass().add("event-meta");

        Label deadlineLabel = new Label("⏳ Reg Deadline: " + (ev.getRegistrationDeadline() != null ? ev.getRegistrationDeadline().format(dateFormatter) : "None"));
        deadlineLabel.getStyleClass().add("event-meta");

        Label capacityLabel = new Label("👥 Cap: " + ev.getMaxParticipants());
        capacityLabel.getStyleClass().add("event-meta");

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        HBox actionsBox = new HBox(8);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);

        Button editBtn = new Button("✎ Edit");
        editBtn.getStyleClass().add("secondary-btn");
        editBtn.setMinWidth(Region.USE_PREF_SIZE);
        editBtn.setOnAction(e -> openEditEventDialog(ev));

        Button deleteBtn = new Button("🗑 Delete");
        deleteBtn.getStyleClass().add("danger-btn");
        deleteBtn.setMinWidth(Region.USE_PREF_SIZE);
        deleteBtn.setOnAction(e -> openDeleteConfirmDialog(ev));

        actionsBox.getChildren().addAll(editBtn, deleteBtn);

        bottomRow.getChildren().addAll(scheduleLabel, venueLabel, deadlineLabel, capacityLabel, bottomSpacer, actionsBox);

        card.getChildren().addAll(topRow, descLabel, bottomRow);
        return card;
    }

    private String resolveClubName(int clubId) {
        for (Club c : masterClubs) {
            if (c.getClubId() == clubId) {
                return c.getClubName();
            }
        }
        return "Club #" + clubId;
    }

    /**
     * Opens modal dialog for creating a brand new event.
     */
    public void openCreateEventDialog() {
        showEventFormDialog(null);
    }

    /**
     * Opens modal dialog for editing an existing event.
     */
    public void openEditEventDialog(Event event) {
        showEventFormDialog(event);
    }

    /**
     * Displays a clean modal form for event creation or modification.
     */
    private void showEventFormDialog(Event existingEvent) {
        boolean isEdit = existingEvent != null;
        modalOverlay.getChildren().clear();

        VBox dialogCard = new VBox(16);
        dialogCard.getStyleClass().add("modal-dialog-large");

        // Header
        HBox modalHeader = new HBox();
        modalHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(3);
        Label title = new Label(isEdit ? "Edit Event" : "Create New Event");
        title.getStyleClass().add("modal-title");

        Label sub = new Label(isEdit ? "Modify event information, schedule, or capacity limits."
                : "Schedule a new campus event and configure student registration settings.");
        sub.getStyleClass().add("modal-subtitle");
        titleBox.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("secondary-btn");
        closeBtn.setOnAction(e -> closeModal());

        modalHeader.getChildren().addAll(titleBox, spacer, closeBtn);

        // Error message box
        VBox errorBanner = new VBox();
        errorBanner.getStyleClass().add("error-banner");
        Label errorText = new Label();
        errorText.getStyleClass().add("error-text");
        errorBanner.getChildren().add(errorText);
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);

        // Form Fields (GridPane for clean responsive alignment)
        GridPane formGrid = new GridPane();
        formGrid.setHgap(14);
        formGrid.setVgap(12);

        // 1. Event Title
        Label nameLabel = new Label("Event Title *");
        nameLabel.getStyleClass().add("form-label");
        TextField nameField = new TextField(isEdit ? existingEvent.getEventName() : "");
        nameField.setPromptText("e.g. Annual Tech Summit 2026");
        nameField.getStyleClass().add("text-input");
        GridPane.setColumnSpan(nameField, 2);

        // 2. Organizing Club & Category
        Label clubLabel = new Label("Organizing Club *");
        clubLabel.getStyleClass().add("form-label");
        ComboBox<Club> clubCombo = new ComboBox<>();
        clubCombo.getStyleClass().add("combo-box");
        clubCombo.setMaxWidth(Double.MAX_VALUE);
        clubCombo.getItems().addAll(masterClubs);
        clubCombo.setConverter(new StringConverter<Club>() {
            @Override
            public String toString(Club club) {
                return club != null ? club.getClubName() : "";
            }
            @Override
            public Club fromString(String string) {
                return null;
            }
        });
        if (isEdit) {
            for (Club c : masterClubs) {
                if (c.getClubId() == existingEvent.getClubId()) {
                    clubCombo.setValue(c);
                    break;
                }
            }
        } else if (!masterClubs.isEmpty()) {
            clubCombo.setValue(masterClubs.get(0));
        }

        Label categoryLabel = new Label("Category *");
        categoryLabel.getStyleClass().add("form-label");
        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getStyleClass().add("combo-box");
        catCombo.setMaxWidth(Double.MAX_VALUE);
        catCombo.getItems().addAll("Technology", "Workshop", "Cultural", "Sports", "Academic", "Career", "Social", "Competition");
        catCombo.setEditable(true);
        if (isEdit && existingEvent.getCategory() != null) {
            catCombo.setValue(existingEvent.getCategory());
        } else {
            catCombo.setValue("Technology");
        }

        // 3. Event Date & Event Time
        Label dateLabel = new Label("Event Date *");
        dateLabel.getStyleClass().add("form-label");
        DatePicker datePicker = new DatePicker(isEdit ? existingEvent.getEventDate() : LocalDate.now().plusDays(7));
        datePicker.setMaxWidth(Double.MAX_VALUE);
        datePicker.getStyleClass().add("date-picker");

        Label timeLabel = new Label("Event Time");
        timeLabel.getStyleClass().add("form-label");
        String initialTime = (isEdit && existingEvent.getEventTime() != null)
                ? existingEvent.getEventTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                : "10:00";
        TextField timeField = new TextField(initialTime);
        timeField.setPromptText("HH:mm (e.g. 14:30 or 10:00)");
        timeField.getStyleClass().add("text-input");

        // 4. Venue & Max Participants
        Label venueLabel = new Label("Venue / Location *");
        venueLabel.getStyleClass().add("form-label");
        TextField venueField = new TextField(isEdit ? existingEvent.getVenue() : "");
        venueField.setPromptText("e.g. Main Auditorium, Innovation Lab 3");
        venueField.getStyleClass().add("text-input");

        Label maxLabel = new Label("Max Capacity *");
        maxLabel.getStyleClass().add("form-label");
        TextField maxField = new TextField(isEdit ? String.valueOf(existingEvent.getMaxParticipants()) : "100");
        maxField.setPromptText("e.g. 100");
        maxField.getStyleClass().add("text-input");

        // 5. Registration Deadline
        Label deadlineLabel = new Label("Registration Deadline *");
        deadlineLabel.getStyleClass().add("form-label");
        DatePicker deadlinePicker = new DatePicker(isEdit ? existingEvent.getRegistrationDeadline() : LocalDate.now().plusDays(5));
        deadlinePicker.setMaxWidth(Double.MAX_VALUE);
        deadlinePicker.getStyleClass().add("date-picker");

        // 6. Description
        Label descLabel = new Label("Description");
        descLabel.getStyleClass().add("form-label");
        TextArea descArea = new TextArea(isEdit ? existingEvent.getDescription() : "");
        descArea.setPromptText("Describe the event details, objectives, agenda, or guest speakers...");
        descArea.getStyleClass().add("text-area");
        descArea.setPrefRowCount(3);
        GridPane.setColumnSpan(descArea, 2);

        // Add controls to grid: (node, col, row)
        // Row 0: Title label
        formGrid.add(nameLabel, 0, 0, 2, 1);
        formGrid.add(nameField, 0, 1, 2, 1);

        // Row 2: Club & Category labels
        formGrid.add(clubLabel, 0, 2);
        formGrid.add(categoryLabel, 1, 2);
        formGrid.add(clubCombo, 0, 3);
        formGrid.add(catCombo, 1, 3);

        // Row 4: Date & Time
        formGrid.add(dateLabel, 0, 4);
        formGrid.add(timeLabel, 1, 4);
        formGrid.add(datePicker, 0, 5);
        formGrid.add(timeField, 1, 5);

        // Row 6: Venue & Max Participants
        formGrid.add(venueLabel, 0, 6);
        formGrid.add(maxLabel, 1, 6);
        formGrid.add(venueField, 0, 7);
        formGrid.add(maxField, 1, 7);

        // Row 8: Deadline
        formGrid.add(deadlineLabel, 0, 8, 2, 1);
        formGrid.add(deadlinePicker, 0, 9, 2, 1);

        // Row 10: Description
        formGrid.add(descLabel, 0, 10, 2, 1);
        formGrid.add(descArea, 0, 11, 2, 1);

        // Scrollable Form Container to prevent overflow on constrained displays
        ScrollPane formScrollPane = new ScrollPane(formGrid);
        formScrollPane.getStyleClass().add("dialog-scroll-pane");
        formScrollPane.setFitToWidth(true);
        formScrollPane.setMaxHeight(380);
        VBox.setVgrow(formScrollPane, Priority.ALWAYS);

        // Footer Actions
        HBox footerActions = new HBox(12);
        footerActions.setAlignment(Pos.CENTER_RIGHT);
        footerActions.setPadding(new Insets(10, 0, 0, 0));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button submitBtn = new Button(isEdit ? "Save Changes" : "Create Event");
        submitBtn.getStyleClass().add("primary-btn");
        submitBtn.setOnAction(e -> {
            // Read inputs
            String name = nameField.getText() != null ? nameField.getText().trim() : "";
            Club selectedClub = clubCombo.getValue();
            int clubId = selectedClub != null ? selectedClub.getClubId() : -1;
            String category = catCombo.getValue() != null ? catCombo.getValue().trim() : "";
            LocalDate eventDate = datePicker.getValue();
            LocalDate regDeadline = deadlinePicker.getValue();
            String venue = venueField.getText() != null ? venueField.getText().trim() : "";
            String desc = descArea.getText() != null ? descArea.getText().trim() : "";

            int maxCapacity = 0;
            try {
                maxCapacity = Integer.parseInt(maxField.getText().trim());
            } catch (NumberFormatException nfe) {
                showError(errorBanner, errorText, "Max capacity must be a valid whole number.");
                return;
            }

            LocalTime eventTime = parseFlexibleTime(timeField.getText());

            // Build or update Event entity
            Event targetEvent;
            if (isEdit) {
                targetEvent = existingEvent;
                targetEvent.setEventName(name);
                targetEvent.setClubId(clubId);
                targetEvent.setCategory(category);
                targetEvent.setEventDate(eventDate);
                targetEvent.setEventTime(eventTime);
                targetEvent.setVenue(venue);
                targetEvent.setMaxParticipants(maxCapacity);
                targetEvent.setRegistrationDeadline(regDeadline);
                targetEvent.setDescription(desc);
                if (selectedClub != null) targetEvent.setClubName(selectedClub.getClubName());
            } else {
                targetEvent = new Event(
                        clubId, name, desc, category,
                        eventDate, eventTime, venue, maxCapacity, regDeadline
                );
                if (selectedClub != null) targetEvent.setClubName(selectedClub.getClubName());
            }

            // Execute service in background
            submitBtn.setDisable(true);
            submitBtn.setText("Saving...");

            new Thread(() -> {
                OperationResult result = isEdit ? eventService.updateEvent(targetEvent) : eventService.createEvent(targetEvent);
                Platform.runLater(() -> {
                    if (result.isSuccess()) {
                        closeModal();
                        showToast(result.getMessage());
                        loadInitialData();
                    } else {
                        submitBtn.setDisable(false);
                        submitBtn.setText(isEdit ? "Save Changes" : "Create Event");
                        showError(errorBanner, errorText, result.getMessage());
                    }
                });
            }).start();
        });

        footerActions.getChildren().addAll(cancelBtn, submitBtn);

        dialogCard.getChildren().addAll(modalHeader, errorBanner, formScrollPane, footerActions);
        modalOverlay.getChildren().add(dialogCard);
        modalOverlay.setVisible(true);
    }

    /**
     * Parses time strings flexibly (e.g. "14:30", "10:00 AM", "2:30 pm").
     */
    private LocalTime parseFlexibleTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return LocalTime.of(10, 0);
        }
        String trimmed = raw.trim();
        try {
            return LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ignored) {}

        try {
            return LocalTime.parse(trimmed.toUpperCase(), DateTimeFormatter.ofPattern("h:mm a"));
        } catch (DateTimeParseException ignored) {}

        try {
            return LocalTime.parse(trimmed.toUpperCase(), DateTimeFormatter.ofPattern("hh:mm a"));
        } catch (DateTimeParseException ignored) {}

        return LocalTime.of(10, 0);
    }

    /**
     * Opens modal dialog requesting confirmation to delete an event.
     */
    private void openDeleteConfirmDialog(Event event) {
        modalOverlay.getChildren().clear();

        VBox dialogCard = new VBox(16);
        dialogCard.getStyleClass().add("modal-dialog-card");

        Label title = new Label("Delete Event");
        title.getStyleClass().add("modal-title");

        Label msg = new Label("Are you sure you want to permanently delete '" + event.getEventName() + "'?");
        msg.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 13.5px; -fx-font-weight: 600;");
        msg.setWrapText(true);

        VBox warningBox = new VBox(6);
        warningBox.getStyleClass().add("error-banner");

        Label warnHeader = new Label();
        warnHeader.getStyleClass().add("error-text");
        warnHeader.setStyle("-fx-font-weight: bold;");

        Label warnDesc = new Label();
        warnDesc.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12px;");
        warnDesc.setWrapText(true);

        warningBox.getChildren().addAll(warnHeader, warnDesc);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button deleteBtn = new Button("Delete Event");
        deleteBtn.getStyleClass().add("danger-btn-solid");

        // Inspect dependent records to update dialog notice and state
        dao.EventDAO.DependentSummary summary = eventService.getDependentSummary(event.getEventId());
        if (summary.hasDependencies()) {
            warnHeader.setText("⚠️ Deletion Blocked: Existing Participant History");
            warnDesc.setText("This event has " + summary.toDescription() + ". University policy strictly prohibits deleting events with existing participant records to preserve data integrity.");
            deleteBtn.setDisable(true);
            deleteBtn.setText("Deletion Blocked");
        } else {
            warnHeader.setText("⚠️ Confirm Event Deletion");
            warnDesc.setText("This event has no registered participants. It will be permanently removed from the campus schedule. This action cannot be undone.");
        }

        deleteBtn.setOnAction(e -> {
            deleteBtn.setDisable(true);
            deleteBtn.setText("Deleting...");

            new Thread(() -> {
                OperationResult result = eventService.deleteEvent(event.getEventId());
                Platform.runLater(() -> {
                    closeModal();
                    if (result.isSuccess()) {
                        showToast("Event '" + event.getEventName() + "' was successfully deleted.");
                        loadInitialData();
                    } else {
                        showToast("Failed to delete event: " + result.getMessage());
                    }
                });
            }).start();
        });

        actions.getChildren().addAll(cancelBtn, deleteBtn);
        dialogCard.getChildren().addAll(title, msg, warningBox, actions);

        modalOverlay.getChildren().add(dialogCard);
        modalOverlay.setVisible(true);
    }

    private void closeModal() {
        modalOverlay.setVisible(false);
        modalOverlay.getChildren().clear();
    }

    private void showError(VBox banner, Label label, String msg) {
        label.setText(msg);
        banner.setVisible(true);
        banner.setManaged(true);
    }

    private void showToast(String message) {
        toastText.setText("✔  " + message);
        toastBanner.setVisible(true);
        toastBanner.setManaged(true);
    }

    private void hideToast() {
        toastBanner.setVisible(false);
        toastBanner.setManaged(false);
    }

    /**
     * Helper wrapper for club filter combo box.
     */
    public static class ClubFilterItem {
        private final int clubId;
        private final String clubName;

        public ClubFilterItem(int clubId, String clubName) {
            this.clubId = clubId;
            this.clubName = clubName;
        }

        public int getClubId() {
            return clubId;
        }

        @Override
        public String toString() {
            return clubName;
        }
    }
}
