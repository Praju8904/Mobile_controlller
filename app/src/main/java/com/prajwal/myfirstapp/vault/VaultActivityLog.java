package com.prajwal.myfirstapp.vault;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Audit log entry for vault activity.
 */
public class VaultActivityLog {

    public enum Action {
        UNLOCKED, LOCKED, FILE_IMPORTED, FILE_DELETED, FILE_VIEWED,
        FAILED_ATTEMPT, PIN_CHANGED, ALBUM_CREATED, ALBUM_DELETED, FILE_EXPORTED
    }

    public String id;
    public Action action;
    public long timestamp;
    public String details;

    public VaultActivityLog() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public VaultActivityLog(Action action, String details) {
        this();
        this.action = action;
        this.details = details;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("action", action != null ? action.name() : Action.UNLOCKED.name());
            o.put("timestamp", timestamp);
            o.put("details", details != null ? details : "");
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static VaultActivityLog fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            VaultActivityLog log = new VaultActivityLog();
            log.id = o.optString("id", UUID.randomUUID().toString());
            String actionStr = o.optString("action", "UNLOCKED");
            try { log.action = Action.valueOf(actionStr); } catch (Exception e) { log.action = Action.UNLOCKED; }
            log.timestamp = o.optLong("timestamp", System.currentTimeMillis());
            log.details = o.optString("details", "");
            return log;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isFailedAttempt() {
        return action == Action.FAILED_ATTEMPT;
    }

    public String getActionLabel() {
        if (action == null) return "Unknown";
        switch (action) {
            case UNLOCKED: return "Vault Unlocked";
            case LOCKED: return "Vault Locked";
            case FILE_IMPORTED: return "File Imported";
            case FILE_DELETED: return "File Deleted";
            case FILE_VIEWED: return "File Viewed";
            case FAILED_ATTEMPT: return "Failed Unlock Attempt";
            case PIN_CHANGED: return "PIN Changed";
            case ALBUM_CREATED: return "Album Created";
            case ALBUM_DELETED: return "Album Deleted";
            case FILE_EXPORTED: return "File Exported";
            default: return "Unknown";
        }
    }
}
