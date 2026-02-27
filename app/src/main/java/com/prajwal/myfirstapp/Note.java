package com.prajwal.myfirstapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data model for a note in the redesigned Notes feature.
 * Supports rich text body, categories, tags, pinning, locking, archiving, and trash.
 */
public class Note {

    public String id;
    public String title;
    public String body;               // rich text compatible (HTML)
    public String plainTextPreview;    // stripped preview for display
    public String colorHex;            // e.g. "#FF6B6B"
    public String category;            // "Personal", "Work", "Ideas", "Study", or custom
    public List<String> tags;
    public boolean isPinned;
    public boolean isLocked;
    public boolean isArchived;
    public boolean isTrashed;
    public long reminderDateTime;      // 0 = no reminder
    public long createdAt;
    public long updatedAt;
    public long deletedAt;             // 0 = not deleted

    // ─── Default Categories ──────────────────────────────────────

    public static final String[] DEFAULT_CATEGORIES = {
        "All", "Personal", "Work", "Ideas", "Study"
    };

    public static final String[] CATEGORY_ICONS = {
        "📋", "👤", "💼", "💡", "📚"
    };

    public static final int[] CATEGORY_COLORS = {
        0xFFF59E0B, 0xFF3B82F6, 0xFFEF4444, 0xFFA855F7, 0xFF10B981
    };

    // ─── Note Colors ─────────────────────────────────────────────

    public static final String[] NOTE_COLORS = {
        "#2D2D2D",  // Default dark
        "#FF6B6B",  // Red
        "#FBBF24",  // Yellow
        "#34D399",  // Green
        "#60A5FA",  // Blue
        "#A78BFA",  // Purple
        "#F472B6",  // Pink
        "#FB923C",  // Orange
        "#2DD4BF",  // Teal
        "#818CF8"   // Indigo
    };

    public static final String[] NOTE_COLOR_NAMES = {
        "Default", "Red", "Yellow", "Green", "Blue",
        "Purple", "Pink", "Orange", "Teal", "Indigo"
    };

    // ─── Constructors ────────────────────────────────────────────

    public Note() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.title = "";
        this.body = "";
        this.plainTextPreview = "";
        this.colorHex = NOTE_COLORS[0];
        this.category = "Personal";
        this.tags = new ArrayList<>();
        this.isPinned = false;
        this.isLocked = false;
        this.isArchived = false;
        this.isTrashed = false;
        this.reminderDateTime = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.deletedAt = 0;
    }

    public Note(String title, String body, String category) {
        this();
        this.title = title != null ? title : "";
        this.body = body != null ? body : "";
        this.category = category != null ? category : "Personal";
        updatePlainTextPreview();
    }

    // ─── Preview Generation ──────────────────────────────────────

    public void updatePlainTextPreview() {
        if (body == null || body.isEmpty()) {
            plainTextPreview = "";
            return;
        }
        // Strip HTML tags for preview
        String stripped = body.replaceAll("<[^>]*>", "").replaceAll("&nbsp;", " ").trim();
        plainTextPreview = stripped.length() > 200 ? stripped.substring(0, 200) : stripped;
    }

    // ─── Color Parsing ───────────────────────────────────────────

    public int getColorInt() {
        try {
            return android.graphics.Color.parseColor(colorHex);
        } catch (Exception e) {
            return 0xFF2D2D2D;
        }
    }

    public static int parseColorSafe(String hex) {
        try {
            return android.graphics.Color.parseColor(hex);
        } catch (Exception e) {
            return 0xFF2D2D2D;
        }
    }

    // ─── Category Helpers ────────────────────────────────────────

    public static String getCategoryIcon(String category) {
        for (int i = 0; i < DEFAULT_CATEGORIES.length; i++) {
            if (DEFAULT_CATEGORIES[i].equals(category)) return CATEGORY_ICONS[i];
        }
        return "🏷️";
    }

    public static int getCategoryColor(String category) {
        for (int i = 0; i < DEFAULT_CATEGORIES.length; i++) {
            if (DEFAULT_CATEGORIES[i].equals(category)) return CATEGORY_COLORS[i];
        }
        return 0xFF64748B;
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("body", body);
            json.put("plainTextPreview", plainTextPreview);
            json.put("colorHex", colorHex);
            json.put("category", category);

            JSONArray tagsArr = new JSONArray();
            if (tags != null) {
                for (String tag : tags) tagsArr.put(tag);
            }
            json.put("tags", tagsArr);

            json.put("isPinned", isPinned);
            json.put("isLocked", isLocked);
            json.put("isArchived", isArchived);
            json.put("isTrashed", isTrashed);
            json.put("reminderDateTime", reminderDateTime);
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
            json.put("deletedAt", deletedAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static Note fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            Note note = new Note();
            note.id = json.optString("id", UUID.randomUUID().toString().substring(0, 12));
            note.title = json.optString("title", "");
            note.body = json.optString("body", "");
            note.plainTextPreview = json.optString("plainTextPreview", "");
            note.colorHex = json.optString("colorHex", NOTE_COLORS[0]);
            note.category = json.optString("category", "Personal");

            note.tags = new ArrayList<>();
            JSONArray tagsArr = json.optJSONArray("tags");
            if (tagsArr != null) {
                for (int i = 0; i < tagsArr.length(); i++) {
                    note.tags.add(tagsArr.getString(i));
                }
            }

            note.isPinned = json.optBoolean("isPinned", false);
            note.isLocked = json.optBoolean("isLocked", false);
            note.isArchived = json.optBoolean("isArchived", false);
            note.isTrashed = json.optBoolean("isTrashed", false);
            note.reminderDateTime = json.optLong("reminderDateTime", 0);
            note.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            note.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            note.deletedAt = json.optLong("deletedAt", 0);

            if (note.plainTextPreview.isEmpty() && !note.body.isEmpty()) {
                note.updatePlainTextPreview();
            }
            return note;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ─── Search Matching ─────────────────────────────────────────

    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase();
        if (title != null && title.toLowerCase().contains(q)) return true;
        if (plainTextPreview != null && plainTextPreview.toLowerCase().contains(q)) return true;
        if (body != null && body.toLowerCase().contains(q)) return true;
        if (tags != null) {
            for (String tag : tags) {
                if (tag.toLowerCase().contains(q)) return true;
            }
        }
        return false;
    }

    // ─── Category / Filter Matching ──────────────────────────────

    public boolean matchesFilter(String filter) {
        if (filter == null || filter.equals("All")) return true;
        if (filter.equals("Pinned")) return isPinned;
        if (filter.equals("Locked")) return isLocked;
        return filter.equals(category);
    }

    // ─── Formatted Date Helpers ──────────────────────────────────

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

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd", java.util.Locale.US);
        return sdf.format(new java.util.Date(updatedAt));
    }

    public String getFullFormattedDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.US);
        return sdf.format(new java.util.Date(updatedAt));
    }
}
