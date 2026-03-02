package com.prajwal.myfirstapp.meetings;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Represents a single agenda item within a meeting.
 *
 * Supports: duration tracking, presenter assignment, completion state,
 * notes, and sort ordering.
 */
public class AgendaItem {

    // ─── Fields ──────────────────────────────────────────────────

    public String id;
    public String meetingId;
    public String title;
    public int    durationMinutes;  // Planned duration in minutes
    public String presenter;        // Attendee name presenting this item
    public boolean isCompleted;
    public String notes;
    public int    sortOrder;        // 0-based display order

    // ─── Constructors ────────────────────────────────────────────

    public AgendaItem() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.meetingId = "";
        this.title = "";
        this.durationMinutes = 0;
        this.presenter = "";
        this.isCompleted = false;
        this.notes = "";
        this.sortOrder = 0;
    }

    public AgendaItem(String title, int durationMinutes) {
        this();
        this.title = title != null ? title : "";
        this.durationMinutes = Math.max(0, durationMinutes);
    }

    public AgendaItem(String title, int durationMinutes, String presenter) {
        this(title, durationMinutes);
        this.presenter = presenter != null ? presenter : "";
    }

    // ─── Helper Methods ──────────────────────────────────────────

    /**
     * Returns a human-readable duration string.
     * E.g. 0 → "", 15 → "15 min", 60 → "1 hr", 90 → "1 hr 30 min".
     */
    public String getDurationText() {
        return formatAgendaDuration(durationMinutes);
    }

    public static String formatAgendaDuration(int minutes) {
        if (minutes <= 0) return "";
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60;
        int m = minutes % 60;
        return m > 0 ? h + " hr " + m + " min" : h + " hr";
    }

    public boolean hasPresenter() {
        return presenter != null && !presenter.trim().isEmpty();
    }

    public boolean hasNotes() {
        return notes != null && !notes.trim().isEmpty();
    }

    /** Returns a display-ready summary, e.g. "Review Q1 results (30 min)". */
    public String getSummary() {
        String dur = getDurationText();
        if (dur.isEmpty()) return title != null ? title : "";
        return (title != null ? title : "") + " (" + dur + ")";
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("meetingId", meetingId != null ? meetingId : "");
            json.put("title", title != null ? title : "");
            json.put("durationMinutes", durationMinutes);
            json.put("presenter", presenter != null ? presenter : "");
            json.put("isCompleted", isCompleted);
            json.put("notes", notes != null ? notes : "");
            json.put("sortOrder", sortOrder);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static AgendaItem fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            AgendaItem item = new AgendaItem();
            item.id = json.optString("id", item.id);
            item.meetingId = json.optString("meetingId", "");
            item.title = json.optString("title", "");
            item.durationMinutes = json.optInt("durationMinutes", 0);
            item.presenter = json.optString("presenter", "");
            item.isCompleted = json.optBoolean("isCompleted", false);
            item.notes = json.optString("notes", "");
            item.sortOrder = json.optInt("sortOrder", 0);
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
