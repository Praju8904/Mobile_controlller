package com.prajwal.myfirstapp.tasks;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Encapsulates all filter, sort and group-by state for the task list.
 * Gap refs: 6.5 (date range), 6.7 (energy), 6.12/6.13 (sort), 6.17 (group by)
 */
public class TaskFilter {

    // ─── Sort Order ──────────────────────────────────────────────

    public enum SortOrder {
        PRIORITY,       // default: urgent → high → normal → low → none
        DUE_DATE,       // earliest first
        CREATED,        // newest first
        TITLE_AZ,       // alphabetical A→Z
        TITLE_ZA,       // reverse alphabetical Z→A
        DURATION_ASC,   // shortest estimated duration first
        DURATION_DESC,  // longest estimated duration first
        STATUS          // todo → inprogress → completed → cancelled
    }

    // ─── Group By ────────────────────────────────────────────────

    public enum GroupBy {
        NONE,       // flat list
        DUE_DATE,   // by getDueDateGroup()
        PRIORITY,   // Urgent → None
        STATUS,     // todo/inprogress/completed/cancelled
        CATEGORY    // alphabetical by category name
    }

    // ─── Filter Fields ───────────────────────────────────────────

    public String  filterPriority;      // null=any, or priority constant
    public String  filterStatus;        // null=any, or status constant
    public String  filterCategory;      // null=any, or category name
    public boolean filterStarred;       // false=any, true=starred only
    public String  searchQuery;         // search string (null=no search)
    public String  filterDueDateGroup;  // null=any, or getDueDateGroup() value

    // Date range filter (gap 6.5)
    public String  filterDateFrom;      // nullable "YYYY-MM-DD"
    public String  filterDateTo;        // nullable "YYYY-MM-DD"

    // Energy level filter (gap 6.7)
    public String  filterEnergyLevel;   // null=any | "deep_work" | "light" | "low_energy"

    // GTD filters (Batch 4)
    public boolean filterNextAction;    // false=any, true=next-action only
    public boolean filterMIT;           // false=any, true=MIT only
    public boolean filterWaiting;       // false=any, true=waiting-for only
    public boolean filterSomeday;       // false=any, true=someday/maybe only
    public String  filterContextTag;    // null=any | "@home"|"@computer"|etc.
    public String  filterAssignedTo;    // null=any | "Mobile"|"PC"
    public boolean filterPrivate;       // false=any, true=private-only
    public boolean filterTwoMinOnly;    // false=any, true=only tasks ≤2 min estimated

    // Group-by mode (gap 6.17)
    public GroupBy groupBy = GroupBy.NONE;

    // Sort order
    public SortOrder sortOrder = SortOrder.PRIORITY;

    // ─── Constructor ─────────────────────────────────────────────

    public TaskFilter() {
        // all fields default null / false / NONE
    }

    // ─── matches() ───────────────────────────────────────────────

    /**
     * Returns true when the task passes all active filters.
     */
    public boolean matches(Task task) {
        if (task == null) return false;

        // Deferred guard: hide deferred tasks from Today/Upcoming smart views (Feature 2C)
        if (task.isDeferred()) {
            if ("Today".equals(filterDueDateGroup) || "Tomorrow".equals(filterDueDateGroup)
                || "This Week".equals(filterDueDateGroup) || "Overdue".equals(filterDueDateGroup)) {
                return false;
            }
        }

        // Priority filter
        if (filterPriority != null && !filterPriority.equals(task.priority)) return false;

        // Status filter
        if (filterStatus != null && !filterStatus.equals(task.status)) return false;

        // Category filter
        if (filterCategory != null) {
            if (task.category == null || !filterCategory.equalsIgnoreCase(task.category)) return false;
        }

        // Starred filter
        if (filterStarred && !task.isStarred) return false;

        // Due date group filter
        if (filterDueDateGroup != null && !filterDueDateGroup.equals(task.getDueDateGroup())) return false;

        // Date range filter (gap 6.5)
        if (filterDateFrom != null || filterDateTo != null) {
            // Tasks with no due date are excluded when a date range is set
            if (task.dueDate == null || task.dueDate.isEmpty()) {
                if (filterDateTo != null) return false; // explicit "to" requires a date
                // if only "from" is set, also exclude no-date tasks for consistency
                if (filterDateFrom != null) return false;
            } else {
                if (filterDateFrom != null && task.dueDate.compareTo(filterDateFrom) < 0) return false;
                if (filterDateTo   != null && task.dueDate.compareTo(filterDateTo)   > 0) return false;
            }
        }

        // Energy level filter (gap 6.7)
        if (filterEnergyLevel != null) {
            if (!filterEnergyLevel.equals(task.energyLevel)) return false;
        }

        // GTD filters (Batch 4)
        if (filterNextAction && !task.isNextAction) return false;
        if (filterMIT && !task.isMIT) return false;
        if (filterWaiting && !task.isWaiting()) return false;
        if (filterSomeday && !task.isSomeday()) return false;
        if (filterContextTag != null && !filterContextTag.equals(task.contextTag)) return false;
        if (filterAssignedTo != null) {
            if (task.assignedTo == null || !filterAssignedTo.equals(task.assignedTo)) return false;
        }
        if (filterPrivate && !task.isPrivate) return false;
        if (filterTwoMinOnly && (task.estimatedDuration <= 0 || task.estimatedDuration > 2 || task.isCompleted())) return false;

        // Search query
        if (searchQuery != null && !searchQuery.isEmpty()) {
            String q = searchQuery.toLowerCase(Locale.ROOT);
            String title = task.title != null ? task.title.toLowerCase(Locale.ROOT) : "";
            String desc  = task.description != null ? task.description.toLowerCase(Locale.ROOT) : "";
            String notes = task.notes != null ? task.notes.toLowerCase(Locale.ROOT) : "";
            boolean matchesTags = false;
            if (task.tags != null) {
                for (String tag : task.tags) {
                    if (tag != null && tag.toLowerCase(Locale.ROOT).contains(q)) {
                        matchesTags = true;
                        break;
                    }
                }
            }
            if (!title.contains(q) && !desc.contains(q) && !notes.contains(q) && !matchesTags) {
                return false;
            }
        }

        return true;
    }

