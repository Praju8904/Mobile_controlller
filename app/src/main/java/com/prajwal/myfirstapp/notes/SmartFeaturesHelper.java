package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.tasks.Task;
import com.prajwal.myfirstapp.tasks.TaskRepository;
import android.content.Context;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class providing smart feature support for task creation and management.
 *
 * Features:
 *  - Smart due date suggestions from natural language keywords in task titles
 *  - Smart priority detection from urgency/importance keywords
 *  - Smart category suggestions from topic/domain keywords
 *  - Duplicate task detection via word-overlap similarity
 *  - Overdue task priority escalation
 *  - Daily focus task suggestion
 *  - Weekly review data aggregation
 */
public class SmartFeaturesHelper {

    private static final String TAG = "SmartFeaturesHelper";

    // ─── SmartSuggestion Inner Class ─────────────────────────────

    public static class SmartSuggestion {
        public String type;              // "date", "priority", "category"
        public String suggestedDate;     // for type = "date"  (yyyy-MM-dd)
        public String suggestedPriority; // for type = "priority"
        public String suggestedCategory; // for type = "category"
        public String displayText;       // human-readable prompt, e.g. "Set due date to Today?"
        public float confidence;         // 0.0 – 1.0
    }

    // ─── WeeklyReviewData Inner Class ────────────────────────────

    public static class WeeklyReviewData {
        public int completedThisWeek;
        public int pendingTasks;
        public List<Task> completedTasks;
        public List<Task> pendingTaskList;
    }

    // ─── Date Format Helper ──────────────────────────────────────

    /** Flag written to {@link Task#recurrenceRule} to record that a task has been escalated. */
    private static final String ESCALATION_FLAG = "escalated";

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private static String formatDate(Calendar cal) {
        return DATE_FORMAT.format(cal.getTime());
    }

    // ─── 1. Smart Due Date Suggestions ───────────────────────────

    /**
     * Analyses the task title for natural-language date keywords and returns a
     * {@link SmartSuggestion} (type = "date") when a match is found.
     *
     * @param title task title to analyse
     * @return SmartSuggestion, or null if no date keyword was detected
     */
    public static SmartSuggestion analyzeDueDate(String title) {
        if (title == null || title.isEmpty()) return null;

        String lower = title.toLowerCase(Locale.US);
        Calendar cal = Calendar.getInstance();

        if (lower.contains("today") || lower.contains("tonight")) {
            return buildDateSuggestion(cal, "Today", 0.95f);
        }

        if (lower.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            return buildDateSuggestion(cal, "Tomorrow", 0.95f);
        }

        if (lower.contains("asap") || lower.contains("urgent")) {
            return buildDateSuggestion(cal, "Today (ASAP)", 0.9f);
        }

        if (lower.contains("next week")) {
            // Advance to the next Monday
            cal.add(Calendar.DAY_OF_YEAR, 1);
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            return buildDateSuggestion(cal, "Next Monday", 0.85f);
        }

        if (lower.contains("this week")) {
            // Advance to the coming Friday of the current week
            cal.add(Calendar.DAY_OF_YEAR, 1);
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            return buildDateSuggestion(cal, "This Friday", 0.8f);
        }

        // "next <weekday>" patterns
        String[][] weekdayKeywords = {
            {"next monday",    String.valueOf(Calendar.MONDAY)},
            {"next tuesday",   String.valueOf(Calendar.TUESDAY)},
            {"next wednesday", String.valueOf(Calendar.WEDNESDAY)},
            {"next thursday",  String.valueOf(Calendar.THURSDAY)},
            {"next friday",    String.valueOf(Calendar.FRIDAY)},
            {"next saturday",  String.valueOf(Calendar.SATURDAY)},
            {"next sunday",    String.valueOf(Calendar.SUNDAY)},
        };

        for (String[] entry : weekdayKeywords) {
            if (lower.contains(entry[0])) {
                int targetDay = Integer.parseInt(entry[1]);
                cal.add(Calendar.DAY_OF_YEAR, 1);
                while (cal.get(Calendar.DAY_OF_WEEK) != targetDay) {
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                }
                String dayName = entry[0].substring(5); // strip "next "
                String label = "Next " + capitalize(dayName);
                return buildDateSuggestion(cal, label, 0.9f);
            }
        }

        return null;
    }

