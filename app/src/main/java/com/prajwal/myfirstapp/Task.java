package com.prajwal.myfirstapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Complete Task data model for the redesigned Task Manager.
 *
 * Supports: priorities, statuses, categories, tags, subtasks,
 * multiple reminders, recurrence, duration tracking, attachments,
 * starring, and soft-delete (trash).
 */
public class Task {

    // ─── Fields ──────────────────────────────────────────────────

    public String id;
    public String title;
    public String description;
    public String priority;               // "none", "low", "normal", "high", "urgent"
    public String status;                 // "todo", "inprogress", "completed", "cancelled"
    public String category;               // Category name (e.g. "Personal", "Work")
    public List<String> tags;
    public List<SubTask> subtasks;
    public String dueDate;                // "YYYY-MM-DD" or null
    public String dueTime;                // "HH:MM" or null
    public List<Long> reminderDateTimes;  // Multiple reminder timestamps (millis)
    public String recurrence;             // "none", "daily", "weekly", "monthly", "custom"
    public String recurrenceRule;         // Custom rule description (for "custom")
    public int estimatedDuration;         // Minutes
    public int actualDuration;            // Minutes
    public List<String> attachments;      // File path URIs
    public List<long[]> timerSessions;    // Each entry: [startMs, endMs]
    public String notes;                  // Rich notes / extra text
    public long createdAt;
    public long updatedAt;
    public long completedAt;              // 0 = not completed
    public boolean isStarred;
    public boolean isTrashed;
    public long trashedAt;                // 0 = not trashed
    public String source;                 // "mobile" or "pc"

    // ─── Constants ───────────────────────────────────────────────

    public static final String PRIORITY_NONE   = "none";
    public static final String PRIORITY_LOW    = "low";
    public static final String PRIORITY_NORMAL = "normal";
    public static final String PRIORITY_HIGH   = "high";
    public static final String PRIORITY_URGENT = "urgent";

    public static final String STATUS_TODO       = "todo";
    public static final String STATUS_INPROGRESS = "inprogress";
    public static final String STATUS_COMPLETED  = "completed";
    public static final String STATUS_CANCELLED  = "cancelled";

    public static final String RECURRENCE_NONE    = "none";
    public static final String RECURRENCE_DAILY   = "daily";
    public static final String RECURRENCE_WEEKLY  = "weekly";
    public static final String RECURRENCE_MONTHLY = "monthly";
    public static final String RECURRENCE_CUSTOM  = "custom";

    public static final String[] PRIORITY_OPTIONS = {
        PRIORITY_NONE, PRIORITY_LOW, PRIORITY_NORMAL, PRIORITY_HIGH, PRIORITY_URGENT
    };

    public static final String[] STATUS_OPTIONS = {
        STATUS_TODO, STATUS_INPROGRESS, STATUS_COMPLETED, STATUS_CANCELLED
    };

    // ─── Priority Colors ─────────────────────────────────────────

    public static final int COLOR_PRIORITY_URGENT = 0xFFEF4444; // Red
    public static final int COLOR_PRIORITY_HIGH   = 0xFFF97316; // Orange
    public static final int COLOR_PRIORITY_NORMAL = 0xFF3B82F6; // Blue
    public static final int COLOR_PRIORITY_LOW    = 0xFF6B7280; // Grey
    public static final int COLOR_PRIORITY_NONE   = 0xFF374151; // Dark grey

    // ─── Constructors ────────────────────────────────────────────

