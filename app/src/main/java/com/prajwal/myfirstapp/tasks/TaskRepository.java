package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for Task data — handles persistence, CRUD, filtering,
 * sorting, searching, bulk operations, categories, trash management,
 * and migration from the old flat task format.
 */
public class TaskRepository {

    private static final String TAG = "TaskRepository";

    // Prefs for new task data
    private static final String PREFS_NAME = "task_manager_v2_prefs";
    private static final String TASKS_KEY = "tasks_data";
    private static final String CATEGORIES_KEY = "custom_categories";
    private static final String SORT_MODE_KEY = "sort_mode";
    private static final String GROUP_MODE_KEY = "group_mode";

    // Old prefs for migration
    private static final String OLD_PREFS_NAME = "task_manager_prefs";
    private static final String OLD_TASKS_KEY = "tasks_json";
    private static final String MIGRATION_DONE_KEY = "migration_v2_done";

    // Trash retention: 30 days
    private static final long TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000;

    // Sort modes
    public static final String SORT_PRIORITY   = "priority";
    public static final String SORT_DUE_DATE   = "due_date";
    public static final String SORT_CREATED     = "created";
    public static final String SORT_TITLE       = "title";
    public static final String SORT_STATUS      = "status";

    // Group modes
    public static final String GROUP_NONE     = "none";
    public static final String GROUP_DUE_DATE = "due_date";
    public static final String GROUP_PRIORITY = "priority";
    public static final String GROUP_CATEGORY = "category";
    public static final String GROUP_STATUS   = "status";

    private final Context context;
    private ArrayList<Task> tasks;
    private ArrayList<TaskCategory> customCategories;

    // ─── Constructor ─────────────────────────────────────────────

    public TaskRepository(Context context) {
        this.context = context;
        this.tasks = new ArrayList<>();
        this.customCategories = new ArrayList<>();
        loadTasks();
        loadCustomCategories();
        migrateOldTasksIfNeeded();
        purgeExpiredTrash();
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Persistence ─────────────────────────────────────────────

    private void loadTasks() {
        tasks.clear();
        String json = getPrefs().getString(TASKS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                Task task = Task.fromJson(array.getJSONObject(i));
                if (task != null) tasks.add(task);
            }
            Log.i(TAG, "Loaded " + tasks.size() + " tasks");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load tasks: " + e.getMessage());
        }
    }

    private void saveTasks() {
        JSONArray array = new JSONArray();
        for (Task task : tasks) {
            array.put(task.toJson());
        }
        getPrefs().edit().putString(TASKS_KEY, array.toString()).apply();
    }

    // ─── Migration ───────────────────────────────────────────────

    private void migrateOldTasksIfNeeded() {
        if (getPrefs().getBoolean(MIGRATION_DONE_KEY, false)) return;

        SharedPreferences oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE);
        String oldJson = oldPrefs.getString(OLD_TASKS_KEY, "[]");

        try {
            JSONArray oldArray = new JSONArray(oldJson);
            if (oldArray.length() > 0) {
                int migrated = 0;
                for (int i = 0; i < oldArray.length(); i++) {
                    Task task = Task.fromLegacyJson(oldArray.getJSONObject(i));
                    if (task != null) {
                        // Check if already exists (by title match to avoid duplicates)
                        boolean exists = false;
                        for (Task existing : tasks) {
                            if (existing.title.equals(task.title)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            tasks.add(task);
                            migrated++;
                        }
                    }
                }
                if (migrated > 0) {
                    saveTasks();
                    Log.i(TAG, "Migrated " + migrated + " tasks from old format");
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Migration error: " + e.getMessage());
        }

        getPrefs().edit().putBoolean(MIGRATION_DONE_KEY, true).apply();
    }

    // ─── CRUD Operations ─────────────────────────────────────────

    public void addTask(Task task) {
        tasks.add(0, task);
        saveTasks();
    }

    public void updateTask(Task task) {
        task.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id.equals(task.id)) {
                tasks.set(i, task);
                break;
            }
        }
        saveTasks();
    }

    public Task getTaskById(String id) {
        for (Task task : tasks) {
            if (task.id.equals(id)) return task;
        }
        return null;
    }

    public void deleteTaskPermanently(String id) {
        tasks.removeIf(t -> t.id.equals(id));
        saveTasks();
    }

