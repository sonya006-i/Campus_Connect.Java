package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.StudentCertificateDTO;
import model.User;
import service.CertificateManagementService;
import util.DBConnection;
import util.Session;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * MyCertificatesController — Student "My Certificates" view.
 *
 * Implements Phase 12 per AGENTS.md:
 * - Scoped strictly to the authenticated student (derived from Session)
 * - Views all certificates issued to the current student
 * - Displays certificate ID, event title, event date, issue date, club, and verification ID
 * - Interactive, formal on-screen JavaFX certificate preview modal
 * - Handles loading, empty, offline, and error states gracefully
 * - Non-blocking asynchronous database loading
 */
public class MyCertificatesController {

    private final CertificateManagementService certificateService;

    private StackPane rootContainer;
    private ScrollPane mainScrollPane;
    private StackPane modalOverlay;
    private VBox certificatesContainer;

    // Metric Labels
    private Label totalCertificatesLabel;
    private Label latestIssuedLabel;
    private Label clubsCountLabel;

    // Banner notifications
    private HBox feedbackBanner;
    private Label feedbackText;
    private Button dismissFeedbackBtn;

    // Cached certificates
    private final List<StudentCertificateDTO> cachedCertificates = new ArrayList<>();

    // Formatters
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Navigation callbacks
    private Runnable onNavigateDashboard;
    private Runnable onExploreEvents;

