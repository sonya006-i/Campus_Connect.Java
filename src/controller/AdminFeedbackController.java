package controller;

import dao.EventDAO;
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
import model.AdminFeedbackDTO;
import model.Event;
import model.FeedbackSummaryDTO;
import model.User;
import service.FeedbackManagementService;
import util.DBConnection;
import util.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminFeedbackController — Administrator Feedback Management view.
 *
 * Implements Phase 13 per AGENTS.md:
 * - Scoped strictly to system administrators (role: ADMIN)
 * - Campus-wide overview of student ratings, reviews, and event experience reports
 * - Real-time metrics: Total Feedback, Average Rating, 5-Star Count, Events Reviewed
 * - Dynamic filtering by Event and Rating (All, 5★, 4★, 3★, 2★, 1★)
 * - Instant keyword search across event titles, student names, emails, and comments
 * - Complete, untruncated comment viewer modal
 * - Safe against data corruption: no destructive deletion permitted by default
 * - Full offline resilience and defensive null handling
 */
public class AdminFeedbackController {

    private final FeedbackManagementService feedbackService;
    private final EventDAO eventDAO;

    private StackPane rootContainer;
    private ScrollPane mainScrollPane;
    private StackPane modalOverlay;
    private VBox feedbackListContainer;

    // KPI Counters
    private Label totalFeedbackLabel;
    private Label avgRatingLabel;
    private Label fiveStarCountLabel;
    private Label eventsReviewedLabel;

    // Filters UI
    private ComboBox<EventFilterItem> eventFilterCombo;
    private TextField searchField;
    private Button filterAllRatingBtn;
    private Button filter5RatingBtn;
    private Button filter4RatingBtn;
    private Button filter3RatingBtn;
    private Button filterLowRatingBtn;
    private Label resultsCountLabel;

    // Banner notifications
    private HBox feedbackBanner;
    private Label feedbackText;
    private Button dismissFeedbackBtn;

    // Active state
    private int selectedRatingFilter = 0; // 0 = all, 5, 4, 3, -1 = low (1-2)
    private final List<AdminFeedbackDTO> cachedFeedback = new ArrayList<>();
    private final List<Event> cachedEvents = new ArrayList<>();

    // Navigation callbacks
    private Runnable onNavigateDashboard;

    public AdminFeedbackController() {
        this.feedbackService = new FeedbackManagementService();
        this.eventDAO = new EventDAO();
        buildView();
    }

    public void setOnNavigateDashboard(Runnable onNavigateDashboard) {
        this.onNavigateDashboard = onNavigateDashboard;
    }

    public Parent getView() {
        return rootContainer;
    }

    public static class EventFilterItem {
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

    private void buildView() {
        rootContainer = new StackPane();
        rootContainer.getStyleClass().add("root");

        // 1. Main Scrollable Container
        mainScrollPane = new ScrollPane();
        mainScrollPane.getStyleClass().add("main-scroll-pane");
        mainScrollPane.setFitToWidth(true);

        VBox contentBox = new VBox(20);
        contentBox.getStyleClass().add("main-content");

        // Header
        HBox headerBox = createHeader();

        // Feedback Banner
        feedbackBanner = createFeedbackBanner();

        // KPI Summary Bar
        HBox kpiStrip = createKpiSummaryBar();

        // Search & Filter Toolbar
        VBox filterBar = createFilterToolbar();

        // Feedback List Container
        feedbackListContainer = new VBox(12);

        contentBox.getChildren().addAll(headerBox, feedbackBanner, kpiStrip, filterBar, feedbackListContainer);
        mainScrollPane.setContent(contentBox);

        // 2. Modal Overlay Container for Comment Viewer
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
        Label titleLabel = new Label("Campus Feedback Overview");
        titleLabel.getStyleClass().add("header-title");

        Label subtitleLabel = new Label("Review student ratings, feedback submissions, and quality reports across campus events.");
        subtitleLabel.getStyleClass().add("header-subtitle");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button backBtn = new Button("⊞ Dashboard");
        backBtn.getStyleClass().add("secondary-btn");
        backBtn.setOnAction(e -> {
            if (onNavigateDashboard != null) onNavigateDashboard.run();
        });

        Button refreshBtn = new Button("↻ Refresh");
        refreshBtn.getStyleClass().add("secondary-btn");
        refreshBtn.setOnAction(e -> loadInitialData());

        actions.getChildren().addAll(backBtn, refreshBtn);
        headerBox.getChildren().addAll(titleBox, spacer, actions);
        return headerBox;
    }

