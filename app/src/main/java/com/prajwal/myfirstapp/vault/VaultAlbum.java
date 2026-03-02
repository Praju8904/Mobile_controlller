package com.prajwal.myfirstapp.vault;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Data model for a vault album (collection of vault files).
 */
public class VaultAlbum {

    public String id;
    public String name;
    public String colorHex;
    public String iconIdentifier;
    public String coverFileId;     // file used as album cover thumbnail
    public int fileCount;
    public long createdAt;
    public long updatedAt;

    public VaultAlbum() {
        this.id = UUID.randomUUID().toString();
        this.colorHex = "#3B82F6";
        this.fileCount = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name != null ? name : "");
            o.put("colorHex", colorHex != null ? colorHex : "#3B82F6");
            o.put("iconIdentifier", iconIdentifier != null ? iconIdentifier : "");
            o.put("coverFileId", coverFileId != null ? coverFileId : "");
            o.put("fileCount", fileCount);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static VaultAlbum fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            VaultAlbum a = new VaultAlbum();
            a.id = o.optString("id", UUID.randomUUID().toString());
            a.name = o.optString("name", "");
            a.colorHex = o.optString("colorHex", "#3B82F6");
            a.iconIdentifier = o.optString("iconIdentifier", "");
            a.coverFileId = o.optString("coverFileId", "");
            if (a.coverFileId.isEmpty()) a.coverFileId = null;
            a.fileCount = o.optInt("fileCount", 0);
            a.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            a.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
            return a;
        } catch (Exception e) {
            return null;
        }
    }
}
