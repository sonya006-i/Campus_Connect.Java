package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.StudentFeedbackDTO;
import model.User;
import service.FeedbackManagementService;
import service.FeedbackManagementService.OperationResult;
import util.DBConnection;
import util.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * StudentFeedbackController — Student feedback & ratings interface.
 *
 * Implements Phase 13 per AGENTS.md:
 * - Scoped strictly to the authenticated student (derived from Session)
 * - Displays events eligible for feedback (APPROVED registration + PRESENT attendance)
 * - Interactive 5-star rating selector with live rating feedback
 * - Optional written review comments with character count and validation
 * - Clear distinction between submitted reviews and events awaiting feedback
 * - Duplicate prevention pre-check and confirmation feedback
 * - Graceful loading, empty, validation, and offline states
 * - Non-blocking asynchronous database loading
 */
public class StudentFeedbackController {

    private final FeedbackManagementService feedbackService;

    private StackPane rootContainer;
    private ScrollPane mainScrollPane;
    private StackPane modalOverlay;
    private VBox feedbackListContainer;

    // Metric Labels
    private Label attendedCountLabel;
    private Label submittedCountLabel;
    private Label pendingCountLabel;

    // Filter Buttons
    private Button filterAllBtn;
    private Button filterPendingBtn;
    private Button filterSubmittedBtn;
    private String activeFilter = "ALL"; // "ALL", "PENDING", "SUBMITTED"

    // Banner notifications
    private HBox feedbackBanner;
    private Label feedbackText;
    private Button dismissFeedbackBtn;

    // Cached feedback items
    private final List<StudentFeedbackDTO> cachedItems = new ArrayList<>();

    // Navigation callbacks
    private Runnable onNavigateDashboard;
    private Runnable onExploreEvents;

    public StudentFeedbackController() {
        this.feedbackService = new FeedbackManagementService();
        buildView();
    }

    public void setOnNavigateDashboard(Runnable onNavigateDashboard) {
        this.onNavigateDashboard = onNavigateDashboard;
    }

    public void setOnExploreEvents(Runnable onExploreEvents) {
        this.onExploreEvents = onExploreEvents;
    }

    public Parent getView() {
        return rootContainer;
    }

