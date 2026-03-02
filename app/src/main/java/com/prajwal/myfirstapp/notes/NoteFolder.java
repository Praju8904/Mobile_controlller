package com.prajwal.myfirstapp.notes;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class NoteFolder {
    public String id;
    public String name;
    public String colorHex;        // e.g. "#3B82F6"
    public String iconIdentifier;  // e.g. "person", "briefcase", "book", "lightbulb"
    public String parentFolderId;  // null if root level
    public int depth;              // 0 for root, 1 for first level subfolder, etc.
    public int sortOrder;
    public long createdAt;
    public long updatedAt;

    // Default folder colors (12 premium colors)
    public static final String[] FOLDER_COLORS = {
        "#3B82F6",  // Blue
        "#6366F1",  // Indigo
        "#8B5CF6",  // Purple
        "#F59E0B",  // Orange/Amber
        "#EF4444",  // Red
        "#10B981",  // Green/Emerald
        "#F472B6",  // Pink
        "#06B6D4",  // Cyan
        "#84CC16",  // Lime
        "#EC4899",  // Fuchsia
        "#14B8A6",  // Teal
        "#F97316",  // Orange
    };

    public static final String[] FOLDER_COLOR_NAMES = {
        "Blue", "Indigo", "Purple", "Amber", "Red",
        "Green", "Pink", "Cyan", "Lime", "Fuchsia", "Teal", "Orange"
    };

    // Gradient end colors (slightly lighter) for each folder color
    public static final String[] FOLDER_COLOR_GRADIENTS = {
        "#60A5FA",  // Blue lighter
        "#818CF8",  // Indigo lighter
        "#A78BFA",  // Purple lighter
        "#FCD34D",  // Amber lighter
        "#F87171",  // Red lighter
        "#34D399",  // Green lighter
        "#F9A8D4",  // Pink lighter
        "#67E8F9",  // Cyan lighter
        "#BEF264",  // Lime lighter
        "#F0ABFC",  // Fuchsia lighter
        "#5EEAD4",  // Teal lighter
        "#FB923C",  // Orange lighter
    };

    public static final String[] FOLDER_ICONS = {
        "person", "briefcase", "book", "lightbulb", "heart", "star",
        "home", "code", "music", "camera", "globe", "lock",
        "flag", "trophy", "rocket", "leaf", "coffee", "graduation",
        "calculator", "palette", "calendar", "chart", "cloud", "diamond",
        "fire", "folder", "archive", "bell", "bookmark", "tag"
    };

    // Default icon emojis for display
    public static final String[] FOLDER_ICON_EMOJIS = {
        "👤", "💼", "📚", "💡", "❤️", "⭐",
        "🏠", "💻", "🎵", "📷", "🌐", "🔒",
        "🚩", "🏆", "🚀", "🌿", "☕", "🎓",
        "🔢", "🎨", "📅", "📊", "☁️", "💎",
        "🔥", "📁", "📦", "🔔", "🔖", "🏷️"
    };

    // Default folders
    public static final String PERSONAL_FOLDER_NAME = "Personal";
    public static final String WORK_FOLDER_NAME = "Work";
    public static final String STUDY_FOLDER_NAME = "Study";
    public static final String IDEAS_FOLDER_NAME = "Ideas";
    public static final String QUICK_NOTES_FOLDER_NAME = "Quick Notes";

    public NoteFolder() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.name = "";
        this.colorHex = FOLDER_COLORS[0];
        this.iconIdentifier = FOLDER_ICONS[0];
        this.parentFolderId = null;
        this.depth = 0;
        this.sortOrder = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public NoteFolder(String name, String colorHex, String iconIdentifier, String parentFolderId, int depth) {
        this();
        this.name = name;
        this.colorHex = colorHex;
        this.iconIdentifier = iconIdentifier;
        this.parentFolderId = parentFolderId;
        this.depth = depth;
    }

    public int getColorInt() {
        try {
            return android.graphics.Color.parseColor(colorHex);
        } catch (Exception e) {
            return 0xFF3B82F6;
        }
    }

    public int getGradientColorInt() {
        for (int i = 0; i < FOLDER_COLORS.length; i++) {
            if (FOLDER_COLORS[i].equalsIgnoreCase(colorHex)) {
                try {
                    return android.graphics.Color.parseColor(FOLDER_COLOR_GRADIENTS[i]);
                } catch (Exception e) {
                    break;
                }
            }
        }
        // Default: lighten the color
        try {
            int color = android.graphics.Color.parseColor(colorHex);
            int r = Math.min(255, android.graphics.Color.red(color) + 40);
            int g = Math.min(255, android.graphics.Color.green(color) + 40);
            int b = Math.min(255, android.graphics.Color.blue(color) + 40);
            return android.graphics.Color.rgb(r, g, b);
        } catch (Exception e) {
            return 0xFF60A5FA;
        }
    }

    public String getIconEmoji() {
        for (int i = 0; i < FOLDER_ICONS.length; i++) {
            if (FOLDER_ICONS[i].equals(iconIdentifier)) {
                return FOLDER_ICON_EMOJIS[i];
            }
        }
        return "📁";
    }

    public boolean isRoot() {
        return parentFolderId == null || parentFolderId.isEmpty();
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("colorHex", colorHex);
            json.put("iconIdentifier", iconIdentifier);
            json.put("parentFolderId", parentFolderId == null ? "" : parentFolderId);
            json.put("depth", depth);
            json.put("sortOrder", sortOrder);
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static NoteFolder fromJson(JSONObject json) {
        if (json == null) return null;
        NoteFolder folder = new NoteFolder();
        folder.id = json.optString("id", folder.id);
        folder.name = json.optString("name", "");
        folder.colorHex = json.optString("colorHex", FOLDER_COLORS[0]);
        folder.iconIdentifier = json.optString("iconIdentifier", FOLDER_ICONS[0]);
        String pid = json.optString("parentFolderId", "");
        folder.parentFolderId = pid.isEmpty() ? null : pid;
        folder.depth = json.optInt("depth", 0);
        folder.sortOrder = json.optInt("sortOrder", 0);
        folder.createdAt = json.optLong("createdAt", System.currentTimeMillis());
        folder.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
        return folder;
    }
}
