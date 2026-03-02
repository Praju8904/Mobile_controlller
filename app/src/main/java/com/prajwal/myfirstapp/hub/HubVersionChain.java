package com.prajwal.myfirstapp.hub;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a chain of file versions detected from naming patterns such as
 * _v1/_v2, _final/_final2, _revised, (1)/(2), etc.
 *
 * The chain holds references to fileIds in chronological order (oldest first).
 * The newest file in the chain is the "canonical" version and carries a "versions" badge.
 */
public class HubVersionChain {

    public String id;
    public String baseName;           // Normalised name stripped of version tokens
    public List<String> fileIds;      // Ordered oldest → newest
    public long detectedAt;

    public HubVersionChain() {
        this.id = UUID.randomUUID().toString();
        this.fileIds = new ArrayList<>();
        this.detectedAt = System.currentTimeMillis();
    }

    public int getVersionCount() {
        return fileIds != null ? fileIds.size() : 0;
    }

    /** Returns the newest file ID (last in the ordered list). */
    public String getLatestFileId() {
        if (fileIds == null || fileIds.isEmpty()) return null;
        return fileIds.get(fileIds.size() - 1);
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id != null ? id : "");
            o.put("baseName", baseName != null ? baseName : "");
            JSONArray arr = new JSONArray();
            if (fileIds != null) for (String fid : fileIds) arr.put(fid);
            o.put("fileIds", arr);
            o.put("detectedAt", detectedAt);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static HubVersionChain fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            HubVersionChain c = new HubVersionChain();
            c.id = o.optString("id", UUID.randomUUID().toString());
            c.baseName = o.optString("baseName", "");
            c.fileIds = new ArrayList<>();
            JSONArray arr = o.optJSONArray("fileIds");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) c.fileIds.add(arr.getString(i));
            }
            c.detectedAt = o.optLong("detectedAt", System.currentTimeMillis());
            return c;
        } catch (Exception e) {
            return null;
        }
    }
}
