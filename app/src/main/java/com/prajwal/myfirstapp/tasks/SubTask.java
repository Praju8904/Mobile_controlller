package com.prajwal.myfirstapp.tasks;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * SubTask model — represents a single checklist item within a Task.
 */
public class SubTask {

    public String id;
    public String title;
    public boolean isCompleted;
    public String dueDate;       // Optional: "YYYY-MM-DD"
    public String assigneeNote;  // Optional: assignee/note text

    // ─── Constructors ────────────────────────────────────────────

    public SubTask() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.title = "";
        this.isCompleted = false;
        this.dueDate = null;
        this.assigneeNote = null;
    }

    public SubTask(String title) {
        this();
        this.title = title != null ? title : "";
    }

    public SubTask(String id, String title, boolean isCompleted) {
        this.id = id != null ? id : UUID.randomUUID().toString().substring(0, 8);
        this.title = title != null ? title : "";
        this.isCompleted = isCompleted;
        this.dueDate = null;
        this.assigneeNote = null;
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("isCompleted", isCompleted);
            if (dueDate != null) json.put("dueDate", dueDate);
            if (assigneeNote != null) json.put("assigneeNote", assigneeNote);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static SubTask fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            SubTask sub = new SubTask();
            sub.id = json.optString("id", UUID.randomUUID().toString().substring(0, 8));
            sub.title = json.optString("title", "");
            sub.isCompleted = json.optBoolean("isCompleted", false);
            sub.dueDate = json.has("dueDate") ? json.optString("dueDate", null) : null;
            sub.assigneeNote = json.has("assigneeNote") ? json.optString("assigneeNote", null) : null;
            return sub;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ─── Utility ─────────────────────────────────────────────────

    public SubTask copy() {
        SubTask c = new SubTask(this.id, this.title, this.isCompleted);
        c.dueDate = this.dueDate;
        c.assigneeNote = this.assigneeNote;
        return c;
    }
}
