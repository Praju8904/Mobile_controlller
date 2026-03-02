package com.prajwal.myfirstapp.todo;


import com.prajwal.myfirstapp.tasks.SubtaskItem;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Repository for TodoList and TodoItem data — handles persistence via SharedPreferences,
 * CRUD operations, filtering, sorting, stats, timer sessions, and recurrence.
 */
public class TodoRepository {

    private static final String TAG = "TodoRepository";

    private static final String PREFS_NAME  = "todo_repository_prefs";
    private static final String LISTS_KEY   = "todo_lists_data";
    private static final String ITEMS_KEY   = "todo_items_data";

    private static final long MILLIS_PER_DAY  = 24L * 60 * 60 * 1000;
    private static final long MILLIS_PER_WEEK = 7 * MILLIS_PER_DAY;

    private final Context context;
    private ArrayList<TodoList> lists;
    private ArrayList<TodoItem> items;

    // ─── Constructor ─────────────────────────────────────────────

    public TodoRepository(Context context) {
        this.context = context;
        this.lists = new ArrayList<>();
        this.items = new ArrayList<>();
        loadLists();
        loadItems();
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Persistence ─────────────────────────────────────────────

    private void loadLists() {
        lists.clear();
        String json = getPrefs().getString(LISTS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                TodoList list = TodoList.fromJson(array.getJSONObject(i));
                if (list != null) lists.add(list);
            }
            Log.i(TAG, "Loaded " + lists.size() + " todo lists");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load lists: " + e.getMessage());
        }
    }

    private void saveLists() {
        JSONArray array = new JSONArray();
        for (TodoList list : lists) {
            array.put(list.toJson());
        }
        getPrefs().edit().putString(LISTS_KEY, array.toString()).apply();
    }