    public Task duplicateTask(String id) {
        Task original = getTaskById(id);
        if (original == null) return null;
        Task copy = original.duplicate();
        tasks.add(0, copy);
        saveTasks();
        return copy;
    }

    // ─── Status Operations ───────────────────────────────────────

    public void completeTask(String id) {
        Task task = getTaskById(id);
        if (task != null) {
            task.markCompleted();
            saveTasks();
        }
    }

    public void uncompleteTask(String id) {
        Task task = getTaskById(id);
        if (task != null) {
            task.markTodo();
            saveTasks();
        }
    }

    public void updateStatus(String id, String newStatus) {
        Task task = getTaskById(id);
        if (task != null) {
            task.status = newStatus;
            task.updatedAt = System.currentTimeMillis();
            if (Task.STATUS_COMPLETED.equals(newStatus)) {
                task.completedAt = System.currentTimeMillis();
            } else {
                task.completedAt = 0;
            }
            saveTasks();
        }
    }

    // ─── Star Operations ─────────────────────────────────────────

    public void toggleStar(String id) {
        Task task = getTaskById(id);
        if (task != null) {
            task.isStarred = !task.isStarred;
            task.updatedAt = System.currentTimeMillis();
            saveTasks();
        }
    }

    // ─── Trash Operations ────────────────────────────────────────

    public void trashTask(String id) {
        Task task = getTaskById(id);
        if (task != null) {
            task.moveToTrash();
            saveTasks();
        }
    }

    public void restoreFromTrash(String id) {
        Task task = getTaskById(id);
        if (task != null) {
            task.restoreFromTrash();
            saveTasks();
        }
    }

    public List<Task> getTrashedTasks() {
        List<Task> trashed = new ArrayList<>();
        for (Task task : tasks) {
            if (task.isTrashed) trashed.add(task);
        }
        // Sort by trashed date descending
        Collections.sort(trashed, (a, b) -> Long.compare(b.trashedAt, a.trashedAt));
        return trashed;
    }

    public void clearTrash() {
        tasks.removeIf(t -> t.isTrashed);
        saveTasks();
    }

    public void purgeExpiredTrash() {
        int removed = 0;
        for (int i = tasks.size() - 1; i >= 0; i--) {
            Task task = tasks.get(i);
            if (task.isTrashed && task.isTrashExpired()) {
                tasks.remove(i);
                removed++;
            }
        }
        if (removed > 0) {
            saveTasks();
            Log.i(TAG, "Purged " + removed + " expired trashed tasks");
        }
    }

    // ─── Bulk Operations ─────────────────────────────────────────

    public void bulkComplete(List<String> ids) {
        for (String id : ids) {
            Task task = getTaskById(id);
            if (task != null) task.markCompleted();
        }
        saveTasks();
    }

    public void bulkTrash(List<String> ids) {
        for (String id : ids) {
            Task task = getTaskById(id);
            if (task != null) task.moveToTrash();
        }
        saveTasks();
    }

    public void bulkUpdateCategory(List<String> ids, String category) {
        for (String id : ids) {
            Task task = getTaskById(id);
            if (task != null) {
                task.category = category;
                task.updatedAt = System.currentTimeMillis();
            }
        }
        saveTasks();
    }

    public void bulkUpdatePriority(List<String> ids, String priority) {
        for (String id : ids) {
            Task task = getTaskById(id);
            if (task != null) {
                task.priority = priority;
                task.updatedAt = System.currentTimeMillis();
            }
        }
        saveTasks();
    }

    public void bulkStar(List<String> ids) {
        for (String id : ids) {
            Task task = getTaskById(id);
            if (task != null) {
                task.isStarred = true;
                task.updatedAt = System.currentTimeMillis();
            }
        }
        saveTasks();
    }

    public void bulkSetDueDate(List<String> ids, String dueDate) {
        for (String id : ids) {
            Task task = getTaskById(id);
            if (task != null) {
                task.dueDate = dueDate;
                task.updatedAt = System.currentTimeMillis();
            }
        }
        saveTasks();
    }

    // ─── Category Change ─────────────────────────────────────────

    public void moveToCategory(String taskId, String category) {
        Task task = getTaskById(taskId);
        if (task != null) {
            task.category = category;
            task.updatedAt = System.currentTimeMillis();
            saveTasks();
        }
    }

    // ─── Query: Active Tasks (not trashed) ───────────────────────