    private static SmartSuggestion buildDateSuggestion(Calendar cal, String label, float confidence) {
        SmartSuggestion s = new SmartSuggestion();
        s.type = "date";
        s.suggestedDate = formatDate(cal);
        s.displayText = "Set due date to " + label + "?";
        s.confidence = confidence;
        return s;
    }

    // ─── 2. Smart Priority Detection ─────────────────────────────

    /**
     * Analyses the task title for urgency/importance keywords and returns a
     * {@link SmartSuggestion} (type = "priority") when a match is found.
     *
     * @param title task title to analyse
     * @return SmartSuggestion, or null if no priority keyword was detected
     */
    public static SmartSuggestion analyzePriority(String title) {
        if (title == null || title.isEmpty()) return null;

        String lower = title.toLowerCase(Locale.US);

        if (containsAny(lower, "urgent", "asap", "critical", "emergency")) {
            return buildPrioritySuggestion(Task.PRIORITY_URGENT, "Mark as Urgent priority?", 0.95f);
        }

        if (containsAny(lower, "important", "deadline", "due today")) {
            return buildPrioritySuggestion(Task.PRIORITY_HIGH, "Mark as High priority?", 0.85f);
        }

        if (containsAny(lower, "maybe", "someday", "eventually", "later")) {
            return buildPrioritySuggestion(Task.PRIORITY_LOW, "Mark as Low priority?", 0.8f);
        }

        return null;
    }

    private static SmartSuggestion buildPrioritySuggestion(String priority, String displayText, float confidence) {
        SmartSuggestion s = new SmartSuggestion();
        s.type = "priority";
        s.suggestedPriority = priority;
        s.displayText = displayText;
        s.confidence = confidence;
        return s;
    }

    // ─── 3. Smart Category Suggestions ───────────────────────────

    /**
     * Analyses the task title for domain/topic keywords and returns a
     * {@link SmartSuggestion} (type = "category") when a match is found.
     *
     * @param title task title to analyse
     * @return SmartSuggestion, or null if no category keyword was detected
     */
    public static SmartSuggestion analyzCategory(String title) {
        if (title == null || title.isEmpty()) return null;

        String lower = title.toLowerCase(Locale.US);

        if (containsAny(lower,
                "meeting", "call", "client", "project", "report",
                "work", "deadline", "presentation", "standup")) {
            return buildCategorySuggestion("Work", 0.9f);
        }

        if (containsAny(lower,
                "study", "exam", "assignment", "lecture",
                "homework", "class", "course", "quiz")) {
            return buildCategorySuggestion("Study", 0.9f);
        }

        if (containsAny(lower,
                "gym", "run", "workout", "doctor", "exercise",
                "health", "medicine", "dentist", "yoga")) {
            return buildCategorySuggestion("Health", 0.9f);
        }

        if (containsAny(lower,
                "buy", "shop", "purchase", "grocery", "groceries",
                "amazon", "order")) {
            return buildCategorySuggestion("Shopping", 0.9f);
        }

        if (containsAny(lower,
                "birthday", "party", "family", "friend",
                "social", "dinner", "lunch")) {
            return buildCategorySuggestion("Personal", 0.85f);
        }

        return null;
    }

    private static SmartSuggestion buildCategorySuggestion(String category, float confidence) {
        SmartSuggestion s = new SmartSuggestion();
        s.type = "category";
        s.suggestedCategory = category;
        s.displayText = "Set category to \"" + category + "\"?";
        s.confidence = confidence;
        return s;
    }

    // ─── 4. Duplicate Task Detection ─────────────────────────────

