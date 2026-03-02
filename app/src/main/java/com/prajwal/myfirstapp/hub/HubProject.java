package com.prajwal.myfirstapp.hub;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A project bundles related files of any type together.
 */
public class HubProject {

    public String id;
    public String name;
    public String description;
    public String colorHex;
    public String iconIdentifier;
    public List<String> tags;
    public int fileCount;
    public long totalSize;
    public long createdAt;
    public long updatedAt;

    public HubProject() {
        this.id = UUID.randomUUID().toString();
        this.colorHex = "#8B5CF6";
        this.iconIdentifier = "💼";
        this.tags = new ArrayList<>();
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public HubProject(String name, String description, String colorHex, String iconIdentifier) {
        this();
        this.name = name;
        this.description = description;
        this.colorHex = colorHex;
        this.iconIdentifier = iconIdentifier;
    }

    public String getFormattedSize() {
        if (totalSize < 1024) return totalSize + " B";
        if (totalSize < 1024 * 1024) return String.format("%.1f KB", totalSize / 1024.0);
        if (totalSize < 1024L * 1024 * 1024) return String.format("%.1f MB", totalSize / (1024.0 * 1024));
        return String.format("%.2f GB", totalSize / (1024.0 * 1024 * 1024));
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id != null ? id : "");
            o.put("name", name != null ? name : "");
            o.put("description", description != null ? description : "");
            o.put("colorHex", colorHex != null ? colorHex : "#8B5CF6");
            o.put("iconIdentifier", iconIdentifier != null ? iconIdentifier : "💼");
            JSONArray tagsArr = new JSONArray();
            if (tags != null) for (String t : tags) tagsArr.put(t);
            o.put("tags", tagsArr);
            o.put("fileCount", fileCount);
            o.put("totalSize", totalSize);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static HubProject fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            HubProject p = new HubProject();
            p.id = o.optString("id", UUID.randomUUID().toString());
            p.name = o.optString("name", "");
            p.description = o.optString("description", "");
            p.colorHex = o.optString("colorHex", "#8B5CF6");
            p.iconIdentifier = o.optString("iconIdentifier", "💼");
            p.tags = new ArrayList<>();
            JSONArray tagsArr = o.optJSONArray("tags");
            if (tagsArr != null) {
                for (int i = 0; i < tagsArr.length(); i++) p.tags.add(tagsArr.getString(i));
            }
            p.fileCount = o.optInt("fileCount", 0);
            p.totalSize = o.optLong("totalSize", 0);
            p.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            p.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
            return p;
        } catch (Exception e) {
            return null;
        }
    }
}