    public List<Task> getActiveTasks() {
        List<Task> active = new ArrayList<>();
        for (Task task : tasks) {
            if (!task.isTrashed) active.add(task);
        }
        return active;
    }

    public List<Task> getActiveNonCompletedTasks() {
        List<Task> result = new ArrayList<>();
        for (Task task : tasks) {
            if (!task.isTrashed && task.isActive()) result.add(task);
        }
        return result;
    }

    // ─── Filtering ───────────────────────────────────────────────

    public List<Task> filterTasks(String filter) {
        List<Task> active = getActiveTasks();
        if (filter == null || filter.equals("All")) {
            return active;
        }
        List<Task> filtered = new ArrayList<>();
        for (Task task : active) {
            switch (filter) {
                case "Today":
                    if (task.isDueToday() && !task.isCompleted()) filtered.add(task);
                    break;
                case "Upcoming":
                    if (task.hasDueDate() && !task.isOverdue() && !task.isDueToday() && !task.isCompleted())
                        filtered.add(task);
                    break;
                case "Overdue":
                    if (task.isOverdue()) filtered.add(task);
                    break;
                case "Starred":
                    if (task.isStarred && !task.isCompleted()) filtered.add(task);
                    break;
                case "Completed":
                    if (task.isCompleted()) filtered.add(task);
                    break;
                case "By Priority":
                    if (!task.isCompleted()) filtered.add(task);
                    break;
                case "By Category":
                    if (!task.isCompleted()) filtered.add(task);
                    break;
                default:
                    // Category name filter
                    if (task.category != null && task.category.equals(filter) && !task.isCompleted())
                        filtered.add(task);
                    break;
            }
        }
        return filtered;
    }

    // ─── Searching ───────────────────────────────────────────────

    public List<Task> searchTasks(String query) {
        if (query == null || query.trim().isEmpty()) return getActiveTasks();
        List<Task> results = new ArrayList<>();
        for (Task task : tasks) {
            if (!task.isTrashed && task.matchesSearch(query)) {
                results.add(task);
            }
        }
        return results;
    }

    // ─── Sorting ─────────────────────────────────────────────────

    public void sortTasks(List<Task> taskList, String sortMode) {
        if (sortMode == null) sortMode = SORT_PRIORITY;
        switch (sortMode) {
            case SORT_PRIORITY:
                Collections.sort(taskList, (a, b) -> {
                    // Active before completed
                    if (a.isCompleted() != b.isCompleted()) return a.isCompleted() ? 1 : -1;
                    // Starred first
                    if (a.isStarred != b.isStarred) return a.isStarred ? -1 : 1;
                    // Higher priority first
                    int pa = a.getPriorityWeight();
                    int pb = b.getPriorityWeight();
                    if (pa != pb) return Integer.compare(pa, pb);
                    // Earlier due date first
                    return compareDueDates(a, b);
                });
                break;

            case SORT_DUE_DATE:
                Collections.sort(taskList, (a, b) -> {
                    if (a.isCompleted() != b.isCompleted()) return a.isCompleted() ? 1 : -1;
                    return compareDueDates(a, b);
                });
                break;

            case SORT_CREATED:
                Collections.sort(taskList, (a, b) -> {
                    if (a.isCompleted() != b.isCompleted()) return a.isCompleted() ? 1 : -1;
                    return Long.compare(b.createdAt, a.createdAt);
                });
                break;

            case SORT_TITLE:
                Collections.sort(taskList, (a, b) -> {
                    if (a.isCompleted() != b.isCompleted()) return a.isCompleted() ? 1 : -1;
                    return a.title.compareToIgnoreCase(b.title);
                });
                break;

            case SORT_STATUS:
                Collections.sort(taskList, (a, b) -> {
                    int sa = statusWeight(a.status);
                    int sb = statusWeight(b.status);
                    if (sa != sb) return Integer.compare(sa, sb);
                    return Integer.compare(a.getPriorityWeight(), b.getPriorityWeight());
                });
                break;
        }
    }

    private int compareDueDates(Task a, Task b) {
        boolean aHas = a.hasDueDate();
        boolean bHas = b.hasDueDate();
        if (!aHas && !bHas) return Long.compare(b.createdAt, a.createdAt);
        if (!aHas) return 1;
        if (!bHas) return -1;
        int cmp = a.dueDate.compareTo(b.dueDate);
        if (cmp != 0) return cmp;
        if (a.hasDueTime() && b.hasDueTime()) return a.dueTime.compareTo(b.dueTime);
        return 0;
    }

