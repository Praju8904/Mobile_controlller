package com.prajwal.myfirstapp.expenses;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class Repayment {

    public String id;
    public String moneyRecordId;
    public double amount;
    public long date;
    public String walletId; // optional
    public String notes;
    public long createdAt;

    public Repayment() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    // ─── Serialization ───────────────────────────────────────

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("moneyRecordId", moneyRecordId != null ? moneyRecordId : "");
            obj.put("amount", amount);
            obj.put("date", date);
            obj.put("walletId", walletId != null ? walletId : "");
            obj.put("notes", notes != null ? notes : "");
            obj.put("createdAt", createdAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static Repayment fromJson(JSONObject obj) {
        if (obj == null) return null;
        Repayment r = new Repayment();
        try {
            r.id = obj.optString("id", UUID.randomUUID().toString());
            r.moneyRecordId = obj.optString("moneyRecordId", "");
            r.amount = obj.optDouble("amount", 0);
            r.date = obj.optLong("date", 0);
            r.walletId = obj.optString("walletId", "");
            r.notes = obj.optString("notes", "");
            r.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return r;
    }
}
