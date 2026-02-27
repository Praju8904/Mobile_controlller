package com.prajwal.myfirstapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Handles schema migration for the wallet feature.
 * 
 * Migration v1 → v2:
 *   1. Creates the "Default Wallet" in wallet_prefs
 *   2. Stamps every existing Expense with walletId = "default_wallet"
 *   3. Stamps every existing RecurringExpense with walletId = "default_wallet"
 *   4. Marks migration complete so it never runs again
 *
 * This is idempotent — calling it multiple times is safe.
 * Must be called BEFORE any repo operations on app start.
 */
public class ExpenseMigrationManager {

    private static final String TAG = "ExpenseMigration";
    private static final String MIGRATION_PREFS = "expense_migration_prefs";
    private static final String SCHEMA_VERSION_KEY = "schema_version";

    // Current schema version — increment when adding new migrations
    private static final int CURRENT_VERSION = 2;
    // v1 = original (no walletId)
    // v2 = wallet migration (walletId added to Expense + RecurringExpense)

    private final Context context;

    public ExpenseMigrationManager(Context context) {
        this.context = context;
    }

    /**
     * Run all pending migrations. Safe to call every app start.
     * Returns true if any migrations were performed.
     */
    public boolean runMigrations() {
        int currentVersion = getCurrentSchemaVersion();

        if (currentVersion >= CURRENT_VERSION) {
            Log.d(TAG, "Schema up to date (v" + currentVersion + ")");
            return false;
        }

        Log.i(TAG, "Migrating from v" + currentVersion + " to v" + CURRENT_VERSION);
        boolean migrated = false;

        try {
            if (currentVersion < 2) {
                migrateV1toV2();
                migrated = true;
            }

            // Future migrations would be chained here:
            // if (currentVersion < 3) { migrateV2toV3(); }

            setSchemaVersion(CURRENT_VERSION);
            Log.i(TAG, "Migration complete — now at v" + CURRENT_VERSION);
        } catch (Exception e) {
            Log.e(TAG, "Migration FAILED", e);
            // Do NOT update version on failure — will retry next launch
        }

        return migrated;
    }

    // ─── V1 → V2: Wallet Migration ──────────────────────────

    private void migrateV1toV2() {
        Log.i(TAG, "Running v1→v2 migration (wallets)");

        // Step 1: Create default wallet if it doesn't already exist
        createDefaultWalletIfNeeded();

        // Step 2: Add walletId to all existing expenses
        stampExpensesWithDefaultWallet();

        // Step 3: Add walletId to all existing recurring expenses
        stampRecurringExpensesWithDefaultWallet();

        Log.i(TAG, "v1→v2 migration complete");
    }

    /**
     * Creates the default wallet in wallet_prefs if no wallets exist yet.
     */
    private void createDefaultWalletIfNeeded() {
        SharedPreferences walletPrefs = context.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE);
        String existing = walletPrefs.getString("wallets_data", "[]");

        try {
            JSONArray array = new JSONArray(existing);
            if (array.length() > 0) {
                Log.d(TAG, "Wallets already exist (" + array.length() + "), skipping default creation");
                return;
            }
        } catch (Exception e) {
            // Corrupt data — proceed with creation
        }

        // Create the default wallet
        Wallet defaultWallet = Wallet.createDefaultWallet();

        // Calculate initial balance from existing expenses
        double initialBalance = calculateExistingNetBalance();
        defaultWallet.currentBalance = initialBalance;

        JSONArray walletArray = new JSONArray();
        walletArray.put(defaultWallet.toJson());
        walletPrefs.edit().putString("wallets_data", walletArray.toString()).apply();

        Log.i(TAG, "Created default wallet with balance: " + initialBalance);
    }

    /**
     * Calculate net balance from all existing expenses (income - expenses).
     */
    private double calculateExistingNetBalance() {
        SharedPreferences expensePrefs = context.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE);
        String json = expensePrefs.getString("expenses_data", "[]");

        double net = 0;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                double amount = obj.optDouble("amount", 0);
                boolean isIncome = obj.optBoolean("isIncome", false);
                if (isIncome) {
                    net += amount;
                } else {
                    net -= amount;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating existing balance", e);
        }
        return net;
    }

    /**
     * Add walletId = "default_wallet" to every expense that doesn't have one.
     * Operates directly on the raw JSON to be maximally safe.
     */
    private void stampExpensesWithDefaultWallet() {
        SharedPreferences prefs = context.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE);
        String json = prefs.getString("expenses_data", "[]");

        try {
            JSONArray array = new JSONArray(json);
            int stamped = 0;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (!obj.has("walletId") || obj.optString("walletId", "").isEmpty()) {
                    obj.put("walletId", Wallet.DEFAULT_WALLET_ID);
                    stamped++;
                }
            }

            if (stamped > 0) {
                prefs.edit().putString("expenses_data", array.toString()).apply();
                Log.i(TAG, "Stamped " + stamped + " expenses with default walletId");
            } else {
                Log.d(TAG, "All expenses already have walletId");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stamping expenses", e);
        }
    }

    /**
     * Add walletId = "default_wallet" to every recurring expense that doesn't have one.
     * Separate from the existing walletOrPaymentMethod field (which is free text).
     */
    private void stampRecurringExpensesWithDefaultWallet() {
        SharedPreferences prefs = context.getSharedPreferences("recurring_expense_prefs", Context.MODE_PRIVATE);
        String json = prefs.getString("recurring_expenses_data", "[]");

        try {
            JSONArray array = new JSONArray(json);
            int stamped = 0;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (!obj.has("walletId") || obj.optString("walletId", "").isEmpty()) {
                    obj.put("walletId", Wallet.DEFAULT_WALLET_ID);
                    stamped++;
                }
            }

            if (stamped > 0) {
                prefs.edit().putString("recurring_expenses_data", array.toString()).apply();
                Log.i(TAG, "Stamped " + stamped + " recurring expenses with default walletId");
            } else {
                Log.d(TAG, "All recurring expenses already have walletId");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stamping recurring expenses", e);
        }
    }

    // ─── Version Management ──────────────────────────────────

    public int getCurrentSchemaVersion() {
        return getMigrationPrefs().getInt(SCHEMA_VERSION_KEY, 1);
    }

    private void setSchemaVersion(int version) {
        getMigrationPrefs().edit().putInt(SCHEMA_VERSION_KEY, version).apply();
    }

    /**
     * Check if the wallet migration has been completed.
     */
    public boolean isWalletMigrationDone() {
        return getCurrentSchemaVersion() >= 2;
    }

    /**
     * Force re-run the wallet migration. USE ONLY FOR DEBUGGING.
     */
    public void resetMigration() {
        getMigrationPrefs().edit().putInt(SCHEMA_VERSION_KEY, 1).apply();
    }

    private SharedPreferences getMigrationPrefs() {
        return context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE);
    }
}