    private int statusWeight(String status) {
        if (status == null) return 2;
        switch (status) {
            case Task.STATUS_INPROGRESS: return 0;
            case Task.STATUS_TODO:       return 1;
            case Task.STATUS_COMPLETED:  return 2;
            case Task.STATUS_CANCELLED:  return 3;
            default:                     return 2;
        }
    }

    // ─── Grouping ────────────────────────────────────────────────

    public LinkedHashMap<String, List<Task>> groupTasks(List<Task> taskList, String groupMode) {
        LinkedHashMap<String, List<Task>> groups = new LinkedHashMap<>();
        if (groupMode == null || GROUP_NONE.equals(groupMode)) {
            groups.put("All Tasks", taskList);
            return groups;
        }

        for (Task task : taskList) {
            String key;
            switch (groupMode) {
                case GROUP_DUE_DATE:
                    key = task.getDueDateGroup();
                    break;
                case GROUP_PRIORITY:
                    key = task.getPriorityLabel().isEmpty() ? "None" : task.getPriorityLabel();
                    break;
                case GROUP_CATEGORY:
                    key = task.category != null ? task.category : "Uncategorized";
                    break;
                case GROUP_STATUS:
                    key = getStatusLabel(task.status);
                    break;
                default:
                    key = "All Tasks";
                    break;
            }
            if (!groups.containsKey(key)) {
                groups.put(key, new ArrayList<>());
            }
            groups.get(key).add(task);
        }

        // Order groups logically
        if (GROUP_DUE_DATE.equals(groupMode)) {
            return reorderMap(groups, new String[]{"Overdue", "Today", "Tomorrow", "This Week", "Later", "No Date"});
        } else if (GROUP_PRIORITY.equals(groupMode)) {
            return reorderMap(groups, new String[]{"URGENT", "HIGH", "NORMAL", "LOW", "None"});
        } else if (GROUP_STATUS.equals(groupMode)) {
            return reorderMap(groups, new String[]{"In Progress", "To Do", "Completed", "Cancelled"});
        }

        return groups;
    }

    private LinkedHashMap<String, List<Task>> reorderMap(LinkedHashMap<String, List<Task>> map, String[] order) {
        LinkedHashMap<String, List<Task>> ordered = new LinkedHashMap<>();
        for (String key : order) {
            if (map.containsKey(key)) {
                ordered.put(key, map.get(key));
            }
        }
        // Add any remaining groups not in the order
        for (Map.Entry<String, List<Task>> entry : map.entrySet()) {
            if (!ordered.containsKey(entry.getKey())) {
                ordered.put(entry.getKey(), entry.getValue());
            }
        }
        return ordered;
    }

    private String getStatusLabel(String status) {
        if (status == null) return "To Do";
        switch (status) {
            case Task.STATUS_TODO:       return "To Do";
            case Task.STATUS_INPROGRESS: return "In Progress";
            case Task.STATUS_COMPLETED:  return "Completed";
            case Task.STATUS_CANCELLED:  return "Cancelled";
            default:                     return "To Do";
        }
    }

    // ─── Statistics ──────────────────────────────────────────────

    public int getTotalActiveCount() {
        int count = 0;
        for (Task t : tasks) if (!t.isTrashed) count++;
        return count;
    }

    public int getTotalTodayCount() {
        int count = 0;
        for (Task t : tasks) {
            if (!t.isTrashed && t.isDueToday()) count++;
        }
        return count;
    }

    public int getCompletedTodayCount() {
        int count = 0;
        long todayStart = getTodayStartMillis();
        for (Task t : tasks) {
            if (!t.isTrashed && t.isCompleted() && t.completedAt >= todayStart) count++;
        }
        return count;
    }

    public int getOverdueCount() {
        int count = 0;
        for (Task t : tasks) {
            if (!t.isTrashed && t.isOverdue()) count++;
        }
        return count;
    }

    public int getPendingCount() {
        int count = 0;
        for (Task t : tasks) {
            if (!t.isTrashed && t.isActive()) count++;
        }
        return count;
    }

    public int getStarredCount() {
        int count = 0;
        for (Task t : tasks) {
            if (!t.isTrashed && t.isStarred && !t.isCompleted()) count++;
        }
        return count;
    }

