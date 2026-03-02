package com.prajwal.myfirstapp.expenses;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/**
 * Data model for recurring expenses and subscriptions.
 * Stores all subscription details including recurrence rules,
 * reminder settings, and visual customization.
 */
public class RecurringExpense {

    public String id;
    public String name;
    public String description;
    public double amount;
    public String currency;
    public String categoryId;          // Maps to Expense.CATEGORIES
    public String walletOrPaymentMethod;
    public String walletId;              // Links to Wallet.id for balance tracking
    public String recurrenceType;      // daily, weekly, monthly, quarterly, yearly, custom
    public int recurrenceInterval;     // every X units for custom
    public long startDate;
    public long nextDueDate;
    public long endDate;               // 0 = indefinite
    public int reminderDaysBefore;     // 0 = no reminder
    public boolean isActive;
    public int color;                  // ARGB color for visual differentiation
    public String logoOrIcon;          // Emoji or icon identifier
    public String notes;
    public long createdAt;
    public long updatedAt;

    // Recurrence type constants
    public static final String RECURRENCE_DAILY = "daily";
    public static final String RECURRENCE_WEEKLY = "weekly";
    public static final String RECURRENCE_MONTHLY = "monthly";
    public static final String RECURRENCE_QUARTERLY = "quarterly";
    public static final String RECURRENCE_YEARLY = "yearly";
    public static final String RECURRENCE_CUSTOM = "custom";

    // Common subscription presets: name, icon, typical category, typical amount
    public static final String[][] COMMON_SERVICES = {
        {"Netflix", "🎬", "Entertainment", "649"},
        {"Spotify", "🎵", "Entertainment", "119"},
        {"Amazon Prime", "📦", "Shopping", "1499"},
        {"YouTube Premium", "▶️", "Entertainment", "129"},
        {"Disney+", "🏰", "Entertainment", "299"},
        {"iCloud", "☁️", "Bills", "75"},
        {"Google One", "🔵", "Bills", "130"},
        {"Gym Membership", "💪", "Health", "1500"},
        {"ChatGPT Plus", "🤖", "Education", "1700"},
        {"Adobe Creative Cloud", "🎨", "Bills", "1675"},
        {"Microsoft 365", "📊", "Bills", "420"},
        {"LinkedIn Premium", "💼", "Education", "799"},
        {"Dropbox", "📁", "Bills", "792"},
        {"Notion", "📝", "Bills", "640"},
        {"Medium", "📰", "Education", "300"},
        {"Swiggy One", "🍔", "Food", "299"},
        {"Zomato Gold", "🍕", "Food", "500"},
        {"Hotstar", "⭐", "Entertainment", "299"},
        {"Apple Music", "🎶", "Entertainment", "99"},
        {"Audible", "🎧", "Education", "199"}
    };

    public RecurringExpense() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.currency = "₹";
        this.recurrenceType = RECURRENCE_MONTHLY;
        this.recurrenceInterval = 1;
        this.isActive = true;
        this.reminderDaysBefore = 3;
        this.color = 0xFF7C3AED;
        this.logoOrIcon = "📦";
        this.walletId = Wallet.DEFAULT_WALLET_ID;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public RecurringExpense(String name, double amount, String categoryId, String recurrenceType) {
        this();
        this.name = name;
        this.amount = amount;
        this.categoryId = categoryId;
        this.recurrenceType = recurrenceType;
        this.startDate = System.currentTimeMillis();
        this.nextDueDate = calculateFirstDueDate();
    }

    /**
     * Calculate the monthly equivalent cost for comparison.
     */
    public double getMonthlyEquivalent() {
        switch (recurrenceType) {
            case RECURRENCE_DAILY: return amount * 30;
            case RECURRENCE_WEEKLY: return amount * 4.33;
            case RECURRENCE_MONTHLY: return amount;
            case RECURRENCE_QUARTERLY: return amount / 3.0;
            case RECURRENCE_YEARLY: return amount / 12.0;
            case RECURRENCE_CUSTOM:
                // Approximate based on interval in days
                return amount * (30.0 / Math.max(1, recurrenceInterval));
            default: return amount;
        }
    }

    /**
     * Calculate the yearly equivalent cost.
     */
    public double getYearlyEquivalent() {
        return getMonthlyEquivalent() * 12;
    }

    /**
     * Calculate how many days until next due date.
     */
    public int getDaysUntilDue() {
        long diff = nextDueDate - System.currentTimeMillis();
        return (int) (diff / (1000 * 60 * 60 * 24));
    }

