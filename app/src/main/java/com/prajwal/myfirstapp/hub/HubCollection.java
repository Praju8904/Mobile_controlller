package com.prajwal.myfirstapp.hub;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A lightweight manual file grouping (like a playlist for files).
 * Files are not moved — they are just referenced by ID.
 * Unlike smart folders, collections are 100% manual curation.
 * Unlike projects, they have no metadata beyond name and color.
 */
public class HubCollection {

    public String id;
    public String name;
    public String colorHex;
    public List<String> fileIds;   // IDs of files in this collection
    public long createdAt;
    public long updatedAt;

    public HubCollection() {
        this.id = UUID.randomUUID().toString();
        this.colorHex = "#8B5CF6";
        this.fileIds = new ArrayList<>();
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public HubCollection(String name, String colorHex) {
        this();
        this.name = name;
        this.colorHex = colorHex;
    }

    public int getFileCount() {
        return fileIds != null ? fileIds.size() : 0;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id != null ? id : "");
            o.put("name", name != null ? name : "");
            o.put("colorHex", colorHex != null ? colorHex : "#8B5CF6");
            JSONArray arr = new JSONArray();
            if (fileIds != null) for (String fid : fileIds) arr.put(fid);
            o.put("fileIds", arr);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static HubCollection fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            HubCollection c = new HubCollection();
            c.id = o.optString("id", UUID.randomUUID().toString());
            c.name = o.optString("name", "");
            c.colorHex = o.optString("colorHex", "#8B5CF6");
            c.fileIds = new ArrayList<>();
            JSONArray arr = o.optJSONArray("fileIds");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) c.fileIds.add(arr.getString(i));
            }
            c.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            c.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
            return c;
        } catch (Exception e) {
            return null;
        }
    }
}
