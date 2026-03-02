package com.prajwal.myfirstapp.meetings;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Represents an action item (follow-up task) arising from a meeting.
 *
 * Supports: assignee tracking, due dates, completion state, creation
 * timestamp, and optional linking to a Task Manager Task by ID.
 */
public class ActionItem {

    // ─── Fields ──────────────────────────────────────────────────

    public String  id;
    public String  meetingId;
    public String  title;
    public String  assigneeName;
    public String  dueDate;        // "yyyy-MM-dd" or null
    public boolean isCompleted;
    public long    createdAt;
    public String  linkedTaskId;   // Nullable — links to a Task.id when set

    // ─── Constructors ────────────────────────────────────────────

    public ActionItem() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.meetingId = "";
        this.title = "";
        this.assigneeName = "";
        this.dueDate = null;
        this.isCompleted = false;
        this.createdAt = System.currentTimeMillis();
        this.linkedTaskId = null;
    }

    public ActionItem(String title, String assigneeName) {
        this();
        this.title = title != null ? title : "";
        this.assigneeName = assigneeName != null ? assigneeName : "";
    }

    public ActionItem(String title, String assigneeName, String dueDate) {
        this(title, assigneeName);
        this.dueDate = (dueDate != null && !dueDate.isEmpty() && !"null".equals(dueDate))
                ? dueDate : null;
    }

    // ─── Helper Methods ──────────────────────────────────────────

    public boolean hasDueDate() {
        return dueDate != null && !dueDate.isEmpty() && !"null".equals(dueDate);
    }

    public boolean isLinkedToTask() {
        return linkedTaskId != null && !linkedTaskId.isEmpty();
    }

    public boolean hasAssignee() {
        return assigneeName != null && !assigneeName.trim().isEmpty();
    }

    /** Mark this action item complete. */
    public void markCompleted() {
        this.isCompleted = true;
    }

    /** Reopen a previously completed action item. */
    public void markOpen() {
        this.isCompleted = false;
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("meetingId", meetingId != null ? meetingId : "");
            json.put("title", title != null ? title : "");
            json.put("assigneeName", assigneeName != null ? assigneeName : "");
            json.put("dueDate", dueDate != null ? dueDate : JSONObject.NULL);
            json.put("isCompleted", isCompleted);
            json.put("createdAt", createdAt);
            json.put("linkedTaskId", linkedTaskId != null ? linkedTaskId : JSONObject.NULL);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static ActionItem fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            ActionItem item = new ActionItem();
            item.id = json.optString("id", item.id);
            item.meetingId = json.optString("meetingId", "");
            item.title = json.optString("title", "");
            item.assigneeName = json.optString("assigneeName", "");

            String dueDateVal = json.optString("dueDate", null);
            item.dueDate = (dueDateVal == null || "null".equals(dueDateVal) || dueDateVal.isEmpty())
                    ? null : dueDateVal;

            item.isCompleted = json.optBoolean("isCompleted", false);
            item.createdAt = json.optLong("createdAt", System.currentTimeMillis());

            String linkedTaskVal = json.optString("linkedTaskId", null);
            item.linkedTaskId = (linkedTaskVal == null || "null".equals(linkedTaskVal) || linkedTaskVal.isEmpty())
                    ? null : linkedTaskVal;

            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
