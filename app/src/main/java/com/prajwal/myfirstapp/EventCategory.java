package com.prajwal.myfirstapp;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * EventCategory model for calendar event categorization.
 *
 * Default categories are seeded on first launch:
 * Personal, Work, Study, Health, Social, Birthdays, Anniversaries, Holidays, Reminders, Others.
 */
public class EventCategory {

    // ─── Fields ──────────────────────────────────────────────────

    public String id;
    public String name;
    public String colorHex;
    public String iconIdentifier;  // Emoji or icon name
    public boolean isDefault;      // Default categories can't be deleted
    public long createdAt;

    // ─── Default Category IDs ────────────────────────────────────

    public static final String CAT_PERSONAL      = "cat_personal";
    public static final String CAT_WORK          = "cat_work";
    public static final String CAT_STUDY         = "cat_study";
    public static final String CAT_HEALTH        = "cat_health";
    public static final String CAT_SOCIAL        = "cat_social";
    public static final String CAT_BIRTHDAYS     = "cat_birthdays";
    public static final String CAT_ANNIVERSARIES = "cat_anniversaries";
    public static final String CAT_HOLIDAYS      = "cat_holidays";
    public static final String CAT_REMINDERS     = "cat_reminders";
    public static final String CAT_OTHERS        = "cat_others";

    // ─── Constructors ────────────────────────────────────────────

    public EventCategory() {
        this.id = "cat_" + System.currentTimeMillis();
        this.name = "";
        this.colorHex = CalendarEvent.COLOR_BLUE;
        this.iconIdentifier = "📌";
        this.isDefault = false;
        this.createdAt = System.currentTimeMillis();
    }

    public EventCategory(String id, String name, String colorHex, String iconIdentifier, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.colorHex = colorHex;
        this.iconIdentifier = iconIdentifier;
        this.isDefault = isDefault;
        this.createdAt = System.currentTimeMillis();
    }

    // ─── Get accent color as int ─────────────────────────────────

    public int getColor() {
        try {
            return android.graphics.Color.parseColor(colorHex);
        } catch (Exception e) {
            return 0xFF3B82F6;
        }
    }

    public int getBackgroundColor() {
        try {
            int color = android.graphics.Color.parseColor(colorHex);
            int r = (android.graphics.Color.red(color) * 40) / 255;
            int g = (android.graphics.Color.green(color) * 40) / 255;
            int b = (android.graphics.Color.blue(color) * 40) / 255;
            return android.graphics.Color.rgb(r, g, b);
        } catch (Exception e) {
            return 0xFF1E293B;
        }
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("colorHex", colorHex);
            json.put("iconIdentifier", iconIdentifier);
            json.put("isDefault", isDefault);
            json.put("createdAt", createdAt);
        } catch (JSONException e) {
            // ignore
        }
        return json;
    }

    public static EventCategory fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            EventCategory cat = new EventCategory();
            cat.id = json.optString("id", cat.id);
            cat.name = json.optString("name", "");
            cat.colorHex = json.optString("colorHex", CalendarEvent.COLOR_BLUE);
            cat.iconIdentifier = json.optString("iconIdentifier", "📌");
            cat.isDefault = json.optBoolean("isDefault", false);
            cat.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            return cat;
        } catch (Exception e) {
            return null;
        }
    }

    // ─── Default Categories Factory ──────────────────────────────

    /**
     * Create the default set of event categories.
     * Called on first launch to seed the category list.
     */
    public static EventCategory[] getDefaultCategories() {
        return new EventCategory[] {
            new EventCategory(CAT_PERSONAL,      "Personal",      "#3B82F6", "👤", true),   // Blue
            new EventCategory(CAT_WORK,          "Work",          "#6366F1", "💼", true),   // Indigo
            new EventCategory(CAT_STUDY,         "Study",         "#8B5CF6", "📚", true),   // Purple
            new EventCategory(CAT_HEALTH,        "Health",        "#22C55E", "💪", true),   // Green
            new EventCategory(CAT_SOCIAL,        "Social",        "#F97316", "🎉", true),   // Orange
            new EventCategory(CAT_BIRTHDAYS,     "Birthdays",     "#EC4899", "🎂", true),   // Pink
            new EventCategory(CAT_ANNIVERSARIES, "Anniversaries", "#EF4444", "💍", true),   // Red
            new EventCategory(CAT_HOLIDAYS,      "Holidays",      "#14B8A6", "🎄", true),   // Teal
            new EventCategory(CAT_REMINDERS,     "Reminders",     "#F59E0B", "⏰", true),   // Amber
            new EventCategory(CAT_OTHERS,        "Others",        "#6B7280", "📌", true),   // Grey
        };
    }
}
