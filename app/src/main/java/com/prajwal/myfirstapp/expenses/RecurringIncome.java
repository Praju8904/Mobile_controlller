package com.prajwal.myfirstapp.expenses;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/**
 * Data model for recurring income (mirrors RecurringExpense for consistency).
 */
public class RecurringIncome {

    public String id;
    public String title;
    public double amount;
    public String currency;
    public String walletId;
    public String categoryId;
    public String source;
    public String recurrenceType;
    public int recurrenceInterval;
    public long startDate;
    public long nextDueDate;
    public long endDate;            // 0 = indefinite
    public int reminderDaysBefore;
    public boolean isActive;
    public String notes;
    public long createdAt;
    public long updatedAt;

    // ─── Recurrence Type Constants ────────────────────────────
    public static final String RECURRENCE_DAILY = "daily";
    public static final String RECURRENCE_WEEKLY = "weekly";
    public static final String RECURRENCE_MONTHLY = "monthly";
    public static final String RECURRENCE_YEARLY = "yearly";

    public RecurringIncome() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.currency = "₹";
        this.walletId = Wallet.DEFAULT_WALLET_ID;
        this.source = Income.SOURCE_SALARY;
        this.recurrenceType = RECURRENCE_MONTHLY;
        this.recurrenceInterval = 1;
        this.isActive = true;
        this.reminderDaysBefore = 3;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public RecurringIncome(String title, double amount, String categoryId,
                           String source, String recurrenceType) {
        this();
        this.title = title;
        this.amount = amount;
        this.categoryId = categoryId;
        this.source = source;
        this.recurrenceType = recurrenceType;
        this.startDate = System.currentTimeMillis();
        this.nextDueDate = calculateFirstDueDate();
    }

    // ─── Due Date Calculation ─────────────────────────────────

    public long calculateNextDueDate() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(nextDueDate);
        advanceCalendar(cal);
        return cal.getTimeInMillis();
    }

    private long calculateFirstDueDate() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(startDate > 0 ? startDate : System.currentTimeMillis());
        advanceCalendar(cal);
        return cal.getTimeInMillis();
    }

    private void advanceCalendar(java.util.Calendar cal) {
        switch (recurrenceType) {
            case RECURRENCE_DAILY:
                cal.add(java.util.Calendar.DAY_OF_YEAR, Math.max(1, recurrenceInterval));
                break;
            case RECURRENCE_WEEKLY:
                cal.add(java.util.Calendar.WEEK_OF_YEAR, 1);
                break;
            case RECURRENCE_MONTHLY:
                cal.add(java.util.Calendar.MONTH, 1);
                break;
            case RECURRENCE_YEARLY:
                cal.add(java.util.Calendar.YEAR, 1);
                break;
            default:
                cal.add(java.util.Calendar.MONTH, 1);
                break;
        }
    }

    public int getDaysUntilDue() {
        long diff = nextDueDate - System.currentTimeMillis();
        return (int) (diff / (1000L * 60 * 60 * 24));
    }

    public double getMonthlyEquivalent() {
        switch (recurrenceType) {
            case RECURRENCE_DAILY: return amount * 30;
            case RECURRENCE_WEEKLY: return amount * 4.33;
            case RECURRENCE_MONTHLY: return amount;
            case RECURRENCE_YEARLY: return amount / 12.0;
            default: return amount;
        }
    }

    public String getRecurrenceLabel() {
        switch (recurrenceType) {
            case RECURRENCE_DAILY: return "Daily";
            case RECURRENCE_WEEKLY: return "Weekly";
            case RECURRENCE_MONTHLY: return "Monthly";
            case RECURRENCE_YEARLY: return "Yearly";
            default: return "Monthly";
        }
    }

    // ─── JSON Serialization ───────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title != null ? title : "");
            json.put("amount", amount);
            json.put("currency", currency != null ? currency : "₹");
            json.put("walletId", walletId != null ? walletId : Wallet.DEFAULT_WALLET_ID);
            json.put("categoryId", categoryId != null ? categoryId : "");
            json.put("source", source != null ? source : Income.SOURCE_OTHER);
            json.put("recurrenceType", recurrenceType);
            json.put("recurrenceInterval", recurrenceInterval);
            json.put("startDate", startDate);
            json.put("nextDueDate", nextDueDate);
            json.put("endDate", endDate);
            json.put("reminderDaysBefore", reminderDaysBefore);
            json.put("isActive", isActive);
            json.put("notes", notes != null ? notes : "");
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static RecurringIncome fromJson(JSONObject json) {
        try {
            RecurringIncome ri = new RecurringIncome();
            ri.id = json.getString("id");
            ri.title = json.optString("title", "");
            ri.amount = json.getDouble("amount");
            ri.currency = json.optString("currency", "₹");
            ri.walletId = json.optString("walletId", Wallet.DEFAULT_WALLET_ID);
            ri.categoryId = json.optString("categoryId", "");
            ri.source = json.optString("source", Income.SOURCE_OTHER);
            ri.recurrenceType = json.optString("recurrenceType", RECURRENCE_MONTHLY);
            ri.recurrenceInterval = json.optInt("recurrenceInterval", 1);
            ri.startDate = json.optLong("startDate", System.currentTimeMillis());
            ri.nextDueDate = json.optLong("nextDueDate", System.currentTimeMillis());
            ri.endDate = json.optLong("endDate", 0);
            ri.reminderDaysBefore = json.optInt("reminderDaysBefore", 3);
            ri.isActive = json.optBoolean("isActive", true);
            ri.notes = json.optString("notes", "");
            ri.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            ri.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            return ri;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
