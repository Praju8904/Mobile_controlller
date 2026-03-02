package com.prajwal.myfirstapp.tasks;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * TaskCategory model — represents a task category with name, color, and icon.
 * Default categories are provided; users can create custom ones.
 */
public class TaskCategory {

    public String id;
    public String name;
    public String colorHex;
    public String icon;
    public boolean isDefault;

    // ─── Default Categories ──────────────────────────────────────

    public static final TaskCategory[] DEFAULTS = {
        new TaskCategory("cat_personal", "Personal", "#3B82F6", "👤", true),
        new TaskCategory("cat_work",     "Work",     "#EF4444", "💼", true),
        new TaskCategory("cat_study",    "Study",    "#10B981", "📚", true),
        new TaskCategory("cat_health",   "Health",   "#F59E0B", "💪", true),
        new TaskCategory("cat_shopping", "Shopping", "#A855F7", "🛒", true),
        new TaskCategory("cat_finance",  "Finance",  "#06B6D4", "💰", true),
        new TaskCategory("cat_others",   "Others",   "#64748B", "📌", true),
    };

    public static final String[] DEFAULT_NAMES = {
        "Personal", "Work", "Study", "Health", "Shopping", "Finance", "Others"
    };

    public static final String[] DEFAULT_ICONS = {
        "👤", "💼", "📚", "💪", "🛒", "💰", "📌"
    };

    public static final String[] DEFAULT_COLORS = {
        "#3B82F6", "#EF4444", "#10B981", "#F59E0B", "#A855F7", "#06B6D4", "#64748B"
    };

    // ─── Preset Colors for Custom Categories ─────────────────────

    public static final String[] PRESET_COLORS = {
        "#EF4444", "#F59E0B", "#10B981", "#3B82F6", "#A855F7",
        "#EC4899", "#F97316", "#14B8A6", "#6366F1", "#64748B",
        "#E11D48", "#84CC16", "#06B6D4", "#8B5CF6", "#78716C"
    };

    // ─── Constructors ────────────────────────────────────────────

    public TaskCategory() {
        this.id = "cat_" + UUID.randomUUID().toString().substring(0, 8);
        this.name = "";
        this.colorHex = "#64748B";
        this.icon = "📌";
        this.isDefault = false;
    }

    public TaskCategory(String id, String name, String colorHex, String icon, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.colorHex = colorHex;
        this.icon = icon;
        this.isDefault = isDefault;
    }

    public TaskCategory(String name, String colorHex, String icon) {
        this();
        this.name = name;
        this.colorHex = colorHex;
        this.icon = icon;
    }

    // ─── Color Helpers ───────────────────────────────────────────

    public int getColorInt() {
        try {
            return android.graphics.Color.parseColor(colorHex);
        } catch (Exception e) {
            return 0xFF64748B;
        }
    }

    public static int parseColorSafe(String hex) {
        try {
            return android.graphics.Color.parseColor(hex);
        } catch (Exception e) {
            return 0xFF64748B;
        }
    }

    // ─── Static Helpers ──────────────────────────────────────────

    public static String getIconForCategory(String categoryName) {
        if (categoryName == null) return "📌";
        for (int i = 0; i < DEFAULT_NAMES.length; i++) {
            if (DEFAULT_NAMES[i].equalsIgnoreCase(categoryName)) return DEFAULT_ICONS[i];
        }
        return "📌";
    }

    public static String getColorForCategory(String categoryName) {
        if (categoryName == null) return "#64748B";
        for (int i = 0; i < DEFAULT_NAMES.length; i++) {
            if (DEFAULT_NAMES[i].equalsIgnoreCase(categoryName)) return DEFAULT_COLORS[i];
        }
        return "#64748B";
    }

    public static int getColorIntForCategory(String categoryName) {
        return parseColorSafe(getColorForCategory(categoryName));
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("colorHex", colorHex);
            json.put("icon", icon);
            json.put("isDefault", isDefault);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static TaskCategory fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            TaskCategory cat = new TaskCategory();
            cat.id = json.optString("id", cat.id);
            cat.name = json.optString("name", "");
            cat.colorHex = json.optString("colorHex", "#64748B");
            cat.icon = json.optString("icon", "📌");
            cat.isDefault = json.optBoolean("isDefault", false);
            return cat;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ─── Equality ────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskCategory that = (TaskCategory) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
