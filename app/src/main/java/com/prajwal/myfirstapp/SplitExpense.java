package com.prajwal.myfirstapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * A single expense shared among group members.
 */
public class SplitExpense {

    // ─── Split Types ─────────────────────────────────────────
    public static final String SPLIT_EQUAL = "equal";
    public static final String SPLIT_PERCENTAGE = "percentage";
    public static final String SPLIT_CUSTOM = "custom";
    public static final String SPLIT_SHARES = "shares";

    // ─── Fields ──────────────────────────────────────────────
    public String id;
    public String groupId;
    public String title;
    public String description;
    public double totalAmount;
    public String currency;
    public String categoryId;           // Same categories as Expense.CATEGORIES
    public String walletId;             // Wallet current user paid from (if they paid)
    public String paidByMemberId;       // Who paid the full bill
    public long date;
    public String splitType;            // equal / percentage / custom / shares
    public ArrayList<MemberSplit> splits;
    public String receiptImagePath;     // Optional photo
    public String notes;
    public boolean isSettled;
    public long createdAt;
    public long updatedAt;

    // ─── Constructor ─────────────────────────────────────────

    public SplitExpense() {
        this.id = java.util.UUID.randomUUID().toString();
        this.splits = new ArrayList<>();
        this.currency = "₹";
        this.splitType = SPLIT_EQUAL;
        this.date = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // ─── Helpers ─────────────────────────────────────────────

    private static final NumberFormat currFmt = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private static final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public String getFormattedAmount() {
        return currFmt.format(totalAmount);
    }

    public String getFormattedDate() {
        return dateFmt.format(new Date(date));
    }

    public String getSplitSummary(int memberCount) {
        switch (splitType) {
            case SPLIT_EQUAL:
                return "Split equally among " + memberCount;
            case SPLIT_PERCENTAGE:
                return "Split by percentage";
            case SPLIT_CUSTOM:
                return "Custom amounts";
            case SPLIT_SHARES:
                return "Split by shares";
            default:
                return "Split among " + memberCount;
        }
    }

    public MemberSplit getSplitForMember(String memberId) {
        for (MemberSplit ms : splits) {
            if (ms.memberId.equals(memberId)) return ms;
        }
        return null;
    }

    public String getCategoryIcon() {
        return Expense.getCategoryIcon(categoryId != null ? categoryId : "Other");
    }

    // ─── JSON ────────────────────────────────────────────────

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("groupId", groupId != null ? groupId : "");
            obj.put("title", title != null ? title : "");
            obj.put("description", description != null ? description : "");
            obj.put("totalAmount", totalAmount);
            obj.put("currency", currency != null ? currency : "₹");
            obj.put("categoryId", categoryId != null ? categoryId : "Other");
            obj.put("walletId", walletId != null ? walletId : "");
            obj.put("paidByMemberId", paidByMemberId != null ? paidByMemberId : "");
            obj.put("date", date);
            obj.put("splitType", splitType);
            obj.put("receiptImagePath", receiptImagePath != null ? receiptImagePath : "");
            obj.put("notes", notes != null ? notes : "");
            obj.put("isSettled", isSettled);
            obj.put("createdAt", createdAt);
            obj.put("updatedAt", updatedAt);

            JSONArray splitsArr = new JSONArray();
            for (MemberSplit ms : splits) splitsArr.put(ms.toJson());
            obj.put("splits", splitsArr);

            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static SplitExpense fromJson(JSONObject obj) {
        try {
            SplitExpense se = new SplitExpense();
            se.id = obj.getString("id");
            se.groupId = obj.optString("groupId", "");
            se.title = obj.optString("title", "");
            se.description = obj.optString("description", "");
            se.totalAmount = obj.optDouble("totalAmount", 0);
            se.currency = obj.optString("currency", "₹");
            se.categoryId = obj.optString("categoryId", "Other");
            se.walletId = obj.optString("walletId", "");
            se.paidByMemberId = obj.optString("paidByMemberId", "");
            se.date = obj.optLong("date", 0);
            se.splitType = obj.optString("splitType", SPLIT_EQUAL);
            se.receiptImagePath = obj.optString("receiptImagePath", "");
            se.notes = obj.optString("notes", "");
            se.isSettled = obj.optBoolean("isSettled", false);
            se.createdAt = obj.optLong("createdAt", 0);
            se.updatedAt = obj.optLong("updatedAt", 0);

            se.splits = new ArrayList<>();
            JSONArray splitsArr = obj.optJSONArray("splits");
            if (splitsArr != null) {
                for (int i = 0; i < splitsArr.length(); i++) {
                    MemberSplit ms = MemberSplit.fromJson(splitsArr.getJSONObject(i));
                    if (ms != null) se.splits.add(ms);
                }
            }
            return se;
        } catch (Exception e) {
            return null;
        }
    }
}
