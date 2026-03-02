package com.prajwal.myfirstapp.vault;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data model representing a single encrypted file in the Personal Media Vault.
 */
public class VaultFileItem {

    public enum FileType { IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER }

    public String id;
    public String originalFileName;
    public String vaultFileName;       // UUID-based, no revealing extension
    public FileType fileType;
    public String mimeType;
    public long originalSize;          // bytes
    public long encryptedSize;         // bytes
    public long duration;              // seconds (video/audio)
    public int width;                  // pixels (image/video)
    public int height;                 // pixels (image/video)
    public String thumbnailPath;       // encrypted thumbnail file path
    public String albumId;             // nullable
    public List<String> tags;
    public boolean isFavourited;
    public boolean isHidden;
    public long importedAt;
    public long lastAccessedAt;
    public long originalCreatedAt;
    public String notes;

    public VaultFileItem() {
        this.id = UUID.randomUUID().toString();
        this.tags = new ArrayList<>();
        this.isFavourited = false;
        this.isHidden = false;
        this.importedAt = System.currentTimeMillis();
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public static FileType fileTypeFromString(String s) {
        if (s == null) return FileType.OTHER;
        try { return FileType.valueOf(s); } catch (Exception e) { return FileType.OTHER; }
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("originalFileName", originalFileName != null ? originalFileName : "");
            o.put("vaultFileName", vaultFileName != null ? vaultFileName : "");
            o.put("fileType", fileType != null ? fileType.name() : FileType.OTHER.name());
            o.put("mimeType", mimeType != null ? mimeType : "");
            o.put("originalSize", originalSize);
            o.put("encryptedSize", encryptedSize);
            o.put("duration", duration);
            o.put("width", width);
            o.put("height", height);
            o.put("thumbnailPath", thumbnailPath != null ? thumbnailPath : "");
            o.put("albumId", albumId != null ? albumId : "");
            JSONArray tagsArr = new JSONArray();
            if (tags != null) for (String t : tags) tagsArr.put(t);
            o.put("tags", tagsArr);
            o.put("isFavourited", isFavourited);
            o.put("isHidden", isHidden);
            o.put("importedAt", importedAt);
            o.put("lastAccessedAt", lastAccessedAt);
            o.put("originalCreatedAt", originalCreatedAt);
            o.put("notes", notes != null ? notes : "");
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static VaultFileItem fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            VaultFileItem f = new VaultFileItem();
            f.id = o.optString("id", UUID.randomUUID().toString());
            f.originalFileName = o.optString("originalFileName", "");
            f.vaultFileName = o.optString("vaultFileName", "");
            f.fileType = fileTypeFromString(o.optString("fileType", "OTHER"));
            f.mimeType = o.optString("mimeType", "");
            f.originalSize = o.optLong("originalSize", 0);
            f.encryptedSize = o.optLong("encryptedSize", 0);
            f.duration = o.optLong("duration", 0);
            f.width = o.optInt("width", 0);
            f.height = o.optInt("height", 0);
            f.thumbnailPath = o.optString("thumbnailPath", "");
            f.albumId = o.optString("albumId", "");
            if (f.albumId.isEmpty()) f.albumId = null;
            f.tags = new ArrayList<>();
            JSONArray tagsArr = o.optJSONArray("tags");
            if (tagsArr != null) {
                for (int i = 0; i < tagsArr.length(); i++) f.tags.add(tagsArr.getString(i));
            }
            f.isFavourited = o.optBoolean("isFavourited", false);
            f.isHidden = o.optBoolean("isHidden", false);
            f.importedAt = o.optLong("importedAt", System.currentTimeMillis());
            f.lastAccessedAt = o.optLong("lastAccessedAt", System.currentTimeMillis());
            f.originalCreatedAt = o.optLong("originalCreatedAt", 0);
            f.notes = o.optString("notes", "");
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    public String getFormattedSize() {
        long size = originalSize;
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    public String getFormattedDuration() {
        if (duration <= 0) return "";
        long mins = duration / 60;
        long secs = duration % 60;
        return String.format("%d:%02d", mins, secs);
    }
}