    public Task() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.title = "";
        this.description = "";
        this.priority = PRIORITY_NORMAL;
        this.status = STATUS_TODO;
        this.category = "Personal";
        this.tags = new ArrayList<>();
        this.subtasks = new ArrayList<>();
        this.dueDate = null;
        this.dueTime = null;
        this.reminderDateTimes = new ArrayList<>();
        this.recurrence = RECURRENCE_NONE;
        this.recurrenceRule = "";
        this.estimatedDuration = 0;
        this.actualDuration = 0;
        this.attachments = new ArrayList<>();
        this.timerSessions = new ArrayList<>();
        this.notes = "";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.completedAt = 0;
        this.isStarred = false;
        this.isTrashed = false;
        this.trashedAt = 0;
        this.source = "mobile";
    }

    public Task(String title, String priority) {
        this();
        this.title = title != null ? title : "";
        this.priority = priority != null ? priority : PRIORITY_NORMAL;
    }

    // ─── Priority Helpers ────────────────────────────────────────

    public int getPriorityColor() {
        return getPriorityColorFor(this.priority);
    }

    public static int getPriorityColorFor(String priority) {
        if (priority == null) return COLOR_PRIORITY_NONE;
        switch (priority) {
            case PRIORITY_URGENT: return COLOR_PRIORITY_URGENT;
            case PRIORITY_HIGH:   return COLOR_PRIORITY_HIGH;
            case PRIORITY_NORMAL: return COLOR_PRIORITY_NORMAL;
            case PRIORITY_LOW:    return COLOR_PRIORITY_LOW;
            default:              return COLOR_PRIORITY_NONE;
        }
    }

    public int getPriorityWeight() {
        return getPriorityWeightFor(this.priority);
    }

    public static int getPriorityWeightFor(String priority) {
        if (priority == null) return 3;
        switch (priority) {
            case PRIORITY_URGENT: return 0;
            case PRIORITY_HIGH:   return 1;
            case PRIORITY_NORMAL: return 2;
            case PRIORITY_LOW:    return 3;
            case PRIORITY_NONE:   return 4;
            default:              return 3;
        }
    }

    public String getPriorityLabel() {
        return getPriorityLabelFor(this.priority);
    }

    public static String getPriorityLabelFor(String priority) {
        if (priority == null) return "";
        switch (priority) {
            case PRIORITY_URGENT: return "URGENT";
            case PRIORITY_HIGH:   return "HIGH";
            case PRIORITY_NORMAL: return "NORMAL";
            case PRIORITY_LOW:    return "LOW";
            case PRIORITY_NONE:   return "";
            default:              return "";
        }
    }

    // ─── Status Helpers ──────────────────────────────────────────

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    public boolean isActive() {
        return STATUS_TODO.equals(status) || STATUS_INPROGRESS.equals(status);
    }

    public void markCompleted() {
        this.status = STATUS_COMPLETED;
        this.completedAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public void markTodo() {
        this.status = STATUS_TODO;
        this.completedAt = 0;
        this.updatedAt = System.currentTimeMillis();
    }

    // ─── Due Date Helpers ────────────────────────────────────────

    public boolean hasDueDate() {
        return dueDate != null && !dueDate.isEmpty() && !"null".equals(dueDate);
    }

    public boolean hasDueTime() {
        return dueTime != null && !dueTime.isEmpty() && !"null".equals(dueTime);
    }

    public boolean isOverdue() {
        if (!hasDueDate() || isCompleted() || isCancelled() || isTrashed) return false;
        try {
            Calendar now = Calendar.getInstance();
            String[] parts = dueDate.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]) - 1;
            int day = Integer.parseInt(parts[2]);

            Calendar due = Calendar.getInstance();
            due.set(year, month, day);

            if (hasDueTime()) {
                String[] timeParts = dueTime.split(":");
                due.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeParts[0]));
                due.set(Calendar.MINUTE, Integer.parseInt(timeParts[1]));
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

    public boolean isDueToday() {
        if (!hasDueDate()) return false;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return today.equals(dueDate);
    }

    public boolean isDueTomorrow() {
        if (!hasDueDate()) return false;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        String tomorrow = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
        return tomorrow.equals(dueDate);
    }

    public boolean isDueThisWeek() {
        if (!hasDueDate()) return false;
        try {
            String[] parts = dueDate.split("-");
            Calendar due = Calendar.getInstance();
            due.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));

            Calendar now = Calendar.getInstance();
            Calendar weekEnd = Calendar.getInstance();
            weekEnd.add(Calendar.DAY_OF_YEAR, 7);

            return !due.before(now) && due.before(weekEnd);
        } catch (Exception e) {
            return false;
        }
    }

    public String getDueDateGroup() {
        if (!hasDueDate()) return "No Date";
        if (isOverdue()) return "Overdue";
        if (isDueToday()) return "Today";
        if (isDueTomorrow()) return "Tomorrow";
        if (isDueThisWeek()) return "This Week";
        return "Later";
    }

    public String getFormattedDueDate() {
        if (!hasDueDate()) return "";
        try {
            String[] parts = dueDate.split("-");
            Calendar due = Calendar.getInstance();
            due.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.US);
            String dateStr = sdf.format(due.getTime());
            if (hasDueTime()) {
                dateStr += " at " + dueTime;
            }
            return dateStr;
        } catch (Exception e) {
            return dueDate;
        }
    }

    public String getFormattedDueTime() {
        if (!hasDueTime()) return "";
        try {
            String[] parts = dueTime.split(":");
            int hour = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);
            String ampm = hour >= 12 ? "PM" : "AM";
            if (hour > 12) hour -= 12;
            if (hour == 0) hour = 12;
            return String.format(Locale.US, "%d:%02d %s", hour, min, ampm);
        } catch (Exception e) {
            return dueTime;
        }
    }

    // ─── Subtask Helpers ─────────────────────────────────────────

    public boolean hasSubtasks() {
        return subtasks != null && !subtasks.isEmpty();
    }

    public int getSubtaskCompletedCount() {
        if (subtasks == null) return 0;
        int count = 0;
        for (SubTask st : subtasks) {
            if (st.isCompleted) count++;
        }
        return count;
    }

    public int getSubtaskTotalCount() {
        return subtasks != null ? subtasks.size() : 0;
    }

    public float getSubtaskProgress() {
        int total = getSubtaskTotalCount();
        if (total == 0) return 0f;
        return (float) getSubtaskCompletedCount() / total;
    }

    public String getSubtaskProgressText() {
        return getSubtaskCompletedCount() + "/" + getSubtaskTotalCount() + " subtasks";
    }

    // ─── Trash Helpers ───────────────────────────────────────────

    public void moveToTrash() {
        this.isTrashed = true;
        this.trashedAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public void restoreFromTrash() {
        this.isTrashed = false;
        this.trashedAt = 0;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getTrashDaysRemaining() {
        if (trashedAt == 0) return 30;
        long elapsed = System.currentTimeMillis() - trashedAt;
        long daysElapsed = elapsed / (24L * 60 * 60 * 1000);
        return Math.max(0, 30 - daysElapsed);
    }

    public boolean isTrashExpired() {
        if (trashedAt == 0) return false;
        long elapsed = System.currentTimeMillis() - trashedAt;
        return elapsed > 30L * 24 * 60 * 60 * 1000;
    }

    // ─── Recurrence Helpers ──────────────────────────────────────

    public boolean isRecurring() {
        return recurrence != null && !RECURRENCE_NONE.equals(recurrence);
    }

    public String getRecurrenceLabel() {
        if (recurrence == null) return "";
        switch (recurrence) {
            case RECURRENCE_DAILY:   return "Daily";
            case RECURRENCE_WEEKLY:  return "Weekly";
            case RECURRENCE_MONTHLY: return "Monthly";
            case RECURRENCE_CUSTOM:  return recurrenceRule != null ? recurrenceRule : "Custom";
            default:                 return "";
        }
    }

    // ─── Search Matching ─────────────────────────────────────────

    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase();
        if (title != null && title.toLowerCase().contains(q)) return true;
        if (description != null && description.toLowerCase().contains(q)) return true;
        if (category != null && category.toLowerCase().contains(q)) return true;
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && tag.toLowerCase().contains(q)) return true;
            }
        }
        if (subtasks != null) {
            for (SubTask st : subtasks) {
                if (st.title != null && st.title.toLowerCase().contains(q)) return true;
            }
        }
        return false;
    }

    // ─── Duration Helpers ────────────────────────────────────────

    public String getEstimatedDurationText() {
        return formatDuration(estimatedDuration);
    }

    public String getActualDurationText() {
        return formatDuration(actualDuration);
    }

    public int getTotalTimerMinutes() {
        if (timerSessions == null || timerSessions.isEmpty()) return 0;
        long total = 0;
        for (long[] session : timerSessions) {
            if (session.length == 2 && session[1] > session[0]) {
                total += session[1] - session[0];
            }
        }
        return (int) (total / 60000);
    }

    public String getTotalTimerText() {
        return formatDuration(getTotalTimerMinutes());
    }

    public void addTimerSession(long startMs, long endMs) {
        if (timerSessions == null) timerSessions = new ArrayList<>();
        timerSessions.add(new long[]{startMs, endMs});
        long durationMin = (endMs - startMs) / 60000;
        actualDuration += (int) durationMin;
        updatedAt = System.currentTimeMillis();
    }

    private static String formatDuration(int minutes) {
        if (minutes <= 0) return "";
        if (minutes < 60) return minutes + "min";
        int h = minutes / 60;
        int m = minutes % 60;
        return m > 0 ? h + "h " + m + "min" : h + "h";
    }

    // ─── Created/Updated Date Formatting ─────────────────────────

    public String getFormattedCreatedAt() {
        return formatRelativeTime(createdAt);
    }

    public String getFormattedUpdatedAt() {
        return formatRelativeTime(updatedAt);
    }

    private static String formatRelativeTime(long timestamp) {
        if (timestamp == 0) return "";
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) return "Just now";
        if (minutes < 60) return minutes + "m ago";
        if (hours < 24) return hours + "h ago";
        if (days < 7) return days + "d ago";

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("description", description);
            json.put("priority", priority);
            json.put("status", status);
            json.put("category", category);

            JSONArray tagsArr = new JSONArray();
            if (tags != null) for (String tag : tags) tagsArr.put(tag);
            json.put("tags", tagsArr);

            JSONArray subtasksArr = new JSONArray();
            if (subtasks != null) for (SubTask st : subtasks) subtasksArr.put(st.toJson());
            json.put("subtasks", subtasksArr);

            json.put("dueDate", dueDate);
            json.put("dueTime", dueTime);

            JSONArray remindersArr = new JSONArray();
            if (reminderDateTimes != null) for (Long r : reminderDateTimes) remindersArr.put(r);
            json.put("reminderDateTimes", remindersArr);

            json.put("recurrence", recurrence);
            json.put("recurrenceRule", recurrenceRule);
            json.put("estimatedDuration", estimatedDuration);
            json.put("actualDuration", actualDuration);

            JSONArray attachArr = new JSONArray();
            if (attachments != null) for (String a : attachments) attachArr.put(a);
            json.put("attachments", attachArr);

            JSONArray sessionsArr = new JSONArray();
            if (timerSessions != null) {
                for (long[] session : timerSessions) {
                    JSONArray s = new JSONArray();
                    s.put(session[0]);
                    s.put(session[1]);
                    sessionsArr.put(s);
                }
            }
            json.put("timerSessions", sessionsArr);
            json.put("notes", notes != null ? notes : "");

            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
            json.put("completedAt", completedAt);
            json.put("isStarred", isStarred);
            json.put("isTrashed", isTrashed);
            json.put("trashedAt", trashedAt);
            json.put("source", source);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static Task fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            Task task = new Task();
            task.id = json.optString("id", task.id);
            task.title = json.optString("title", "");
            task.description = json.optString("description", "");
            task.priority = json.optString("priority", PRIORITY_NORMAL);
            task.status = json.optString("status", STATUS_TODO);
            task.category = json.optString("category", "Personal");

            // Legacy compatibility: map "completed" boolean to status
            if (json.has("completed") && !json.has("status")) {
                task.status = json.optBoolean("completed", false) ? STATUS_COMPLETED : STATUS_TODO;
            }

            task.tags = new ArrayList<>();
            JSONArray tagsArr = json.optJSONArray("tags");
            if (tagsArr != null) {
                for (int i = 0; i < tagsArr.length(); i++) {
                    task.tags.add(tagsArr.getString(i));
                }
            }

            task.subtasks = new ArrayList<>();
            JSONArray subtasksArr = json.optJSONArray("subtasks");
            if (subtasksArr != null) {
                for (int i = 0; i < subtasksArr.length(); i++) {
                    SubTask st = SubTask.fromJson(subtasksArr.getJSONObject(i));
                    if (st != null) task.subtasks.add(st);
                }
            }

            task.dueDate = json.optString("dueDate", null);
            task.dueTime = json.optString("dueTime", null);
            // Legacy field names
            if (task.dueDate == null) task.dueDate = json.optString("due_date", null);
            if (task.dueTime == null) task.dueTime = json.optString("due_time", null);
            if ("null".equals(task.dueDate)) task.dueDate = null;
            if ("null".equals(task.dueTime)) task.dueTime = null;

            task.reminderDateTimes = new ArrayList<>();
            JSONArray remindersArr = json.optJSONArray("reminderDateTimes");
            if (remindersArr != null) {
                for (int i = 0; i < remindersArr.length(); i++) {
                    task.reminderDateTimes.add(remindersArr.getLong(i));
                }
            }

            task.recurrence = json.optString("recurrence", RECURRENCE_NONE);
            task.recurrenceRule = json.optString("recurrenceRule", "");
            task.estimatedDuration = json.optInt("estimatedDuration", 0);
            task.actualDuration = json.optInt("actualDuration", 0);

            task.attachments = new ArrayList<>();
            JSONArray attachArr = json.optJSONArray("attachments");
            if (attachArr != null) {
                for (int i = 0; i < attachArr.length(); i++) {
                    task.attachments.add(attachArr.getString(i));
                }
            }

            task.timerSessions = new ArrayList<>();
            JSONArray sessionsArr = json.optJSONArray("timerSessions");
            if (sessionsArr != null) {
                for (int i = 0; i < sessionsArr.length(); i++) {
                    JSONArray s = sessionsArr.getJSONArray(i);
                    task.timerSessions.add(new long[]{s.getLong(0), s.getLong(1)});
                }
            }
            task.notes = json.optString("notes", "");

            task.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            // Legacy field: created_at as ISO string
            if (!json.has("createdAt") && json.has("created_at")) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                    Date d = sdf.parse(json.optString("created_at", ""));
                    if (d != null) task.createdAt = d.getTime();
                } catch (Exception ignored) {}
            }

            task.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            task.completedAt = json.optLong("completedAt", 0);
            task.isStarred = json.optBoolean("isStarred", false);
            task.isTrashed = json.optBoolean("isTrashed", false);
            task.trashedAt = json.optLong("trashedAt", 0);
            task.source = json.optString("source", "mobile");

            return task;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ─── Legacy Migration ────────────────────────────────────────

    /**
     * Create a Task from the old TaskItem format (legacy migration).
     */
    public static Task fromLegacyJson(JSONObject json) {
        if (json == null) return null;
        try {
            Task task = new Task();
            long legacyId = json.optLong("id", 0);
            task.id = legacyId > 0 ? String.valueOf(legacyId) : task.id;
            task.title = json.optString("title", "");
            task.status = json.optBoolean("completed", false) ? STATUS_COMPLETED : STATUS_TODO;

            String legacyPriority = json.optString("priority", "normal");
            switch (legacyPriority) {
                case "high": task.priority = PRIORITY_HIGH; break;
                case "low":  task.priority = PRIORITY_LOW; break;
                default:     task.priority = PRIORITY_NORMAL; break;
            }

            task.dueDate = json.optString("due_date", null);
            task.dueTime = json.optString("due_time", null);
            if ("null".equals(task.dueDate)) task.dueDate = null;
            if ("null".equals(task.dueTime)) task.dueTime = null;

            task.source = json.optString("source", "mobile");

            String createdAtStr = json.optString("created_at", "");
            if (!createdAtStr.isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                    Date d = sdf.parse(createdAtStr);
                    if (d != null) task.createdAt = d.getTime();
                } catch (Exception ignored) {}
            }
            task.updatedAt = task.createdAt;

            if (task.isCompleted()) {
                task.completedAt = task.createdAt;
            }

            return task;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ─── Copy ────────────────────────────────────────────────────

    public Task duplicate() {
        Task copy = new Task();
        copy.title = this.title + " (copy)";
        copy.description = this.description;
        copy.priority = this.priority;
        copy.status = STATUS_TODO;
        copy.category = this.category;
        copy.tags = new ArrayList<>(this.tags);
        copy.subtasks = new ArrayList<>();
        if (this.subtasks != null) {
            for (SubTask st : this.subtasks) copy.subtasks.add(st.copy());
        }
        copy.dueDate = this.dueDate;
        copy.dueTime = this.dueTime;
        copy.reminderDateTimes = new ArrayList<>();
        copy.recurrence = this.recurrence;
        copy.recurrenceRule = this.recurrenceRule;
        copy.estimatedDuration = this.estimatedDuration;
        copy.attachments = new ArrayList<>(this.attachments != null ? this.attachments : new ArrayList<>());
        copy.timerSessions = new ArrayList<>();
        copy.notes = this.notes;
        copy.isStarred = false;
        return copy;
    }
}
