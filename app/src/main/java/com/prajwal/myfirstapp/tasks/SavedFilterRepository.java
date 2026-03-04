package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists saved filters ("Smart Lists") in SharedPreferences.
 * Gap ref: 6.15
 */
public class SavedFilterRepository {

    private static final String PREF_FILE = "task_prefs";
    private static final String PREF_KEY  = "saved_filters";

    private final SharedPreferences prefs;

    public SavedFilterRepository(Context context) {
        this.prefs = context.getApplicationContext()
                            .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public List<SavedFilter> getAll() {
        List<SavedFilter> result = new ArrayList<>();
        String json = prefs.getString(PREF_KEY, null);
        if (json == null) return result;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                SavedFilter sf = SavedFilter.fromJson(arr.optJSONObject(i));
                if (sf != null) result.add(sf);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public void save(SavedFilter sf) {
        List<SavedFilter> all = getAll();
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(sf.id)) {
                all.set(i, sf);
                found = true;
                break;
            }
        }
        if (!found) all.add(sf);
        persist(all);
    }

    public void delete(String id) {
        List<SavedFilter> all = getAll();
        all.removeIf(sf -> sf.id.equals(id));
        persist(all);
    }

    public void reorder(List<String> orderedIds) {
        List<SavedFilter> all = getAll();
        List<SavedFilter> reordered = new ArrayList<>();
        for (String id : orderedIds) {
            for (SavedFilter sf : all) {
                if (sf.id.equals(id)) {
                    reordered.add(sf);
                    break;
                }
            }
        }
        persist(reordered);
    }

    private void persist(List<SavedFilter> list) {
        JSONArray arr = new JSONArray();
        for (SavedFilter sf : list) arr.put(sf.toJson());
        prefs.edit().putString(PREF_KEY, arr.toString()).apply();
    }
}
