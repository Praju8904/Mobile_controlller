package com.prajwal.myfirstapp.expenses;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Repository for wallet CRUD, balance tracking, daily snapshots, and transfer operations.
 */
public class WalletRepository {

    private static final String PREFS_NAME = "wallet_prefs";
    private static final String DATA_KEY = "wallets_data";
    private static final String TRANSFERS_KEY = "wallet_transfers_data";
    private static final String SNAPSHOTS_KEY = "balance_snapshots_data";
    private static final String BALANCE_HIDDEN_KEY = "balance_hidden";

    private final Context context;

    public WalletRepository(Context context) {
        this.context = context;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Wallet CRUD ─────────────────────────────────────────

    public synchronized void saveAll(ArrayList<Wallet> wallets) {
        JSONArray array = new JSONArray();
        for (Wallet w : wallets) array.put(w.toJson());
        getPrefs().edit().putString(DATA_KEY, array.toString()).apply();
    }

    public ArrayList<Wallet> loadAll() {
        ArrayList<Wallet> wallets = new ArrayList<>();
        String json = getPrefs().getString(DATA_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                wallets.add(Wallet.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Sort by displayOrder, then createdAt
        Collections.sort(wallets, (a, b) -> {
            if (a.displayOrder != b.displayOrder) return a.displayOrder - b.displayOrder;
            return Long.compare(a.createdAt, b.createdAt);
        });
        return wallets;
    }

    public void addWallet(Wallet wallet) {
        ArrayList<Wallet> all = loadAll();
        // If setting as default, clear other defaults
        if (wallet.isDefault) {
            for (Wallet w : all) w.isDefault = false;
        }
        // If first wallet, force as default
        if (all.isEmpty()) {
            wallet.isDefault = true;
        }
        wallet.displayOrder = all.size();
        all.add(wallet);
        saveAll(all);
    }

    public void updateWallet(Wallet wallet) {
        ArrayList<Wallet> all = loadAll();
        if (wallet.isDefault) {
            for (Wallet w : all) w.isDefault = false;
        }
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(wallet.id)) {
                all.set(i, wallet);
                break;
            }
        }
        saveAll(all);
    }

    public void deleteWallet(String walletId) {
        ArrayList<Wallet> all = loadAll();
        all.removeIf(w -> w.id.equals(walletId));
        saveAll(all);
    }

    public Wallet getById(String walletId) {
        if (walletId == null) return getDefaultWallet();
        for (Wallet w : loadAll()) {
            if (w.id.equals(walletId)) return w;
        }
        return getDefaultWallet();
    }

    public Wallet getDefaultWallet() {
        for (Wallet w : loadAll()) {
            if (w.isDefault) return w;
        }
        // If no default exists, return first
        ArrayList<Wallet> all = loadAll();
        return all.isEmpty() ? null : all.get(0);
    }

    public void setDefault(String walletId) {
        ArrayList<Wallet> all = loadAll();
        for (Wallet w : all) {
            w.isDefault = w.id.equals(walletId);
        }
        saveAll(all);
    }

    public void archiveWallet(String walletId) {
        ArrayList<Wallet> all = loadAll();
        for (Wallet w : all) {
            if (w.id.equals(walletId)) {
                w.isArchived = true;
                w.updatedAt = System.currentTimeMillis();
                break;
            }
        }
        saveAll(all);
    }

    public void unarchiveWallet(String walletId) {
        ArrayList<Wallet> all = loadAll();
        for (Wallet w : all) {
            if (w.id.equals(walletId)) {
                w.isArchived = false;
                w.updatedAt = System.currentTimeMillis();
                break;
            }
        }
        saveAll(all);
    }

    // ─── Active / Archived ───────────────────────────────────

    public ArrayList<Wallet> getActiveWallets() {
        ArrayList<Wallet> result = new ArrayList<>();
        for (Wallet w : loadAll()) {
            if (!w.isArchived) result.add(w);
        }
        return result;
    }

    public ArrayList<Wallet> getArchivedWallets() {
        ArrayList<Wallet> result = new ArrayList<>();
        for (Wallet w : loadAll()) {
            if (w.isArchived) result.add(w);
        }
        return result;
    }

    public boolean hasWallets() {
        return !loadAll().isEmpty();
    }

    public int getActiveCount() {
        int count = 0;
        for (Wallet w : loadAll()) if (!w.isArchived) count++;
        return count;
    }

    // ─── Balance Operations ──────────────────────────────────

    /**
     * Update wallet balance: deduct for expense, add for income.
     */
    public void adjustBalance(String walletId, double amount, boolean isIncome) {
        ArrayList<Wallet> all = loadAll();
        for (Wallet w : all) {
            if (w.id.equals(walletId)) {
                if (w.isCreditCard()) {
                    // Credit card: expense increases debt (balance), income decreases debt
                    if (isIncome) {
                        w.currentBalance -= amount; // Payment reduces debt
                    } else {
                        w.currentBalance += amount; // Purchase increases debt
                    }
                } else {
                    // Normal wallet: expense decreases balance, income increases
                    if (isIncome) {
                        w.currentBalance += amount;
                    } else {
                        w.currentBalance -= amount;
                    }
                }
                w.updatedAt = System.currentTimeMillis();
                break;
            }
        }
        saveAll(all);
    }

    /**
     * Reverse a balance change (e.g. when deleting an expense).
     */
    public void reverseBalanceAdjustment(String walletId, double amount, boolean wasIncome) {
        adjustBalance(walletId, amount, !wasIncome);
    }

    /**
     * Total balance across all wallets with includeInTotalBalance=true (excluding archived).
     */
    public double getTotalBalance() {
        double total = 0;
        for (Wallet w : loadAll()) {
            if (!w.isArchived && w.includeInTotalBalance) {
                if (w.isCreditCard()) {
                    total -= w.currentBalance; // Credit card debt is negative
                } else {
                    total += w.currentBalance;
                }
            }
        }
        return total;
    }

    /**
     * Get balance breakdown by wallet type: type → total balance.
     */
    public Map<String, Double> getBalanceByType() {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        for (Wallet w : loadAll()) {
            if (w.isArchived) continue;
            double bal = w.isCreditCard() ? -w.currentBalance : w.currentBalance;
            breakdown.put(w.type, breakdown.getOrDefault(w.type, 0.0) + bal);
        }
        return breakdown;
    }

    /**
     * Get balance distribution for pie chart: wallet name → balance.
     */
    public Map<String, Double> getBalanceDistribution() {
        Map<String, Double> dist = new LinkedHashMap<>();
        for (Wallet w : getActiveWallets()) {
            if (w.includeInTotalBalance) {
                double bal = w.isCreditCard() ? 0 : w.currentBalance; // Don't show debt in pie
                if (bal > 0) {
                    dist.put(w.name, bal);
                }
            }
        }
        return dist;
    }

    // ─── Wallet Transfers ────────────────────────────────────

    public void saveTransfers(ArrayList<WalletTransfer> transfers) {
        JSONArray array = new JSONArray();
        for (WalletTransfer t : transfers) array.put(t.toJson());
        getPrefs().edit().putString(TRANSFERS_KEY, array.toString()).apply();
    }

    public ArrayList<WalletTransfer> loadTransfers() {
        ArrayList<WalletTransfer> transfers = new ArrayList<>();
        String json = getPrefs().getString(TRANSFERS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                transfers.add(WalletTransfer.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Sort newest first
        Collections.sort(transfers, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        return transfers;
    }

    /**
     * Execute a transfer between two wallets.
     * Creates a WalletTransfer record and adjusts both balances.
     */
    public WalletTransfer executeTransfer(String fromWalletId, String toWalletId,
                                           double amount, String notes) {
        WalletTransfer transfer = new WalletTransfer();
        transfer.fromWalletId = fromWalletId;
        transfer.toWalletId = toWalletId;
        transfer.amount = amount;
        transfer.notes = notes;

        // Get wallet names for display
        Wallet from = getById(fromWalletId);
        Wallet to = getById(toWalletId);
        if (from != null) transfer.fromWalletName = from.name;
        if (to != null) transfer.toWalletName = to.name;

        // Adjust balances
        ArrayList<Wallet> all = loadAll();
        for (Wallet w : all) {
            if (w.id.equals(fromWalletId)) {
                if (w.isCreditCard()) {
                    // Paying a credit card bill FROM a bank means the bank balance goes down
                    // But if transferring FROM credit card... that's unusual. Handle generically.
                    w.currentBalance += amount; // Increases debt
                } else {
                    w.currentBalance -= amount;
                }
                w.updatedAt = System.currentTimeMillis();
            }
            if (w.id.equals(toWalletId)) {
                if (w.isCreditCard()) {
                    w.currentBalance -= amount; // Payment reduces debt
                } else {
                    w.currentBalance += amount;
                }
                w.updatedAt = System.currentTimeMillis();
            }
        }
        saveAll(all);

        // Save transfer record
        ArrayList<WalletTransfer> transfers = loadTransfers();
        transfers.add(0, transfer);
        saveTransfers(transfers);

        return transfer;
    }

    /**
     * Get transfers for a specific wallet (both from and to).
     */
    public ArrayList<WalletTransfer> getTransfersForWallet(String walletId) {
        ArrayList<WalletTransfer> result = new ArrayList<>();
        for (WalletTransfer t : loadTransfers()) {
            if (walletId.equals(t.fromWalletId) || walletId.equals(t.toWalletId)) {
                result.add(t);
            }
        }
        return result;
    }

    // ─── Per-Wallet Expense Queries ──────────────────────────

    /**
     * Get total income for a wallet this month.
     */
    public double getWalletIncomeThisMonth(String walletId, ExpenseRepository expenseRepo) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();

        double total = 0;
        for (Expense e : expenseRepo.loadAll()) {
            if (e.isIncome && walletId.equals(e.walletId) && e.timestamp >= monthStart) {
                total += e.amount;
            }
        }
        return total;
    }

    /**
     * Get total expenses for a wallet this month.
     */
    public double getWalletExpensesThisMonth(String walletId, ExpenseRepository expenseRepo) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();

        double total = 0;
        for (Expense e : expenseRepo.loadAll()) {
            if (!e.isIncome && walletId.equals(e.walletId) && e.timestamp >= monthStart) {
                total += e.amount;
            }
        }
        return total;
    }

    /**
     * Get all expenses for a wallet, filtered.
     */
    public ArrayList<Expense> getWalletExpenses(String walletId, ExpenseRepository expenseRepo,
                                                 String filter) {
        ArrayList<Expense> result = new ArrayList<>();
        for (Expense e : expenseRepo.loadAll()) {
            if (!walletId.equals(e.walletId)) continue;

            if ("All".equals(filter)) {
                result.add(e);
            } else if ("Income".equals(filter)) {
                if (e.isIncome) result.add(e);
            } else if ("Expenses".equals(filter)) {
                if (!e.isIncome) result.add(e);
            } else {
                // Category filter
                if (e.category.equals(filter)) result.add(e);
            }
        }
        return result;
    }

    /**
     * Get monthly net change for a wallet over last N months (for chart).
     */
    public double[] getWalletMonthlyTrend(String walletId, int months,
                                           ExpenseRepository expenseRepo) {
        double[] trend = new double[months];
        Calendar cal = Calendar.getInstance();

        for (int m = 0; m < months; m++) {
            Calendar monthStart = (Calendar) cal.clone();
            monthStart.add(Calendar.MONTH, -(months - 1 - m));
            monthStart.set(Calendar.DAY_OF_MONTH, 1);
            monthStart.set(Calendar.HOUR_OF_DAY, 0);
            monthStart.set(Calendar.MINUTE, 0);
            monthStart.set(Calendar.SECOND, 0);
            monthStart.set(Calendar.MILLISECOND, 0);

            Calendar monthEnd = (Calendar) monthStart.clone();
            monthEnd.add(Calendar.MONTH, 1);

            double income = 0, expenses = 0;
            for (Expense e : expenseRepo.loadAll()) {
                if (!walletId.equals(e.walletId)) continue;
                if (e.timestamp >= monthStart.getTimeInMillis() && e.timestamp < monthEnd.getTimeInMillis()) {
                    if (e.isIncome) income += e.amount;
                    else expenses += e.amount;
                }
            }
            trend[m] = income - expenses;
        }
        return trend;
    }

    // ─── Quick Stats (All Wallets) ───────────────────────────

    public double getTotalIncomeThisMonth(ExpenseRepository expenseRepo) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();

        double total = 0;
        for (Expense e : expenseRepo.loadAll()) {
            if (e.isIncome && e.timestamp >= monthStart) total += e.amount;
        }
        return total;
    }

    public double getTotalExpensesThisMonth(ExpenseRepository expenseRepo) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();

        double total = 0;
        for (Expense e : expenseRepo.loadAll()) {
            if (!e.isIncome && e.timestamp >= monthStart) total += e.amount;
        }
        return total;
    }

