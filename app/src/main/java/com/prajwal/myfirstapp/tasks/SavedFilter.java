package com.prajwal.myfirstapp.tasks;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * A user-saved filter preset ("Smart List").
 * Gap ref: 6.15
 */
public class SavedFilter {
    public String     id;           // UUID
    public String     name;         // e.g. "Sprint"
    public String     iconEmoji;    // single emoji, default "🔖"
    public int        accentColor;  // ARGB from design palette
    public TaskFilter filter;       // the serialized filter state

    public SavedFilter() {
        this.id          = UUID.randomUUID().toString();
        this.name        = "";
        this.iconEmoji   = "🔖";
        this.accentColor = 0xFF6366F1; // accent indigo
        this.filter      = new TaskFilter();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id",          id);
            o.put("name",        name);
            o.put("iconEmoji",   iconEmoji);
            o.put("accentColor", accentColor);
            o.put("filter",      filter != null ? filter.toJson() : new JSONObject());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o;
    }

    public static SavedFilter fromJson(JSONObject o) {
        if (o == null) return null;
        SavedFilter sf = new SavedFilter();
        sf.id          = o.optString("id", sf.id);
        sf.name        = o.optString("name", "");
        sf.iconEmoji   = o.optString("iconEmoji", "🔖");
        sf.accentColor = o.optInt("accentColor", 0xFF6366F1);
        JSONObject fJson = o.optJSONObject("filter");
        sf.filter = fJson != null ? TaskFilter.fromJson(fJson) : new TaskFilter();
        return sf;
    }
}
