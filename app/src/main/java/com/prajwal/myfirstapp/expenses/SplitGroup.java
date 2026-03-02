package com.prajwal.myfirstapp.expenses;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * A group of people who share expenses together (e.g., a trip, flat, dinner).
 */
public class SplitGroup {

    public String id;
    public String name;
    public String description;
    public String createdByUserId;      // Always the current user's member ID
    public ArrayList<SplitMember> members;
    public double totalExpenses;
    public String currency;
    public boolean isSettled;
    public boolean isArchived;
    public long createdAt;
    public long updatedAt;

    // ─── Constructor ─────────────────────────────────────────

    public SplitGroup() {
        this.id = java.util.UUID.randomUUID().toString();
        this.members = new ArrayList<>();
        this.currency = "₹";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // ─── Helpers ─────────────────────────────────────────────

    public SplitMember getCurrentUser() {
        for (SplitMember m : members) {
            if (m.isCurrentUser) return m;
        }
        return null;
    }

    public SplitMember getMemberById(String memberId) {
        for (SplitMember m : members) {
            if (m.id.equals(memberId)) return m;
        }
        return null;
    }

    public String getMemberName(String memberId) {
        SplitMember m = getMemberById(memberId);
        return m != null ? m.name : "Unknown";
    }

    public int getMemberCount() {
        return members.size();
    }

    // ─── JSON ────────────────────────────────────────────────

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("name", name != null ? name : "");
            obj.put("description", description != null ? description : "");
            obj.put("createdByUserId", createdByUserId != null ? createdByUserId : "");
            obj.put("totalExpenses", totalExpenses);
            obj.put("currency", currency != null ? currency : "₹");
            obj.put("isSettled", isSettled);
            obj.put("isArchived", isArchived);
            obj.put("createdAt", createdAt);
            obj.put("updatedAt", updatedAt);

            JSONArray membersArr = new JSONArray();
            for (SplitMember m : members) membersArr.put(m.toJson());
            obj.put("members", membersArr);

            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static SplitGroup fromJson(JSONObject obj) {
        try {
            SplitGroup g = new SplitGroup();
            g.id = obj.getString("id");
            g.name = obj.optString("name", "");
            g.description = obj.optString("description", "");
            g.createdByUserId = obj.optString("createdByUserId", "");
            g.totalExpenses = obj.optDouble("totalExpenses", 0);
            g.currency = obj.optString("currency", "₹");
            g.isSettled = obj.optBoolean("isSettled", false);
            g.isArchived = obj.optBoolean("isArchived", false);
            g.createdAt = obj.optLong("createdAt", 0);
            g.updatedAt = obj.optLong("updatedAt", 0);

            g.members = new ArrayList<>();
            JSONArray membersArr = obj.optJSONArray("members");
            if (membersArr != null) {
                for (int i = 0; i < membersArr.length(); i++) {
                    SplitMember m = SplitMember.fromJson(membersArr.getJSONObject(i));
                    if (m != null) g.members.add(m);
                }
            }
            return g;
        } catch (Exception e) {
            return null;
        }
    }
}
