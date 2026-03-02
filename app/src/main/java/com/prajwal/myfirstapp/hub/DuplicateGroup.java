package com.prajwal.myfirstapp.hub;

import org.json.JSONObject;
import java.util.UUID;

/**
 * Groups files with identical content (same hash) as duplicates.
 */
public class DuplicateGroup {

    public String id;
    public String fileHash;
    public long fileSize;
    public String fileName;
    public int duplicateCount;
    public long totalWastedBytes;
    public long firstDetectedAt;

    public DuplicateGroup() {
        this.id = UUID.randomUUID().toString();
        this.firstDetectedAt = System.currentTimeMillis();
    }

    public String getFormattedWasted() {
        if (totalWastedBytes < 1024) return totalWastedBytes + " B";
        if (totalWastedBytes < 1024 * 1024) return String.format("%.1f KB", totalWastedBytes / 1024.0);
        if (totalWastedBytes < 1024L * 1024 * 1024) return String.format("%.1f MB", totalWastedBytes / (1024.0 * 1024));
        return String.format("%.2f GB", totalWastedBytes / (1024.0 * 1024 * 1024));
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id != null ? id : "");
            o.put("fileHash", fileHash != null ? fileHash : "");
            o.put("fileSize", fileSize);
            o.put("fileName", fileName != null ? fileName : "");
            o.put("duplicateCount", duplicateCount);
            o.put("totalWastedBytes", totalWastedBytes);
            o.put("firstDetectedAt", firstDetectedAt);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static DuplicateGroup fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            DuplicateGroup g = new DuplicateGroup();
            g.id = o.optString("id", UUID.randomUUID().toString());
            g.fileHash = o.optString("fileHash", "");
            g.fileSize = o.optLong("fileSize", 0);
            g.fileName = o.optString("fileName", "");
            g.duplicateCount = o.optInt("duplicateCount", 0);
            g.totalWastedBytes = o.optLong("totalWastedBytes", 0);
            g.firstDetectedAt = o.optLong("firstDetectedAt", System.currentTimeMillis());
            return g;
        } catch (Exception e) {
            return null;
        }
    }
}
