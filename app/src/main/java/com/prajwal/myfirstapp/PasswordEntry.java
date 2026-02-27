package com.prajwal.myfirstapp;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Data model for a password vault entry (password or secure note).
 */
public class PasswordEntry {

    public String id;
    public String type;          // "password" or "secure_note"
    public String siteName;
    public String siteUrl;
    public String username;
    public String password;
    public String category;
    public String notes;
    public boolean isFavourite;
    public long createdAt;
    public long modifiedAt;
    public long lastUsedAt;
    public boolean isDeleted;
    public long deletedAt;

    // ─── Categories ──────────────────────────────────────────────

    public static final String[] CATEGORIES = {
        "Social Media", "Banking", "Work", "Shopping",
        "Email", "Gaming", "Streaming", "Others"
    };

    public static final String[] CATEGORY_ICONS = {
        "👥", "🏦", "💼", "🛒",
        "📧", "🎮", "📺", "📦"
    };

    public static final int[] CATEGORY_COLORS = {
        0xFF3B82F6, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899,
        0xFF6366F1, 0xFF8B5CF6, 0xFFEF4444, 0xFF64748B
    };

    public static String getCategoryIcon(String category) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(category)) return CATEGORY_ICONS[i];
        }
        return "📦";
    }

    public static int getCategoryColor(String category) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(category)) return CATEGORY_COLORS[i];
        }
        return 0xFF64748B;
    }

    // ─── Constructors ────────────────────────────────────────────

    public PasswordEntry() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.type = "password";
        this.category = "Others";
        this.isFavourite = false;
        this.isDeleted = false;
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = this.createdAt;
        this.lastUsedAt = this.createdAt;
    }

    public static PasswordEntry createPassword(String siteName, String siteUrl, String username,
                                                 String password, String category, String notes) {
        PasswordEntry entry = new PasswordEntry();
        entry.type = "password";
        entry.siteName = siteName;
        entry.siteUrl = siteUrl;
        entry.username = username;
        entry.password = password;
        entry.category = category != null ? category : "Others";
        entry.notes = notes;
        return entry;
    }

    public static PasswordEntry createSecureNote(String title, String content, String category) {
        PasswordEntry entry = new PasswordEntry();
        entry.type = "secure_note";
        entry.siteName = title;
        entry.notes = content;
        entry.category = category != null ? category : "Others";
        return entry;
    }

    // ─── Strength ────────────────────────────────────────────────

    public int getPasswordStrength() {
        if (!"password".equals(type) || password == null) return 0;
        return VaultCryptoManager.calculateStrength(password);
    }

    public String getStrengthLabel() {
        return VaultCryptoManager.getStrengthLabel(getPasswordStrength());
    }

    public int getStrengthColor() {
        return VaultCryptoManager.getStrengthColor(getPasswordStrength());
    }

    public boolean isWeak() {
        return "password".equals(type) && getPasswordStrength() < 50;
    }

    public boolean isOld() {
        long sixMonths = 180L * 24 * 60 * 60 * 1000;
        return (System.currentTimeMillis() - modifiedAt) > sixMonths;
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("type", type);
            obj.put("siteName", siteName != null ? siteName : "");
            obj.put("siteUrl", siteUrl != null ? siteUrl : "");
            obj.put("username", username != null ? username : "");
            obj.put("password", password != null ? password : "");
            obj.put("category", category != null ? category : "Others");
            obj.put("notes", notes != null ? notes : "");
            obj.put("isFavourite", isFavourite);
            obj.put("createdAt", createdAt);
            obj.put("modifiedAt", modifiedAt);
            obj.put("lastUsedAt", lastUsedAt);
            obj.put("isDeleted", isDeleted);
            obj.put("deletedAt", deletedAt);
            return obj;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public static PasswordEntry fromJson(JSONObject obj) {
        if (obj == null) return null;
        try {
            PasswordEntry entry = new PasswordEntry();
            entry.id = obj.optString("id", entry.id);
            entry.type = obj.optString("type", "password");
            entry.siteName = obj.optString("siteName", "");
            entry.siteUrl = obj.optString("siteUrl", "");
            entry.username = obj.optString("username", "");
            entry.password = obj.optString("password", "");
            entry.category = obj.optString("category", "Others");
            entry.notes = obj.optString("notes", "");
            entry.isFavourite = obj.optBoolean("isFavourite", false);
            entry.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
            entry.modifiedAt = obj.optLong("modifiedAt", entry.createdAt);
            entry.lastUsedAt = obj.optLong("lastUsedAt", entry.createdAt);
            entry.isDeleted = obj.optBoolean("isDeleted", false);
            entry.deletedAt = obj.optLong("deletedAt", 0);
            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    // ─── CSV Support ─────────────────────────────────────────────

    public String toCsvLine() {
        return escapeCsv(siteName) + "," + escapeCsv(siteUrl) + ","
             + escapeCsv(username) + "," + escapeCsv(password) + ","
             + escapeCsv(notes);
    }

    public static String csvHeader() {
        return "name,url,username,password,notes";
    }

    public static PasswordEntry fromCsvLine(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        String[] parts = parseCsvLine(line);
        if (parts.length < 4) return null;
        PasswordEntry entry = new PasswordEntry();
        entry.type = "password";
        entry.siteName = parts[0];
        entry.siteUrl = parts.length > 1 ? parts[1] : "";
        entry.username = parts.length > 2 ? parts[2] : "";
        entry.password = parts.length > 3 ? parts[3] : "";
        entry.notes = parts.length > 4 ? parts[4] : "";
        return entry;
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String[] parseCsvLine(String line) {
        java.util.List<String> fields = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') { inQuotes = true; }
                else if (c == ',') { fields.add(current.toString()); current.setLength(0); }
                else { current.append(c); }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
