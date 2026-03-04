package com.prajwal.myfirstapp.tasks;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Represents a URL link attached to a task.
 * Gap ref: 4.8
 */
public class TaskLink {
    public String id;      // UUID short (8 chars)
    public String title;   // user-given label e.g. "Design Doc"
    public String url;     // full URL string

    public TaskLink(String title, String url) {
        this.id    = UUID.randomUUID().toString().substring(0, 8);
        this.title = title != null ? title : "";
        this.url   = url   != null ? url   : "";
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id",    id);
            o.put("title", title);
            o.put("url",   url);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o;
    }

    public static TaskLink fromJson(JSONObject o) {
        if (o == null) return null;
        TaskLink l = new TaskLink(o.optString("title", ""), o.optString("url", ""));
        String id = o.optString("id", "");
        if (!id.isEmpty()) l.id = id;
        return l;
    }
}
