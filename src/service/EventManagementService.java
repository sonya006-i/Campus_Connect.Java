package service;

import dao.ClubDAO;
import dao.EventDAO;
import dao.RegistrationDAO;
import model.Club;
import model.Event;
import util.DBConnection;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * EventManagementService — Business logic layer for administrator & club event management.
 *
 * Implements Phase 9: Event Management per AGENTS.md:
 * - Robust input validation pipeline for event creation and modification
 * - Safe atomic CRUD operations via EventDAO
 * - Offline-resilient handling and clean status messages
 * - Querying, searching, and filtering of campus events and clubs
 */
public class EventManagementService {

    private final EventDAO eventDAO;
    private final ClubDAO clubDAO;
    private final RegistrationDAO registrationDAO;

    public EventManagementService() {
        this.eventDAO = new EventDAO();
        this.clubDAO = new ClubDAO();
        this.registrationDAO = new RegistrationDAO();
    }

    public EventManagementService(EventDAO eventDAO, ClubDAO clubDAO, RegistrationDAO registrationDAO) {
        this.eventDAO = eventDAO;
        this.clubDAO = clubDAO;
        this.registrationDAO = registrationDAO;
    }

    /**
     * DTO representing the outcome of an event operation (create, update, delete).
     */
    public static class OperationResult {
        private final boolean success;
        private final String message;
        private final Event event;

        public OperationResult(boolean success, String message, Event event) {
            this.success = success;
            this.message = message;
            this.event = event;
        }

        public static OperationResult success(String message, Event event) {
            return new OperationResult(true, message, event);
        }