    private void loadItems() {
        items.clear();
        String json = getPrefs().getString(ITEMS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                TodoItem item = TodoItem.fromJson(array.getJSONObject(i));
                if (item != null) items.add(item);
            }
            Log.i(TAG, "Loaded " + items.size() + " todo items");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load items: " + e.getMessage());
        }
    }

    private void saveItems() {
        JSONArray array = new JSONArray();
        for (TodoItem item : items) {
            array.put(item.toJson());
        }
        getPrefs().edit().putString(ITEMS_KEY, array.toString()).apply();
    }

    // ─── TodoList CRUD ───────────────────────────────────────────

    public List<TodoList> getAllLists() {
        List<TodoList> copy = new ArrayList<>(lists);
        Collections.sort(copy, (a, b) -> Integer.compare(a.sortOrder, b.sortOrder));
        return copy;
    }

    public TodoList getListById(String id) {
        if (id == null) return null;
        for (TodoList list : lists) {
            if (id.equals(list.id)) return list;
        }
        return null;
    }

    public void addList(TodoList list) {
        if (list == null) return;
        lists.add(list);
        saveLists();
    }

    public void updateList(TodoList list) {
        if (list == null) return;
        list.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < lists.size(); i++) {
            if (lists.get(i).id.equals(list.id)) {
                lists.set(i, list);
                break;
            }
        }
        saveLists();
    }

    /** Deletes a list and all items belonging to it. */
    public void deleteList(String id) {
        if (id == null) return;
        lists.removeIf(l -> id.equals(l.id));
        items.removeIf(item -> id.equals(item.listId));
        saveLists();
        saveItems();
    }

    // ─── TodoItem CRUD ───────────────────────────────────────────

    public List<TodoItem> getAllItems() {
        return new ArrayList<>(items);
    }

    public List<TodoItem> getItemsByListId(String listId) {
        List<TodoItem> result = new ArrayList<>();
        if (listId == null) return result;
        for (TodoItem item : items) {
            if (listId.equals(item.listId)) result.add(item);
        }
        return result;
    }

    public TodoItem getItemById(String id) {
        if (id == null) return null;
        for (TodoItem item : items) {
            if (id.equals(item.id)) return item;
        }
        return null;
    }

    public void addItem(TodoItem item) {
        if (item == null) return;
        items.add(0, item);
        saveItems();
    }

    public void updateItem(TodoItem item) {
        if (item == null) return;
        item.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(item.id)) {
                items.set(i, item);
                break;
            }
        }
        saveItems();
    }

    public void deleteItem(String id) {
        if (id == null) return;
        items.removeIf(item -> id.equals(item.id));
        saveItems();
    }

    // ─── Complete Item & Recurrence ──────────────────────────────

    /** Marks an item complete; if recurring, schedules the next occurrence. */
    public void completeItem(String itemId) {
        TodoItem item = getItemById(itemId);
        if (item == null) return;
        item.isCompleted = true;
        item.status = TodoItem.STATUS_COMPLETED;
        item.completedAt = System.currentTimeMillis();
        item.updatedAt = System.currentTimeMillis();
        saveItems();

        if (!TodoItem.RECURRENCE_NONE.equals(item.recurrence)) {
            createNextRecurrence(item);
        }
    }

    /**
     * Creates the next occurrence of a recurring item.
     * Calculates next dueDate from recurrence rule and inserts a fresh item.
     */
    public void createNextRecurrence(TodoItem completed) {
        if (completed == null) return;
        String nextDueDate = calculateNextDueDate(completed.dueDate, completed.recurrence);
        if (nextDueDate == null) return;

        TodoItem next = new TodoItem(completed.listId, completed.title);
        next.description            = completed.description;
        next.priority               = completed.priority;
        next.status                 = TodoItem.STATUS_ACTIVE;
        next.dueDate                = nextDueDate;
        next.dueTime                = completed.dueTime;
        next.isCompleted            = false;
        next.completedAt            = 0;
        next.reminderDateTime       = 0;
        next.recurrence             = completed.recurrence;
        next.recurrenceRule         = completed.recurrenceRule;
        next.tags                   = new ArrayList<>(completed.tags != null ? completed.tags : new ArrayList<>());
        next.sortOrder              = completed.sortOrder;
        next.estimatedDurationMinutes = completed.estimatedDurationMinutes;
        addItem(next);
        Log.i(TAG, "Created next recurrence '" + next.title + "' due " + nextDueDate);
    }

    private String calculateNextDueDate(String dueDate, String recurrence) {
        if (dueDate == null || dueDate.isEmpty() || recurrence == null) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            java.util.Date parsed = sdf.parse(dueDate);
            if (parsed == null) return null;

            Calendar cal = Calendar.getInstance();
            cal.setTime(parsed);

            switch (recurrence) {
                case TodoItem.RECURRENCE_DAILY:   cal.add(Calendar.DATE, 1);         break;
                case TodoItem.RECURRENCE_WEEKLY:  cal.add(Calendar.WEEK_OF_YEAR, 1); break;
                case TodoItem.RECURRENCE_MONTHLY: cal.add(Calendar.MONTH, 1);        break;
                case TodoItem.RECURRENCE_YEARLY:  cal.add(Calendar.YEAR, 1);         break;
                default: return null;
            }
            return sdf.format(cal.getTime());
        } catch (Exception e) {
            Log.e(TAG, "Error calculating next due date: " + e.getMessage());
            return null;
        }
    }

    // ─── Filtering ───────────────────────────────────────────────

    /** Items in a list that are active (status=active) and not completed. */
    public List<TodoItem> getActiveItems(String listId) {
        List<TodoItem> result = new ArrayList<>();
        for (TodoItem item : getItemsByListId(listId)) {
            if (TodoItem.STATUS_ACTIVE.equals(item.status) && !item.isCompleted) {
                result.add(item);
            }
        }
        return result;
    }

    /** Items in a list that are completed. */
    public List<TodoItem> getCompletedItems(String listId) {
        List<TodoItem> result = new ArrayList<>();
        for (TodoItem item : getItemsByListId(listId)) {
            if (item.isCompleted) result.add(item);
        }
        return result;
    }

    /** Active items in a list whose due date has passed. */
    public List<TodoItem> getOverdueItems(String listId) {
        List<TodoItem> result = new ArrayList<>();
        for (TodoItem item : getActiveItems(listId)) {
            if (item.isOverdue()) result.add(item);
        }
        return result;
    }

    /** Active items in a list with priority HIGH or URGENT. */
    public List<TodoItem> getHighPriorityItems(String listId) {
        List<TodoItem> result = new ArrayList<>();
        for (TodoItem item : getActiveItems(listId)) {
            if (TodoItem.PRIORITY_HIGH.equals(item.priority)
                    || TodoItem.PRIORITY_URGENT.equals(item.priority)) {
                result.add(item);
            }
        }
        return result;
    }

    /** Items in a list that contain the given tag. */
    public List<TodoItem> getItemsByTag(String listId, String tag) {
        List<TodoItem> result = new ArrayList<>();
        if (tag == null) return result;
        for (TodoItem item : getItemsByListId(listId)) {
            if (item.tags != null && item.tags.contains(tag)) result.add(item);
        }
        return result;
    }

    // ─── Sorting ─────────────────────────────────────────────────

    public void sortByDueDate(List<TodoItem> list) {
        Collections.sort(list, (a, b) -> {
            boolean aEmpty = a.dueDate == null || a.dueDate.isEmpty();
            boolean bEmpty = b.dueDate == null || b.dueDate.isEmpty();
            if (aEmpty && bEmpty) return 0;
            if (aEmpty) return 1;
            if (bEmpty) return -1;
            return a.dueDate.compareTo(b.dueDate);
        });
    }

    public void sortByPriority(List<TodoItem> list) {
        Collections.sort(list, (a, b) ->
                Integer.compare(priorityOrdinal(b.priority), priorityOrdinal(a.priority)));
    }

    public void sortByCreated(List<TodoItem> list) {
        Collections.sort(list, (a, b) -> Long.compare(b.createdAt, a.createdAt));
    }

    private int priorityOrdinal(String priority) {
        if (priority == null) return 0;
        switch (priority) {
            case TodoItem.PRIORITY_URGENT: return 4;
            case TodoItem.PRIORITY_HIGH:   return 3;
            case TodoItem.PRIORITY_MEDIUM: return 2;
            case TodoItem.PRIORITY_LOW:    return 1;
            default:                       return 0;
        }
    }

    // ─── Stats Per List ──────────────────────────────────────────

    /** Returns completion rate for a list as a float from 0.0 to 1.0. */
    public float getCompletionRate(String listId) {
        List<TodoItem> all = getItemsByListId(listId);
        if (all.isEmpty()) return 0f;
        int completed = 0;
        for (TodoItem item : all) {
            if (item.isCompleted) completed++;
        }
        return (float) completed / all.size();
    }

    /** Returns the number of overdue items in a list. */
    public int getOverdueCount(String listId) {
        return getOverdueItems(listId).size();
    }

    /** Returns items in a list completed within the last 7 days. */
    public List<TodoItem> getCompletedThisWeek(String listId) {
        long weekAgo = System.currentTimeMillis() - MILLIS_PER_WEEK;
        List<TodoItem> result = new ArrayList<>();
        for (TodoItem item : getCompletedItems(listId)) {
            if (item.completedAt >= weekAgo) result.add(item);
        }
        return result;
    }

    /** Returns a map of priority name → item count for all items in a list. */
    public Map<String, Integer> getPriorityBreakdown(String listId) {
        Map<String, Integer> breakdown = new HashMap<>();
        breakdown.put(TodoItem.PRIORITY_NONE,   0);
        breakdown.put(TodoItem.PRIORITY_LOW,    0);
        breakdown.put(TodoItem.PRIORITY_MEDIUM, 0);
        breakdown.put(TodoItem.PRIORITY_HIGH,   0);
        breakdown.put(TodoItem.PRIORITY_URGENT, 0);

        for (TodoItem item : getItemsByListId(listId)) {
            String p = item.priority != null ? item.priority : TodoItem.PRIORITY_NONE;
            if (breakdown.containsKey(p)) {
                breakdown.put(p, breakdown.get(p) + 1);
            }
        }
        return breakdown;
    }

    /** Returns the total tracked time in minutes across all items in a list. */
    public int getTotalTimeTrackedMinutes(String listId) {
        int total = 0;
        for (TodoItem item : getItemsByListId(listId)) {
            total += item.getTotalTimeTrackedMinutes();
        }
        return total;
    }

    // ─── Global Stats ────────────────────────────────────────────

    /** Returns the total count of active (non-completed) items across all lists. */
    public int getGlobalActiveCount() {
        int count = 0;
        for (TodoItem item : items) {
            if (TodoItem.STATUS_ACTIVE.equals(item.status) && !item.isCompleted) count++;
        }
        return count;
    }

    /** Returns the total count of overdue items across all lists. */
    public int getGlobalOverdueCount() {
        int count = 0;
        for (TodoItem item : items) {
            if (!item.isCompleted && item.isOverdue()) count++;
        }
        return count;
    }

    // ─── Timer Sessions ──────────────────────────────────────────

    /** Adds a timer session to an item and persists. */
    public void addTimerSession(String itemId, TimerSession session) {
        if (itemId == null || session == null) return;
        TodoItem item = getItemById(itemId);
        if (item == null) return;
        if (item.timerSessions == null) item.timerSessions = new ArrayList<>();
        item.timerSessions.add(session);
        item.actualDurationMinutes = item.getTotalTimeTrackedMinutes();
        item.updatedAt = System.currentTimeMillis();
        saveItems();
        Log.i(TAG, "Added timer session to item: " + itemId);
    }

    // ─── Bulk operations ─────────────────────────────────────────

    /** Permanently deletes all completed to-do items across all lists. */
    public void clearAllCompleted() {
        List<TodoItem> all = new ArrayList<>(items);
        for (TodoItem item : all) {
            if (item.isCompleted || TodoItem.STATUS_COMPLETED.equals(item.status)) {
                items.remove(item);
            }
        }
        saveItems();
        Log.i(TAG, "Cleared all completed to-do items");
    }

    // ─── Subtasks ────────────────────────────────────────────────

    /** Replaces the subtask list for an item and persists. */
    public void updateSubtasks(String itemId, List<SubtaskItem> subtasks) {
        if (itemId == null) return;
        TodoItem item = getItemById(itemId);
        if (item == null) return;
        item.subtasks = subtasks != null ? subtasks : new ArrayList<>();
        item.updatedAt = System.currentTimeMillis();
        saveItems();
        Log.i(TAG, "Updated subtasks for item: " + itemId);
    }
}
