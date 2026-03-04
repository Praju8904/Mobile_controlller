package com.prajwal.myfirstapp.tasks;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Static utility class for smart detection of due dates, priorities and categories
 * from free-form task title text.
 * Gap refs: 13.5, 16.3
 */
public class SmartDetectionHelper {

    // Date patterns
    private static final Pattern PATTERN_TODAY     = Pattern.compile("\\btoday\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_TOMORROW  = Pattern.compile("\\btomorrow\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_MONDAY    = Pattern.compile("\\bmonday\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_TUESDAY   = Pattern.compile("\\btuesday\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_WEDNESDAY = Pattern.compile("\\bwednesday\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_THURSDAY  = Pattern.compile("\\bthursday\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_FRIDAY    = Pattern.compile("\\bfriday\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_SATURDAY  = Pattern.compile("\\bsaturday\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_SUNDAY    = Pattern.compile("\\bsunday\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_THIS_WEEK = Pattern.compile("\\bthis week\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_NEXT_WEEK = Pattern.compile("\\bnext week\\b", Pattern.CASE_INSENSITIVE);

    // Priority keywords
    private static final Pattern PATTERN_URGENT = Pattern.compile("\\b(urgent|asap|critical|emergency)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_HIGH   = Pattern.compile("\\b(high|important|priority)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_LOW    = Pattern.compile("\\b(low|minor|sometime|whenever)\\b", Pattern.CASE_INSENSITIVE);

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static void applyAll(Task task, String text) {
        if (text == null || text.isEmpty()) return;
        applyDueDate(task, text);
        applyPriority(task, text);
        applyCategory(task, text);
        applySentimentPriority(task, text);
    }

    public static void applyDueDate(Task task, String text) {
        if (text == null) return;
        Calendar cal = Calendar.getInstance();

        if (PATTERN_TODAY.matcher(text).find()) {
            task.dueDate = DATE_FORMAT.format(cal.getTime());
        } else if (PATTERN_TOMORROW.matcher(text).find()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            task.dueDate = DATE_FORMAT.format(cal.getTime());
        } else if (PATTERN_NEXT_WEEK.matcher(text).find()) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            task.dueDate = DATE_FORMAT.format(cal.getTime());
        } else if (PATTERN_THIS_WEEK.matcher(text).find()) {
            cal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
            task.dueDate = DATE_FORMAT.format(cal.getTime());
        } else {
            int dayOfWeek = -1;
            if (PATTERN_MONDAY.matcher(text).find())         dayOfWeek = Calendar.MONDAY;
            else if (PATTERN_TUESDAY.matcher(text).find())   dayOfWeek = Calendar.TUESDAY;
            else if (PATTERN_WEDNESDAY.matcher(text).find()) dayOfWeek = Calendar.WEDNESDAY;
            else if (PATTERN_THURSDAY.matcher(text).find())  dayOfWeek = Calendar.THURSDAY;
            else if (PATTERN_FRIDAY.matcher(text).find())    dayOfWeek = Calendar.FRIDAY;
            else if (PATTERN_SATURDAY.matcher(text).find())  dayOfWeek = Calendar.SATURDAY;
            else if (PATTERN_SUNDAY.matcher(text).find())    dayOfWeek = Calendar.SUNDAY;

            if (dayOfWeek != -1) {
                int today = cal.get(Calendar.DAY_OF_WEEK);
                int daysAhead = (dayOfWeek - today + 7) % 7;
                if (daysAhead == 0) daysAhead = 7; // same day as today — use next week's occurrence
                cal.add(Calendar.DAY_OF_YEAR, daysAhead);
                task.dueDate = DATE_FORMAT.format(cal.getTime());
            }
        }
    }

    public static void applyPriority(Task task, String text) {
        if (text == null) return;
        if (PATTERN_URGENT.matcher(text).find()) {
            task.priority = Task.PRIORITY_URGENT;
        } else if (PATTERN_HIGH.matcher(text).find()) {
            task.priority = Task.PRIORITY_HIGH;
        } else if (PATTERN_LOW.matcher(text).find()) {
            task.priority = Task.PRIORITY_LOW;
        }
    }

    public static void applyCategory(Task task, String text) {
        if (text == null) return;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("work") || lower.contains("meeting") || lower.contains("office") || lower.contains("deadline")) {
            task.category = "Work";
        } else if (lower.contains("buy") || lower.contains("shop") || lower.contains("grocery") || lower.contains("groceries")) {
            task.category = "Shopping";
        } else if (lower.contains("exercise") || lower.contains("gym") || lower.contains("health") || lower.contains("doctor")) {
            task.category = "Health";
        } else if (lower.contains("study") || lower.contains("learn") || lower.contains("read") || lower.contains("course")) {
            task.category = "Study";
        }
    }

    // ─── Sentiment-Based Priority Elevation (Feature 12) ────────

    private static final Pattern SENTIMENT_NEGATIVE = Pattern.compile(
            "\\b(stressed|anxious|worried|scared|frustrated|angry|can't|failing|forgot|" +
            "overdue|deadline|last chance|final notice|overdue|panic|nightmare|terrible|" +
            "desperate|stuck|blocked|broken|crash|error|bug|fix now|help|sos)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SENTIMENT_EXCLAMATION = Pattern.compile("[!]{2,}");

    private static final Pattern SENTIMENT_CAPS_WORDS = Pattern.compile("\\b[A-Z]{3,}\\b");

    /**
     * Elevates task priority based on negative/urgent sentiment cues in text.
     * Only elevates — never downgrades — the current priority.
     */
    public static void applySentimentPriority(Task task, String text) {
        if (text == null || text.isEmpty()) return;

        int score = 0;

        // Negative keywords
        java.util.regex.Matcher negMatcher = SENTIMENT_NEGATIVE.matcher(text);
        while (negMatcher.find()) score += 2;

        // Multiple exclamation marks
        if (SENTIMENT_EXCLAMATION.matcher(text).find()) score += 3;

        // ALL CAPS words (3+ letters)
        java.util.regex.Matcher capsMatcher = SENTIMENT_CAPS_WORDS.matcher(text);
        while (capsMatcher.find()) score += 1;

        // Only elevate if score is significant
        if (score >= 4) {
            int currentWeight = Task.getPriorityWeightFor(task.priority);
            // Urgent = 0, High = 1, Normal = 2, Low = 3
            if (score >= 8 && currentWeight > 0) {
                task.priority = Task.PRIORITY_URGENT;
            } else if (score >= 4 && currentWeight > 1) {
                task.priority = Task.PRIORITY_HIGH;
            }
        }
    }

    /**
     * Feature 12: Suggest a priority based on keyword/sentiment analysis.
     * Returns "urgent", "high", or null if no suggestion.
     * Does NOT modify any Task — purely a suggestion.
     */
    public static String suggestPriority(String title, String description) {
        String combined = ((title == null ? "" : title) + " " +
                (description == null ? "" : description)).toLowerCase(Locale.ROOT);
        if (combined.trim().isEmpty()) return null;

        // Check urgent keywords first
        if (PATTERN_URGENT.matcher(combined).find()) return Task.PRIORITY_URGENT;
        if (PATTERN_HIGH.matcher(combined).find()) return Task.PRIORITY_HIGH;

        // Sentiment scoring
        int score = 0;
        java.util.regex.Matcher negMatcher = SENTIMENT_NEGATIVE.matcher(combined);
        while (negMatcher.find()) score += 2;
        if (SENTIMENT_EXCLAMATION.matcher(title != null ? title : "").find()) score += 3;
        java.util.regex.Matcher capsMatcher = SENTIMENT_CAPS_WORDS.matcher(title != null ? title : "");
        while (capsMatcher.find()) score += 1;

        if (score >= 8) return Task.PRIORITY_URGENT;
        if (score >= 4) return Task.PRIORITY_HIGH;
        return null;
    }
}
