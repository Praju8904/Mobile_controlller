package com.prajwal.myfirstapp.core;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ActivityLogRepository {
    private static final String PREFS_NAME = "ActivityLog";
    private static final String KEY_LOG = "log_entries";
    private static final int MAX_ENTRIES = 200;

    private final SharedPreferences prefs;

    public ActivityLogRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void logActivity(String feature, String description, String icon) {
        try {
            List<ActivityLogEntry> entries = getAllEntries();
            ActivityLogEntry entry = new ActivityLogEntry(feature, description, icon);
            entries.add(0, entry);
            if (entries.size() > MAX_ENTRIES) {
                entries = entries.subList(0, MAX_ENTRIES);
            }
            JSONArray arr = new JSONArray();
            for (ActivityLogEntry e : entries) {
                JSONObject obj = new JSONObject();
                obj.put("feature", e.feature);
                obj.put("description", e.description);
                obj.put("timestamp", e.timestamp);
                obj.put("icon", e.icon);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_LOG, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public List<ActivityLogEntry> getAllEntries() {
        List<ActivityLogEntry> entries = new ArrayList<>();
        try {
            String json = prefs.getString(KEY_LOG, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                ActivityLogEntry e = new ActivityLogEntry(
                    obj.optString("feature", ""),
                    obj.optString("description", ""),
                    obj.optString("icon", "📋")
                );
                e.timestamp = obj.optLong("timestamp", 0);
                entries.add(e);
            }
        } catch (Exception ignored) {}
        return entries;
    }

    public List<ActivityLogEntry> getEntriesForFeature(String feature) {
        List<ActivityLogEntry> all = getAllEntries();
        List<ActivityLogEntry> filtered = new ArrayList<>();
        for (ActivityLogEntry e : all) {
            if (e.feature.equals(feature)) filtered.add(e);
        }
        return filtered;
    }

    public int countThisWeek(String feature) {
        long weekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        int count = 0;
        for (ActivityLogEntry e : getAllEntries()) {
            if (e.timestamp >= weekAgo && (feature == null || e.feature.equals(feature))) {
                count++;
            }
        }
        return count;
    }

    public void clearAll() {
        prefs.edit().remove(KEY_LOG).apply();
    }

    public void clearToday() {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            List<ActivityLogEntry> all = getAllEntries();
            List<ActivityLogEntry> filtered = new ArrayList<>();
            for (ActivityLogEntry e : all) {
                if (e.timestamp < startOfDay) filtered.add(e);
            }
            JSONArray arr = new JSONArray();
            for (ActivityLogEntry e : filtered) {
                JSONObject obj = new JSONObject();
                obj.put("feature", e.feature);
                obj.put("description", e.description);
                obj.put("timestamp", e.timestamp);
                obj.put("icon", e.icon);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_LOG, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
