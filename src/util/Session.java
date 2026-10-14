package util;

import model.User;

/**
 * Session — In-memory session manager for Campus Connect.
 *
 * Tracks the currently authenticated user across the application lifecycle.
 * Simple, thread-safe, and zero extraneous dependencies.
 */
public class Session {

    private static User currentUser = null;

    private Session() {
    }

    /**
     * Stores the currently authenticated user in session.
     */
    public static synchronized void setCurrentUser(User user) {
        currentUser = user;
    }

    /**
     * Retrieves the currently authenticated user.
     *
     * @return current User instance, or null if unauthenticated
     */
    public static synchronized User getCurrentUser() {
        return currentUser;
    }

    /**
     * Clears the active session upon logout.
     */
    public static synchronized void clearSession() {
        currentUser = null;
    }

    /**
     * Checks if a user is currently logged in.
     */
    public static synchronized boolean isLoggedIn() {
        return currentUser != null;
    }
}
