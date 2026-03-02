package com.prajwal.myfirstapp.tasks;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for formatting task due dates into user-friendly
 * relative strings: "Today at 9:00 AM", "Tomorrow", "Mar 5", etc.
 */
public class TaskDateFormatter {

    private TaskDateFormatter() {} // Utility class — no instances

    /**
     * Format a due date/time pair into a friendly relative string.
     *
     * @param date "YYYY-MM-DD" or null
     * @param time "HH:MM" or null
     * @return e.g. "Today at 9:00 AM", "Tomorrow", "Mon, Feb 3"
     */
    public static String formatDueDate(String date, String time) {
        if (date == null || date.isEmpty() || "null".equals(date)) return "";

        try {
            String[] parts = date.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]) - 1;
            int day = Integer.parseInt(parts[2]);

            Calendar due = Calendar.getInstance();
            due.set(year, month, day, 0, 0, 0);

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar tomorrow = (Calendar) today.clone();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);

            Calendar yesterday = (Calendar) today.clone();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            String timeStr = formatTime12h(time);

            if (isSameDay(due, today)) {
                return timeStr.isEmpty() ? "Today" : "Today at " + timeStr;
            } else if (isSameDay(due, tomorrow)) {
                return timeStr.isEmpty() ? "Tomorrow" : "Tomorrow at " + timeStr;
            } else if (isSameDay(due, yesterday)) {
                return timeStr.isEmpty() ? "Yesterday" : "Yesterday at " + timeStr;
            } else {
                // Within this year → "Mon, Mar 5"
                // Different year → "Mar 5, 2027"
                Calendar now = Calendar.getInstance();
                SimpleDateFormat sdf;
                if (due.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                    sdf = new SimpleDateFormat("EEE, MMM d", Locale.US);
                } else {
                    sdf = new SimpleDateFormat("MMM d, yyyy", Locale.US);
                }
                String dateStr = sdf.format(due.getTime());
                return timeStr.isEmpty() ? dateStr : dateStr + " at " + timeStr;
            }
        } catch (Exception e) {
            return date; // fallback to raw
        }
    }

    /**
     * Convert "HH:MM" 24h format to "9:00 AM" 12h format.
     */
    public static String formatTime12h(String time) {
        if (time == null || time.isEmpty() || "null".equals(time)) return "";
        try {
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);
            String ampm = hour >= 12 ? "PM" : "AM";
            if (hour > 12) hour -= 12;
            if (hour == 0) hour = 12;
            return String.format(Locale.US, "%d:%02d %s", hour, min, ampm);
        } catch (Exception e) {
            return time;
        }
    }

    /**
     * Check if a due date/time is overdue (past current moment).
     */
    public static boolean isOverdue(String date, String time) {
        if (date == null || date.isEmpty() || "null".equals(date)) return false;
        try {
            Calendar now = Calendar.getInstance();
            String[] parts = date.split("-");
            Calendar due = Calendar.getInstance();
            due.set(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]) - 1,
                    Integer.parseInt(parts[2]));

            if (time != null && !time.isEmpty() && !"null".equals(time)) {
                String[] tp = time.split(":");
                due.set(Calendar.HOUR_OF_DAY, Integer.parseInt(tp[0]));
                due.set(Calendar.MINUTE, Integer.parseInt(tp[1]));
                due.set(Calendar.SECOND, 0);
            } else {
                due.set(Calendar.HOUR_OF_DAY, 23);
                due.set(Calendar.MINUTE, 59);
                due.set(Calendar.SECOND, 59);
            }
            return now.after(due);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a date is today.
     */
    public static boolean isDueToday(String date) {
        if (date == null || date.isEmpty()) return false;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return today.equals(date);
    }

    /**
     * Check if a date is due within the next 48 hours.
     */
    public static boolean isDueSoon(String date) {
        if (date == null || date.isEmpty()) return false;
        try {
            String[] parts = date.split("-");
            Calendar due = Calendar.getInstance();
            due.set(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]) - 1,
                    Integer.parseInt(parts[2]));

            Calendar now = Calendar.getInstance();
            Calendar soon = (Calendar) now.clone();
            soon.add(Calendar.HOUR_OF_DAY, 48);

            return due.after(now) && due.before(soon);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Format elapsed seconds into a compact "1h 23m" or "45m" string.
     */
    public static String formatElapsedTime(int totalSeconds) {
        if (totalSeconds <= 0) return "";
        int hours = totalSeconds / 3600;
        int mins = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return mins > 0 ? hours + "h " + mins + "m" : hours + "h";
        }
        return mins + "m";
    }

    /**
     * Format estimated duration from minutes into "~45 min", "~2h", etc.
     */
    public static String formatEstimatedDuration(int minutes) {
        if (minutes <= 0) return "";
        if (minutes < 60) return "~" + minutes + " min";
        int h = minutes / 60;
        int m = minutes % 60;
        return m > 0 ? "~" + h + "h " + m + "m" : "~" + h + "h";
    }

    // ─── Private helpers ────────────────────────────────────────

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
