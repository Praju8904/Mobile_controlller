package com.prajwal.myfirstapp.hub;

import org.json.JSONObject;
import java.util.UUID;

/**
 * A custom folder that can contain HubFiles. Supports infinite nesting via parentFolderId.
 * Smart folders auto-populate based on rules stored as JSON.
 */
public class HubFolder {

    public String id;
    public String name;
    public String colorHex;
    public String iconIdentifier;
    public String parentFolderId;
    public int sortOrder;
    public boolean isSmartFolder;
    public String smartFolderRules;
    public long createdAt;
    public long updatedAt;

    public HubFolder() {
        this.id = UUID.randomUUID().toString();
        this.colorHex = "#3B82F6";
        this.iconIdentifier = "📁";
        this.sortOrder = 0;
        this.isSmartFolder = false;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public HubFolder(String name, String colorHex, String iconIdentifier) {
        this();
        this.name = name;
        this.colorHex = colorHex;
        this.iconIdentifier = iconIdentifier;
    }

    public static HubFolder createSmartFolder(String name, String colorHex, String iconIdentifier, String rules) {
        HubFolder f = new HubFolder(name, colorHex, iconIdentifier);
        f.isSmartFolder = true;
        f.smartFolderRules = rules;
        return f;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id != null ? id : "");
            o.put("name", name != null ? name : "");
            o.put("colorHex", colorHex != null ? colorHex : "#3B82F6");
            o.put("iconIdentifier", iconIdentifier != null ? iconIdentifier : "📁");
            o.put("parentFolderId", parentFolderId != null ? parentFolderId : "");
            o.put("sortOrder", sortOrder);
            o.put("isSmartFolder", isSmartFolder);
            o.put("smartFolderRules", smartFolderRules != null ? smartFolderRules : "");
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static HubFolder fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            HubFolder f = new HubFolder();
            f.id = o.optString("id", UUID.randomUUID().toString());
            f.name = o.optString("name", "");
            f.colorHex = o.optString("colorHex", "#3B82F6");
            f.iconIdentifier = o.optString("iconIdentifier", "📁");
            f.parentFolderId = o.optString("parentFolderId", "");
            if (f.parentFolderId.isEmpty()) f.parentFolderId = null;
            f.sortOrder = o.optInt("sortOrder", 0);
            f.isSmartFolder = o.optBoolean("isSmartFolder", false);
            f.smartFolderRules = o.optString("smartFolderRules", "");
            f.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            f.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
            return f;
        } catch (Exception e) {
            return null;
        }
    }
}
