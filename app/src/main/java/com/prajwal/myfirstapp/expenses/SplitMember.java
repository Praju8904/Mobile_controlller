package com.prajwal.myfirstapp.expenses;

import org.json.JSONObject;

/**
 * A member of a split group. One member always represents the current app user (isCurrentUser=true).
 * Other members are contacts — no real user accounts needed.
 */
public class SplitMember {

    // ─── Avatar Color Palette ────────────────────────────────
    public static final int[] AVATAR_COLORS = {
        0xFF7C3AED, 0xFF3B82F6, 0xFFEF4444, 0xFF22C55E,
        0xFFF59E0B, 0xFFA855F7, 0xFF06B6D4, 0xFFEC4899,
        0xFF6366F1, 0xFF14B8A6, 0xFFFF6B6B, 0xFF8B5CF6,
        0xFFE11D48, 0xFF0EA5E9, 0xFF84CC16, 0xFFFB923C
    };

    // ─── Fields ──────────────────────────────────────────────
    public String id;
    public String name;
    public String phone;        // Optional
    public String email;        // Optional
    public int avatarColorHex;  // Auto-assigned from palette
    public boolean isCurrentUser;

    // ─── Constructors ────────────────────────────────────────

    public SplitMember() {
        this.id = java.util.UUID.randomUUID().toString();
        this.avatarColorHex = AVATAR_COLORS[0];
    }

    /** Create the "Me" member */
    public static SplitMember createCurrentUser() {
        SplitMember me = new SplitMember();
        me.name = "Me";
        me.isCurrentUser = true;
        me.avatarColorHex = AVATAR_COLORS[0];
        return me;
    }

    /** Create a regular member with auto-assigned color */
    public static SplitMember create(String name, String phone, int memberIndex) {
        SplitMember m = new SplitMember();
        m.name = name;
        m.phone = phone;
        m.isCurrentUser = false;
        m.avatarColorHex = AVATAR_COLORS[memberIndex % AVATAR_COLORS.length];
        return m;
    }

    // ─── Helpers ─────────────────────────────────────────────

    public String getInitials() {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    // ─── JSON ────────────────────────────────────────────────

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("name", name);
            obj.put("phone", phone != null ? phone : "");
            obj.put("email", email != null ? email : "");
            obj.put("avatarColorHex", avatarColorHex);
            obj.put("isCurrentUser", isCurrentUser);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static SplitMember fromJson(JSONObject obj) {
        try {
            SplitMember m = new SplitMember();
            m.id = obj.getString("id");
            m.name = obj.optString("name", "");
            m.phone = obj.optString("phone", "");
            m.email = obj.optString("email", "");
            m.avatarColorHex = obj.optInt("avatarColorHex", AVATAR_COLORS[0]);
            m.isCurrentUser = obj.optBoolean("isCurrentUser", false);
            return m;
        } catch (Exception e) {
            return null;
        }
    }
}