        public static OperationResult failure(String message) {
            return new OperationResult(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Event getEvent() {
            return event;
        }
    }

    /**
     * DTO representing the validation status of event fields.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Validates event fields according to business and database rules.
     */
    public ValidationResult validate(Event event) {
        if (event == null) {
            return ValidationResult.invalid("Event data cannot be null.");
        }

        // 1. Event Name validation
        if (event.getEventName() == null || event.getEventName().trim().isEmpty()) {
            return ValidationResult.invalid("Event title cannot be blank.");
        }
        String trimmedName = event.getEventName().trim();
        if (trimmedName.length() < 3 || trimmedName.length() > 150) {
            return ValidationResult.invalid("Event title must be between 3 and 150 characters.");
        }

        // 2. Organizing Club validation
        if (event.getClubId() <= 0) {
            return ValidationResult.invalid("Please select an organizing club.");
        }

        // 3. Category validation
        if (event.getCategory() == null || event.getCategory().trim().isEmpty()) {
            return ValidationResult.invalid("Please specify an event category.");
        }
        if (event.getCategory().trim().length() > 50) {
            return ValidationResult.invalid("Event category must not exceed 50 characters.");
        }

        // 4. Date and Deadline validation
        LocalDate eventDate = event.getEventDate();
        if (eventDate == null) {
            return ValidationResult.invalid("Event schedule date is required.");
        }

        LocalDate regDeadline = event.getRegistrationDeadline();
        if (regDeadline == null) {
            return ValidationResult.invalid("Registration deadline is required.");
        }

        if (regDeadline.isAfter(eventDate)) {
            return ValidationResult.invalid("Registration deadline cannot be after the event date.");
        }

        // 5. Venue validation
        if (event.getVenue() == null || event.getVenue().trim().isEmpty()) {
            return ValidationResult.invalid("Event venue / location is required.");
        }
        if (event.getVenue().trim().length() > 150) {
            return ValidationResult.invalid("Venue location must not exceed 150 characters.");
        }

        // 6. Max participants capacity validation
        if (event.getMaxParticipants() <= 0) {
            return ValidationResult.invalid("Maximum participant capacity must be at least 1.");
        }

        return ValidationResult.valid();
    }

    /**
     * Creates a new event in the system after full validation.
     */
    public OperationResult createEvent(Event event) {
        ValidationResult validation = validate(event);
        if (!validation.isValid()) {
            return OperationResult.failure(validation.getErrorMessage());
        }

        if (!DBConnection.testConnection()) {
            return OperationResult.failure("Database is offline. Unable to create event.");
        }

        int generatedId = eventDAO.insert(event);
        if (generatedId > 0) {
            return OperationResult.success("Event created successfully! (ID: #" + generatedId + ")", event);
        } else {
            return OperationResult.failure("Failed to save event to MySQL database.");
        }
    }

    /**
     * Updates an existing event after validation.
     */
    public OperationResult updateEvent(Event event) {
        if (event == null || event.getEventId() <= 0) {
            return OperationResult.failure("Invalid event identifier for update.");
        }

        ValidationResult validation = validate(event);
        if (!validation.isValid()) {
            return OperationResult.failure(validation.getErrorMessage());
        }

        if (!DBConnection.testConnection()) {
            return OperationResult.failure("Database is offline. Unable to update event.");
        }

        boolean updated = eventDAO.update(event);
        if (updated) {
            return OperationResult.success("Event '" + event.getEventName() + "' updated successfully.", event);
        } else {
            return OperationResult.failure("Failed to update event in database. The event may no longer exist.");
        }
    }

    /**
     * Safely deletes an event by its ID:
     * - Checks whether dependent records exist; if so, refuses deletion and returns an actionable error message
     * - Preserves all participant registrations, attendance, feedback, and certificate records
     * - Deletes only from 'events' table if no dependent records exist
     * - Handles foreign-key constraint violations gracefully
     */
    public OperationResult deleteEvent(int eventId) {
        if (eventId <= 0) {
            return OperationResult.failure("Invalid event identifier for deletion.");
        }

        if (!DBConnection.testConnection()) {
            return OperationResult.failure("Database is offline. Unable to delete event.");
        }

        // 1. Explicit check: verify whether dependent records prevent safe deletion
        EventDAO.DependentSummary summary = eventDAO.getDependentSummary(eventId);
        if (summary.hasDependencies()) {
            return OperationResult.failure("Cannot delete event: " + summary.toDescription()
                    + " exist. Deleting events with existing participant records is prohibited to preserve data integrity.");
        }

        // 2. Perform safe deletion (deletes only the event row, handles FK violations gracefully)
        EventDAO.DeletionOutcome outcome = eventDAO.deleteEvent(eventId);
        if (outcome.isSuccess()) {
            return OperationResult.success("Event successfully deleted.", null);
        } else {
            return OperationResult.failure(outcome.getMessage());
        }
    }

    /**
     * Retrieves the dependent records summary for an event.
     */
    public EventDAO.DependentSummary getDependentSummary(int eventId) {
        if (!DBConnection.testConnection()) {
            return EventDAO.DependentSummary.empty();
        }
        return eventDAO.getDependentSummary(eventId);
    }

    /**
     * Retrieves all events in the database.
     */
    public List<Event> getAllEvents() {
        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }
        return eventDAO.getAll();
    }

    /**
     * Searches and filters events by keyword, category, and club ID.
     */
    public List<Event> searchEvents(String keyword, String category, Integer clubId) {
        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }
        return eventDAO.searchAndFilter(keyword, category, clubId);
    }

    /**
     * Retrieves all registered clubs on campus.
     */
    public List<Club> getAllClubs() {
        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }
        return clubDAO.getAll();
    }

    /**
     * Retrieves distinct categories currently in use.
     */
    public List<String> getCategories() {
        if (!DBConnection.testConnection()) {
            return Collections.emptyList();
        }
        return eventDAO.getDistinctCategories();
    }

    /**
     * Retrieves the count of student registrations for an event.
     */
    public int getRegistrationCount(int eventId) {
        if (!DBConnection.testConnection()) {
            return 0;
        }
        return registrationDAO.countTotalForEvent(eventId);
    }
}
