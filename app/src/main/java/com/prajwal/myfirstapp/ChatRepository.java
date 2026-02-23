package com.prajwal.myfirstapp;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ChatRepository {
    private static final String FILE_NAME = "chat_history.json";
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
    
    public void clearHistory() {
        context.deleteFile(FILE_NAME);
    }
}