    public int getTaskCountByCategory(String category) {
        int count = 0;
        for (Task t : tasks) {
            if (!t.isTrashed && category.equals(t.category)) count++;
        }
        return count;
    }

    public int getTrashCount() {
        int count = 0;
        for (Task t : tasks) if (t.isTrashed) count++;
        return count;
    }

    private long getTodayStartMillis() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ─── Today's Focus ───────────────────────────────────────────

    public List<Task> getTodayFocusTasks() {
        List<Task> todayTasks = new ArrayList<>();
        for (Task task : tasks) {
            if (!task.isTrashed && !task.isCompleted() && task.isDueToday()) {
                todayTasks.add(task);
            }
        }
        // Sort by priority then time
        Collections.sort(todayTasks, (a, b) -> {
            int pa = a.getPriorityWeight();
            int pb = b.getPriorityWeight();
            if (pa != pb) return Integer.compare(pa, pb);
            if (a.hasDueTime() && b.hasDueTime()) return a.dueTime.compareTo(b.dueTime);
            return 0;
        });
        return todayTasks;
    }

    // ─── Overdue Tasks ───────────────────────────────────────────

    public List<Task> getOverdueTasks() {
        List<Task> overdue = new ArrayList<>();
        for (Task task : tasks) {
            if (!task.isTrashed && task.isOverdue()) overdue.add(task);
        }
        return overdue;
    }

    // ─── Categories Management ───────────────────────────────────

    private void loadCustomCategories() {
        customCategories.clear();
        String json = getPrefs().getString(CATEGORIES_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                TaskCategory cat = TaskCategory.fromJson(array.getJSONObject(i));
                if (cat != null) customCategories.add(cat);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load custom categories: " + e.getMessage());
        }
    }

    private void saveCustomCategories() {
        JSONArray array = new JSONArray();
        for (TaskCategory cat : customCategories) {
            array.put(cat.toJson());
        }
        getPrefs().edit().putString(CATEGORIES_KEY, array.toString()).apply();
    }

    public List<TaskCategory> getAllCategories() {
        List<TaskCategory> all = new ArrayList<>();
        for (TaskCategory def : TaskCategory.DEFAULTS) {
            all.add(def);
        }
        all.addAll(customCategories);
        return all;
    }

    public List<String> getAllCategoryNames() {
        List<String> names = new ArrayList<>();
        for (TaskCategory cat : getAllCategories()) {
            names.add(cat.name);
        }
        return names;
    }

    public void addCustomCategory(TaskCategory category) {
        customCategories.add(category);
        saveCustomCategories();
    }

    public void updateCustomCategory(TaskCategory category) {
        for (int i = 0; i < customCategories.size(); i++) {
            if (customCategories.get(i).id.equals(category.id)) {
                customCategories.set(i, category);
                break;
            }
        }
        saveCustomCategories();
    }

    public void deleteCustomCategory(String categoryId) {
        String categoryName = null;
        for (TaskCategory cat : customCategories) {
            if (cat.id.equals(categoryId)) {
                categoryName = cat.name;
                break;
            }
        }
        customCategories.removeIf(c -> c.id.equals(categoryId));
        saveCustomCategories();

        // Move tasks in this category to "Others"
        if (categoryName != null) {
            for (Task task : tasks) {
                if (categoryName.equals(task.category)) {
                    task.category = "Others";
                    task.updatedAt = System.currentTimeMillis();
                }
            }
            saveTasks();
        }
    }

    public List<TaskCategory> getCustomCategories() {
        return new ArrayList<>(customCategories);
    }

    public TaskCategory getCategoryByName(String name) {
        for (TaskCategory cat : getAllCategories()) {
            if (cat.name.equals(name)) return cat;
        }
        return null;
    }

    // ─── Sort/Group Preferences ──────────────────────────────────

    public String getSavedSortMode() {
        return getPrefs().getString(SORT_MODE_KEY, SORT_PRIORITY);
    }

    public void saveSortMode(String mode) {
        getPrefs().edit().putString(SORT_MODE_KEY, mode).apply();
    }

    public String getSavedGroupMode() {
        return getPrefs().getString(GROUP_MODE_KEY, GROUP_NONE);
    }

    public void saveGroupMode(String mode) {
        getPrefs().edit().putString(GROUP_MODE_KEY, mode).apply();
    }

