package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Feature 14: Gamification — XP, Level, and Karma manager.
 * Singleton that tracks XP earned from completing tasks, streak bonuses,
 * and level progression with named tiers.
 */
public class TaskXpManager {

    private static final String PREFS = "task_xp_manager";
    private static final String KEY_TOTAL_XP = "total_xp";
    private static final String KEY_LEVEL = "current_level";
    private static final String KEY_LEVEL_XP = "level_xp";       // XP within current level
    private static final String KEY_LONGEST_STREAK = "longest_streak";
    private static final String KEY_LAST_STREAK_DATE = "last_streak_date";

    // XP award amounts
    private static final int XP_COMPLETE_ONTIME = 15;
    private static final int XP_COMPLETE_LATE = 5;
    private static final int XP_COMPLETE_URGENT = 10;   // bonus for urgent task
    private static final int XP_COMPLETE_HIGH = 5;       // bonus for high priority
    private static final int XP_STREAK_DAILY = 5;        // per streak day maintained
    private static final int XP_STREAK_MILESTONE = 20;   // every 7 days

    // Level thresholds — XP needed to reach each level (cumulative within level)
    private static final int[] LEVEL_THRESHOLDS = {
            0, 100, 250, 500, 900, 1400, 2000, 2800, 3800, 5000
    };
    private static final String[] LEVEL_NAMES = {
            "Beginner", "Focused", "Productive", "Achiever", "Expert",
            "Master", "Legend", "Champion", "Grandmaster", "Mythic"
    };

    private final Context context;
    private static TaskXpManager instance;

    private TaskXpManager(Context ctx) {
        this.context = ctx.getApplicationContext();
    }

    public static synchronized TaskXpManager getInstance(Context ctx) {
        if (instance == null) {
            instance = new TaskXpManager(ctx);
        }
        return instance;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ─── Getters ──────────────────────────────────────────────────

    public int getTotalXp() {
        return getPrefs().getInt(KEY_TOTAL_XP, 0);
    }

    public int getCurrentLevel() {
        return getPrefs().getInt(KEY_LEVEL, 1);
    }

    /** XP accumulated within the current level. */
    public int getXpForCurrentLevel() {
        return getPrefs().getInt(KEY_LEVEL_XP, 0);
    }

    /** XP needed to reach the next level from current level. */
    public int getXpToNextLevel() {
        int level = getCurrentLevel();
        if (level >= LEVEL_THRESHOLDS.length) {
            return LEVEL_THRESHOLDS[LEVEL_THRESHOLDS.length - 1]; // max level repeats threshold
        }
        return LEVEL_THRESHOLDS[level];
    }

    /** Progress within current level as 0.0..1.0 */
    public float getLevelProgress() {
        int xpInLevel = getXpForCurrentLevel();
        int needed = getXpToNextLevel();
        if (needed <= 0) return 1.0f;
        return Math.min(1.0f, (float) xpInLevel / needed);
    }

    public String getLevelName() {
        int level = getCurrentLevel();
        int idx = Math.min(level - 1, LEVEL_NAMES.length - 1);
        if (idx < 0) idx = 0;
        return LEVEL_NAMES[idx];
    }

    public int getLongestStreak() {
        return getPrefs().getInt(KEY_LONGEST_STREAK, 0);
    }

    // ─── XP Award Methods ─────────────────────────────────────────

    /**
     * Called when a task is completed. Returns XP awarded (positive),
     * or negative level number if a level-up occurred (e.g., -4 means leveled up to 4).
     */
    public int onTaskCompleted(Task task) {
        if (task == null) return 0;

        int xp = task.isOverdue() ? XP_COMPLETE_LATE : XP_COMPLETE_ONTIME;
        if (Task.PRIORITY_URGENT.equals(task.priority)) xp += XP_COMPLETE_URGENT;
        else if (Task.PRIORITY_HIGH.equals(task.priority)) xp += XP_COMPLETE_HIGH;

        // Bonus for subtasks
        if (task.hasSubtasks()) xp += task.getSubtaskTotalCount() * 2;
        // Bonus for effort
        if (task.effortPoints > 0) xp += task.effortPoints;

        int oldLevel = getCurrentLevel();
        addXp(xp);
        int newLevel = getCurrentLevel();

        if (newLevel > oldLevel) {
            return -newLevel; // negative = level-up signal
        }
        return xp;
    }

    /**
     * Award streak XP. Deduplicates — only awards once per calendar day.
     */
    public void onStreakDay(int streak) {
        String today = java.text.SimpleDateFormat.getDateInstance().format(new java.util.Date());
        String lastDate = getPrefs().getString(KEY_LAST_STREAK_DATE, "");
        if (today.equals(lastDate)) return; // already awarded today

        int xp = XP_STREAK_DAILY;
        if (streak > 0 && streak % 7 == 0) xp += XP_STREAK_MILESTONE;

        addXp(xp);

        // Update longest streak
        if (streak > getLongestStreak()) {
            getPrefs().edit().putInt(KEY_LONGEST_STREAK, streak).apply();
        }

        getPrefs().edit().putString(KEY_LAST_STREAK_DATE, today).apply();
    }

    private void addXp(int amount) {
        SharedPreferences prefs = getPrefs();
        int totalXp = prefs.getInt(KEY_TOTAL_XP, 0) + amount;
        int levelXp = prefs.getInt(KEY_LEVEL_XP, 0) + amount;
        int level = prefs.getInt(KEY_LEVEL, 1);

        // Check for level-ups
        int threshold = getThresholdForLevel(level);
        while (levelXp >= threshold && threshold > 0) {
            levelXp -= threshold;
            level++;
            threshold = getThresholdForLevel(level);
        }

        prefs.edit()
                .putInt(KEY_TOTAL_XP, totalXp)
                .putInt(KEY_LEVEL_XP, levelXp)
                .putInt(KEY_LEVEL, level)
                .apply();
    }

    private int getThresholdForLevel(int level) {
        if (level <= 0) return LEVEL_THRESHOLDS[1];
        if (level >= LEVEL_THRESHOLDS.length) {
            return LEVEL_THRESHOLDS[LEVEL_THRESHOLDS.length - 1];
        }
        return LEVEL_THRESHOLDS[level];
    }
}
