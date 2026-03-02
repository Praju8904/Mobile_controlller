package com.prajwal.myfirstapp.hub;

import org.json.JSONObject;
import java.util.UUID;

/**
 * Records an action performed on a HubFile for the activity feed.
 */
public class FileActivity {

    public enum Action {
        IMPORTED, VIEWED, SHARED, MOVED, RENAMED, DELETED,
        FAVOURITED, TAGGED, NOTED, DUPLICATE_FOUND
    }

    public String id;
    public String fileId;
    public String fileName;
    public String fileTypeEmoji;
    public Action action;
    public long timestamp;
    public String details;

    public FileActivity() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public FileActivity(String fileId, String fileName, String fileTypeEmoji, Action action, String details) {
        this();
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileTypeEmoji = fileTypeEmoji;
        this.action = action;
        this.details = details;
    }

    public String getActionDescription() {
        if (action == null) return "Activity";
        switch (action) {
            case IMPORTED: return "Imported";
            case VIEWED: return "Viewed";
            case SHARED: return "Shared";
            case MOVED: return "Moved";
            case RENAMED: return "Renamed";
            case DELETED: return "Deleted";
            case FAVOURITED: return "Favourited";
            case TAGGED: return "Tagged";
            case NOTED: return "Note added";
            case DUPLICATE_FOUND: return "Duplicate found";
            default: return "Activity";
        }
    }

    public String getRelativeTime() {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60_000) return "just now";
        if (diff < 3_600_000) return (diff / 60_000) + "m ago";
        if (diff < 86_400_000) return (diff / 3_600_000) + "h ago";
        return (diff / 86_400_000) + "d ago";
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id != null ? id : "");
            o.put("fileId", fileId != null ? fileId : "");
            o.put("fileName", fileName != null ? fileName : "");
            o.put("fileTypeEmoji", fileTypeEmoji != null ? fileTypeEmoji : "📄");
            o.put("action", action != null ? action.name() : Action.VIEWED.name());
            o.put("timestamp", timestamp);
            o.put("details", details != null ? details : "");
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static FileActivity fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            FileActivity a = new FileActivity();
            a.id = o.optString("id", UUID.randomUUID().toString());
            a.fileId = o.optString("fileId", "");
            a.fileName = o.optString("fileName", "");
            a.fileTypeEmoji = o.optString("fileTypeEmoji", "📄");
            String act = o.optString("action", "VIEWED");
            try { a.action = Action.valueOf(act); } catch (Exception e) { a.action = Action.VIEWED; }
            a.timestamp = o.optLong("timestamp", System.currentTimeMillis());
            a.details = o.optString("details", "");
            return a;
        } catch (Exception e) {
            return null;
        }
    }
}
