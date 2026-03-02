package com.prajwal.myfirstapp.vault;

import android.util.Log;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Data model for vault collections — lightweight groupings where a file can
 * belong to multiple collections simultaneously (unlike albums).
 */
public class VaultCollection {

    public String id;
    public String name;
    public String colorHex;
    public long createdAt;
    public long updatedAt;

    /** Creates a new collection with auto-generated id and current timestamps. */
    public VaultCollection(String name, String colorHex) {
        this.id        = UUID.randomUUID().toString();
        this.name      = name;
        this.colorHex  = colorHex;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    /** No-arg constructor for deserialization. */
    public VaultCollection() {}

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id",        id        != null ? id        : "");
            o.put("name",      name      != null ? name      : "");
            o.put("colorHex",  colorHex  != null ? colorHex  : "");
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            return o;
        } catch (Exception e) {
            android.util.Log.e("VaultCollection", "toJson failed", e);
            return new JSONObject();
        }
    }

    public static VaultCollection fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            VaultCollection c = new VaultCollection();
            c.id        = o.optString("id",        UUID.randomUUID().toString());
            c.name      = o.optString("name",      "");
            c.colorHex  = o.optString("colorHex",  "#607D8B");
            c.createdAt = o.optLong("createdAt",   System.currentTimeMillis());
            c.updatedAt = o.optLong("updatedAt",   c.createdAt);
            return c;
        } catch (Exception e) {
            android.util.Log.e("VaultCollection", "fromJson failed", e);
            return null;
        }
    }
}