    private HBox createFeedbackBanner() {
        HBox banner = new HBox(12);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setVisible(false);
        banner.setManaged(false);

        feedbackText = new Label();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        dismissFeedbackBtn = new Button("✕");
        dismissFeedbackBtn.getStyleClass().add("secondary-btn");
        dismissFeedbackBtn.setStyle("-fx-padding: 2px 8px; -fx-font-size: 11px;");
        dismissFeedbackBtn.setOnAction(e -> hideFeedback());

        banner.getChildren().addAll(feedbackText, spacer, dismissFeedbackBtn);
        return banner;
    }

    private HBox createKpiSummaryBar() {
        HBox strip = new HBox(16);
        strip.setAlignment(Pos.CENTER_LEFT);

        VBox totalCard = createKpiCard("Total Feedback", totalFeedbackLabel = new Label("—"), "#6366F1");
        VBox avgCard = createKpiCard("Campus Average Rating", avgRatingLabel = new Label("—"), "#F59E0B");
        VBox fiveStarCard = createKpiCard("5-Star Ratings", fiveStarCountLabel = new Label("—"), "#22C55E");
        VBox eventsCard = createKpiCard("Events Reviewed", eventsReviewedLabel = new Label("—"), "#8B5CF6");

        HBox.setHgrow(totalCard, Priority.ALWAYS);
        HBox.setHgrow(avgCard, Priority.ALWAYS);
        HBox.setHgrow(fiveStarCard, Priority.ALWAYS);
        HBox.setHgrow(eventsCard, Priority.ALWAYS);

        strip.getChildren().addAll(totalCard, avgCard, fiveStarCard, eventsCard);
        return strip;
    }

    private VBox createKpiCard(String title, Label valueLabel, String accentColor) {
        VBox card = new VBox(4);
        card.getStyleClass().add("kpi-card");
        card.setPrefWidth(210);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px; -fx-font-weight: 600;");

        valueLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 20px; -fx-font-weight: 800;");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private VBox createFilterToolbar() {
        VBox barBox = new VBox(10);
        barBox.getStyleClass().add("filter-bar-card");
        barBox.setStyle("-fx-background-color: #14132A; -fx-padding: 14px 18px; -fx-border-color: rgba(255, 255, 255, 0.08); -fx-border-radius: 12px; -fx-background-radius: 12px;");

        // Row 1: Search field and Event selector ComboBox
        HBox row1 = new HBox(12);
        row1.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Search student, event, or review keyword...");
        searchField.getStyleClass().add("text-input");
        searchField.setPrefWidth(350);
        searchField.setMinWidth(200);
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFiltersAndRender());

        eventFilterCombo = new ComboBox<>();
        eventFilterCombo.setPrefWidth(260);
        eventFilterCombo.setMinWidth(160);
        eventFilterCombo.setPromptText("Filter by Event");
        eventFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(EventFilterItem item) {
                return item != null ? item.getEventName() : "All Events";
            }

