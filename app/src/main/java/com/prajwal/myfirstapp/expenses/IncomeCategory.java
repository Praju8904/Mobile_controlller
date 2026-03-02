package com.prajwal.myfirstapp.expenses;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/**
 * Data model for an income category (distinct from expense categories).
 */
public class IncomeCategory {

    public String id;
    public String name;
    public int colorHex;
    public String iconIdentifier;   // Emoji icon
    public boolean isDefault;
    public long createdAt;

    // ─── Default Category IDs ─────────────────────────────────
    public static final String ID_SALARY = "income_cat_salary";
    public static final String ID_FREELANCE = "income_cat_freelance";
    public static final String ID_GIFT = "income_cat_gift";
    public static final String ID_POCKET_MONEY = "income_cat_pocket";
    public static final String ID_REFUND = "income_cat_refund";
    public static final String ID_INVESTMENT = "income_cat_investment";
    public static final String ID_RENTAL = "income_cat_rental";
    public static final String ID_BONUS = "income_cat_bonus";
    public static final String ID_SCHOLARSHIP = "income_cat_scholarship";
    public static final String ID_OTHERS = "income_cat_others";

    public IncomeCategory() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.colorHex = 0xFF22C55E;
        this.iconIdentifier = "💰";
        this.isDefault = false;
        this.createdAt = System.currentTimeMillis();
    }

    public IncomeCategory(String id, String name, int colorHex, String iconIdentifier, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.colorHex = colorHex;
        this.iconIdentifier = iconIdentifier;
        this.isDefault = isDefault;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Build the 10 default income categories.
     */
    public static IncomeCategory[] getDefaults() {
        return new IncomeCategory[]{
            new IncomeCategory(ID_SALARY,      "Salary",             0xFF22C55E, "💼", true),
            new IncomeCategory(ID_FREELANCE,   "Freelance",          0xFF3B82F6, "💻", true),
            new IncomeCategory(ID_GIFT,        "Gift",               0xFFEC4899, "🎁", true),
            new IncomeCategory(ID_POCKET_MONEY,"Pocket Money",       0xFFF97316, "✋", true),
            new IncomeCategory(ID_REFUND,      "Refund",             0xFF14B8A6, "↩️", true),
            new IncomeCategory(ID_INVESTMENT,  "Investment Returns", 0xFFA855F7, "📈", true),
            new IncomeCategory(ID_RENTAL,      "Rental Income",      0xFFF59E0B, "🏠", true),
            new IncomeCategory(ID_BONUS,       "Bonus",              0xFFEAB308, "⭐", true),
            new IncomeCategory(ID_SCHOLARSHIP, "Scholarship",        0xFF6366F1, "🎓", true),
            new IncomeCategory(ID_OTHERS,      "Others",             0xFF6B7280, "➕", true),
        };
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name != null ? name : "");
            json.put("colorHex", colorHex);
            json.put("iconIdentifier", iconIdentifier != null ? iconIdentifier : "💰");
            json.put("isDefault", isDefault);
            json.put("createdAt", createdAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static IncomeCategory fromJson(JSONObject json) {
        try {
            IncomeCategory cat = new IncomeCategory();
            cat.id = json.getString("id");
            cat.name = json.optString("name", "");
            cat.colorHex = json.optInt("colorHex", 0xFF22C55E);
            cat.iconIdentifier = json.optString("iconIdentifier", "💰");
            cat.isDefault = json.optBoolean("isDefault", false);
            cat.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            return cat;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
