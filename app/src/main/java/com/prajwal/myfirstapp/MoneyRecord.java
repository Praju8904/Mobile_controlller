package com.prajwal.myfirstapp;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class MoneyRecord {

    public String id;
    public String type; // LENT or BORROWED
    public String personName;
    public String personPhone;
    public int personAvatarColorHex;
    public double amount;
    public String currency;
    public String walletId;
    public String description;
    public long date;
    public long expectedReturnDate; // 0 if not set
    public long actualReturnDate;   // 0 until settled
    public String status;
    public double amountPaid;
    public boolean reminderEnabled;
    public int reminderFrequencyDays;
    public String notes;
    public long createdAt;
    public long updatedAt;
    public boolean logInWallet;

    // ─── Constants ──────────────────────────────────────────

    public static final String TYPE_LENT = "LENT";
    public static final String TYPE_BORROWED = "BORROWED";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PARTIALLY_PAID = "PARTIALLY_PAID";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_OVERDUE = "OVERDUE";
    public static final String STATUS_WRITTEN_OFF = "WRITTEN_OFF";

    public static final int[] AVATAR_COLORS = {
        0xFF7C3AED, 0xFF3B82F6, 0xFFEF4444, 0xFF22C55E,
        0xFFF59E0B, 0xFFA855F7, 0xFF06B6D4, 0xFFEC4899,
        0xFF6366F1, 0xFF14B8A6, 0xFF8B5CF6, 0xFFFF6B6B
    };

    public MoneyRecord() {
        this.id = UUID.randomUUID().toString();
        this.currency = "₹";
        this.status = STATUS_ACTIVE;
        this.amountPaid = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.reminderFrequencyDays = 7;
        this.logInWallet = false;
    }

    /** Outstanding amount remaining to be paid/returned. */
    public double getOutstandingAmount() {
        return Math.max(0, amount - amountPaid);
    }

    /** Up to 2-character initials from personName. */
    public String getAvatarInitials() {
        if (personName == null || personName.isEmpty()) return "?";
        String[] parts = personName.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        int len = Math.min(2, parts[0].length());
        return parts[0].substring(0, len).toUpperCase();
    }

    /** Assign avatar color based on personName hash. */
    public static int pickAvatarColor(String personName) {
        if (personName == null || personName.isEmpty()) return AVATAR_COLORS[0];
        int index = Math.abs(personName.hashCode()) % AVATAR_COLORS.length;
        return AVATAR_COLORS[index];
    }

    // ─── Serialization ───────────────────────────────────────

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("type", type);
            obj.put("personName", personName != null ? personName : "");
            obj.put("personPhone", personPhone != null ? personPhone : "");
            obj.put("personAvatarColorHex", personAvatarColorHex);
            obj.put("amount", amount);
            obj.put("currency", currency != null ? currency : "₹");
            obj.put("walletId", walletId != null ? walletId : "");
            obj.put("description", description != null ? description : "");
            obj.put("date", date);
            obj.put("expectedReturnDate", expectedReturnDate);
            obj.put("actualReturnDate", actualReturnDate);
            obj.put("status", status != null ? status : STATUS_ACTIVE);
            obj.put("amountPaid", amountPaid);
            obj.put("reminderEnabled", reminderEnabled);
            obj.put("reminderFrequencyDays", reminderFrequencyDays);
            obj.put("notes", notes != null ? notes : "");
            obj.put("createdAt", createdAt);
            obj.put("updatedAt", updatedAt);
            obj.put("logInWallet", logInWallet);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static MoneyRecord fromJson(JSONObject obj) {
        if (obj == null) return null;
        MoneyRecord r = new MoneyRecord();
        try {
            r.id = obj.optString("id", UUID.randomUUID().toString());
            r.type = obj.optString("type", TYPE_LENT);
            r.personName = obj.optString("personName", "");
            r.personPhone = obj.optString("personPhone", "");
            r.personAvatarColorHex = obj.optInt("personAvatarColorHex", AVATAR_COLORS[0]);
            r.amount = obj.optDouble("amount", 0);
            r.currency = obj.optString("currency", "₹");
            r.walletId = obj.optString("walletId", "");
            r.description = obj.optString("description", "");
            r.date = obj.optLong("date", 0);
            r.expectedReturnDate = obj.optLong("expectedReturnDate", 0);
            r.actualReturnDate = obj.optLong("actualReturnDate", 0);
            r.status = obj.optString("status", STATUS_ACTIVE);
            r.amountPaid = obj.optDouble("amountPaid", 0);
            r.reminderEnabled = obj.optBoolean("reminderEnabled", false);
            r.reminderFrequencyDays = obj.optInt("reminderFrequencyDays", 7);
            r.notes = obj.optString("notes", "");
            r.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
            r.updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
            r.logInWallet = obj.optBoolean("logInWallet", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return r;
    }
}
