package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

/**
 * Utility methods shared across the Task Manager feature.
 *
 * <p>Provides reduced-motion detection, haptic helpers, accessibility
 * announcements, and common formatting used by multiple classes.</p>
 */
public final class TaskUtils {

    private TaskUtils() {} // no instances

    // ── Reduced Motion ──────────────────────────────────────────

    /**
     * Returns {@code true} when the user or the system has requested reduced
     * motion — either via the Android "Animator duration scale" developer setting
     * or via the app's own {@link TaskManagerSettings#reducedMotion} toggle.
     */
    public static boolean isReducedMotionEnabled(Context context) {
        float animScale = 1f;
        try {
            animScale = Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (Exception ignored) {}
        boolean systemReduced = (animScale == 0f);
        boolean appReduced = TaskManagerSettings.getInstance(context).reducedMotion;
        return systemReduced || appReduced;
    }

    // ── Haptic helpers ──────────────────────────────────────────

    /**
     * Fires a light haptic click if haptic feedback is enabled in settings.
     */
    public static void hapticClick(Context context) {
        haptic(context, 20);
    }

    /**
     * Fires a heavy haptic click if haptic feedback is enabled in settings.
     */
    public static void hapticHeavy(Context context) {
        haptic(context, 50);
    }

    /**
     * Fires a haptic pulse of the given duration (ms).
     */
    public static void haptic(Context context, int durationMs) {
        if (!TaskManagerSettings.getInstance(context).hapticFeedback) return;
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMs);
            }
        } catch (Exception ignored) {}
    }

    // ── Accessibility ───────────────────────────────────────────

    /**
     * Announces text to TalkBack / screen readers via an accessibility event.
     */
    public static void announce(View view, String text) {
        if (view == null || text == null) return;
        view.announceForAccessibility(text);
    }

    // ── Formatting helpers ──────────────────────────────────────

    /**
     * Capitalizes the first letter of a string.
     */
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * Formats a due date string "YYYY-MM-DD" to a more readable form
     * like "Mar 15, 2024".
     */
    public static String formatDateForDisplay(String dateStr) {
        if (dateStr == null) return "";
        try {
            String[] parts = dateStr.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            return months[month - 1] + " " + day + ", " + year;
        } catch (Exception e) {
            return dateStr;
        }
    }

    /**
     * Computes a countdown text for the given task's due date/time.
     *
     * @return a string like "In 2h", "Tomorrow", "3d overdue", or null if
     *         no badge should be shown.
     */
    public static String computeCountdownText(Task task) {
        if (task.dueDate == null) return null;
        if (task.isCompleted() || task.isCancelled()) return null;
        try {
            long dueMillis;
            String[] dateParts = task.dueDate.split("-");
            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]);
            int day = Integer.parseInt(dateParts[2]);

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month - 1, day, 23, 59, 59);

            if (task.dueTime != null && !task.dueTime.isEmpty()) {
                String[] timeParts = task.dueTime.split(":");
                cal.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(timeParts[0]));
                cal.set(java.util.Calendar.MINUTE, Integer.parseInt(timeParts[1]));
                cal.set(java.util.Calendar.SECOND, 0);
            }

            dueMillis = cal.getTimeInMillis();
            long diffMs = dueMillis - System.currentTimeMillis();
            long absDiffMs = Math.abs(diffMs);
            long diffMin = absDiffMs / 60_000;
            long diffHr = absDiffMs / 3_600_000;
            long diffDay = absDiffMs / 86_400_000;

            if (diffMs < 0) {
                // Overdue
                if (diffMin < 60) return diffMin + "m overdue";
                if (diffHr < 24) return diffHr + "h overdue";
                if (diffDay < 7) return diffDay + "d overdue";
                return (diffDay / 7) + "w overdue";
            } else {
                // Upcoming
                if (diffMin < 60) return "In " + diffMin + "m";
                if (diffHr < 24) {
                    if (task.dueTime != null) return "Due today at " + task.dueTime;
                    return "Due today";
                }
                if (diffDay == 1) return "Tomorrow";
                if (diffDay <= 6) return "In " + diffDay + "d";
                return null; // 7+ days = no badge
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the badge color for a countdown text.
     *
     * @return int[]{textColor, backgroundColor}
     */
    public static int[] getCountdownBadgeColors(Task task) {
        if (task.isOverdue()) {
            return new int[]{
                    android.graphics.Color.parseColor("#EF4444"),
                    android.graphics.Color.parseColor("#EF444426")
            };
        } else if (task.isDueToday()) {
            return new int[]{
                    android.graphics.Color.parseColor("#F59E0B"),
                    android.graphics.Color.parseColor("#F59E0B26")
            };
        } else if (task.isDueTomorrow()) {
            return new int[]{
                    android.graphics.Color.parseColor("#3B82F6"),
                    android.graphics.Color.parseColor("#3B82F626")
            };
        } else {
            return new int[]{
                    android.graphics.Color.parseColor("#94A3B8"),
                    android.graphics.Color.parseColor("#94A3B826")
            };
        }
    }
}