    /**
     * Advance nextDueDate to the next occurrence based on recurrence rules.
     */
    public long calculateNextDueDate() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(nextDueDate);

        switch (recurrenceType) {
            case RECURRENCE_DAILY:
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
                break;
            case RECURRENCE_WEEKLY:
                cal.add(java.util.Calendar.WEEK_OF_YEAR, 1);
                break;
            case RECURRENCE_MONTHLY:
                cal.add(java.util.Calendar.MONTH, 1);
                break;
            case RECURRENCE_QUARTERLY:
                cal.add(java.util.Calendar.MONTH, 3);
                break;
            case RECURRENCE_YEARLY:
                cal.add(java.util.Calendar.YEAR, 1);
                break;
            case RECURRENCE_CUSTOM:
                cal.add(java.util.Calendar.DAY_OF_YEAR, Math.max(1, recurrenceInterval));
                break;
        }
        return cal.getTimeInMillis();
    }

    private long calculateFirstDueDate() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(startDate > 0 ? startDate : System.currentTimeMillis());

        switch (recurrenceType) {
            case RECURRENCE_DAILY:
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
                break;
            case RECURRENCE_WEEKLY:
                cal.add(java.util.Calendar.WEEK_OF_YEAR, 1);
                break;
            case RECURRENCE_MONTHLY:
                cal.add(java.util.Calendar.MONTH, 1);
                break;
            case RECURRENCE_QUARTERLY:
                cal.add(java.util.Calendar.MONTH, 3);
                break;
            case RECURRENCE_YEARLY:
                cal.add(java.util.Calendar.YEAR, 1);
                break;
            case RECURRENCE_CUSTOM:
                cal.add(java.util.Calendar.DAY_OF_YEAR, Math.max(1, recurrenceInterval));
                break;
        }
        return cal.getTimeInMillis();
    }

    /**
     * Get the recurrence label for display.
     */
    public String getRecurrenceLabel() {
        switch (recurrenceType) {
            case RECURRENCE_DAILY: return "Daily";
            case RECURRENCE_WEEKLY: return "Weekly";
            case RECURRENCE_MONTHLY: return "Monthly";
            case RECURRENCE_QUARTERLY: return "Quarterly";
            case RECURRENCE_YEARLY: return "Yearly";
            case RECURRENCE_CUSTOM: return "Every " + recurrenceInterval + " days";
            default: return "Monthly";
        }
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("description", description != null ? description : "");
            json.put("amount", amount);
            json.put("currency", currency);
            json.put("categoryId", categoryId);
            json.put("walletOrPaymentMethod", walletOrPaymentMethod != null ? walletOrPaymentMethod : "");
            json.put("walletId", walletId != null ? walletId : Wallet.DEFAULT_WALLET_ID);
            json.put("recurrenceType", recurrenceType);
            json.put("recurrenceInterval", recurrenceInterval);
            json.put("startDate", startDate);
            json.put("nextDueDate", nextDueDate);
            json.put("endDate", endDate);
            json.put("reminderDaysBefore", reminderDaysBefore);
            json.put("isActive", isActive);
            json.put("color", color);
            json.put("logoOrIcon", logoOrIcon);
            json.put("notes", notes != null ? notes : "");
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static RecurringExpense fromJson(JSONObject json) {
        try {
            RecurringExpense re = new RecurringExpense();
            re.id = json.getString("id");
            re.name = json.getString("name");
            re.description = json.optString("description", "");
            re.amount = json.getDouble("amount");
            re.currency = json.optString("currency", "₹");
            re.categoryId = json.getString("categoryId");
            re.walletOrPaymentMethod = json.optString("walletOrPaymentMethod", "");
            re.walletId = json.optString("walletId", Wallet.DEFAULT_WALLET_ID);
            re.recurrenceType = json.optString("recurrenceType", RECURRENCE_MONTHLY);
            re.recurrenceInterval = json.optInt("recurrenceInterval", 1);
            re.startDate = json.getLong("startDate");
            re.nextDueDate = json.getLong("nextDueDate");
            re.endDate = json.optLong("endDate", 0);
            re.reminderDaysBefore = json.optInt("reminderDaysBefore", 3);
            re.isActive = json.optBoolean("isActive", true);
            re.color = json.optInt("color", 0xFF7C3AED);
            re.logoOrIcon = json.optString("logoOrIcon", "📦");
            re.notes = json.optString("notes", "");
            re.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            re.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            return re;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
