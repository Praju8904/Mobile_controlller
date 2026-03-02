package com.prajwal.myfirstapp.expenses;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Data model for an income record.
 */
public class Income {

    // ─── Income Sources ──────────────────────────────────────
    public static final String SOURCE_SALARY = "Salary";
    public static final String SOURCE_FREELANCE = "Freelance";
    public static final String SOURCE_GIFT = "Gift";
    public static final String SOURCE_POCKET_MONEY = "Pocket Money";
    public static final String SOURCE_REFUND = "Refund";
    public static final String SOURCE_INVESTMENT = "Investment Returns";
    public static final String SOURCE_RENTAL = "Rental";
    public static final String SOURCE_BONUS = "Bonus";
    public static final String SOURCE_SCHOLARSHIP = "Scholarship";
    public static final String SOURCE_OTHER = "Other";

    public static final String[] SOURCES = {
        SOURCE_SALARY, SOURCE_FREELANCE, SOURCE_GIFT, SOURCE_POCKET_MONEY,
        SOURCE_REFUND, SOURCE_INVESTMENT, SOURCE_RENTAL,
        SOURCE_BONUS, SOURCE_SCHOLARSHIP, SOURCE_OTHER
    };

    public static final String[] SOURCE_ICONS = {
        "💼", "💻", "🎁", "✋", "↩️", "📈", "🏠", "⭐", "🎓", "➕"
    };

    // ─── Fields ──────────────────────────────────────────────
    public String id;
    public String title;
    public double amount;
    public String currency;
    public String walletId;
    public String categoryId;
    public String source;
    public long date;           // date as millisecond timestamp
    public String time;         // HH:mm display string
    public boolean isRecurring;
    public String recurrenceId; // links to RecurringIncome.id
    public String notes;
    public ArrayList<String> tags;
    public long createdAt;
    public long updatedAt;

    // ─── Constructors ─────────────────────────────────────────

    public Income() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.currency = "₹";
        this.walletId = Wallet.DEFAULT_WALLET_ID;
        this.source = SOURCE_OTHER;
        this.date = System.currentTimeMillis();
        this.isRecurring = false;
        this.tags = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Income(String title, double amount, String categoryId, String source, String walletId) {
        this();
        this.title = title;
        this.amount = amount;
        this.categoryId = categoryId;
        this.source = source;
        this.walletId = walletId != null ? walletId : Wallet.DEFAULT_WALLET_ID;
    }

    // ─── Source Helpers ───────────────────────────────────────

    public static int getSourceIndex(String source) {
        for (int i = 0; i < SOURCES.length; i++) {
            if (SOURCES[i].equals(source)) return i;
        }
        return SOURCES.length - 1;
    }

    public static String getSourceIcon(String source) {
        int idx = getSourceIndex(source);
        return SOURCE_ICONS[idx];
    }

    // ─── JSON Serialization ───────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title != null ? title : "");
            json.put("amount", amount);
            json.put("currency", currency != null ? currency : "₹");
            json.put("walletId", walletId != null ? walletId : Wallet.DEFAULT_WALLET_ID);
            json.put("categoryId", categoryId != null ? categoryId : "");
            json.put("source", source != null ? source : SOURCE_OTHER);
            json.put("date", date);
            json.put("time", time != null ? time : "");
            json.put("isRecurring", isRecurring);
            json.put("recurrenceId", recurrenceId != null ? recurrenceId : "");
            json.put("notes", notes != null ? notes : "");
            JSONArray tagsArray = new JSONArray();
            if (tags != null) for (String t : tags) tagsArray.put(t);
            json.put("tags", tagsArray);
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static Income fromJson(JSONObject json) {
        try {
            Income inc = new Income();
            inc.id = json.getString("id");
            inc.title = json.optString("title", "");
            inc.amount = json.getDouble("amount");
            inc.currency = json.optString("currency", "₹");
            inc.walletId = json.optString("walletId", Wallet.DEFAULT_WALLET_ID);
            inc.categoryId = json.optString("categoryId", "");
            inc.source = json.optString("source", SOURCE_OTHER);
            inc.date = json.optLong("date", System.currentTimeMillis());
            inc.time = json.optString("time", "");
            inc.isRecurring = json.optBoolean("isRecurring", false);
            inc.recurrenceId = json.optString("recurrenceId", "");
            inc.notes = json.optString("notes", "");
            inc.tags = new ArrayList<>();
            JSONArray tagsArray = json.optJSONArray("tags");
            if (tagsArray != null) {
                for (int i = 0; i < tagsArray.length(); i++) {
                    inc.tags.add(tagsArray.getString(i));
                }
            }
            inc.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            inc.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            return inc;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
