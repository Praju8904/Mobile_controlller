package com.prajwal.myfirstapp.expenses;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;

/**
 * Repository for IncomeCategory CRUD. Seeds default categories on first access.
 */
public class IncomeCategoryRepository {

    private static final String PREFS_NAME = "income_category_prefs";
    private static final String DATA_KEY = "income_categories_data";
    private static final String SEEDED_KEY = "defaults_seeded";

    private final Context context;

    public IncomeCategoryRepository(Context context) {
        this.context = context;
        ensureDefaultsSeeded();
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void ensureDefaultsSeeded() {
        if (!getPrefs().getBoolean(SEEDED_KEY, false)) {
            ArrayList<IncomeCategory> defaults = new ArrayList<>();
            for (IncomeCategory cat : IncomeCategory.getDefaults()) {
                defaults.add(cat);
            }
            saveAll(defaults);
            getPrefs().edit().putBoolean(SEEDED_KEY, true).apply();
        }
    }

    public synchronized void saveAll(ArrayList<IncomeCategory> categories) {
        JSONArray array = new JSONArray();
        for (IncomeCategory cat : categories) array.put(cat.toJson());
        getPrefs().edit().putString(DATA_KEY, array.toString()).apply();
    }

    public ArrayList<IncomeCategory> loadAll() {
        ArrayList<IncomeCategory> list = new ArrayList<>();
        String json = getPrefs().getString(DATA_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                IncomeCategory cat = IncomeCategory.fromJson(array.getJSONObject(i));
                if (cat != null) list.add(cat);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void addCategory(IncomeCategory cat) {
        ArrayList<IncomeCategory> all = loadAll();
        all.add(cat);
        saveAll(all);
    }

    public void updateCategory(IncomeCategory updated) {
        ArrayList<IncomeCategory> all = loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(updated.id)) {
                all.set(i, updated);
                break;
            }
        }
        saveAll(all);
    }

    public void deleteCategory(String id) {
        ArrayList<IncomeCategory> all = loadAll();
        all.removeIf(cat -> cat.id.equals(id) && !cat.isDefault);
        saveAll(all);
    }

    public IncomeCategory getById(String id) {
        for (IncomeCategory cat : loadAll()) {
            if (cat.id.equals(id)) return cat;
        }
        return null;
    }

    public IncomeCategory getByName(String name) {
        for (IncomeCategory cat : loadAll()) {
            if (name.equals(cat.name)) return cat;
        }
        return null;
    }
}
