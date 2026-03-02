package com.prajwal.myfirstapp.expenses;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Represents a fund transfer between two wallets.
 * NOT counted as expense or income — purely internal movement.
 */
public class WalletTransfer {

    public String id;
    public String fromWalletId;
    public String toWalletId;
    public String fromWalletName;
    public String toWalletName;
    public double amount;
    public String notes;
    public long timestamp;

    public WalletTransfer() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.timestamp = System.currentTimeMillis();
        this.notes = "";
    }

    // ─── Display Helpers ─────────────────────────────────────

    public String getFormattedAmount() {
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        return nf.format(amount);
    }

    public String getFormattedDate() {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(new Date(timestamp));
    }

    public String getShortDate() {
        return new SimpleDateFormat("dd MMM", Locale.getDefault())
            .format(new Date(timestamp));
    }

    public String getDescription() {
        return fromWalletName + " → " + toWalletName;
    }

    // ─── JSON Serialization ──────────────────────────────────

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("fromWalletId", fromWalletId);
            obj.put("toWalletId", toWalletId);
            obj.put("fromWalletName", fromWalletName);
            obj.put("toWalletName", toWalletName);
            obj.put("amount", amount);
            obj.put("notes", notes);
            obj.put("timestamp", timestamp);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static WalletTransfer fromJson(JSONObject obj) {
        WalletTransfer t = new WalletTransfer();
        try {
            t.id = obj.optString("id", t.id);
            t.fromWalletId = obj.optString("fromWalletId", "");
            t.toWalletId = obj.optString("toWalletId", "");
            t.fromWalletName = obj.optString("fromWalletName", "Unknown");
            t.toWalletName = obj.optString("toWalletName", "Unknown");
            t.amount = obj.optDouble("amount", 0);
            t.notes = obj.optString("notes", "");
            t.timestamp = obj.optLong("timestamp", t.timestamp);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return t;
    }
}