    // ─── Serialization ───────────────────────────────────────────

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("filterPriority",    filterPriority    != null ? filterPriority    : JSONObject.NULL);
            o.put("filterStatus",      filterStatus      != null ? filterStatus      : JSONObject.NULL);
            o.put("filterCategory",    filterCategory    != null ? filterCategory    : JSONObject.NULL);
            o.put("filterStarred",     filterStarred);
            o.put("searchQuery",       searchQuery       != null ? searchQuery       : "");
            o.put("filterDueDateGroup",filterDueDateGroup!= null ? filterDueDateGroup: JSONObject.NULL);
            o.put("filterDateFrom",    filterDateFrom    != null ? filterDateFrom    : JSONObject.NULL);
            o.put("filterDateTo",      filterDateTo      != null ? filterDateTo      : JSONObject.NULL);
            o.put("filterEnergyLevel", filterEnergyLevel != null ? filterEnergyLevel : JSONObject.NULL);
            o.put("filterNextAction",  filterNextAction);
            o.put("filterMIT",         filterMIT);
            o.put("filterWaiting",     filterWaiting);
            o.put("filterSomeday",     filterSomeday);
            o.put("filterContextTag",  filterContextTag  != null ? filterContextTag  : JSONObject.NULL);
            o.put("filterAssignedTo",  filterAssignedTo  != null ? filterAssignedTo  : JSONObject.NULL);
            o.put("filterPrivate",     filterPrivate);
            o.put("filterTwoMinOnly", filterTwoMinOnly);
            o.put("groupBy",           groupBy   != null ? groupBy.name()   : GroupBy.NONE.name());
            o.put("sortOrder",         sortOrder != null ? sortOrder.name() : SortOrder.PRIORITY.name());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o;
    }

    public static TaskFilter fromJson(JSONObject o) {
        TaskFilter f = new TaskFilter();
        if (o == null) return f;
        f.filterPriority     = nullIfJsonNull(o.opt("filterPriority"));
        f.filterStatus       = nullIfJsonNull(o.opt("filterStatus"));
        f.filterCategory     = nullIfJsonNull(o.opt("filterCategory"));
        f.filterStarred      = o.optBoolean("filterStarred", false);
        String sq = o.optString("searchQuery", "");
        f.searchQuery        = sq.isEmpty() ? null : sq;
        f.filterDueDateGroup = nullIfJsonNull(o.opt("filterDueDateGroup"));
        f.filterDateFrom     = nullIfJsonNull(o.opt("filterDateFrom"));
        f.filterDateTo       = nullIfJsonNull(o.opt("filterDateTo"));
        f.filterEnergyLevel  = nullIfJsonNull(o.opt("filterEnergyLevel"));
        f.filterNextAction   = o.optBoolean("filterNextAction", false);
        f.filterMIT          = o.optBoolean("filterMIT", false);
        f.filterWaiting      = o.optBoolean("filterWaiting", false);
        f.filterSomeday      = o.optBoolean("filterSomeday", false);
        f.filterContextTag   = nullIfJsonNull(o.opt("filterContextTag"));
        f.filterAssignedTo   = nullIfJsonNull(o.opt("filterAssignedTo"));
        f.filterPrivate      = o.optBoolean("filterPrivate", false);
        f.filterTwoMinOnly   = o.optBoolean("filterTwoMinOnly", false);
        try {
            f.groupBy   = GroupBy.valueOf(o.optString("groupBy", GroupBy.NONE.name()));
        } catch (Exception ignored) {}
        try {
            f.sortOrder = SortOrder.valueOf(o.optString("sortOrder", SortOrder.PRIORITY.name()));
        } catch (Exception ignored) {}
        return f;
    }

    private static String nullIfJsonNull(Object val) {
        if (val == null || val == JSONObject.NULL || "null".equals(String.valueOf(val))) return null;
        String s = String.valueOf(val);
        return s.isEmpty() ? null : s;
    }

    /** Returns true if no filters are active (matches everything). */
    public boolean isEmpty() {
        return filterPriority == null && filterStatus == null && filterCategory == null
            && !filterStarred && (searchQuery == null || searchQuery.isEmpty())
            && filterDueDateGroup == null && filterDateFrom == null && filterDateTo == null
            && filterEnergyLevel == null
            && !filterNextAction && !filterMIT && !filterWaiting && !filterSomeday
            && filterContextTag == null && filterAssignedTo == null && !filterPrivate
            && !filterTwoMinOnly && groupBy == GroupBy.NONE;
    }
}
