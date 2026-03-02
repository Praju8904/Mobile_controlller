package com.prajwal.myfirstapp.tasks;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Data model for a subtask within a TodoItem.
 * Not to be confused with SubTask.java (used by the task manager).
 */
public class SubtaskItem {

    public String id;
    public String todoItemId;
    public String title;
    public boolean isCompleted;
    public long completedAt;
    public int sortOrder;

    // ─── Constructors ────────────────────────────────────────────

    public SubtaskItem() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.todoItemId = "";
        this.title = "";
        this.isCompleted = false;
        this.completedAt = 0;
        this.sortOrder = 0;
    }

    public SubtaskItem(String todoItemId, String title) {
        this();
        this.todoItemId = todoItemId != null ? todoItemId : "";
        this.title = title != null ? title : "";
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("todoItemId", todoItemId);
            json.put("title", title);
            json.put("isCompleted", isCompleted);
            json.put("completedAt", completedAt);
            json.put("sortOrder", sortOrder);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static SubtaskItem fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            SubtaskItem item = new SubtaskItem();
            item.id = json.optString("id", UUID.randomUUID().toString().substring(0, 12));
            item.todoItemId = json.optString("todoItemId", "");
            item.title = json.optString("title", "");
            item.isCompleted = json.optBoolean("isCompleted", false);
            item.completedAt = json.optLong("completedAt", 0);
            item.sortOrder = json.optInt("sortOrder", 0);
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
