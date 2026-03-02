package com.prajwal.myfirstapp.todo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Data model for a Todo List (a collection of TodoItems).
 */
public class TodoList {

    public String id;
    public String title;
    public String colorHex;
    public String iconIdentifier;
    public String categoryId;
    public String folderId;
    public int sortOrder;
    public long createdAt;
    public long updatedAt;

    // ─── Gradient Colors ─────────────────────────────────────────

    public static final String[] GRADIENT_COLORS = {
        "#FF6B6B",  // Coral Red
        "#FF8E53",  // Sunset Orange
        "#FBBF24",  // Amber
        "#34D399",  // Emerald
        "#10B981",  // Green
        "#60A5FA",  // Sky Blue
        "#3B82F6",  // Blue
        "#818CF8",  // Indigo
        "#A78BFA",  // Violet
        "#F472B6",  // Pink
        "#FB923C",  // Orange
        "#2DD4BF"   // Teal
    };

    // ─── Icon Options ────────────────────────────────────────────

    public static final String[] ICON_OPTIONS = {
        "📋", "⭐", "🛒", "📚", "💼", "🏠", "❤️", "🚀",
        "🏆", "🌿", "☕", "🎵", "💻", "🏁", "🌍", "📷",
        "💊", "🎯", "✈️", "🎓", "🔑", "💡", "🎮", "🍕",
        "💪", "🎁", "🔔", "📝", "🌙", "⚡"
    };

    // ─── Constructors ────────────────────────────────────────────

    public TodoList() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.title = "";
        this.colorHex = GRADIENT_COLORS[0];
        this.iconIdentifier = ICON_OPTIONS[0];
        this.categoryId = null;
        this.folderId = null;
        this.sortOrder = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public TodoList(String title, String colorHex, String iconIdentifier) {
        this();
        this.title = title != null ? title : "";
        this.colorHex = colorHex != null ? colorHex : GRADIENT_COLORS[0];
        this.iconIdentifier = iconIdentifier != null ? iconIdentifier : ICON_OPTIONS[0];
    }

    // ─── Static Helpers ──────────────────────────────────────────

    public static int getCompletionPercent(int completed, int total) {
        if (total <= 0) return 0;
        return (int) Math.round((completed * 100.0) / total);
    }

    // ─── Date Helpers ────────────────────────────────────────────

    private static final java.text.SimpleDateFormat DATE_FORMAT =
            new java.text.SimpleDateFormat("MMM dd", java.util.Locale.US);

    public String getFormattedDate() {
        long diff = System.currentTimeMillis() - updatedAt;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) return "Just now";
        if (minutes < 60) return minutes + "m ago";
        if (hours < 24) return hours + "h ago";
        if (days < 7) return days + "d ago";

        return DATE_FORMAT.format(new java.util.Date(updatedAt));
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("colorHex", colorHex);
            json.put("iconIdentifier", iconIdentifier);
            json.put("categoryId", categoryId != null ? categoryId : "");
            json.put("folderId", folderId != null ? folderId : "");
            json.put("sortOrder", sortOrder);
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static TodoList fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            TodoList list = new TodoList();
            list.id = json.optString("id", UUID.randomUUID().toString().substring(0, 12));
            list.title = json.optString("title", "");
            list.colorHex = json.optString("colorHex", GRADIENT_COLORS[0]);
            list.iconIdentifier = json.optString("iconIdentifier", ICON_OPTIONS[0]);
            list.categoryId = json.optString("categoryId", "");
            if (list.categoryId.isEmpty()) list.categoryId = null;
            list.folderId = json.optString("folderId", "");
            if (list.folderId.isEmpty()) list.folderId = null;
            list.sortOrder = json.optInt("sortOrder", 0);
            list.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            list.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            return list;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
