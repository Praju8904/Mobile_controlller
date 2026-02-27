package com.prajwal.myfirstapp;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Wallet model — represents a financial account (Cash, UPI, Credit Card, Bank Account, etc.).
 * Each expense, recurring expense, and transfer is associated with a wallet.
 */
public class Wallet {

    // ─── Wallet Types ────────────────────────────────────────
    public static final String TYPE_CASH = "Cash";
    public static final String TYPE_UPI = "UPI";
    public static final String TYPE_CREDIT_CARD = "Credit Card";
    public static final String TYPE_DEBIT_CARD = "Debit Card";
    public static final String TYPE_BANK_ACCOUNT = "Bank Account";
    public static final String TYPE_SAVINGS = "Savings";
    public static final String TYPE_INVESTMENT = "Investment";
    public static final String TYPE_OTHER = "Other";

    public static final String[] WALLET_TYPES = {
        TYPE_CASH, TYPE_UPI, TYPE_CREDIT_CARD, TYPE_DEBIT_CARD,
        TYPE_BANK_ACCOUNT, TYPE_SAVINGS, TYPE_INVESTMENT, TYPE_OTHER
    };

    public static final String[] WALLET_TYPE_ICONS = {
        "💵", "📱", "💳", "💳", "🏦", "🐷", "📈", "📦"
    };

    // ─── Common Banks & Services ─────────────────────────────
    public static final String[] COMMON_BANKS = {
        "SBI", "HDFC", "ICICI", "Axis", "Kotak", "PNB",
        "Bank of Baroda", "Union Bank", "Canara Bank", "IndusInd",
        "Yes Bank", "IDFC First", "Federal Bank", "RBL Bank"
    };

    public static final String[] COMMON_UPI_SERVICES = {
        "GPay", "PhonePe", "Paytm", "Amazon Pay", "CRED",
        "BHIM", "WhatsApp Pay", "Mobikwik", "Freecharge"
    };

    // ─── Preset Card Colors ──────────────────────────────────
    public static final int[] WALLET_COLORS = {
        0xFF7C3AED, 0xFF3B82F6, 0xFFEF4444, 0xFF22C55E,
        0xFFF59E0B, 0xFFA855F7, 0xFF06B6D4, 0xFFEC4899,
        0xFF6366F1, 0xFFFF6B6B, 0xFF14B8A6, 0xFF8B5CF6
    };

    // ─── Default Wallet ID (used for migration) ──────────────
    public static final String DEFAULT_WALLET_ID = "default_wallet";

    // ─── Fields ──────────────────────────────────────────────
    public String id;
    public String name;
    public String type;                     // One of WALLET_TYPES
    public String bankOrServiceName;        // e.g. "HDFC", "GPay"
    public String accountNumberLastFour;    // Optional, display only
    public double currentBalance;
    public String currency;
    public int colorHex;
    public String iconIdentifier;           // Emoji icon
    public String icon;                     // Alias for iconIdentifier (backward compatibility)
    public boolean isDefault;
    public boolean includeInTotalBalance;
    public double creditLimit;              // Only for Credit Card
    public int billingCycleDate;            // Only for Credit Card (day of month, 1-31)
    public String notes;
    public boolean isArchived;
    public int displayOrder;                // For drag-to-reorder
    public long createdAt;
    public long updatedAt;

    // ─── Constructor ─────────────────────────────────────────

    public Wallet() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.name = "";
        this.type = TYPE_CASH;
        this.bankOrServiceName = "";
        this.accountNumberLastFour = "";
        this.currentBalance = 0;
        this.currency = "₹";
        this.colorHex = WALLET_COLORS[0];
        this.iconIdentifier = "💵";
        this.icon = this.iconIdentifier;
        this.isDefault = false;
        this.includeInTotalBalance = true;
        this.creditLimit = 0;
        this.billingCycleDate = 0;
        this.notes = "";
        this.isArchived = false;
        this.displayOrder = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Create the default wallet used for migration.
     */
    public static Wallet createDefaultWallet() {
        Wallet w = new Wallet();
        w.id = DEFAULT_WALLET_ID;
        w.name = "Default Wallet";
        w.type = TYPE_CASH;
        w.iconIdentifier = "💵";
        w.colorHex = 0xFF7C3AED;
        w.isDefault = true;
        w.includeInTotalBalance = true;
        w.displayOrder = 0;
        return w;
    }

    // ─── Credit Card Helpers ─────────────────────────────────

    public boolean isCreditCard() {
        return TYPE_CREDIT_CARD.equals(type);
    }

