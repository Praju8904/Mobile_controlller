package com.prajwal.myfirstapp;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class Expense {
    public String id;
    public double amount;
    public String category;
    public String note;
    public long timestamp;
    public boolean isIncome;
    public String walletId;

    public static final String[] CATEGORIES = {
        "Food", "Transport", "Shopping", "Bills", "Entertainment",
        "Health", "Education", "Travel", "Salary", "Other"
    };

    public static final String[] CATEGORY_ICONS = {
        "🍔", "🚗", "🛍️", "📄", "🎮",
        "💊", "📚", "✈️", "💰", "📦"
    };

    public static final int[] CATEGORY_COLORS = {
        0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFBE0B, 0xFF3B82F6, 0xFFA855F7,
        0xFF10B981, 0xFFF59E0B, 0xFF06B6D4, 0xFF22C55E, 0xFF6B7280
    };

    public Expense(double amount, String category, String note, boolean isIncome) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.amount = amount;
        this.category = category;
        this.note = note;
        this.timestamp = System.currentTimeMillis();
        this.isIncome = isIncome;
        this.walletId = Wallet.DEFAULT_WALLET_ID;
    }

    public Expense(double amount, String category, String note, boolean isIncome, String walletId) {
        this(amount, category, note, isIncome);
        this.walletId = walletId != null ? walletId : Wallet.DEFAULT_WALLET_ID;
    }

    public static int getCategoryIndex(String category) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(category)) return i;
        }
        return CATEGORIES.length - 1;
    }

    public static int getCategoryColor(String category) {
        return CATEGORY_COLORS[getCategoryIndex(category)];
    }

    public static String getCategoryIcon(String category) {
        return CATEGORY_ICONS[getCategoryIndex(category)];
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("amount", amount);
            json.put("category", category);
            json.put("note", note);
            json.put("timestamp", timestamp);
            json.put("isIncome", isIncome);
            json.put("walletId", walletId != null ? walletId : Wallet.DEFAULT_WALLET_ID);
        } catch (JSONException e) { e.printStackTrace(); }
        return json;
    }

    public static Expense fromJson(JSONObject json) {
        try {
            Expense e = new Expense(
                json.getDouble("amount"),
                json.getString("category"),
                json.optString("note", ""),
                json.optBoolean("isIncome", false)
            );
            e.id = json.getString("id");
            e.timestamp = json.getLong("timestamp");
            e.walletId = json.optString("walletId", Wallet.DEFAULT_WALLET_ID);
            return e;
        } catch (JSONException e) { return null; }
    }
}
