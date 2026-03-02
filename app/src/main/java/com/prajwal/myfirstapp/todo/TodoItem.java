package com.prajwal.myfirstapp.todo;


import com.prajwal.myfirstapp.tasks.SubtaskItem;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Data model for a single to-do item within a TodoList.
 */
public class TodoItem {

    // ─── Priority Constants ──────────────────────────────────────

    public static final String PRIORITY_NONE   = "none";
    public static final String PRIORITY_LOW    = "low";
    public static final String PRIORITY_MEDIUM = "medium";
    public static final String PRIORITY_HIGH   = "high";
    public static final String PRIORITY_URGENT = "urgent";

    public static final int COLOR_URGENT = 0xFFEF4444;
    public static final int COLOR_HIGH   = 0xFFF97316;
    public static final int COLOR_MEDIUM = 0xFF3B82F6;
    public static final int COLOR_LOW    = 0xFF6B7280;
    public static final int COLOR_NONE   = 0xFF374151;

    // ─── Status Constants ────────────────────────────────────────

    public static final String STATUS_ACTIVE    = "active";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    // ─── Recurrence Constants ────────────────────────────────────

    public static final String RECURRENCE_NONE    = "none";
    public static final String RECURRENCE_DAILY   = "daily";
    public static final String RECURRENCE_WEEKLY  = "weekly";
    public static final String RECURRENCE_MONTHLY = "monthly";
    public static final String RECURRENCE_YEARLY  = "yearly";

    // ─── Fields ──────────────────────────────────────────────────

    public String id;
    public String listId;
    public String title;
    public String description;
    public String priority;
    public String status;
    public String dueDate;              // "YYYY-MM-DD"
    public String dueTime;             // "HH:MM"
    public boolean isCompleted;
    public long completedAt;
    public long reminderDateTime;
    public String recurrence;
    public String recurrenceRule;
    public List<String> tags;
    public int sortOrder;
    public int estimatedDurationMinutes;
    public int actualDurationMinutes;
    public List<TimerSession> timerSessions;
    public List<SubtaskItem> subtasks;
    public long createdAt;
    public long updatedAt;

    // ─── Constructors ────────────────────────────────────────────

    public TodoItem() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.listId = "";
        this.title = "";
        this.description = "";
        this.priority = PRIORITY_NONE;
        this.status = STATUS_ACTIVE;
        this.dueDate = "";
        this.dueTime = "";
        this.isCompleted = false;
        this.completedAt = 0;
        this.reminderDateTime = 0;
        this.recurrence = RECURRENCE_NONE;
        this.recurrenceRule = "";
        this.tags = new ArrayList<>();
        this.sortOrder = 0;
        this.estimatedDurationMinutes = 0;
        this.actualDurationMinutes = 0;
        this.timerSessions = new ArrayList<>();
        this.subtasks = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public TodoItem(String listId, String title) {
        this();
        this.listId = listId != null ? listId : "";
        this.title = title != null ? title : "";
    }

    // ─── Priority Helpers ────────────────────────────────────────

    public int getPriorityColor() {
        if (priority == null) return COLOR_NONE;
        switch (priority) {
            case PRIORITY_URGENT: return COLOR_URGENT;
            case PRIORITY_HIGH:   return COLOR_HIGH;
            case PRIORITY_MEDIUM: return COLOR_MEDIUM;
            case PRIORITY_LOW:    return COLOR_LOW;
            default:              return COLOR_NONE;
        }
    }

    // ─── Due Date Helpers ────────────────────────────────────────