    /**
     * For credit cards, available credit = limit - current balance.
     * Current balance represents amount owed (positive = debt).
     */
    public double getAvailableCredit() {
        if (!isCreditCard() || creditLimit <= 0) return 0;
        return creditLimit - currentBalance;
    }

    /**
     * Credit usage percentage (0-100+).
     */
    public double getCreditUsagePercent() {
        if (!isCreditCard() || creditLimit <= 0) return 0;
        return (currentBalance / creditLimit) * 100;
    }

    /**
     * Days until next billing cycle date from today.
     */
    public int getDaysUntilBilling() {
        if (billingCycleDate <= 0) return -1;
        java.util.Calendar today = java.util.Calendar.getInstance();
        int currentDay = today.get(java.util.Calendar.DAY_OF_MONTH);
        int currentMonth = today.get(java.util.Calendar.MONTH);
        int currentYear = today.get(java.util.Calendar.YEAR);

        java.util.Calendar billing = java.util.Calendar.getInstance();
        billing.set(currentYear, currentMonth, Math.min(billingCycleDate,
            billing.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)));

        if (billing.getTimeInMillis() <= today.getTimeInMillis()) {
            billing.add(java.util.Calendar.MONTH, 1);
            billing.set(java.util.Calendar.DAY_OF_MONTH,
                Math.min(billingCycleDate, billing.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)));
        }

        long diff = billing.getTimeInMillis() - today.getTimeInMillis();
        return (int) (diff / (24L * 60 * 60 * 1000));
    }

    // ─── Type Helpers ────────────────────────────────────────

    public static String getTypeIcon(String type) {
        for (int i = 0; i < WALLET_TYPES.length; i++) {
            if (WALLET_TYPES[i].equals(type)) return WALLET_TYPE_ICONS[i];
        }
        return "📦";
    }

    /** Instance convenience method */
    public String getTypeIcon() {
        return getTypeIcon(this.type);
    }

    public static int getTypeIndex(String type) {
        for (int i = 0; i < WALLET_TYPES.length; i++) {
            if (WALLET_TYPES[i].equals(type)) return i;
        }
        return WALLET_TYPES.length - 1;
    }

    /** Display string like "HDFC •••• 4523" or just "GPay" */
    public String getDisplayLabel() {
        StringBuilder sb = new StringBuilder();
        if (bankOrServiceName != null && !bankOrServiceName.isEmpty()) {
            sb.append(bankOrServiceName);
        }
        if (accountNumberLastFour != null && !accountNumberLastFour.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("•••• ").append(accountNumberLastFour);
        }
        return sb.length() > 0 ? sb.toString() : name;
    }

    // ─── JSON Serialization ──────────────────────────────────

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("name", name);
            obj.put("type", type);
            obj.put("bankOrServiceName", bankOrServiceName != null ? bankOrServiceName : "");
            obj.put("accountNumberLastFour", accountNumberLastFour != null ? accountNumberLastFour : "");
            obj.put("currentBalance", currentBalance);
            obj.put("currency", currency);
            obj.put("colorHex", colorHex);
            obj.put("iconIdentifier", iconIdentifier != null ? iconIdentifier : "💵");
            obj.put("isDefault", isDefault);
            obj.put("includeInTotalBalance", includeInTotalBalance);
            obj.put("creditLimit", creditLimit);
            obj.put("billingCycleDate", billingCycleDate);
            obj.put("notes", notes != null ? notes : "");
            obj.put("isArchived", isArchived);
            obj.put("displayOrder", displayOrder);
            obj.put("createdAt", createdAt);
            obj.put("updatedAt", updatedAt);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static Wallet fromJson(JSONObject obj) {
        Wallet w = new Wallet();
        try {
            w.id = obj.getString("id");
            w.name = obj.getString("name");
            w.type = obj.optString("type", TYPE_CASH);
            w.bankOrServiceName = obj.optString("bankOrServiceName", "");
            w.accountNumberLastFour = obj.optString("accountNumberLastFour", "");
            w.currentBalance = obj.optDouble("currentBalance", 0);
            w.currency = obj.optString("currency", "₹");
            w.colorHex = obj.optInt("colorHex", WALLET_COLORS[0]);
            w.iconIdentifier = obj.optString("iconIdentifier", "💵");
            w.isDefault = obj.optBoolean("isDefault", false);
            w.includeInTotalBalance = obj.optBoolean("includeInTotalBalance", true);
            w.creditLimit = obj.optDouble("creditLimit", 0);
            w.billingCycleDate = obj.optInt("billingCycleDate", 0);
            w.notes = obj.optString("notes", "");
            w.isArchived = obj.optBoolean("isArchived", false);
            w.displayOrder = obj.optInt("displayOrder", 0);
            w.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
            w.updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return w;
    }
}
