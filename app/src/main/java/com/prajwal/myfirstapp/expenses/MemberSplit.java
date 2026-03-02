package com.prajwal.myfirstapp.expenses;

import org.json.JSONObject;

/**
 * Represents one member's portion in a split expense.
 */
public class MemberSplit {

    public String memberId;
    public double amountOwed;
    public double percentage;   // Used for percentage split type
    public int shares;          // Used for shares split type
    public boolean isPaid;
    public long paidAt;

    public MemberSplit() {}

    public MemberSplit(String memberId, double amountOwed) {
        this.memberId = memberId;
        this.amountOwed = amountOwed;
    }

    // ─── JSON ────────────────────────────────────────────────

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("memberId", memberId);
            obj.put("amountOwed", amountOwed);
            obj.put("percentage", percentage);
            obj.put("shares", shares);
            obj.put("isPaid", isPaid);
            obj.put("paidAt", paidAt);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static MemberSplit fromJson(JSONObject obj) {
        try {
            MemberSplit ms = new MemberSplit();
            ms.memberId = obj.getString("memberId");
            ms.amountOwed = obj.optDouble("amountOwed", 0);
            ms.percentage = obj.optDouble("percentage", 0);
            ms.shares = obj.optInt("shares", 0);
            ms.isPaid = obj.optBoolean("isPaid", false);
            ms.paidAt = obj.optLong("paidAt", 0);
            return ms;
        } catch (Exception e) {
            return null;
        }
    }
}
