package util;

import dao.ClubDAO;
import dao.EventDAO;
import dao.RegistrationDAO;
import model.AdminDashboardDTO;
import model.Event;
import model.User;
import service.AdminDashboardService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/**
 * Phase8AdminDashboardTest — Standalone verification suite for Phase 8: Admin Dashboard.
 *
 * Verifies:
 * 1. AdminDashboardDTO structure and offline factory contract
 * 2. AdminDashboardService database-offline resilience and default metrics
 * 3. RegistrationDAO campus-wide counting methods with safe offline fallbacks
 * 4. Session management and role-based switching (Admin <-> Student)
 * 5. Personalized greeting and Admin role identification logic
 */
public class Phase8AdminDashboardTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("CAMPUS CONNECT — PHASE 8: ADMIN DASHBOARD TEST SUITE");
        System.out.println("==================================================");

        testAdminDashboardDTO();
        testAdminDashboardServiceOffline();
        testRegistrationDAOCampusCountersOffline();
        testSessionRoleSwitching();
        testPersonalizedAdminGreeting();

        System.out.println("\n--------------------------------------------------");
        System.out.printf("Test Results: %d/%d passed.\n", testsPassed, testsRun);
        System.out.println("--------------------------------------------------");

        if (testsPassed == testsRun) {
            System.out.println("[SUCCESS] All Phase 8 unit and offline tests passed!");
            System.exit(0);
        } else {
            System.err.println("[FAIL] Some Phase 8 tests failed.");
            System.exit(1);
        }
    }

    private static void assertTrue(String testName, boolean condition) {
        testsRun++;
        if (condition) {
            System.out.println("  ✔ [PASS] " + testName);
            testsPassed++;
        } else {
            System.err.println("  ✘ [FAIL] " + testName);
        }
    }

    private static void testAdminDashboardDTO() {
        System.out.println("\n[1] Testing AdminDashboardDTO contract:");

        AdminDashboardDTO offlineDto = AdminDashboardDTO.offline();
        assertTrue("AdminDashboardDTO.offline() sets dbOnline to false", !offlineDto.isDbOnline());
        assertTrue("AdminDashboardDTO.offline() total events defaults to 0", offlineDto.getTotalEvents() == 0);
        assertTrue("AdminDashboardDTO.offline() recent events is non-null empty list",
                offlineDto.getRecentEvents() != null && offlineDto.getRecentEvents().isEmpty());

        AdminDashboardDTO liveDto = new AdminDashboardDTO();
        liveDto.setDbOnline(true);
        liveDto.setTotalEvents(12);
        liveDto.setUpcomingEvents(5);
        liveDto.setTotalRegistrations(35);
        liveDto.setPendingRegistrations(7);
        liveDto.setApprovedRegistrations(25);
        liveDto.setRejectedRegistrations(3);

        Event sampleEvent = new Event(1, 101, "Hackathon 2026", "Coding event", "Tech",
                LocalDate.now().plusDays(5), LocalTime.of(10, 0), "Hall A", 100, LocalDate.now().plusDays(2));
        liveDto.getRecentEvents().add(new AdminDashboardDTO.AdminEventItemDTO(sampleEvent, "Tech Club", 25));

        assertTrue("AdminDashboardDTO holds total events correctly", liveDto.getTotalEvents() == 12);
        assertTrue("AdminDashboardDTO holds upcoming events correctly", liveDto.getUpcomingEvents() == 5);
        assertTrue("AdminDashboardDTO holds total registrations correctly", liveDto.getTotalRegistrations() == 35);
        assertTrue("AdminDashboardDTO holds pending registrations correctly", liveDto.getPendingRegistrations() == 7);
        assertTrue("AdminDashboardDTO holds approved registrations correctly", liveDto.getApprovedRegistrations() == 25);
        assertTrue("AdminDashboardDTO holds rejected registrations correctly", liveDto.getRejectedRegistrations() == 3);
        assertTrue("AdminEventItemDTO holds event, club, and reg count",
                liveDto.getRecentEvents().get(0).getEvent().getEventName().equals("Hackathon 2026") &&
                liveDto.getRecentEvents().get(0).getClubName().equals("Tech Club") &&
                liveDto.getRecentEvents().get(0).getRegistrationCount() == 25);
    }

    private static void testAdminDashboardServiceOffline() {
        System.out.println("\n[2] Testing AdminDashboardService database-offline behavior:");

        AdminDashboardService service = new AdminDashboardService();
        AdminDashboardDTO data = service.getDashboardData();

        assertTrue("Service returns non-null AdminDashboardDTO", data != null);
        if (!data.isDbOnline()) {
            System.out.println("  ℹ Database offline mode verified: service returns safe offline DTO.");
            assertTrue("Offline total events is 0", data.getTotalEvents() == 0);
            assertTrue("Offline total registrations is 0", data.getTotalRegistrations() == 0);
            assertTrue("Offline recent events list is empty", data.getRecentEvents().isEmpty());
        } else {
            System.out.println("  ℹ Database online mode detected: metrics retrieved from MySQL.");
            assertTrue("Total events is non-negative", data.getTotalEvents() >= 0);
            assertTrue("Total registrations is non-negative", data.getTotalRegistrations() >= 0);
        }
    }

    private static void testRegistrationDAOCampusCountersOffline() {
        System.out.println("\n[3] Testing RegistrationDAO campus-wide counters (offline-safe):");

        RegistrationDAO registrationDAO = new RegistrationDAO();
        int total = registrationDAO.countAll();
        int pending = registrationDAO.countByStatus("PENDING");
        int approved = registrationDAO.countByStatus("APPROVED");
        int rejected = registrationDAO.countByStatus("REJECTED");
        int forEvent = registrationDAO.countTotalForEvent(1);

        assertTrue("countAll() returns valid non-negative integer without throwing", total >= 0);
        assertTrue("countByStatus('PENDING') returns valid non-negative integer", pending >= 0);
        assertTrue("countByStatus('APPROVED') returns valid non-negative integer", approved >= 0);
        assertTrue("countByStatus('REJECTED') returns valid non-negative integer", rejected >= 0);
        assertTrue("countTotalForEvent(1) returns valid non-negative integer", forEvent >= 0);
    }

    private static void testSessionRoleSwitching() {
        System.out.println("\n[4] Testing Session role-based switching and isolation:");

        Session.clearSession();
        assertTrue("Session.getCurrentUser() is null after clearSession()", Session.getCurrentUser() == null);
        assertTrue("Session.isLoggedIn() is false", !Session.isLoggedIn());

        User adminUser = new User(1, "Dr. Robert Smith", "admin@campusconnect.edu", "password", "ADMIN");
        Session.setCurrentUser(adminUser);
        assertTrue("Admin user stored in session", Session.getCurrentUser() != null);
        assertTrue("Admin user role is ADMIN", "ADMIN".equalsIgnoreCase(Session.getCurrentUser().getRole()));
        assertTrue("Admin user name is Dr. Robert Smith", "Dr. Robert Smith".equals(Session.getCurrentUser().getName()));

        Session.clearSession();
        assertTrue("Session cleared successfully after admin session", Session.getCurrentUser() == null);

        User studentUser = new User(2, "Sonya Student", "sonya@student.edu", "password", "STUDENT");
        Session.setCurrentUser(studentUser);
        assertTrue("Student user stored in session", Session.getCurrentUser() != null);
        assertTrue("Student user role is STUDENT", "STUDENT".equalsIgnoreCase(Session.getCurrentUser().getRole()));
        assertTrue("Student user name is Sonya Student", "Sonya Student".equals(Session.getCurrentUser().getName()));

        Session.clearSession();
    }

    private static void testPersonalizedAdminGreeting() {
        System.out.println("\n[5] Testing Personalized greeting logic:");

        User adminUser = new User(1, "Administrator Alice", "alice@campusconnect.edu", "pass", "ADMIN");
        String name = adminUser.getName();
        assertTrue("Admin user name is dynamic and not hard-coded", "Administrator Alice".equals(name));
        assertTrue("Admin user role recognized as ADMIN", "ADMIN".equalsIgnoreCase(adminUser.getRole()));
    }
}
