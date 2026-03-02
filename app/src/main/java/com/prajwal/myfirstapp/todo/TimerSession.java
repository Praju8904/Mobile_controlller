package com.prajwal.myfirstapp.todo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Data model representing a single timed work session for a TodoItem.
 */
public class TimerSession {

    public String id;
    public String todoItemId;
    public long startTime;
    public long endTime;
    public long durationSeconds;

    // ─── Constructors ────────────────────────────────────────────

    public TimerSession() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.todoItemId = "";
        this.startTime = 0;
        this.endTime = 0;
        this.durationSeconds = 0;
    }

    public TimerSession(String todoItemId, long startTime, long endTime) {
        this();
        this.todoItemId = todoItemId != null ? todoItemId : "";
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = endTime > startTime ? (endTime - startTime) / 1000 : 0;
    }

    // ─── Duration Formatting ─────────────────────────────────────

    /** Returns a human-readable duration string, e.g. "1h 23m", "45m", or "30s". */
    public String getDurationFormatted() {
        long totalSeconds = durationSeconds;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            if (minutes > 0) return hours + "h " + minutes + "m";
            return hours + "h";
        }
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("todoItemId", todoItemId);
            json.put("startTime", startTime);
            json.put("endTime", endTime);
            json.put("durationSeconds", durationSeconds);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static TimerSession fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            TimerSession session = new TimerSession();
            session.id = json.optString("id", UUID.randomUUID().toString().substring(0, 12));
            session.todoItemId = json.optString("todoItemId", "");
            session.startTime = json.optLong("startTime", 0);
            session.endTime = json.optLong("endTime", 0);
            session.durationSeconds = json.optLong("durationSeconds", 0);
            return session;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
