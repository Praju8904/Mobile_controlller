package com.prajwal.myfirstapp.expenses;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/**
 * Data model for category-based budget goals.
 * Tracks budget amount, period, alert thresholds,
 * and integrates with expense tracking for real-time monitoring.
 */
public class CategoryBudget {

    public String id;
    public String categoryId;          // Maps to Expense.CATEGORIES
    public double budgetAmount;
    public String currency;
    public String period;              // weekly, monthly, custom
    public long startDate;
    public long endDate;
    public int alertThresholdPercent;  // Alert when X% consumed (default 80)
    public boolean isActive;
    public boolean thresholdAlertFired;  // Track if threshold alert was sent this period
    public boolean exceededAlertFired;   // Track if exceeded alert was sent this period
    public long createdAt;
    public long updatedAt;

    // Period constants
    public static final String PERIOD_WEEKLY = "weekly";
    public static final String PERIOD_MONTHLY = "monthly";
    public static final String PERIOD_QUARTERLY = "quarterly";
    public static final String PERIOD_YEARLY = "yearly";
    public static final String PERIOD_CUSTOM = "custom";

    public CategoryBudget() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.currency = "₹";
        this.period = PERIOD_MONTHLY;
        this.alertThresholdPercent = 80;
        this.isActive = true;
        this.thresholdAlertFired = false;
        this.exceededAlertFired = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public CategoryBudget(String categoryId, double budgetAmount, String period) {
        this();
        this.categoryId = categoryId;
        this.budgetAmount = budgetAmount;
        this.period = period;
        calculatePeriodDates();
    }

    /**
     * Calculate start and end dates based on period type.
     */
    public void calculatePeriodDates() {
        java.util.Calendar cal = java.util.Calendar.getInstance();

        if (PERIOD_MONTHLY.equals(period)) {
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            startDate = cal.getTimeInMillis();

            cal.add(java.util.Calendar.MONTH, 1);
            endDate = cal.getTimeInMillis();
        } else if (PERIOD_WEEKLY.equals(period)) {
            cal.set(java.util.Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            startDate = cal.getTimeInMillis();

            cal.add(java.util.Calendar.WEEK_OF_YEAR, 1);
            endDate = cal.getTimeInMillis();
        } else if (PERIOD_QUARTERLY.equals(period)) {
            int month = cal.get(java.util.Calendar.MONTH);
            int quarterStart = (month / 3) * 3;
            cal.set(java.util.Calendar.MONTH, quarterStart);
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            startDate = cal.getTimeInMillis();

            cal.add(java.util.Calendar.MONTH, 3);
            endDate = cal.getTimeInMillis();
        } else if (PERIOD_YEARLY.equals(period)) {
            cal.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY);
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            startDate = cal.getTimeInMillis();

            cal.add(java.util.Calendar.YEAR, 1);
            endDate = cal.getTimeInMillis();
        }
        // For PERIOD_CUSTOM, startDate and endDate set manually
    }

    /**
     * Get the number of days remaining in this budget period.
     */
    public int getDaysRemaining() {
        long diff = endDate - System.currentTimeMillis();
        return Math.max(0, (int) (diff / (1000 * 60 * 60 * 24)));
    }

    /**
     * Calculate the percentage of budget consumed given a spent amount.
     */
    public float getPercentUsed(double spent) {
        if (budgetAmount <= 0) return 0f;
        return (float) (spent / budgetAmount * 100);
    }

    /**
     * Get the remaining budget amount.
     */
    public double getRemaining(double spent) {
        return budgetAmount - spent;
    }

    /**
     * Check if the budget period has expired and needs renewal.
     */
    public boolean isPeriodExpired() {
        return System.currentTimeMillis() >= endDate;
    }

    /**
     * Advance to the next budget period, preserving settings.
     */
    public void advanceToNextPeriod() {
        thresholdAlertFired = false;
        exceededAlertFired = false;
        updatedAt = System.currentTimeMillis();

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(endDate);

        if (PERIOD_MONTHLY.equals(period)) {
            startDate = endDate;
            cal.add(java.util.Calendar.MONTH, 1);
            endDate = cal.getTimeInMillis();
        } else if (PERIOD_WEEKLY.equals(period)) {
            startDate = endDate;
            cal.add(java.util.Calendar.WEEK_OF_YEAR, 1);
            endDate = cal.getTimeInMillis();
        } else if (PERIOD_QUARTERLY.equals(period)) {
            startDate = endDate;
            cal.add(java.util.Calendar.MONTH, 3);
            endDate = cal.getTimeInMillis();
        } else if (PERIOD_YEARLY.equals(period)) {
            startDate = endDate;
            cal.add(java.util.Calendar.YEAR, 1);
            endDate = cal.getTimeInMillis();
        }
        // Custom period stays as is
    }

    /**
     * Get the period label for display.
     */
    public String getPeriodLabel() {
        switch (period) {
            case PERIOD_WEEKLY: return "This Week";
            case PERIOD_MONTHLY: return "This Month";
            case PERIOD_QUARTERLY: return "This Quarter";
            case PERIOD_YEARLY: return "This Year";
            case PERIOD_CUSTOM: return "Custom Period";
            default: return "This Month";
        }
    }

    /**
     * Get the progress bar color based on percentage consumed.
     * Green (0-70%) → Amber (70-90%) → Red (90%+)
     */
    public static int getProgressColor(float percent) {
        if (percent >= 90f) return 0xFFEF4444;   // Red
        if (percent >= 70f) return 0xFFF59E0B;   // Amber
        return 0xFF22C55E;                         // Green
    }

    /**
     * Get the health label based on percentage.
     */
    public static String getHealthLabel(float percent) {
        if (percent > 100f) return "Over Budget";
        if (percent >= 90f) return "Critical";
        if (percent >= 70f) return "Nearing Limit";
        return "On Track";
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("categoryId", categoryId);
            json.put("budgetAmount", budgetAmount);
            json.put("currency", currency);
            json.put("period", period);
            json.put("startDate", startDate);
            json.put("endDate", endDate);
            json.put("alertThresholdPercent", alertThresholdPercent);
            json.put("isActive", isActive);
            json.put("thresholdAlertFired", thresholdAlertFired);
            json.put("exceededAlertFired", exceededAlertFired);
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static CategoryBudget fromJson(JSONObject json) {
        try {
            CategoryBudget cb = new CategoryBudget();
            cb.id = json.getString("id");
            cb.categoryId = json.getString("categoryId");
            cb.budgetAmount = json.getDouble("budgetAmount");
            cb.currency = json.optString("currency", "₹");
            cb.period = json.optString("period", PERIOD_MONTHLY);
            cb.startDate = json.getLong("startDate");
            cb.endDate = json.getLong("endDate");
            cb.alertThresholdPercent = json.optInt("alertThresholdPercent", 80);
            cb.isActive = json.optBoolean("isActive", true);
            cb.thresholdAlertFired = json.optBoolean("thresholdAlertFired", false);
            cb.exceededAlertFired = json.optBoolean("exceededAlertFired", false);
            cb.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            cb.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            return cb;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
