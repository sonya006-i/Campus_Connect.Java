package model;

/**
 * StudentAchievementDTO — Lightweight gamification badge model derived from actual
 * student participation (attendance, certificates, feedback, multi-club involvement).
 *
 * Implements Phase 15: Lightweight Gamification per AGENTS.md.
 */
public class StudentAchievementDTO {

    private final String id;
    private final String title;
    private final String description;
    private final String icon;
    private final boolean unlocked;
    private final int currentProgress;
    private final int targetGoal;
    private final String category; // "ATTENDANCE", "CERTIFICATE", "FEEDBACK", "DIVERSITY"

    public StudentAchievementDTO(String id, String title, String description, String icon,
                                 boolean unlocked, int currentProgress, int targetGoal, String category) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.unlocked = unlocked;
        this.currentProgress = Math.max(0, Math.min(currentProgress, targetGoal));
        this.targetGoal = Math.max(1, targetGoal);
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public int getTargetGoal() {
        return targetGoal;
    }

    public String getCategory() {
        return category;
    }

    /**
     * Formats progress label, e.g. "Unlocked" or "2 / 3".
     */
    public String getProgressLabel() {
        if (unlocked) {
            return "Unlocked";
        }
        return currentProgress + " / " + targetGoal;
    }

    /**
     * Percentage completed from 0.0 to 100.0.
     */
    public double getProgressPercentage() {
        if (targetGoal <= 0) return 0.0;
        return Math.max(0.0, Math.min(100.0, (currentProgress * 100.0) / targetGoal));
    }

    @Override
    public String toString() {
        return (unlocked ? "[✔] " : "[🔒] ") + title + " (" + getProgressLabel() + "): " + description;
    }
}
