package com.prajwal.myfirstapp;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Records a payment from one member to another to settle a debt.
 */
public class Settlement {

    public String id;
    public String groupId;
    public String fromMemberId;     // Who paid
    public String toMemberId;       // Who received
    public double amount;
    public String currency;
    public long date;
    public String notes;
    public boolean isConfirmed;

    private static final NumberFormat currFmt = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private static final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    public Settlement() {
        this.id = java.util.UUID.randomUUID().toString();
        this.currency = "₹";
        this.date = System.currentTimeMillis();
        this.isConfirmed = true;
    }

    public String getFormattedAmount() {
        return currFmt.format(amount);
    }

    public String getFormattedDate() {
        return dateFmt.format(new Date(date));
    }

    // ─── JSON ────────────────────────────────────────────────

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("groupId", groupId != null ? groupId : "");
            obj.put("fromMemberId", fromMemberId != null ? fromMemberId : "");
            obj.put("toMemberId", toMemberId != null ? toMemberId : "");
            obj.put("amount", amount);
            obj.put("currency", currency != null ? currency : "₹");
            obj.put("date", date);
            obj.put("notes", notes != null ? notes : "");
            obj.put("isConfirmed", isConfirmed);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static Settlement fromJson(JSONObject obj) {
        try {
            Settlement s = new Settlement();
            s.id = obj.getString("id");
            s.groupId = obj.optString("groupId", "");
            s.fromMemberId = obj.optString("fromMemberId", "");
            s.toMemberId = obj.optString("toMemberId", "");
            s.amount = obj.optDouble("amount", 0);
            s.currency = obj.optString("currency", "₹");
            s.date = obj.optLong("date", 0);
            s.notes = obj.optString("notes", "");
            s.isConfirmed = obj.optBoolean("isConfirmed", true);
            return s;
        } catch (Exception e) {
            return null;
        }
    }
}