    /** Shared date-only format for parsing/formatting due dates. Not thread-safe; use via newDueDateFormat(). */
    private static SimpleDateFormat newDueDateFormat() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setLenient(false);
        return sdf;
    }

    /** Returns true if the item is not completed and its due date has passed. */
    public boolean isOverdue() {
        if (isCompleted || dueDate == null || dueDate.isEmpty()) return false;
        try {
            SimpleDateFormat sdf = newDueDateFormat();
            Date due = sdf.parse(dueDate);
            if (due == null) return false;
            Date today = sdf.parse(sdf.format(new Date()));
            return today != null && due.before(today);
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the number of days the item is past due, or 0 if not overdue. */
    public long getDaysOverdue() {
        if (!isOverdue()) return 0;
        try {
            Date due = newDueDateFormat().parse(dueDate);
            if (due == null) return 0;
            long diffMs = System.currentTimeMillis() - due.getTime();
            return TimeUnit.MILLISECONDS.toDays(diffMs);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Returns a human-readable string for the due date relative to today. */
    public String getRelativeDueDate() {
        if (dueDate == null || dueDate.isEmpty()) return "";
        try {
            SimpleDateFormat sdf = newDueDateFormat();
            Date due = sdf.parse(dueDate);
            if (due == null) return dueDate;

            Date todayDate = sdf.parse(sdf.format(new Date()));
            if (todayDate == null) return dueDate;

            long diffMs = due.getTime() - todayDate.getTime();
            long diffDays = TimeUnit.MILLISECONDS.toDays(diffMs);

            if (diffDays == 0) return "Today";
            if (diffDays == 1) return "Tomorrow";
            if (diffDays > 1)  return "In " + diffDays + " days";
            long overdueDays = -diffDays;
            return "Overdue by " + overdueDays + (overdueDays == 1 ? " day" : " days");
        } catch (Exception e) {
            return dueDate;
        }
    }

    // ─── Timer Helpers ───────────────────────────────────────────

    /** Returns the total tracked time across all timer sessions, in minutes. */
    public int getTotalTimeTrackedMinutes() {
        if (timerSessions == null) return 0;
        long totalSeconds = 0;
        for (TimerSession session : timerSessions) {
            totalSeconds += session.durationSeconds;
        }
        return (int) (totalSeconds / 60);
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("listId", listId);
            json.put("title", title);
            json.put("description", description);
            json.put("priority", priority);
            json.put("status", status);
            json.put("dueDate", dueDate);
            json.put("dueTime", dueTime);
            json.put("isCompleted", isCompleted);
            json.put("completedAt", completedAt);
            json.put("reminderDateTime", reminderDateTime);
            json.put("recurrence", recurrence);
            json.put("recurrenceRule", recurrenceRule);

            JSONArray tagsArr = new JSONArray();
            if (tags != null) {
                for (String tag : tags) tagsArr.put(tag);
            }
            json.put("tags", tagsArr);

            json.put("sortOrder", sortOrder);
            json.put("estimatedDurationMinutes", estimatedDurationMinutes);
            json.put("actualDurationMinutes", actualDurationMinutes);

            JSONArray sessionsArr = new JSONArray();
            if (timerSessions != null) {
                for (TimerSession session : timerSessions) sessionsArr.put(session.toJson());
            }
            json.put("timerSessions", sessionsArr);

            JSONArray subtasksArr = new JSONArray();
            if (subtasks != null) {
                for (SubtaskItem subtask : subtasks) subtasksArr.put(subtask.toJson());
            }
            json.put("subtasks", subtasksArr);

            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static TodoItem fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            TodoItem item = new TodoItem();
            item.id = json.optString("id", UUID.randomUUID().toString().substring(0, 12));
            item.listId = json.optString("listId", "");
            item.title = json.optString("title", "");
            item.description = json.optString("description", "");
            item.priority = json.optString("priority", PRIORITY_NONE);
            item.status = json.optString("status", STATUS_ACTIVE);
            item.dueDate = json.optString("dueDate", "");
            item.dueTime = json.optString("dueTime", "");
            item.isCompleted = json.optBoolean("isCompleted", false);
            item.completedAt = json.optLong("completedAt", 0);
            item.reminderDateTime = json.optLong("reminderDateTime", 0);
            item.recurrence = json.optString("recurrence", RECURRENCE_NONE);
            item.recurrenceRule = json.optString("recurrenceRule", "");

            item.tags = new ArrayList<>();
            JSONArray tagsArr = json.optJSONArray("tags");
            if (tagsArr != null) {
                for (int i = 0; i < tagsArr.length(); i++) {
                    item.tags.add(tagsArr.getString(i));
                }
            }

            item.sortOrder = json.optInt("sortOrder", 0);
            item.estimatedDurationMinutes = json.optInt("estimatedDurationMinutes", 0);
            item.actualDurationMinutes = json.optInt("actualDurationMinutes", 0);

            item.timerSessions = new ArrayList<>();
            JSONArray sessionsArr = json.optJSONArray("timerSessions");
            if (sessionsArr != null) {
                for (int i = 0; i < sessionsArr.length(); i++) {
                    TimerSession session = TimerSession.fromJson(sessionsArr.optJSONObject(i));
                    if (session != null) item.timerSessions.add(session);
                }
            }

            item.subtasks = new ArrayList<>();
            JSONArray subtasksArr = json.optJSONArray("subtasks");
            if (subtasksArr != null) {
                for (int i = 0; i < subtasksArr.length(); i++) {
                    SubtaskItem subtask = SubtaskItem.fromJson(subtasksArr.optJSONObject(i));
                    if (subtask != null) item.subtasks.add(subtask);
                }
            }

            item.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            item.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