    private void buildView() {
        rootContainer = new StackPane();
        rootContainer.getStyleClass().add("root");

        // 1. Main Scrollable Container
        mainScrollPane = new ScrollPane();
        mainScrollPane.getStyleClass().add("main-scroll-pane");
        mainScrollPane.setFitToWidth(true);

        VBox contentBox = new VBox(22);
        contentBox.getStyleClass().add("main-content");

        // Top Header
        HBox headerBox = createHeader();

        // Notification Banner
        feedbackBanner = createFeedbackBanner();

        // Metric Strip
        HBox metricsStrip = createMetricsStrip();

        // Filter Toolbar
        HBox filterToolbar = createFilterToolbar();

        // Event List Container
        feedbackListContainer = new VBox(14);

        contentBox.getChildren().addAll(headerBox, feedbackBanner, metricsStrip, filterToolbar, feedbackListContainer);
        mainScrollPane.setContent(contentBox);

        // 2. Modal Overlay for Rating Dialog
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
        Label titleLabel = new Label("Event Feedback & Ratings");
        titleLabel.getStyleClass().add("header-title");

        Label subtitleLabel = new Label("Share your experience, rate mentors, and help improve campus activities you attended.");
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

    private HBox createMetricsStrip() {
        HBox strip = new HBox(16);
        strip.setAlignment(Pos.CENTER_LEFT);

        VBox attendedCard = createMetricCard("Attended Events", attendedCountLabel = new Label("—"), "#6366F1");
        VBox submittedCard = createMetricCard("Feedback Submitted", submittedCountLabel = new Label("—"), "#22C55E");
        VBox pendingCard = createMetricCard("Awaiting Your Rating", pendingCountLabel = new Label("—"), "#F59E0B");

        strip.getChildren().addAll(attendedCard, submittedCard, pendingCard);
        return strip;
    }

    private VBox createMetricCard(String title, Label valueLabel, String accentColor) {
        VBox card = new VBox(4);
        card.getStyleClass().add("kpi-card");
        card.setPrefWidth(220);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px; -fx-font-weight: 600;");

        valueLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 20px; -fx-font-weight: 800;");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private HBox createFilterToolbar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);

        filterAllBtn = createFilterButton("All Attended Events", "ALL");
        filterPendingBtn = createFilterButton("Awaiting Feedback", "PENDING");
        filterSubmittedBtn = createFilterButton("Submitted Reviews", "SUBMITTED");

        updateFilterButtonStyles();

        bar.getChildren().addAll(filterAllBtn, filterPendingBtn, filterSubmittedBtn);
        return bar;
    }

    private Button createFilterButton(String text, String filterKey) {
        Button btn = new Button(text);
        btn.setOnAction(e -> {
            activeFilter = filterKey;
            updateFilterButtonStyles();
            renderList();
        });
        return btn;
    }

    private void updateFilterButtonStyles() {
        setFilterBtnState(filterAllBtn, "ALL".equals(activeFilter));
        setFilterBtnState(filterPendingBtn, "PENDING".equals(activeFilter));
        setFilterBtnState(filterSubmittedBtn, "SUBMITTED".equals(activeFilter));
    }

    private void setFilterBtnState(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("secondary-btn", "primary-btn");
        if (active) {
            btn.getStyleClass().add("primary-btn");
        } else {
            btn.getStyleClass().add("secondary-btn");
        }
    }

    /**
     * Loads student feedback events asynchronously off the JavaFX application thread.
     */
    public void loadInitialData() {
        User user = Session.getCurrentUser();
        if (user == null || !"STUDENT".equalsIgnoreCase(user.getRole())) {
            showFeedback("Please sign in as a student to access feedback.", false);
            return;
        }

        renderLoadingState();

        new Thread(() -> {
            boolean dbOnline = DBConnection.testConnection();
            List<StudentFeedbackDTO> list;

            if (!dbOnline) {
                list = new ArrayList<>();
            } else {
                list = feedbackService.getMyFeedbackEvents();
            }

            Platform.runLater(() -> {
                cachedItems.clear();
                cachedItems.addAll(list);

                if (!dbOnline) {
                    attendedCountLabel.setText("—");
                    submittedCountLabel.setText("—");
                    pendingCountLabel.setText("—");
                    showFeedback("MySQL database is offline. Connect to MySQL to view and submit feedback.", false);
                    renderOfflineState();
                } else {
                    int totalAttended = list.size();
                    int submitted = (int) list.stream().filter(StudentFeedbackDTO::isFeedbackSubmitted).count();
                    int pending = totalAttended - submitted;

                    attendedCountLabel.setText(String.valueOf(totalAttended));
                    submittedCountLabel.setText(String.valueOf(submitted));
                    pendingCountLabel.setText(String.valueOf(pending));

                    renderList();
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
        pb.setPrefWidth(200);

        Label lbl = new Label("Loading your event feedback records...");
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

        Label sub = new Label("Unable to connect to the MySQL database. Feedback records are currently unreachable.");
        sub.getStyleClass().add("empty-state-sub");

        Button retryBtn = new Button("↻ Retry Connection");
        retryBtn.getStyleClass().add("secondary-btn");
        retryBtn.setOnAction(e -> loadInitialData());

        box.getChildren().addAll(title, sub, retryBtn);
        feedbackListContainer.getChildren().add(box);
    }

    private void renderList() {
        feedbackListContainer.getChildren().clear();

        List<StudentFeedbackDTO> filtered = cachedItems.stream()
                .filter(item -> {
                    if ("PENDING".equals(activeFilter)) {
                        return !item.isFeedbackSubmitted();
                    } else if ("SUBMITTED".equals(activeFilter)) {
                        return item.isFeedbackSubmitted();
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            renderEmptyState();
            return;
        }

        for (StudentFeedbackDTO dto : filtered) {
            feedbackListContainer.getChildren().add(createFeedbackItemCard(dto));
        }
    }

    private void renderEmptyState() {
        VBox box = new VBox(12);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        String title;
        String sub;

        if (cachedItems.isEmpty()) {
            title = "No eligible events found for feedback";
            sub = "Feedback is unlocked once you attend campus events with verified presence.\nExplore upcoming events to participate and share your reviews!";
        } else if ("PENDING".equals(activeFilter)) {
            title = "All caught up!";
            sub = "You have submitted feedback for all attended events. Thank you for helping improve campus clubs!";
        } else {
            title = "No feedback submitted yet";
            sub = "Switch to 'Awaiting Feedback' to share your experience on events you attended.";
        }

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");

        Label subLabel = new Label(sub);
        subLabel.getStyleClass().add("empty-state-sub");
        subLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        box.getChildren().addAll(titleLabel, subLabel);

        if (cachedItems.isEmpty() && onExploreEvents != null) {
            Button exploreBtn = new Button("Explore Events →");
            exploreBtn.getStyleClass().add("primary-btn");
            exploreBtn.setOnAction(e -> onExploreEvents.run());
            box.getChildren().add(exploreBtn);
        }

        feedbackListContainer.getChildren().add(box);
    }

    /**
     * Builds an event feedback card for a student.
     */
    private VBox createFeedbackItemCard(StudentFeedbackDTO dto) {
        VBox card = new VBox(12);
        card.getStyleClass().add("feedback-card");

        // Top line: Event Name and Status Badge
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(dto.getEventName());
        titleLabel.getStyleClass().add("event-title");
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (dto.isFeedbackSubmitted()) {
            HBox submittedBadgeBox = new HBox(8);
            submittedBadgeBox.setAlignment(Pos.CENTER_RIGHT);

            Label starsLabel = new Label(dto.getStarString() + "  " + dto.getRating() + "/5");
            starsLabel.getStyleClass().add("star-label-gold");

            Label statusBadge = new Label("✔ Submitted");
            statusBadge.getStyleClass().add("badge-success");

            submittedBadgeBox.getChildren().addAll(starsLabel, statusBadge);
            topRow.getChildren().addAll(titleLabel, spacer, submittedBadgeBox);
        } else {
            Label statusBadge = new Label("Pending Your Review");
            statusBadge.getStyleClass().add("badge-purple");

            Button rateBtn = new Button("★ Rate Event");
            rateBtn.getStyleClass().add("primary-btn");
            rateBtn.setStyle("-fx-padding: 5px 14px; -fx-font-size: 12px;");
            rateBtn.setOnAction(e -> openRatingDialog(dto));

            topRow.getChildren().addAll(titleLabel, spacer, statusBadge, rateBtn);
        }

        // Meta row: Club, category, date, venue
        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label clubLabel = new Label("🏛  " + dto.getClubName());
        clubLabel.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 12px; -fx-font-weight: 600;");

        Label catLabel = new Label("•  " + dto.getCategory());
        catLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");

        Label dateLabel = new Label("•  📅  " + dto.getFormattedEventDate() + "  📍  " + dto.getVenue());
        dateLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 12px;");

        metaRow.getChildren().addAll(clubLabel, catLabel, dateLabel);

        card.getChildren().addAll(topRow, metaRow);

        // If feedback has been submitted, display the student's review quote box
        if (dto.isFeedbackSubmitted()) {
            VBox quoteBox = new VBox(6);
            quoteBox.getStyleClass().add("feedback-quote-box");

            HBox quoteHeader = new HBox(8);
            quoteHeader.setAlignment(Pos.CENTER_LEFT);

            Label quoteTitle = new Label("Your Review");
            quoteTitle.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12px; -fx-font-weight: 700;");

            Region quoteSpacer = new Region();
            HBox.setHgrow(quoteSpacer, Priority.ALWAYS);

            Label timestampLabel = new Label("Submitted: " + dto.getFormattedSubmittedAt());
            timestampLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11px;");

            quoteHeader.getChildren().addAll(quoteTitle, quoteSpacer, timestampLabel);

            Label commentText = new Label(dto.getComment().isBlank() ? "No written comment provided." : "“" + dto.getComment() + "”");
            commentText.getStyleClass().add("feedback-comment-text");
            commentText.setWrapText(true);

            quoteBox.getChildren().addAll(quoteHeader, commentText);
            card.getChildren().add(quoteBox);
        } else {
            // Helper message encouraging review
            Label promptLabel = new Label("You attended this event with verified presence. Share your feedback to help organizers improve future workshops.");
            promptLabel.setStyle("-fx-text-fill: #68677A; -fx-font-size: 11.5px; -fx-font-style: italic;");
            card.getChildren().add(promptLabel);
        }

        return card;
    }

    /**
     * Opens the interactive modal dialog for rating and writing feedback.
     */
    private void openRatingDialog(StudentFeedbackDTO dto) {
        modalOverlay.getChildren().clear();

        VBox dialog = new VBox(18);
        dialog.getStyleClass().add("feedback-modal-dialog");

        // Header
        VBox dialogHeader = new VBox(4);
        Label headerTitle = new Label("Rate & Review Event");
        headerTitle.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 20px; -fx-font-weight: 800;");

        Label headerSub = new Label(dto.getEventName() + "  •  " + dto.getClubName());
        headerSub.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 13px; -fx-font-weight: 600;");

        dialogHeader.getChildren().addAll(headerTitle, headerSub);

        // Dialog Error / Validation Banner
        HBox dialogBanner = new HBox();
        dialogBanner.setVisible(false);
        dialogBanner.setManaged(false);
        dialogBanner.getStyleClass().add("error-banner");
        Label dialogBannerText = new Label();
        dialogBannerText.getStyleClass().add("error-text");
        dialogBanner.getChildren().add(dialogBannerText);

        // Rating Section
        VBox ratingSection = new VBox(8);
        Label ratingLabel = new Label("YOUR RATING *");
        ratingLabel.getStyleClass().add("form-label");

        HBox starBox = new HBox(8);
        starBox.setAlignment(Pos.CENTER_LEFT);

        Label ratingDescLabel = new Label("5 - Excellent");
        ratingDescLabel.setStyle("-fx-text-fill: #F59E0B; -fx-font-size: 13px; -fx-font-weight: 700;");

        // Array of 5 star buttons
        final int[] selectedRating = {5};
        List<Button> starButtons = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            final int starValue = i;
            Button starBtn = new Button("★");
            starBtn.getStyleClass().add("star-btn");

            starBtn.setOnAction(e -> {
                selectedRating[0] = starValue;
                updateStarButtonStyles(starButtons, starValue);
                ratingDescLabel.setText(getRatingDescription(starValue));
            });

            starButtons.add(starBtn);
            starBox.getChildren().add(starBtn);
        }

        Region starSpacer = new Region();
        HBox.setHgrow(starSpacer, Priority.ALWAYS);
        starBox.getChildren().addAll(starSpacer, ratingDescLabel);

        // Initial default to 5 stars
        updateStarButtonStyles(starButtons, 5);

        ratingSection.getChildren().addAll(ratingLabel, starBox);

        // Comments Section
        VBox commentSection = new VBox(6);
        Label commentLabel = new Label("WRITTEN COMMENTS & SUGGESTIONS (OPTIONAL)");
        commentLabel.getStyleClass().add("form-label");

        TextArea commentArea = new TextArea();
        commentArea.setPromptText("What did you enjoy most? How could the organizers improve the schedule, venue, or presentation?");
        commentArea.setPrefRowCount(4);
        commentArea.setWrapText(true);

        Label charCountLabel = new Label("0 / 2000 characters");
        charCountLabel.setStyle("-fx-text-fill: #68677A; -fx-font-size: 11px;");
        charCountLabel.setAlignment(Pos.CENTER_RIGHT);

        commentArea.textProperty().addListener((obs, oldV, newV) -> {
            int len = (newV != null) ? newV.length() : 0;
            charCountLabel.setText(len + " / 2000 characters");
            if (len > 2000) {
                charCountLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 11px; -fx-font-weight: bold;");
            } else {
                charCountLabel.setStyle("-fx-text-fill: #68677A; -fx-font-size: 11px;");
            }
        });

        commentSection.getChildren().addAll(commentLabel, commentArea, charCountLabel);

        // Buttons
        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button submitBtn = new Button("Submit Feedback ★");
        submitBtn.getStyleClass().add("primary-btn");

        submitBtn.setOnAction(e -> {
            int rating = selectedRating[0];
            String comment = commentArea.getText();

            if (comment != null && comment.length() > 2000) {
                dialogBannerText.setText("Comments cannot exceed 2000 characters.");
                dialogBanner.setVisible(true);
                dialogBanner.setManaged(true);
                return;
            }

            submitBtn.setDisable(true);
            cancelBtn.setDisable(true);
            submitBtn.setText("Submitting...");

            new Thread(() -> {
                OperationResult result = feedbackService.submitFeedback(dto.getEventId(), rating, comment);

                Platform.runLater(() -> {
                    submitBtn.setDisable(false);
                    cancelBtn.setDisable(false);
                    submitBtn.setText("Submit Feedback ★");

                    if (result.isSuccess()) {
                        closeModal();
                        showFeedback("✔ " + result.getMessage(), true);
                        loadInitialData();
                    } else {
                        dialogBannerText.setText(result.getMessage());
                        dialogBanner.setVisible(true);
                        dialogBanner.setManaged(true);
                    }
                });
            }).start();
        });

        actionRow.getChildren().addAll(cancelBtn, submitBtn);

        dialog.getChildren().addAll(dialogHeader, dialogBanner, ratingSection, commentSection, actionRow);
        modalOverlay.getChildren().add(dialog);
        modalOverlay.setVisible(true);
    }

    private void updateStarButtonStyles(List<Button> buttons, int activeRating) {
        for (int i = 0; i < buttons.size(); i++) {
            Button btn = buttons.get(i);
            btn.getStyleClass().removeAll("star-btn", "star-btn-active");
            if (i < activeRating) {
                btn.getStyleClass().add("star-btn-active");
            } else {
                btn.getStyleClass().add("star-btn");
            }
        }
    }

    private String getRatingDescription(int rating) {
        return switch (rating) {
            case 5 -> "5 - Excellent ★★★★★";
            case 4 -> "4 - Very Good ★★★★☆";
            case 3 -> "3 - Good ★★★☆☆";
            case 2 -> "2 - Fair ★★☆☆☆";
            case 1 -> "1 - Poor ★☆☆☆☆";
            default -> rating + " Stars";
        };
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
