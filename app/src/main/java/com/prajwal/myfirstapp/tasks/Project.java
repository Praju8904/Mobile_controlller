package com.prajwal.myfirstapp.tasks;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Project data model for grouping tasks into projects/lists (Feature 17).
 */
public class Project {
    public String id;
    public String name;
    public String description;
    public String color;         // hex color string
    public String colorHex;      // alternate hex color for accent (#RRGGBB)
    public String icon;          // emoji icon
    public String goalDeadline;  // "YYYY-MM-DD" optional
    public long createdAt;
    public long updatedAt;
    public boolean isArchived;

    public Project() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.name = "";
        this.description = "";
        this.color = "#3B82F6";
        this.icon = "📁";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.isArchived = false;
    }

    public Project(String name, String icon, String color) {
        this();
        this.name = name;
        this.icon = icon;
        this.color = color;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("description", description);
            json.put("color", color);
            json.put("colorHex", colorHex);
            json.put("icon", icon);
            json.put("goalDeadline", goalDeadline != null ? goalDeadline : JSONObject.NULL);
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
            json.put("isArchived", isArchived);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static Project fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            Project p = new Project();
            p.id = json.optString("id", p.id);
            p.name = json.optString("name", "");
            p.description = json.optString("description", "");
            p.color = json.optString("color", "#3B82F6");
            p.colorHex = json.optString("colorHex", null);
            p.icon = json.optString("icon", "\uD83D\uDCC1");
            String dl = json.optString("goalDeadline", null);
            p.goalDeadline = (dl != null && !"null".equals(dl)) ? dl : null;
            p.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            p.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            p.isArchived = json.optBoolean("isArchived", false);
            return p;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
