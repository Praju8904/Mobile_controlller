package com.prajwal.myfirstapp.mathstudio;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MathHistoryManager {

    private static final String PREFS_NAME = "math_history_prefs";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_ENTRIES = 100;

    public static class HistoryEntry {
        public String expression;
        public String result;
        public String mode;
        public long timestamp;

        public HistoryEntry(String expression, String result, String mode, long timestamp) {
            this.expression = expression;
            this.result = result;
            this.mode = mode;
            this.timestamp = timestamp;
        }
    }

    public static void saveCalculation(Context context, String expression, String result, String mode) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_HISTORY, "[]");
            JSONArray array = new JSONArray(json);

            JSONObject entry = new JSONObject();
            entry.put("expression", expression);
            entry.put("result", result);
            entry.put("mode", mode);
            entry.put("timestamp", System.currentTimeMillis());

            // Insert at front
            JSONArray newArray = new JSONArray();
            newArray.put(entry);
            for (int i = 0; i < Math.min(array.length(), MAX_ENTRIES - 1); i++) {
                newArray.put(array.get(i));
            }

            prefs.edit().putString(KEY_HISTORY, newArray.toString()).apply();
        } catch (Exception e) {
            // Ignore
        }
    }

    public static List<HistoryEntry> getHistory(Context context) {
        List<HistoryEntry> list = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_HISTORY, "[]");
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new HistoryEntry(
                    obj.optString("expression", ""),
                    obj.optString("result", ""),
                    obj.optString("mode", "DEG"),
                    obj.optLong("timestamp", 0)
                ));
            }
        } catch (Exception e) {
            // Return empty list
        }
        return list;
    }

    public static void clearHistory(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
               .edit().remove(KEY_HISTORY).apply();
    }
}