    // ─── Daily Balance Snapshots ─────────────────────────────

    /**
     * Store today's total balance as a snapshot for net worth trending.
     * Called on app open. Idempotent — only stores once per day.
     */
    public void takeDailySnapshot() {
        Map<String, Double> snapshots = loadSnapshots();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (!snapshots.containsKey(today)) {
            snapshots.put(today, getTotalBalance());
            saveSnapshots(snapshots);
        }
    }

    /**
     * Get net worth trend over last N months (monthly average snapshots).
     */
    public double[] getNetWorthTrend(int months) {
        double[] trend = new double[months];
        Map<String, Double> snapshots = loadSnapshots();
        Calendar cal = Calendar.getInstance();

        for (int m = 0; m < months; m++) {
            Calendar monthCal = (Calendar) cal.clone();
            monthCal.add(Calendar.MONTH, -(months - 1 - m));
            String prefix = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                .format(monthCal.getTime());

            double sum = 0;
            int count = 0;
            for (Map.Entry<String, Double> entry : snapshots.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    sum += entry.getValue();
                    count++;
                }
            }
            trend[m] = count > 0 ? sum / count : (m > 0 ? trend[m - 1] : getTotalBalance());
        }
        return trend;
    }

    private void saveSnapshots(Map<String, Double> snapshots) {
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, Double> entry : snapshots.entrySet()) {
                obj.put(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        getPrefs().edit().putString(SNAPSHOTS_KEY, obj.toString()).apply();
    }

    private Map<String, Double> loadSnapshots() {
        Map<String, Double> snapshots = new LinkedHashMap<>();
        String json = getPrefs().getString(SNAPSHOTS_KEY, "{}");
        try {
            JSONObject obj = new JSONObject(json);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                snapshots.put(key, obj.getDouble(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return snapshots;
    }

    // ─── Balance Visibility Toggle ───────────────────────────

    public boolean isBalanceHidden() {
        return getPrefs().getBoolean(BALANCE_HIDDEN_KEY, false);
    }

    public void setBalanceHidden(boolean hidden) {
        getPrefs().edit().putBoolean(BALANCE_HIDDEN_KEY, hidden).apply();
    }

    // ─── Credit Card Specific ────────────────────────────────

    /**
     * Get credit card statement balance (expenses since last billing date).
     */
    public double getCreditCardStatementBalance(String walletId, ExpenseRepository expenseRepo) {
        Wallet wallet = getById(walletId);
        if (wallet == null || !wallet.isCreditCard() || wallet.billingCycleDate <= 0) {
            return wallet != null ? wallet.currentBalance : 0;
        }

        // Find last billing date
        Calendar lastBilling = Calendar.getInstance();
        int currentDay = lastBilling.get(Calendar.DAY_OF_MONTH);
        lastBilling.set(Calendar.DAY_OF_MONTH,
            Math.min(wallet.billingCycleDate, lastBilling.getActualMaximum(Calendar.DAY_OF_MONTH)));
        lastBilling.set(Calendar.HOUR_OF_DAY, 0);
        lastBilling.set(Calendar.MINUTE, 0);
        lastBilling.set(Calendar.SECOND, 0);
        lastBilling.set(Calendar.MILLISECOND, 0);

        if (lastBilling.getTimeInMillis() > System.currentTimeMillis()) {
            lastBilling.add(Calendar.MONTH, -1);
        }

        long billingStart = lastBilling.getTimeInMillis();
        double statement = 0;
        for (Expense e : expenseRepo.loadAll()) {
            if (walletId.equals(e.walletId) && !e.isIncome && e.timestamp >= billingStart) {
                statement += e.amount;
            }
        }
        return statement;
    }

    /**
     * Mark credit card bill as paid — creates a transfer from linked bank to this CC.
     * Returns the transfer record, or null if no suitable bank found.
     */
    public WalletTransfer markCreditCardBillPaid(String creditCardWalletId,
                                                  String payFromWalletId, double amount) {
        return executeTransfer(payFromWalletId, creditCardWalletId, amount,
            "Credit card bill payment");
    }

    // ─── Credit Cards with upcoming billing ──────────────────

    public ArrayList<Wallet> getCreditCardsWithUpcomingBilling(int withinDays) {
        ArrayList<Wallet> result = new ArrayList<>();
        for (Wallet w : getActiveWallets()) {
            if (w.isCreditCard() && w.billingCycleDate > 0) {
                int daysUntil = w.getDaysUntilBilling();
                if (daysUntil >= 0 && daysUntil <= withinDays) {
                    result.add(w);
                }
            }
        }
        return result;
    }
}