            @Override
            public EventFilterItem fromString(String string) {
                return null;
            }
        });
        eventFilterCombo.setOnAction(e -> applyFiltersAndRender());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        resultsCountLabel = new Label("0 reviews");
        resultsCountLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px; -fx-font-weight: 600;");

        row1.getChildren().addAll(searchField, eventFilterCombo, spacer, resultsCountLabel);

        // Row 2: Star rating filter buttons
        HBox row2 = new HBox(10);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label ratingFilterLabel = new Label("Rating:");
        ratingFilterLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px; -fx-font-weight: 600;");

        filterAllRatingBtn = createRatingFilterButton("All Ratings", 0);
        filter5RatingBtn = createRatingFilterButton("★★★★★ 5 Stars", 5);
        filter4RatingBtn = createRatingFilterButton("★★★★☆ 4 Stars", 4);
        filter3RatingBtn = createRatingFilterButton("★★★☆☆ 3 Stars", 3);
        filterLowRatingBtn = createRatingFilterButton("★ / ★★ Low (1-2)", -1);

        updateRatingFilterStyles();

        row2.getChildren().addAll(ratingFilterLabel, filterAllRatingBtn, filter5RatingBtn, filter4RatingBtn, filter3RatingBtn, filterLowRatingBtn);

        barBox.getChildren().addAll(row1, row2);
        return barBox;
    }

    private Button createRatingFilterButton(String title, int ratingVal) {
        Button btn = new Button(title);
        btn.setOnAction(e -> {
            selectedRatingFilter = ratingVal;
            updateRatingFilterStyles();
            applyFiltersAndRender();
        });
        return btn;
    }

    private void updateRatingFilterStyles() {
        setBtnState(filterAllRatingBtn, selectedRatingFilter == 0);
        setBtnState(filter5RatingBtn, selectedRatingFilter == 5);
        setBtnState(filter4RatingBtn, selectedRatingFilter == 4);
        setBtnState(filter3RatingBtn, selectedRatingFilter == 3);
        setBtnState(filterLowRatingBtn, selectedRatingFilter == -1);
    }

    private void setBtnState(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("secondary-btn", "primary-btn");
        if (active) {
            btn.getStyleClass().add("primary-btn");
        } else {
            btn.getStyleClass().add("secondary-btn");
        }
    }

    /**
     * Loads campus feedback and events list asynchronously.
     */
    public void loadInitialData() {
        User user = Session.getCurrentUser();
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            showFeedback("Administrator authorization required to access feedback management.", false);
            return;
        }

        renderLoadingState();

        new Thread(() -> {
            boolean dbOnline = DBConnection.testConnection();
            List<AdminFeedbackDTO> feedbackList = new ArrayList<>();
            List<Event> eventList = new ArrayList<>();
            FeedbackSummaryDTO summary = FeedbackSummaryDTO.offline();

            if (dbOnline) {
                feedbackList = feedbackService.getAllFeedbackForAdmin();
                eventList = eventDAO.getAll();
                summary = feedbackService.getFeedbackSummary();
            }

            final List<AdminFeedbackDTO> finalFeedback = feedbackList;
            final List<Event> finalEvents = eventList;
            final FeedbackSummaryDTO finalSummary = summary;

            Platform.runLater(() -> {
                cachedFeedback.clear();
                cachedFeedback.addAll(finalFeedback);

                cachedEvents.clear();
                cachedEvents.addAll(finalEvents);

                // Populate event combo
                EventFilterItem selected = eventFilterCombo.getValue();
                eventFilterCombo.getItems().clear();
                eventFilterCombo.getItems().add(new EventFilterItem(0, "All Events (Campus-wide)"));
                for (Event ev : finalEvents) {
                    eventFilterCombo.getItems().add(new EventFilterItem(ev.getEventId(), ev.getEventName()));
                }
                eventFilterCombo.getSelectionModel().select(0);

                if (!dbOnline) {
                    totalFeedbackLabel.setText("—");
                    avgRatingLabel.setText("—");
                    fiveStarCountLabel.setText("—");
                    eventsReviewedLabel.setText("—");
                    showFeedback("Database is offline. Connect to MySQL to view verified campus feedback.", false);
                    renderOfflineState();
                } else {
                    totalFeedbackLabel.setText(String.valueOf(finalSummary.getTotalFeedback()));
                    avgRatingLabel.setText(finalSummary.getFormattedAverageRating() + " ★");
                    fiveStarCountLabel.setText(String.valueOf(finalSummary.getFiveStarCount()));
                    eventsReviewedLabel.setText(String.valueOf(finalSummary.getEventsReviewedCount()));

                    applyFiltersAndRender();
                }
            });
        }).start();
    }

    private void renderLoadingState() {
        feedbackListContainer.getChildren().clear();
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 20, 40, 20));

        ProgressBar pb = new ProgressBar();
        pb.setPrefWidth(220);

        Label lbl = new Label("Loading campus feedback submissions...");
        lbl.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 13px;");

        box.getChildren().addAll(pb, lbl);
        feedbackListContainer.getChildren().add(box);
    }

    private void renderOfflineState() {
        feedbackListContainer.getChildren().clear();
        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label("Database Offline");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label("Unable to connect to the MySQL database. Campus feedback submissions are currently unreachable.");
        sub.getStyleClass().add("empty-state-sub");

        Button retryBtn = new Button("↻ Retry Connection");
        retryBtn.getStyleClass().add("secondary-btn");
        retryBtn.setOnAction(e -> loadInitialData());

        box.getChildren().addAll(title, sub, retryBtn);
        feedbackListContainer.getChildren().add(box);
    }

    private void applyFiltersAndRender() {
        feedbackListContainer.getChildren().clear();

        EventFilterItem selectedEvent = eventFilterCombo.getValue();
        int eventIdFilter = (selectedEvent != null) ? selectedEvent.getEventId() : 0;
        String query = (searchField != null && searchField.getText() != null) ? searchField.getText().trim().toLowerCase() : "";

        List<AdminFeedbackDTO> filtered = cachedFeedback.stream()
                .filter(f -> {
                    // Filter by event ID
                    if (eventIdFilter > 0 && f.getEventId() != eventIdFilter) {
                        return false;
                    }

                    // Filter by rating
                    if (selectedRatingFilter == -1) {
                        if (f.getRating() > 2) return false;
                    } else if (selectedRatingFilter > 0) {
                        if (f.getRating() != selectedRatingFilter) return false;
                    }

                    // Search query
                    if (!query.isEmpty()) {
                        boolean matchesEvent = f.getEventName().toLowerCase().contains(query);
                        boolean matchesStudent = f.getStudentName().toLowerCase().contains(query);
                        boolean matchesEmail = f.getStudentEmail().toLowerCase().contains(query);
                        boolean matchesComment = f.getComment().toLowerCase().contains(query);
                        boolean matchesClub = f.getClubName().toLowerCase().contains(query);
                        if (!matchesEvent && !matchesStudent && !matchesEmail && !matchesComment && !matchesClub) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        resultsCountLabel.setText("Showing " + filtered.size() + " of " + cachedFeedback.size() + " reviews");

        if (filtered.isEmpty()) {
            renderEmptyState();
            return;
        }

        for (AdminFeedbackDTO dto : filtered) {
            feedbackListContainer.getChildren().add(createAdminFeedbackCard(dto));
        }
    }

    private void renderEmptyState() {
        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        Label title = new Label(cachedFeedback.isEmpty()
                ? "No feedback submitted yet"
                : "No reviews match your current filters");
        title.getStyleClass().add("empty-state-title");

        Label sub = new Label(cachedFeedback.isEmpty()
                ? "When students complete event feedback, their ratings and reviews will appear here."
                : "Try clearing your search query or selecting a different rating filter.");
        sub.getStyleClass().add("empty-state-sub");

        box.getChildren().addAll(title, sub);
        feedbackListContainer.getChildren().add(box);
    }

    /**
     * Builds an individual feedback card for the administrator overview.
     */
    private VBox createAdminFeedbackCard(AdminFeedbackDTO dto) {
        VBox card = new VBox(10);
        card.getStyleClass().add("feedback-card");

        // Row 1: Event Name, Club, and Rating Pill
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label eventTitle = new Label(dto.getEventName());
        eventTitle.getStyleClass().add("event-title");
        eventTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox ratingBox = new HBox(6);
        ratingBox.setAlignment(Pos.CENTER_RIGHT);

        Label starsLabel = new Label(dto.getStarString());
        starsLabel.getStyleClass().add("star-label-gold");

        Label scorePill = new Label(dto.getRating() + ".0 / 5.0");
        scorePill.getStyleClass().add("star-score-pill");

        ratingBox.getChildren().addAll(starsLabel, scorePill);
        topRow.getChildren().addAll(eventTitle, spacer, ratingBox);

        // Row 2: Club, category, date, student metadata
        HBox metaRow = new HBox(10);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label clubLabel = new Label("🏛  " + dto.getClubName());
        clubLabel.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 12px; -fx-font-weight: 600;");

        Label studentLabel = new Label("•  👤  " + dto.getStudentName() + " (" + dto.getStudentEmail() + ")");
        studentLabel.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12px; -fx-font-weight: 600;");

        Label dateLabel = new Label("•  🕒  " + dto.getFormattedSubmittedAt());
        dateLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px;");

        metaRow.getChildren().addAll(clubLabel, studentLabel, dateLabel);

        // Row 3: Comment quote box
        VBox quoteBox = new VBox(6);
        quoteBox.getStyleClass().add("feedback-quote-box");

        HBox quoteTop = new HBox();
        quoteTop.setAlignment(Pos.CENTER_LEFT);

        Label quoteTitle = new Label("Student Review");
        quoteTitle.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11px; -fx-font-weight: 600;");

        Region quoteSpacer = new Region();
        HBox.setHgrow(quoteSpacer, Priority.ALWAYS);

        Button readMoreBtn = new Button("Open Full Review ↗");
        readMoreBtn.getStyleClass().add("secondary-btn");
        readMoreBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2px 8px;");
        readMoreBtn.setOnAction(e -> openCommentViewerModal(dto));

        quoteTop.getChildren().addAll(quoteTitle, quoteSpacer, readMoreBtn);

        String comment = dto.getComment();
        if (comment == null || comment.isBlank()) {
            comment = "No written comments submitted. Numerical rating only.";
        }

        Label commentLabel = new Label("“" + comment + "”");
        commentLabel.getStyleClass().add("feedback-comment-text");
        commentLabel.setWrapText(true);

        quoteBox.getChildren().addAll(quoteTop, commentLabel);

        card.getChildren().addAll(topRow, metaRow, quoteBox);
        return card;
    }

    /**
     * Opens modal dialog displaying complete comment without truncation.
     */
    private void openCommentViewerModal(AdminFeedbackDTO dto) {
        modalOverlay.getChildren().clear();

        VBox dialog = new VBox(18);
        dialog.getStyleClass().add("feedback-modal-dialog");

        // Header
        VBox dialogHeader = new VBox(4);
        Label headerTitle = new Label("Student Feedback Details");
        headerTitle.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 20px; -fx-font-weight: 800;");

        Label headerSub = new Label(dto.getEventName() + "  •  " + dto.getClubName());
        headerSub.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 13px; -fx-font-weight: 600;");

        dialogHeader.getChildren().addAll(headerTitle, headerSub);

        // Metadata grid/box
        VBox metaBox = new VBox(8);
        metaBox.setStyle("-fx-background-color: rgba(11, 10, 26, 0.7); -fx-padding: 12px 14px; -fx-border-color: rgba(255, 255, 255, 0.08); -fx-border-radius: 8px; -fx-background-radius: 8px;");

        HBox r1 = new HBox(8);
        r1.getChildren().addAll(
                createDetailItem("Student", dto.getStudentName()),
                createDetailItem("Email", dto.getStudentEmail())
        );

        HBox r2 = new HBox(8);
        r2.getChildren().addAll(
                createDetailItem("Rating", dto.getStarString() + " (" + dto.getRating() + "/5)"),
                createDetailItem("Submitted", dto.getFormattedSubmittedAt())
        );

        metaBox.getChildren().addAll(r1, r2);

        // Full comment section
        VBox commentBox = new VBox(6);
        Label commentTitle = new Label("COMPLETE WRITTEN REVIEW");
        commentTitle.getStyleClass().add("form-label");

        ScrollPane commentScroll = new ScrollPane();
        commentScroll.setFitToWidth(true);
        commentScroll.setStyle("-fx-background-color: transparent; -fx-border-color: rgba(255, 255, 255, 0.1); -fx-border-radius: 8px; -fx-background-radius: 8px;");
        commentScroll.setPrefHeight(140);

        Label fullCommentLabel = new Label(dto.getComment().isBlank() ? "No written comments submitted. (Numerical rating only)" : dto.getComment());
        fullCommentLabel.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 13px; -fx-line-spacing: 4px; -fx-padding: 12px;");
        fullCommentLabel.setWrapText(true);
        commentScroll.setContent(fullCommentLabel);

        commentBox.getChildren().addAll(commentTitle, commentScroll);

        // Actions
        HBox actions = new HBox();
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("primary-btn");
        closeBtn.setOnAction(e -> closeModal());
        actions.getChildren().add(closeBtn);

        dialog.getChildren().addAll(dialogHeader, metaBox, commentBox, actions);
        modalOverlay.getChildren().add(dialog);
        modalOverlay.setVisible(true);
    }

    private VBox createDetailItem(String label, String value) {
        VBox box = new VBox(2);
        box.setPrefWidth(220);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11px;");

        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12.5px; -fx-font-weight: 600;");

        box.getChildren().addAll(lbl, val);
        return box;
    }

    private void closeModal() {
        modalOverlay.setVisible(false);
        modalOverlay.getChildren().clear();
    }

    private void showFeedback(String msg, boolean isSuccess) {
        feedbackText.setText(msg);
        feedbackBanner.getStyleClass().removeAll("success-banner", "error-banner");
        feedbackText.getStyleClass().removeAll("success-banner-text", "error-text");

        if (isSuccess) {
            feedbackBanner.getStyleClass().add("success-banner");
            feedbackText.getStyleClass().add("success-banner-text");
        } else {
            feedbackBanner.getStyleClass().add("error-banner");
            feedbackText.getStyleClass().add("error-text");
        }

        feedbackBanner.setVisible(true);
        feedbackBanner.setManaged(true);
    }

    private void hideFeedback() {
        feedbackBanner.setVisible(false);
        feedbackBanner.setManaged(false);
    }
}