    /**
     * Loads all tasks from {@link TaskRepository} and searches for an active
     * (non-trashed) task that is similar to {@code title}.
     * Similarity is determined by exact case-insensitive match first, then by
     * ≥ 70% word overlap.
     *
     * @param context application context
     * @param title   title of the new task being created
     * @return the most similar existing Task, or null if none found
     */
    public static Task findSimilarTask(Context context, String title) {
        if (context == null || title == null || title.trim().isEmpty()) return null;

        TaskRepository repo = new TaskRepository(context);
        List<Task> allTasks = repo.getAllTasks();

        Task bestMatch = null;
        float bestScore = 0f;

        for (Task task : allTasks) {
            if (task.isTrashed || task.title == null) continue;
            if (!task.isActive()) continue;

            // Exact match (case-insensitive)
            if (task.title.equalsIgnoreCase(title.trim())) {
                return task;
            }

            float score = calculateSimilarity(title, task.title);
            if (score >= 0.70f && score > bestScore) {
                bestScore = score;
                bestMatch = task;
            }
        }

        return bestMatch;
    }

    /**
     * Calculates a simple word-overlap similarity score between two strings.
     *
     * @param a first string
     * @param b second string
     * @return score in [0, 1] where 1 means all words overlap
     */
    public static float calculateSimilarity(String a, String b) {
        if (a == null || b == null) return 0f;
        if (a.trim().isEmpty() || b.trim().isEmpty()) return 0f;

        String[] wordsA = a.toLowerCase(Locale.US).trim().split("\\s+");
        String[] wordsB = b.toLowerCase(Locale.US).trim().split("\\s+");

        if (wordsA.length == 0 || wordsB.length == 0) return 0f;

        // Count words from A that appear in B
        int matches = 0;
        for (String word : wordsA) {
            if (word.length() < 2) continue; // skip single-char noise words
            for (String wordB : wordsB) {
                if (word.equals(wordB)) {
                    matches++;
                    break;
                }
            }
        }

        // Jaccard-style: matches / union size
        int union = wordsA.length + wordsB.length - matches;
        if (union <= 0) return 0f;
        return (float) matches / union;
    }

    // ─── 5. Overdue Task Escalation ──────────────────────────────

    /**
     * Iterates all active tasks and escalates the priority of any task that
     * has been overdue for 3 or more days and has not already been escalated.
     * Uses {@link #ESCALATION_FLAG} written into {@code recurrenceRule} as an escalation flag.
     *
     * @param context application context
     */
    public static void escalateOverdueTasks(Context context) {
        if (context == null) return;

        TaskRepository repo = new TaskRepository(context);
        List<Task> allTasks = repo.getAllTasks();

        long threeDaysMs = 3L * 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();

        for (Task task : allTasks) {
            if (task.isTrashed) continue;
            if (!task.isActive()) continue;
            if (!task.hasDueDate()) continue;
            if (ESCALATION_FLAG.equals(task.recurrenceRule)) continue;

            // Determine how long the task has been overdue
            long dueMs = parseDateToMillis(task.dueDate);
            if (dueMs <= 0) continue;

            long overdueMs = now - dueMs;
            if (overdueMs < threeDaysMs) continue;

            // Escalate priority by one level
            String newPriority = escalatedPriority(task.priority);
            if (newPriority.equals(task.priority)) continue; // already at max

            task.priority = newPriority;
            task.recurrenceRule = ESCALATION_FLAG;
            task.updatedAt = now;
            repo.updateTask(task);

            Log.i(TAG, "Escalated task \"" + task.title + "\" to " + newPriority);
        }
    }

    /** Returns the next higher priority, capped at PRIORITY_URGENT. */
    private static String escalatedPriority(String current) {
        if (current == null) return Task.PRIORITY_NORMAL;
        switch (current) {
            case Task.PRIORITY_LOW:    return Task.PRIORITY_NORMAL;
            case Task.PRIORITY_NORMAL: return Task.PRIORITY_HIGH;
            case Task.PRIORITY_HIGH:   return Task.PRIORITY_URGENT;
            case Task.PRIORITY_URGENT: return Task.PRIORITY_URGENT; // already max
            case Task.PRIORITY_NONE:   return Task.PRIORITY_LOW;
            default:                   return Task.PRIORITY_NORMAL;
        }
    }

