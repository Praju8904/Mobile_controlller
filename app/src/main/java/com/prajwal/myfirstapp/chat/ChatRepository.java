package com.prajwal.myfirstapp.chat;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ChatRepository {
    private static final String FILE_NAME = "chat_history.json";
    private static final String PREFS_NAME = "chat_sync_prefs";
    private static final String LAST_SYNC_KEY = "last_sync_timestamp";
    private Context context;

    public ChatRepository(Context context) {
        this.context = context;
    }

    public void saveMessages(ArrayList<ChatMessage> messages) {
        JSONArray array = new JSONArray();
        for (ChatMessage msg : messages) {
            array.put(msg.toJson());
        }
        try (FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)) {
            fos.write(array.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public ArrayList<ChatMessage> loadMessages() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.exists()) return messages;

            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String jsonStr = new String(data, StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(jsonStr);

            for (int i = 0; i < array.length(); i++) {
                ChatMessage msg = ChatMessage.fromJson(array.getJSONObject(i));
                if (msg != null) messages.add(msg);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return messages;
    }
    
    /**
     * Merge incoming messages (from PC sync) with existing local messages.
     * Deduplicates by message ID. Returns the merged list.
     */
    public ArrayList<ChatMessage> mergeMessages(ArrayList<ChatMessage> local, ArrayList<ChatMessage> incoming) {
        Set<String> existingIds = new HashSet<>();
        for (ChatMessage msg : local) {
            if (msg.id != null) existingIds.add(msg.id);
        }

        int added = 0;
        for (ChatMessage msg : incoming) {
            if (msg.id != null && !existingIds.contains(msg.id)) {
                local.add(msg);
                existingIds.add(msg.id);
                added++;
            }
        }

        if (added > 0) {
            local.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        }

        return local;
    }

    /** Get the timestamp of the most recent message (for sync requests). */
    public long getLastSyncTimestamp() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(LAST_SYNC_KEY, 0);
    }

    /** Update the last sync timestamp. */
    public void setLastSyncTimestamp(long timestamp) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(LAST_SYNC_KEY, timestamp).apply();
    }

    public void clearHistory() {
        context.deleteFile(FILE_NAME);
    }
}