    public MyCertificatesController() {
        this.certificateService = new CertificateManagementService();
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

        // Header
        HBox headerBox = createHeader();

        // Notification Banner
        feedbackBanner = createFeedbackBanner();

        // Metric Strip
        HBox metricsStrip = createMetricsStrip();

        // Certificates List Container
        certificatesContainer = new VBox(14);

        contentBox.getChildren().addAll(headerBox, feedbackBanner, metricsStrip, certificatesContainer);
        mainScrollPane.setContent(contentBox);

        // 2. Modal Overlay for Certificate Preview
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
        Label titleLabel = new Label("My Certificates");
        titleLabel.getStyleClass().add("header-title");

        Label subtitleLabel = new Label("Verified event participation credentials and academic club achievements.");
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

        VBox totalCard = createMetricCard("Certificates Earned", totalCertificatesLabel = new Label("—"), "#F59E0B");
        VBox latestCard = createMetricCard("Latest Issued", latestIssuedLabel = new Label("—"), "#6366F1");
        VBox clubsCard = createMetricCard("Clubs Represented", clubsCountLabel = new Label("—"), "#10B981");

        strip.getChildren().addAll(totalCard, latestCard, clubsCard);
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

    /**
     * Loads certificates for currently authenticated student asynchronously.
     */
    public void loadInitialData() {
        showLoadingState();

        Thread loadThread = new Thread(() -> {
            boolean dbOnline = DBConnection.testConnection();
            List<StudentCertificateDTO> certs = certificateService.getMyCertificates();

            Platform.runLater(() -> {
                cachedCertificates.clear();
                cachedCertificates.addAll(certs);

                if (!dbOnline && certs.isEmpty()) {
                    showOfflineState();
                } else {
                    renderCertificates(cachedCertificates);
                    updateMetrics(cachedCertificates);
                }
            });
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void updateMetrics(List<StudentCertificateDTO> certs) {
        totalCertificatesLabel.setText(String.valueOf(certs.size()));

        if (certs.isEmpty()) {
            latestIssuedLabel.setText("None yet");
            clubsCountLabel.setText("0");
            return;
        }

        // Latest issued date
        String latest = certs.stream()
                .filter(c -> c.getGeneratedDate() != null)
                .map(c -> c.getGeneratedDate().format(shortDateFormatter))
                .findFirst()
                .orElse("Recent");
        latestIssuedLabel.setText(latest);

        // Distinct clubs
        long distinctClubs = certs.stream()
                .map(StudentCertificateDTO::getClubName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .count();
        clubsCountLabel.setText(String.valueOf(distinctClubs));
    }

    private void renderCertificates(List<StudentCertificateDTO> certs) {
        certificatesContainer.getChildren().clear();

        if (certs.isEmpty()) {
            showEmptyState();
            return;
        }

        Label sectionHeader = new Label("Issued Credentials (" + certs.size() + ")");
        sectionHeader.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 15px; -fx-font-weight: 700;");
        certificatesContainer.getChildren().add(sectionHeader);

        for (StudentCertificateDTO cert : certs) {
            certificatesContainer.getChildren().add(createCertificateCard(cert));
        }
    }

    private VBox createCertificateCard(StudentCertificateDTO cert) {
        VBox card = new VBox(10);
        card.getStyleClass().add("dashboard-card");
        card.setStyle(card.getStyle() + "; -fx-border-color: rgba(245, 158, 11, 0.2); -fx-border-width: 1px;");

        // Top Row: Certificate Number & Verification Badge
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label certNumberLabel = new Label(cert.getFormattedCertId());
        certNumberLabel.setStyle("-fx-text-fill: #F59E0B; -fx-font-size: 13px; -fx-font-weight: 800; -fx-letter-spacing: 1px;");

        Label badge = new Label("Verified Credential");
        badge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #F59E0B; -fx-padding: 3px 10px; -fx-background-radius: 12px; -fx-font-size: 11px; -fx-font-weight: 600;");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Label dateBadge = new Label("Issued " + (cert.getGeneratedDate() != null ? cert.getGeneratedDate().format(shortDateFormatter) : "N/A"));
        dateBadge.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px;");

        topRow.getChildren().addAll(certNumberLabel, badge, topSpacer, dateBadge);

        // Middle: Event Title & Club
        VBox midBox = new VBox(4);
        Label eventTitle = new Label(cert.getEventName());
        eventTitle.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 16px; -fx-font-weight: 800;");

        String meta = (cert.getClubName() != null ? cert.getClubName() : "Campus Event")
                + (cert.getCategory() != null ? "  •  " + cert.getCategory() : "");
        Label clubLabel = new Label("🏛 " + meta);
        clubLabel.setStyle("-fx-text-fill: #6366F1; -fx-font-size: 12px; -fx-font-weight: 600;");

        midBox.getChildren().addAll(eventTitle, clubLabel);

        // Bottom Row: Event date, verification code & Preview Button
        HBox bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        String eventDateStr = cert.getEventDate() != null ? cert.getEventDate().format(shortDateFormatter) : "TBD";
        Label eventDateLabel = new Label("📅 Event Date: " + eventDateStr);
        eventDateLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px;");

        Label codeLabel = new Label("🔑 Code: " + cert.getVerificationCode());
        codeLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11.5px; -fx-font-family: monospace;");

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        Button previewBtn = new Button("📜 View Certificate");
        previewBtn.getStyleClass().add("gold-btn-solid");
        previewBtn.setOnAction(e -> openCertificatePreview(cert));

        bottomRow.getChildren().addAll(eventDateLabel, codeLabel, bottomSpacer, previewBtn);

        card.getChildren().addAll(topRow, midBox, bottomRow);
        return card;
    }

    /**
     * Opens a formal, polished JavaFX Certificate Preview dialog.
     */
    public void openCertificatePreview(StudentCertificateDTO cert) {
        if (cert == null) return;

        VBox certBox = new VBox(18);
        certBox.getStyleClass().add("certificate-preview-dialog");
        certBox.setAlignment(Pos.CENTER);

        // Top University Header
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

        // Recipient Section
        VBox recipientBox = new VBox(6);
        recipientBox.setAlignment(Pos.CENTER);

        Label presentedTo = new Label("PROUDLY PRESENTED TO");
        presentedTo.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 11.5px; -fx-letter-spacing: 2px; -fx-font-weight: 700;");

        Label studentName = new Label(cert.getStudentName());
        studentName.getStyleClass().add("certificate-recipient");

        Label studentEmail = new Label(cert.getStudentEmail() != null ? cert.getStudentEmail() : "");
        studentEmail.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px;");

        recipientBox.getChildren().addAll(presentedTo, studentName, studentEmail);

        // Event Recognition Section
        VBox eventBox = new VBox(6);
        eventBox.setAlignment(Pos.CENTER);

        Label inRecognitionOf = new Label("for successfully attending and participating in the campus event");
        inRecognitionOf.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 13px;");

        Label eventName = new Label(cert.getEventName());
        eventName.setStyle("-fx-text-fill: #F59E0B; -fx-font-size: 18px; -fx-font-weight: 800;");

        String eventMeta = "Organized by " + (cert.getClubName() != null ? cert.getClubName() : "Campus Club")
                + "  •  " + (cert.getEventDate() != null ? cert.getEventDate().format(dateFormatter) : "Date TBD")
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
        Label dateVal = new Label(cert.getGeneratedDate() != null ? cert.getGeneratedDate().format(dateFormatter) : "N/A");
        dateVal.setStyle("-fx-text-fill: #F5F5F7; -fx-font-size: 12px; -fx-font-weight: 700;");
        dateBox.getChildren().addAll(dateTitle, dateVal);

        footerRow.getChildren().addAll(certIdBox, sealBox, dateBox);

        // Verification Code Note
        Label verifyNote = new Label("Verification Identifier: " + cert.getVerificationCode() + "  •  Database Record Validated");
        verifyNote.setStyle("-fx-text-fill: #475569; -fx-font-size: 10.5px; -fx-font-family: monospace;");

        // Modal Action Buttons
        HBox buttonRow = new HBox(12);
        buttonRow.setAlignment(Pos.CENTER);

        Button closeBtn = new Button("Close Preview");
        closeBtn.getStyleClass().add("primary-btn");
        closeBtn.setOnAction(e -> closeModal());

        buttonRow.getChildren().add(closeBtn);

        certBox.getChildren().addAll(
                topBranding, sep1, recipientBox, eventBox, sep2, footerRow, verifyNote, buttonRow
        );

        modalOverlay.getChildren().clear();
        modalOverlay.getChildren().add(certBox);
        modalOverlay.setVisible(true);
    }

    private void closeModal() {
        modalOverlay.getChildren().clear();
        modalOverlay.setVisible(false);
    }

    private void showLoadingState() {
        certificatesContainer.getChildren().clear();
        VBox loadingBox = new VBox(10);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPadding(new Insets(40, 20, 40, 20));

        Label loadingLabel = new Label("Loading your certificates...");
        loadingLabel.setStyle("-fx-text-fill: #9695A8; -fx-font-size: 14px;");

        loadingBox.getChildren().add(loadingLabel);
        certificatesContainer.getChildren().add(loadingBox);
    }

    private void showEmptyState() {
        certificatesContainer.getChildren().clear();
        VBox emptyBox = new VBox(12);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.getStyleClass().add("empty-state-box");
        emptyBox.setPadding(new Insets(40, 20, 40, 20));

        Label emptyIcon = new Label("📜");
        emptyIcon.setStyle("-fx-font-size: 32px;");

        Label emptyTitle = new Label("No Certificates Issued Yet");
        emptyTitle.getStyleClass().add("empty-state-title");

        Label emptySub = new Label("When you register, attend campus events, and have your attendance verified by club admins, your official certificates will appear here.");
        emptySub.getStyleClass().add("empty-state-sub");
        emptySub.setMaxWidth(480);
        emptySub.setWrapText(true);

        Button exploreBtn = new Button("Explore Events →");
        exploreBtn.getStyleClass().add("primary-btn");
        exploreBtn.setOnAction(e -> {
            if (onExploreEvents != null) onExploreEvents.run();
        });

        emptyBox.getChildren().addAll(emptyIcon, emptyTitle, emptySub, exploreBtn);
        certificatesContainer.getChildren().add(emptyBox);
    }

    private void showOfflineState() {
        certificatesContainer.getChildren().clear();
        VBox offlineBox = new VBox(10);
        offlineBox.setAlignment(Pos.CENTER);
        offlineBox.getStyleClass().add("empty-state-box");
        offlineBox.setPadding(new Insets(40, 20, 40, 20));

        Label offlineTitle = new Label("Database Offline");
        offlineTitle.getStyleClass().add("empty-state-title");

        Label offlineSub = new Label("Unable to reach the campus database. Certificates cannot be loaded right now.");
        offlineSub.getStyleClass().add("empty-state-sub");

        offlineBox.getChildren().addAll(offlineTitle, offlineSub);
        certificatesContainer.getChildren().add(offlineBox);
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
}