    // ─── 6. Daily Focus Suggestion ───────────────────────────────

    /**
     * Returns the highest-priority active task that is overdue or due today.
     * Tasks are ranked by priority weight ({@link Task#getPriorityWeightFor(String)});
     * lower weight = higher priority.
     *
     * @param context application context
     * @return the suggested focus task, or null if there are none
     */
    public static Task getSuggestedFocusTask(Context context) {
        if (context == null) return null;

        TaskRepository repo = new TaskRepository(context);
        List<Task> allTasks = repo.getAllTasks();

        Task best = null;
        int bestWeight = Integer.MAX_VALUE;

        for (Task task : allTasks) {
            if (task.isTrashed) continue;
            if (!task.isActive()) continue;
            if (!task.isOverdue() && !task.isDueToday()) continue;

            int weight = Task.getPriorityWeightFor(task.priority);
            if (best == null || weight < bestWeight) {
                bestWeight = weight;
                best = task;
            }
        }

        return best;
    }

    // ─── 7. Weekly Review Data ───────────────────────────────────

    /**
     * Aggregates task statistics for the current week (last 7 days).
     *
     * @param context application context
     * @return populated {@link WeeklyReviewData}
     */
    public static WeeklyReviewData getWeeklyReviewData(Context context) {
        WeeklyReviewData data = new WeeklyReviewData();
        data.completedTasks = new ArrayList<>();
        data.pendingTaskList = new ArrayList<>();

        if (context == null) return data;

        TaskRepository repo = new TaskRepository(context);
        List<Task> allTasks = repo.getAllTasks();

        // Start of the current week (7 days ago at 00:00:00)
        Calendar weekStart = Calendar.getInstance();
        weekStart.add(Calendar.DAY_OF_YEAR, -7);
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);
        long weekStartMs = weekStart.getTimeInMillis();

        for (Task task : allTasks) {
            if (task.isTrashed) continue;

            if (task.isCompleted() && task.completedAt >= weekStartMs) {
                data.completedTasks.add(task);
            } else if (task.isActive()) {
                data.pendingTaskList.add(task);
            }
        }

        data.completedThisWeek = data.completedTasks.size();
        data.pendingTasks = data.pendingTaskList.size();

        return data;
    }

    // ─── Private Utility Methods ─────────────────────────────────

    /**
     * Returns true if {@code text} contains any of the supplied {@code keywords}.
     * Each keyword is matched as a whole word when possible (wrapped in spaces/start/end),
     * but falls back to a plain {@link String#contains(CharSequence)} check so that
     * compound strings like "urgent!" are still matched.
     */
    private static boolean containsAny(String text, String... keywords) {
        if (text == null || keywords == null) return false;
        for (String kw : keywords) {
            if (kw == null || kw.isEmpty()) continue;
            // Whole-word boundary check using a simple surrounding-whitespace approach
            String padded = " " + text + " ";
            if (padded.contains(" " + kw + " ")
                    || padded.contains(" " + kw + "!")
                    || padded.contains(" " + kw + ",")
                    || padded.contains(" " + kw + ".")
                    || padded.contains(" " + kw + "?")
                    || padded.contains(" " + kw + ":")) {
                return true;
            }
            // Fallback: plain substring match (handles partial compound words)
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Parses a "yyyy-MM-dd" date string to milliseconds (end of that day: 23:59:59).
     * Returns 0 on parse failure.
     */
    private static long parseDateToMillis(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return 0L;
        try {
            String[] parts = dateStr.split("-");
            if (parts.length != 3) return 0L;
            int year  = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]) - 1; // Calendar months are 0-based
            int day   = Integer.parseInt(parts[2]);
            Calendar cal = Calendar.getInstance();
            cal.set(year, month, day, 23, 59, 59);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse date: " + dateStr);
            return 0L;
        }
    }

    /** Capitalises the first letter of a word. */
    private static String capitalize(String word) {
        if (word == null || word.isEmpty()) return word;
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
