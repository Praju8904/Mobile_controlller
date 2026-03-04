package com.prajwal.myfirstapp.tasks;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/**
 * Represents a comment in a task's discussion thread.
 * Comments are authored from either "Mobile" (Android) or "PC" (server).
 */
public class TaskComment {
    public String id;           // UUID short (8 chars)
    public String author;       // "Mobile" on Android, "PC" on server
    public String text;         // comment body
    public long timestamp;      // epoch millis

    public TaskComment(String author, String text) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.author = author;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("author", author);
        o.put("text", text);
        o.put("timestamp", timestamp);
        return o;
    }

    public static TaskComment fromJson(JSONObject o) {
        if (o == null) return null;
        TaskComment c = new TaskComment(
            o.optString("author", "Mobile"),
            o.optString("text", "")
        );
        c.id = o.optString("id", c.id);
        c.timestamp = o.optLong("timestamp", System.currentTimeMillis());
        return c;
    }
}