    // ─── Sync Support ────────────────────────────────────────────

    /**
     * Get all non-trashed tasks as a JSON string for syncing to PC.
     */
    public String getTasksJsonForSync() {
        JSONArray array = new JSONArray();
        for (Task task : tasks) {
            if (!task.isTrashed) {
                array.put(task.toJson());
            }
        }
        return array.toString();
    }

    /**
     * Receive synced tasks from PC (merges with local, PC wins on conflicts).
     */
    public void onSyncReceived(String tasksJson) {
        try {
            JSONArray jsonArray = new JSONArray(tasksJson);
            Map<String, Task> localMap = new HashMap<>();
            for (Task t : tasks) localMap.put(t.id, t);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                Task incoming = Task.fromJson(obj);
                if (incoming == null) continue;

                // Also try legacy format
                if (incoming.title.isEmpty() && obj.has("title")) {
                    incoming = Task.fromLegacyJson(obj);
                    if (incoming == null) continue;
                }

                Task existing = localMap.get(incoming.id);
                if (existing == null) {
                    // New task from PC
                    incoming.source = "pc";
                    tasks.add(incoming);
                } else if (incoming.updatedAt > existing.updatedAt) {
                    // PC version is newer — update local
                    incoming.source = existing.source;
                    int idx = tasks.indexOf(existing);
                    if (idx >= 0) tasks.set(idx, incoming);
                }
            }

            saveTasks();
            Log.i(TAG, "Sync received: processed " + jsonArray.length() + " tasks");
        } catch (JSONException e) {
            Log.e(TAG, "Sync parse error: " + e.getMessage());
        }
    }

    // ─── Full Reload ─────────────────────────────────────────────

    public void reload() {
        loadTasks();
        loadCustomCategories();
    }

    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks);
    }

    // ─── Productivity Stats ──────────────────────────────────────

    /**
     * Get completed tasks per day for the last 7 days.
     * Returns an array of 7 counts, index 0 = 6 days ago, index 6 = today.
     */
    public int[] getCompletedLast7Days() {
        int[] counts = new int[7];
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);

        long todayStart = cal.getTimeInMillis();
        for (Task t : tasks) {
            if (t.isCompleted() && t.completedAt > 0) {
                long diff = todayStart - t.completedAt;
                long dayMs = 24L * 60 * 60 * 1000;
                // today = index 6, yesterday = 5, etc.
                if (t.completedAt >= todayStart) {
                    counts[6]++;
                } else {
                    int daysAgo = (int) (diff / dayMs) + 1;
                    if (daysAgo >= 1 && daysAgo <= 6) {
                        counts[6 - daysAgo]++;
                    }
                }
            }
        }
        return counts;
    }

    /**
     * Overall completion rate (completed / total excluding trashed).
     */
    public float getCompletionRate() {
        int total = 0, completed = 0;
        for (Task t : tasks) {
            if (!t.isTrashed) {
                total++;
                if (t.isCompleted()) completed++;
            }
        }
        return total > 0 ? (float) completed / total : 0f;
    }

    /**
     * Average time to complete a task (in minutes). Based on completedAt - createdAt.
     */
    public int getAverageCompletionMinutes() {
        long totalMs = 0;
        int count = 0;
        for (Task t : tasks) {
            if (t.isCompleted() && t.completedAt > t.createdAt) {
                totalMs += (t.completedAt - t.createdAt);
                count++;
            }
        }
        if (count == 0) return 0;
        return (int) ((totalMs / count) / 60000);
    }

    /**
     * Most productive day of week (0=Sun..6=Sat). Returns day index or -1.
     */
    public int getMostProductiveDay() {
        int[] dayCounts = new int[7];
        for (Task t : tasks) {
            if (t.isCompleted() && t.completedAt > 0) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTimeInMillis(t.completedAt);
                int dow = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1; // 0=Sun
                dayCounts[dow]++;
            }
        }
        int maxIdx = -1, maxVal = 0;
        for (int i = 0; i < 7; i++) {
            if (dayCounts[i] > maxVal) {
                maxVal = dayCounts[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    /**
     * Current streak — days in a row with at least one completed task (including today).
     */
    public int getCurrentStreak() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);

        long dayMs = 24L * 60 * 60 * 1000;
        int streak = 0;

        for (int d = 0; d < 365; d++) {
            long dayStart = cal.getTimeInMillis() - (d * dayMs);
            long dayEnd = dayStart + dayMs;
            boolean found = false;
            for (Task t : tasks) {
                if (t.isCompleted() && t.completedAt >= dayStart && t.completedAt < dayEnd) {
                    found = true;
                    break;
                }
            }
            if (found) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    /**
     * Tasks per category (for donut chart). Returns map of categoryName -> count.
     */
    public LinkedHashMap<String, Integer> getTasksByCategory() {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        for (Task t : tasks) {
            if (!t.isTrashed) {
                String cat = t.category != null ? t.category : "Others";
                map.put(cat, map.getOrDefault(cat, 0) + 1);
            }
        }
        return map;
    }

    /**
     * Total completed tasks count (all time).
     */
    public int getTotalCompletedCount() {
        int count = 0;
        for (Task t : tasks) {
            if (!t.isTrashed && t.isCompleted()) count++;
        }
        return count;
    }

    /**
     * Get all unique tags across all tasks.
     */
    public List<String> getAllTags() {
        List<String> allTags = new ArrayList<>();
        for (Task t : tasks) {
            if (t.tags != null) {
                for (String tag : t.tags) {
                    if (!allTags.contains(tag)) allTags.add(tag);
                }
            }
        }
        Collections.sort(allTags, String::compareToIgnoreCase);
        return allTags;
    }

    /**
     * Advanced search with category/priority/status/date filters.
     */
    public List<Task> advancedSearch(String query, String filterPriority,
                                     String filterCategory, String filterStatus,
                                     String dateFrom, String dateTo) {
        List<Task> results = new ArrayList<>();
        String q = (query != null) ? query.trim().toLowerCase() : "";

        for (Task task : tasks) {
            if (task.isTrashed) continue;

            // Text match
            if (!q.isEmpty() && !task.matchesSearch(query)) continue;

            // Priority filter
            if (filterPriority != null && !filterPriority.isEmpty()
                    && !filterPriority.equals(task.priority)) continue;

            // Category filter
            if (filterCategory != null && !filterCategory.isEmpty()
                    && !filterCategory.equals(task.category)) continue;

            // Status filter
            if (filterStatus != null && !filterStatus.isEmpty()
                    && !filterStatus.equals(task.status)) continue;

            // Date range
            if (dateFrom != null && task.hasDueDate() && task.dueDate.compareTo(dateFrom) < 0) continue;
            if (dateTo != null && task.hasDueDate() && task.dueDate.compareTo(dateTo) > 0) continue;

            results.add(task);
        }
        return results;
    }

    /**
     * Get recent search queries (stored in prefs).
     */
    public List<String> getRecentSearches() {
        List<String> recent = new ArrayList<>();
        String json = getPrefs().getString("recent_searches", "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) recent.add(arr.getString(i));
        } catch (Exception ignored) {}
        return recent;
    }

    public void addRecentSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;
        List<String> recent = getRecentSearches();
        recent.remove(query);
        recent.add(0, query);
        while (recent.size() > 10) recent.remove(recent.size() - 1);
        org.json.JSONArray arr = new org.json.JSONArray();
        for (String s : recent) arr.put(s);
        getPrefs().edit().putString("recent_searches", arr.toString()).apply();
    }

    public void clearRecentSearches() {
        getPrefs().edit().remove("recent_searches").apply();
    }

    /**
     * Create next occurrence for a recurring task.
     */
    public Task createNextRecurrence(String taskId) {
        Task task = getTaskById(taskId);
        if (task == null || !task.isRecurring() || !task.hasDueDate()) return null;

        Task next = task.duplicate();
        next.title = task.title; // remove " (copy)"
        next.status = Task.STATUS_TODO;
        next.completedAt = 0;

        // Calculate next due date
        java.util.Calendar nextCal = TaskNotificationHelper.getNextRecurrenceDate(task);
        if (nextCal != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            next.dueDate = sdf.format(nextCal.getTime());
        }

        next.recurrence = task.recurrence;
        next.recurrenceRule = task.recurrenceRule;
        next.timerSessions = new ArrayList<>();
        next.actualDuration = 0;
        addTask(next);

        // Schedule notifications for the new occurrence
        TaskNotificationHelper.scheduleTaskReminders(context, next);
        return next;
    }
}
