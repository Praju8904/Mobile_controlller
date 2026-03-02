package com.prajwal.myfirstapp.chat;

import org.json.JSONException;
import org.json.JSONObject;

public class ChatMessage {
    public String id;           // Unique ID for deduplication
    public String content;      // Text or File Path
    public String type;         // "text", "file", "image", "system"
    public boolean isMe;        // true = Phone, false = PC
    public long timestamp;      // For sorting/display
    public String metadata;     // Extra info (filesize, etc.)

    public ChatMessage(String content, String type, boolean isMe) {
        this.id = "msg_" + (isMe ? "phone" : "pc") + "_" + System.currentTimeMillis();
        this.content = content;
        this.type = type;
        this.isMe = isMe;
        this.timestamp = System.currentTimeMillis();
        this.metadata = "";
    }

    // Convert to JSON for storage
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("content", content);
            json.put("type", type);
            json.put("isMe", isMe);
            json.put("timestamp", timestamp);
            json.put("metadata", metadata);
        } catch (JSONException e) { e.printStackTrace(); }
        return json;
    }

    // Load from JSON
    public static ChatMessage fromJson(JSONObject json) {
        try {
            ChatMessage msg = new ChatMessage(
                json.getString("content"),
                json.getString("type"),
                json.getBoolean("isMe")
            );
            msg.id = json.optString("id", msg.id);
            msg.timestamp = json.optLong("timestamp", System.currentTimeMillis());
            msg.metadata = json.optString("metadata", "");
            return msg;
        } catch (JSONException e) { return null; }
    }
}