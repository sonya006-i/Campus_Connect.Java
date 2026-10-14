package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
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
import model.AnalyticsOverviewDTO;
import model.CategoryDistributionDTO;
import model.ClubEngagementDTO;
import model.EventPerformanceDTO;
import model.MonthlyTrendDTO;
import service.AnalyticsService;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AdminAnalyticsController — Visual intelligence dashboard with live JavaFX charts,
 * KPI telemetry, club engagement metrics, and event performance tables.
 *
 * Implements Phase 14: Analytics per AGENTS.md:
 * - Real-time aggregate charts (Monthly Trends AreaChart, Status Breakdown PieChart, Club BarChart).
 * - Full support for Loading, Empty, and Offline database states.
 * - Adheres strictly to the Cyber SaaS dark theme aesthetic.
 */
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    // View Components
    private StackPane rootContainer;
    private ScrollPane mainScrollPane;
    private VBox contentBox;

    // Header & Status
    private Label headerSubtitleLabel;
    private Label portalBadgeLabel;
    private Circle portalStatusDot;
    private Button refreshBtn;

    // Banner
    private HBox bannerBox;
    private Label bannerText;
    private Button dismissBannerBtn;

    // KPI Summary Labels
    private Label kpiRegsValue;
    private Label kpiRegsSub;
    private Label kpiRegsBadge;

    private Label kpiTurnoutValue;
    private Label kpiTurnoutSub;
    private Label kpiTurnoutBadge;

    private Label kpiCertValue;
    private Label kpiCertSub;
    private Label kpiCertBadge;

    private Label kpiRatingValue;
    private Label kpiRatingSub;
    private Label kpiRatingBadge;

    // Dynamic Chart Containers & Charts
    private VBox trendsChartCard;
    private VBox statusPieCard;
    private VBox clubBarCard;
    private VBox categoryBarCard;
    private VBox eventRosterCard;

    // Navigation callback
    private Runnable onNavigateDashboard;

    public AdminAnalyticsController() {
        this(new AnalyticsService());
    }

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = (analyticsService != null) ? analyticsService : new AnalyticsService();
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

        contentBox = new VBox(22);
        contentBox.getStyleClass().add("main-content");
        contentBox.setPadding(new Insets(24, 32, 32, 32));

        // 1. Header
        HBox headerBox = createHeader();

        // 2. Offline/Alert Banner
        bannerBox = createBanner();

        // 3. KPI Summary Bar (4 Cards)
        HBox kpiBar = createKpiSummaryBar();

        // 4. Middle Row: Monthly Trend AreaChart (65%) + Registration Status PieChart (35%)
        HBox row1 = new HBox(18);
        row1.setAlignment(Pos.TOP_LEFT);

        trendsChartCard = new VBox(12);
        trendsChartCard.getStyleClass().add("dashboard-card");
        HBox.setHgrow(trendsChartCard, Priority.ALWAYS);
        trendsChartCard.prefWidthProperty().bind(row1.widthProperty().multiply(0.62));

        statusPieCard = new VBox(12);
        statusPieCard.getStyleClass().add("dashboard-card");
        HBox.setHgrow(statusPieCard, Priority.ALWAYS);
        statusPieCard.prefWidthProperty().bind(row1.widthProperty().multiply(0.38));

        row1.getChildren().addAll(trendsChartCard, statusPieCard);

        // 5. Secondary Row: Club Engagement BarChart + Category Distribution
        HBox row2 = new HBox(18);
        row2.setAlignment(Pos.TOP_LEFT);

        clubBarCard = new VBox(12);
        clubBarCard.getStyleClass().add("dashboard-card");
        HBox.setHgrow(clubBarCard, Priority.ALWAYS);
        clubBarCard.prefWidthProperty().bind(row2.widthProperty().multiply(0.50));

        categoryBarCard = new VBox(12);
        categoryBarCard.getStyleClass().add("dashboard-card");
        HBox.setHgrow(categoryBarCard, Priority.ALWAYS);
        categoryBarCard.prefWidthProperty().bind(row2.widthProperty().multiply(0.50));

        row2.getChildren().addAll(clubBarCard, categoryBarCard);

        // 6. Bottom Row: Event Performance Roster
        eventRosterCard = new VBox(14);
        eventRosterCard.getStyleClass().add("dashboard-card");

        contentBox.getChildren().addAll(headerBox, bannerBox, kpiBar, row1, row2, eventRosterCard);
        mainScrollPane.setContent(contentBox);
        rootContainer.getChildren().add(mainScrollPane);
    }

    private HBox createHeader() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label titleLabel = new Label("Campus Analytics & Intelligence");
        titleLabel.getStyleClass().add("header-title");

        headerSubtitleLabel = new Label("Live participant turnout, conversion funnels, and club performance metrics");
        headerSubtitleLabel.getStyleClass().add("header-subtitle");

        titleBox.getChildren().addAll(titleLabel, headerSubtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button backBtn = new Button("← Dashboard");
        backBtn.getStyleClass().add("secondary-btn");
        backBtn.setOnAction(e -> {
            if (onNavigateDashboard != null) {
                onNavigateDashboard.run();
            }
        });

        refreshBtn = new Button("↻  Refresh Analytics");
        refreshBtn.getStyleClass().add("secondary-btn");
        refreshBtn.setOnAction(e -> loadInitialData());

        HBox statusPill = new HBox(8);
        statusPill.setAlignment(Pos.CENTER);
        statusPill.getStyleClass().add("status-pill");

        portalStatusDot = new Circle(4);
        portalStatusDot.setStyle("-fx-fill: #22C55E;");

        portalBadgeLabel = new Label("Telemetry Live");
        portalBadgeLabel.getStyleClass().add("status-pill-text");

        statusPill.getChildren().addAll(portalStatusDot, portalBadgeLabel);
        actions.getChildren().addAll(backBtn, refreshBtn, statusPill);

        header.getChildren().addAll(titleBox, spacer, actions);
        return header;
    }

    private HBox createBanner() {
        HBox banner = new HBox(12);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setVisible(false);
        banner.setManaged(false);

        bannerText = new Label();
        bannerText.setStyle("-fx-text-fill: #F59E0B; -fx-font-size: 13px; -fx-font-weight: 600;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        dismissBannerBtn = new Button("✕");
        dismissBannerBtn.getStyleClass().add("secondary-btn");
        dismissBannerBtn.setStyle("-fx-padding: 2px 8px; -fx-font-size: 11px;");
        dismissBannerBtn.setOnAction(e -> hideBanner());

        banner.getChildren().addAll(bannerText, spacer, dismissBannerBtn);
        return banner;
    }

    private void showBanner(String message, boolean isError) {
        bannerText.setText(message);
        bannerBox.setStyle(isError
                ? "-fx-background-color: rgba(244, 63, 94, 0.12); -fx-border-color: rgba(244, 63, 94, 0.4); -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 10px 14px;"
                : "-fx-background-color: rgba(245, 158, 11, 0.12); -fx-border-color: rgba(245, 158, 11, 0.4); -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 10px 14px;");
        bannerBox.setVisible(true);
        bannerBox.setManaged(true);
    }

    private void hideBanner() {
        bannerBox.setVisible(false);
        bannerBox.setManaged(false);
    }

    private HBox createKpiSummaryBar() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);

        // 1. Total Registrations
        kpiRegsValue = new Label("—");
        kpiRegsSub = new Label("Total submissions");
        kpiRegsBadge = new Label("Registrations");
        kpiRegsBadge.getStyleClass().add("badge-indigo");
        VBox card1 = buildKpiCard("Registration Volume", kpiRegsValue, kpiRegsSub, kpiRegsBadge);

        // 2. Turnout Rate
        kpiTurnoutValue = new Label("—");
        kpiTurnoutSub = new Label("Attended vs Approved");
        kpiTurnoutBadge = new Label("Turnout");
        kpiTurnoutBadge.getStyleClass().add("badge-success");
        VBox card2 = buildKpiCard("Turnout Rate", kpiTurnoutValue, kpiTurnoutSub, kpiTurnoutBadge);

        // 3. Certificate Issuance Rate
        kpiCertValue = new Label("—");
        kpiCertSub = new Label("Issued vs Attended");
        kpiCertBadge = new Label("Certificates");
        kpiCertBadge.getStyleClass().add("badge-purple");
        VBox card3 = buildKpiCard("Issuance Rate", kpiCertValue, kpiCertSub, kpiCertBadge);

        // 4. Feedback Rating
        kpiRatingValue = new Label("—");
        kpiRatingSub = new Label("Campus satisfaction");
        kpiRatingBadge = new Label("Feedback");
        kpiRatingBadge.getStyleClass().add("badge-warning");
        VBox card4 = buildKpiCard("Student Rating", kpiRatingValue, kpiRatingSub, kpiRatingBadge);

        HBox.setHgrow(card1, Priority.ALWAYS);
        HBox.setHgrow(card2, Priority.ALWAYS);
        HBox.setHgrow(card3, Priority.ALWAYS);
        HBox.setHgrow(card4, Priority.ALWAYS);

        row.getChildren().addAll(card1, card2, card3, card4);
        return row;
    }

    private VBox buildKpiCard(String title, Label valueLabel, Label subtextLabel, Label badge) {
        VBox card = new VBox(8);
        card.getStyleClass().add("metric-card");

        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topRow.getChildren().addAll(titleLabel, spacer, badge);

        valueLabel.getStyleClass().add("metric-value");
        subtextLabel.getStyleClass().add("metric-subtext");

        card.getChildren().addAll(topRow, valueLabel, subtextLabel);
        return card;
    }

    /**
     * Loads live analytics data and updates all charts and metric cards.
     */
    public void loadInitialData() {
        if (!analyticsService.isAuthorized()) {
            showBanner("Access Denied: Administrator credentials required for campus analytics.", true);
            renderOfflineState();
            return;
        }

        AnalyticsOverviewDTO overview = analyticsService.getOverviewMetrics();
        if (!overview.isDbOnline()) {
            showBanner("Database Offline: Analytics server is unreachable. Showing offline indicators.", false);
            portalBadgeLabel.setText("Database Offline");
            portalStatusDot.setStyle("-fx-fill: #F59E0B;");
            renderOfflineState();
            return;
        }

        hideBanner();
        portalBadgeLabel.setText("Live Telemetry");
        portalStatusDot.setStyle("-fx-fill: #22C55E;");

        // 1. Update KPI Cards
        updateKpiCards(overview);

        // 2. Render Monthly Trends
        List<MonthlyTrendDTO> trends = analyticsService.getMonthlyParticipationTrends();
        renderMonthlyTrendsChart(trends);

        // 3. Render Status PieChart
        Map<String, Integer> statusCounts = analyticsService.getRegistrationStatusCounts();
        renderStatusPieChart(statusCounts);

        // 4. Render Club Engagement BarChart
        List<ClubEngagementDTO> clubs = analyticsService.getClubEngagementMetrics();
        renderClubEngagementChart(clubs);

        // 5. Render Category Distribution BarChart
        List<CategoryDistributionDTO> categories = analyticsService.getCategoryDistribution();
        renderCategoryDistributionChart(categories);

        // 6. Render Event Performance Roster
        List<EventPerformanceDTO> events = analyticsService.getEventPerformanceMetrics();
        renderEventPerformanceRoster(events);
    }

    private void updateKpiCards(AnalyticsOverviewDTO overview) {
        // Registrations
        kpiRegsValue.setText(String.valueOf(overview.getTotalRegistrations()));
        kpiRegsSub.setText(String.format(Locale.US, "%.1f%% Approved (%d of %d)",
                overview.getApprovalRate(), overview.getApprovedRegistrations(), overview.getTotalRegistrations()));

        // Turnout Rate
        kpiTurnoutValue.setText(String.format(Locale.US, "%.1f%%", overview.getTurnoutRate()));
        kpiTurnoutSub.setText(overview.getTotalAttended() + " attended of " + overview.getApprovedRegistrations() + " approved");

        // Certificate Issuance Rate
        kpiCertValue.setText(String.format(Locale.US, "%.1f%%", overview.getCertificateIssuanceRate()));
        kpiCertSub.setText(overview.getTotalCertificatesIssued() + " certificates issued");

        // Student Rating
        kpiRatingValue.setText(overview.getFormattedAverageRating());
        kpiRatingSub.setText(overview.getTotalFeedbackCount() + " verified reviews submitted");
    }

    private void renderMonthlyTrendsChart(List<MonthlyTrendDTO> trends) {
        trendsChartCard.getChildren().clear();

        VBox headerText = new VBox(3);
        Label title = new Label("Monthly Participation & Turnout Trends");
        title.getStyleClass().add("card-title");

        Label subtitle = new Label("Historical student registrations compared with verified event attendance");
        subtitle.getStyleClass().add("card-subtitle");
        headerText.getChildren().addAll(title, subtitle);

        trendsChartCard.getChildren().add(headerText);

        if (trends == null || trends.isEmpty()) {
            trendsChartCard.getChildren().add(createEmptyStateBox("No monthly participation records available yet."));
            return;
        }

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(true);

        AreaChart<String, Number> areaChart = new AreaChart<>(xAxis, yAxis);
        areaChart.setPrefHeight(240);
        areaChart.setAnimated(false);
        areaChart.setLegendVisible(true);

        XYChart.Series<String, Number> regSeries = new XYChart.Series<>();
        regSeries.setName("Total Registrations");

        XYChart.Series<String, Number> attSeries = new XYChart.Series<>();
        attSeries.setName("Verified Attendance");

        for (MonthlyTrendDTO item : trends) {
            regSeries.getData().add(new XYChart.Data<>(item.getMonthLabel(), item.getRegistrationCount()));
            attSeries.getData().add(new XYChart.Data<>(item.getMonthLabel(), item.getAttendanceCount()));
        }

        areaChart.getData().add(regSeries);
        areaChart.getData().add(attSeries);
        trendsChartCard.getChildren().add(areaChart);
    }

    private void renderStatusPieChart(Map<String, Integer> statusCounts) {
        statusPieCard.getChildren().clear();

        VBox headerText = new VBox(3);
        Label title = new Label("Registration Status Breakdown");
        title.getStyleClass().add("card-title");

        Label subtitle = new Label("Proportion of Approved, Pending, and Rejected submissions");
        subtitle.getStyleClass().add("card-subtitle");
        headerText.getChildren().addAll(title, subtitle);

        statusPieCard.getChildren().add(headerText);

        int total = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            statusPieCard.getChildren().add(createEmptyStateBox("No student registrations submitted yet."));
            return;
        }

        PieChart pieChart = new PieChart();
        pieChart.setPrefHeight(240);
        pieChart.setAnimated(false);
        pieChart.setLegendVisible(true);

        int approved = statusCounts.getOrDefault("APPROVED", 0);
        int pending = statusCounts.getOrDefault("PENDING", 0);
        int rejected = statusCounts.getOrDefault("REJECTED", 0);

        if (approved > 0) pieChart.getData().add(new PieChart.Data("Approved (" + approved + ")", approved));
        if (pending > 0) pieChart.getData().add(new PieChart.Data("Pending (" + pending + ")", pending));
        if (rejected > 0) pieChart.getData().add(new PieChart.Data("Rejected (" + rejected + ")", rejected));

        statusPieCard.getChildren().add(pieChart);
    }

    private void renderClubEngagementChart(List<ClubEngagementDTO> clubs) {
        clubBarCard.getChildren().clear();

        VBox headerText = new VBox(3);
        Label title = new Label("Club Engagement & Volumes");
        title.getStyleClass().add("card-title");

        Label subtitle = new Label("Total student registrations and verified attendance across campus clubs");
        subtitle.getStyleClass().add("card-subtitle");
        headerText.getChildren().addAll(title, subtitle);

        clubBarCard.getChildren().add(headerText);

        if (clubs == null || clubs.isEmpty()) {
            clubBarCard.getChildren().add(createEmptyStateBox("No campus club data recorded."));
            return;
        }

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(true);

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setPrefHeight(230);
        barChart.setAnimated(false);
        barChart.setLegendVisible(true);

        XYChart.Series<String, Number> regSeries = new XYChart.Series<>();
        regSeries.setName("Registrations");

        XYChart.Series<String, Number> attSeries = new XYChart.Series<>();
        attSeries.setName("Attended");

        // Show top 6 clubs
        int count = 0;
        for (ClubEngagementDTO club : clubs) {
            if (++count > 6) break;
            regSeries.getData().add(new XYChart.Data<>(club.getClubName(), club.getTotalRegistrations()));
            attSeries.getData().add(new XYChart.Data<>(club.getClubName(), club.getTotalAttended()));
        }

        barChart.getData().add(regSeries);
        barChart.getData().add(attSeries);
        clubBarCard.getChildren().add(barChart);
    }

    private void renderCategoryDistributionChart(List<CategoryDistributionDTO> categories) {
        categoryBarCard.getChildren().clear();

        VBox headerText = new VBox(3);
        Label title = new Label("Category Distribution");
        title.getStyleClass().add("card-title");

        Label subtitle = new Label("Volume of events and student interest by event genre");
        subtitle.getStyleClass().add("card-subtitle");
        headerText.getChildren().addAll(title, subtitle);

        categoryBarCard.getChildren().add(headerText);

        if (categories == null || categories.isEmpty()) {
            categoryBarCard.getChildren().add(createEmptyStateBox("No event categories cataloged."));
            return;
        }

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(true);

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setPrefHeight(230);
        barChart.setAnimated(false);
        barChart.setLegendVisible(true);

        XYChart.Series<String, Number> eventSeries = new XYChart.Series<>();
        eventSeries.setName("Events Hosted");

        XYChart.Series<String, Number> regSeries = new XYChart.Series<>();
        regSeries.setName("Registrations");

        for (CategoryDistributionDTO cat : categories) {
            eventSeries.getData().add(new XYChart.Data<>(cat.getCategory(), cat.getEventCount()));
            regSeries.getData().add(new XYChart.Data<>(cat.getCategory(), cat.getTotalRegistrations()));
        }

        barChart.getData().add(eventSeries);
        barChart.getData().add(regSeries);
        categoryBarCard.getChildren().add(barChart);
    }

    private void renderEventPerformanceRoster(List<EventPerformanceDTO> events) {
        eventRosterCard.getChildren().clear();

        VBox headerText = new VBox(3);
        Label title = new Label("Event Performance Roster");
        title.getStyleClass().add("card-title");

        Label subtitle = new Label("Detailed conversion funnels, turnout percentage, and review ratings per event");
        subtitle.getStyleClass().add("card-subtitle");
        headerText.getChildren().addAll(title, subtitle);

        eventRosterCard.getChildren().add(headerText);

        if (events == null || events.isEmpty()) {
            eventRosterCard.getChildren().add(createEmptyStateBox("No events have been hosted on campus yet."));
            return;
        }

        VBox listContainer = new VBox(10);
        listContainer.setPadding(new Insets(10, 0, 0, 0));

        for (EventPerformanceDTO ev : events) {
            listContainer.getChildren().add(createEventPerformanceRow(ev));
        }

        eventRosterCard.getChildren().add(listContainer);
    }

    private HBox createEventPerformanceRow(EventPerformanceDTO ev) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #14132A; -fx-padding: 12px 18px; -fx-background-radius: 10px; -fx-border-color: rgba(255, 255, 255, 0.06); -fx-border-radius: 10px;");

        // Event info
        VBox titleCol = new VBox(3);
        titleCol.setPrefWidth(240);
        Label nameLabel = new Label(ev.getEventName());
        nameLabel.setStyle("-fx-text-fill: #F5F5F7; -fx-font-weight: 700; -fx-font-size: 13.5px;");

        Label metaLabel = new Label(ev.getClubName() + " • " + ev.getCategory()
                + ((ev.getEventDate() != null) ? " • " + ev.getEventDate() : ""));
        metaLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px;");
        titleCol.getChildren().addAll(nameLabel, metaLabel);

        // Funnel stats
        VBox funnelCol = new VBox(3);
        funnelCol.setPrefWidth(160);
        Label regLabel = new Label("Regs: " + ev.getTotalRegistrations() + " (" + ev.getApprovedRegistrations() + " approved)");
        regLabel.setStyle("-fx-text-fill: #E2E8F0; -fx-font-size: 12px;");

        Label capLabel = new Label("Cap: " + (ev.getCapacity() > 0 ? ev.getCapacity() : "∞")
                + String.format(Locale.US, " (%.0f%% full)", ev.getCapacityUtilizationRate()));
        capLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11px;");
        funnelCol.getChildren().addAll(regLabel, capLabel);

        // Turnout Rate Progress Bar
        VBox turnoutCol = new VBox(4);
        turnoutCol.setPrefWidth(160);
        HBox turnoutHeader = new HBox();
        Label turnoutTitle = new Label("Turnout");
        turnoutTitle.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label turnoutVal = new Label(String.format(Locale.US, "%.0f%% (%d)", ev.getTurnoutRate(), ev.getTotalAttended()));
        turnoutVal.setStyle("-fx-text-fill: #22C55E; -fx-font-weight: 700; -fx-font-size: 11.5px;");
        turnoutHeader.getChildren().addAll(turnoutTitle, spacer, turnoutVal);

        ProgressBar turnoutBar = new ProgressBar(ev.getTurnoutRate() / 100.0);
        turnoutBar.setMaxWidth(Double.MAX_VALUE);
        turnoutBar.setStyle("-fx-accent: #22C55E;");
        turnoutCol.getChildren().addAll(turnoutHeader, turnoutBar);

        // Rating Col
        VBox ratingCol = new VBox(3);
        ratingCol.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(ratingCol, Priority.ALWAYS);

        Label ratingLabel = new Label(ev.getFormattedRating());
        ratingLabel.setStyle("-fx-text-fill: #F59E0B; -fx-font-weight: 700; -fx-font-size: 13px;");

        Label reviewCountLabel = new Label(ev.getFeedbackCount() == 1 ? "1 student review" : ev.getFeedbackCount() + " student reviews");
        reviewCountLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11px;");
        ratingCol.getChildren().addAll(ratingLabel, reviewCountLabel);

        row.getChildren().addAll(titleCol, funnelCol, turnoutCol, ratingCol);
        return row;
    }

    private VBox createEmptyStateBox(String message) {
        VBox emptyBox = new VBox(8);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setPadding(new Insets(30, 20, 30, 20));
        emptyBox.setStyle("-fx-background-color: rgba(20, 19, 42, 0.6); -fx-background-radius: 10px; -fx-border-color: rgba(255, 255, 255, 0.05); -fx-border-radius: 10px;");

        Label title = new Label("No Data Recorded");
        title.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 13px; -fx-font-weight: 700;");

        Label sub = new Label(message);
        sub.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px;");

        emptyBox.getChildren().addAll(title, sub);
        return emptyBox;
    }

    private void renderOfflineState() {
        kpiRegsValue.setText("—");
        kpiRegsSub.setText("Database unavailable");

        kpiTurnoutValue.setText("—");
        kpiTurnoutSub.setText("Database unavailable");

        kpiCertValue.setText("—");
        kpiCertSub.setText("Database unavailable");

        kpiRatingValue.setText("—");
        kpiRatingSub.setText("Database unavailable");

        trendsChartCard.getChildren().setAll(createEmptyStateBox("Offline: Participation trends cannot be loaded."));
        statusPieCard.getChildren().setAll(createEmptyStateBox("Offline: Status distribution cannot be loaded."));
        clubBarCard.getChildren().setAll(createEmptyStateBox("Offline: Club engagement cannot be loaded."));
        categoryBarCard.getChildren().setAll(createEmptyStateBox("Offline: Category distribution cannot be loaded."));
        eventRosterCard.getChildren().setAll(createEmptyStateBox("Offline: Event performance roster cannot be loaded."));
    }
}
