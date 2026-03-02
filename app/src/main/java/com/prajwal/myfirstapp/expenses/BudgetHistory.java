package com.prajwal.myfirstapp.expenses;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Record of a past budget period for history tracking.
 * Stores what was budgeted vs what was actually spent.
 */
public class BudgetHistory {

    public String id;
    public String categoryId;
    public double budgetAmount;
    public double actualSpent;
    public String period;
    public long startDate;
    public long endDate;
    public String currency;
    public long createdAt;

    public BudgetHistory() {
        this.id = java.util.UUID.randomUUID().toString().substring(0, 12);
        this.currency = "₹";
        this.createdAt = System.currentTimeMillis();
    }

    public BudgetHistory(CategoryBudget budget, double spent) {
        this();
        this.categoryId = budget.categoryId;
        this.budgetAmount = budget.budgetAmount;
        this.actualSpent = spent;
        this.period = budget.period;
        this.startDate = budget.startDate;
        this.endDate = budget.endDate;
        this.currency = budget.currency;
    }

    public float getPercentUsed() {
        if (budgetAmount <= 0) return 0f;
        return (float) (actualSpent / budgetAmount * 100);
    }

    public boolean wasOverBudget() {
        return actualSpent > budgetAmount;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("categoryId", categoryId);
            json.put("budgetAmount", budgetAmount);
            json.put("actualSpent", actualSpent);
            json.put("period", period);
            json.put("startDate", startDate);
            json.put("endDate", endDate);
            json.put("currency", currency);
            json.put("createdAt", createdAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static BudgetHistory fromJson(JSONObject json) {
        try {
            BudgetHistory bh = new BudgetHistory();
            bh.id = json.getString("id");
            bh.categoryId = json.getString("categoryId");
            bh.budgetAmount = json.getDouble("budgetAmount");
            bh.actualSpent = json.getDouble("actualSpent");
            bh.period = json.optString("period", "monthly");
            bh.startDate = json.getLong("startDate");
            bh.endDate = json.getLong("endDate");
            bh.currency = json.optString("currency", "₹");
            bh.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            return bh;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
