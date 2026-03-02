package com.prajwal.myfirstapp.notes;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  DATE-TIME DETECTOR — Natural language date/time parser for smart reminders
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Scans text for temporal expressions and resolves them to actual Calendar dates.
 *  All processing is local — no internet required.
 *
 *  Recognized patterns:
 *   "tomorrow", "today", "tonight", "next Monday…Sunday", "next week",
 *   "in 2 days/weeks/hours", "at 3pm", "end of month", "this weekend",
 *   "next month", "day after tomorrow"
 */
public class DateTimeDetector {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DETECTED RESULT
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class DetectedDateTime {
        public final Calendar suggestedDate;
        public final String matchedPhrase;
        public final float confidence; // 0.0 – 1.0

        public DetectedDateTime(Calendar suggestedDate, String matchedPhrase, float confidence) {
            this.suggestedDate = suggestedDate;
            this.matchedPhrase = matchedPhrase;
            this.confidence = confidence;
        }

        /** Human-readable version of the detected date. */
        public String getFormattedDate() {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.US);
            return sdf.format(suggestedDate.getTime());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DAY NAME MAPPING
    // ═══════════════════════════════════════════════════════════════════════════════

    private static final String[] DAY_NAMES = {
        "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"
    };
    private static final int[] DAY_CONSTANTS = {
        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
    };

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PATTERNS (compiled once)
    // ═══════════════════════════════════════════════════════════════════════════════

    private static final Pattern PAT_TOMORROW       = Pattern.compile("\\btomorrow\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_TODAY           = Pattern.compile("\\btoday\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_TONIGHT         = Pattern.compile("\\btonight\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_THIS_WEEKEND    = Pattern.compile("\\bthis weekend\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_DAY_AFTER       = Pattern.compile("\\bday after tomorrow\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_NEXT_WEEK       = Pattern.compile("\\bnext week\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_NEXT_MONTH      = Pattern.compile("\\bnext month\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_END_OF_MONTH    = Pattern.compile("\\bend of (?:the )?month\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_NEXT_DAY        = Pattern.compile(
            "\\bnext (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_IN_N_UNITS      = Pattern.compile(
            "\\bin (\\d{1,3}) (minutes?|hours?|days?|weeks?|months?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_AT_TIME         = Pattern.compile(
            "\\bat (\\d{1,2})(?::(\\d{2}))? ?(am|pm)\\b",
            Pattern.CASE_INSENSITIVE);

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Scans the given text for date/time expressions.
     * Returns the first (highest-confidence) match, or null if none found.
     */
    public static DetectedDateTime detect(String text) {
        if (text == null || text.length() < 3) return null;

        List<DetectedDateTime> hits = detectAll(text);
        if (hits.isEmpty()) return null;

        // Return highest confidence
        DetectedDateTime best = hits.get(0);
        for (DetectedDateTime d : hits) {
            if (d.confidence > best.confidence) best = d;
        }
        return best;
    }

    /**
     * Returns all detected date/time expressions in the text.
     */
    public static List<DetectedDateTime> detectAll(String text) {
        List<DetectedDateTime> results = new ArrayList<>();
        if (text == null || text.length() < 3) return results;

        Calendar now = Calendar.getInstance();
        Matcher m;

        // ── "day after tomorrow" (must check before "tomorrow") ──
        m = PAT_DAY_AFTER.matcher(text);
        if (m.find()) {
            Calendar c = (Calendar) now.clone();
            c.add(Calendar.DAY_OF_YEAR, 2);
            setDefaultTime(c, 9, 0); // 9:00 AM
            results.add(new DetectedDateTime(c, m.group(), 0.9f));
        }

        // ── "tomorrow" ──
        m = PAT_TOMORROW.matcher(text);
        if (m.find()) {
            Calendar c = (Calendar) now.clone();
            c.add(Calendar.DAY_OF_YEAR, 1);
            setDefaultTime(c, 9, 0);
            results.add(new DetectedDateTime(c, m.group(), 0.95f));
        }

        // ── "today" ──
        m = PAT_TODAY.matcher(text);
        if (m.find()) {
            Calendar c = (Calendar) now.clone();
            c.add(Calendar.HOUR_OF_DAY, 1); // 1 hour from now
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            results.add(new DetectedDateTime(c, m.group(), 0.8f));
        }

        // ── "tonight" ──
        m = PAT_TONIGHT.matcher(text);
        if (m.find()) {
            Calendar c = (Calendar) now.clone();
            setDefaultTime(c, 20, 0); // 8:00 PM
            if (c.before(now)) c.add(Calendar.DAY_OF_YEAR, 1);
            results.add(new DetectedDateTime(c, m.group(), 0.9f));
        }

        // ── "this weekend" ──
        m = PAT_THIS_WEEKEND.matcher(text);
        if (m.find()) {
            Calendar c = (Calendar) now.clone();
            int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
            int daysUntilSat = (Calendar.SATURDAY - dayOfWeek + 7) % 7;
            if (daysUntilSat == 0) daysUntilSat = 7;
            c.add(Calendar.DAY_OF_YEAR, daysUntilSat);
            setDefaultTime(c, 10, 0);
            results.add(new DetectedDateTime(c, m.group(), 0.75f));
        }

        // ── "next Monday…Sunday" ──
        m = PAT_NEXT_DAY.matcher(text);
        if (m.find()) {
            String dayName = m.group(1).toLowerCase(Locale.US);
            int targetDay = resolveDayOfWeek(dayName);
            if (targetDay >= 0) {
                Calendar c = (Calendar) now.clone();
                int today = c.get(Calendar.DAY_OF_WEEK);
                int diff = (targetDay - today + 7) % 7;
                if (diff == 0) diff = 7; // "next X" always means next week
                c.add(Calendar.DAY_OF_YEAR, diff);
                setDefaultTime(c, 9, 0);
                results.add(new DetectedDateTime(c, m.group(), 0.9f));
            }
        }

        // ── "next week" ──
        m = PAT_NEXT_WEEK.matcher(text);
        if (m.find()) {
            Calendar c = (Calendar) now.clone();
            c.add(Calendar.WEEK_OF_YEAR, 1);
            c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            setDefaultTime(c, 9, 0);
            results.add(new DetectedDateTime(c, m.group(), 0.85f));
        }

        // ── "next month" ──
        m = PAT_NEXT_MONTH.matcher(text);
        if (m.find()) {
            Calendar c = (Calendar) now.clone();
            c.add(Calendar.MONTH, 1);
            c.set(Calendar.DAY_OF_MONTH, 1);
            setDefaultTime(c, 9, 0);
            results.add(new DetectedDateTime(c, m.group(), 0.8f));
        }

        // ── "end of month" ──
        m = PAT_END_OF_MONTH.matcher(text);
        if (m.find()) {
            Calendar c = (Calendar) now.clone();
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
            setDefaultTime(c, 18, 0);
            results.add(new DetectedDateTime(c, m.group(), 0.8f));
        }

        // ── "in N days/hours/weeks/months" ──
        m = PAT_IN_N_UNITS.matcher(text);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String unit = m.group(2).toLowerCase(Locale.US);
            Calendar c = (Calendar) now.clone();
            if (unit.startsWith("minute"))       c.add(Calendar.MINUTE, n);
            else if (unit.startsWith("hour"))    c.add(Calendar.HOUR_OF_DAY, n);
            else if (unit.startsWith("day"))     c.add(Calendar.DAY_OF_YEAR, n);
            else if (unit.startsWith("week"))    c.add(Calendar.WEEK_OF_YEAR, n);
            else if (unit.startsWith("month"))   c.add(Calendar.MONTH, n);
            c.set(Calendar.SECOND, 0);
            results.add(new DetectedDateTime(c, m.group(), 0.9f));
        }

        // ── "at 3pm" / "at 3:30 pm" ──
        m = PAT_AT_TIME.matcher(text);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            String ampm = m.group(3).toLowerCase(Locale.US);
            if (ampm.equals("pm") && hour < 12) hour += 12;
            if (ampm.equals("am") && hour == 12) hour = 0;
            Calendar c = (Calendar) now.clone();
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            if (c.before(now)) c.add(Calendar.DAY_OF_YEAR, 1);
            results.add(new DetectedDateTime(c, m.group(), 0.85f));
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private static int resolveDayOfWeek(String name) {
        for (int i = 0; i < DAY_NAMES.length; i++) {
            if (DAY_NAMES[i].equals(name)) return DAY_CONSTANTS[i];
        }
        return -1;
    }

    private static void setDefaultTime(Calendar c, int hour, int minute) {
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
